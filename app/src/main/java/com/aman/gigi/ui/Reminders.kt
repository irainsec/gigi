package com.aman.gigi.ui

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.ZoneOffset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aman.gigi.viewmodel.ScreensaverViewModel
import com.aman.gigi.R
import com.aman.gigi.alarm.AlarmUtils
import com.aman.gigi.data.client.ClientIdentityStore
import com.aman.gigi.data.client.ConnectionBootstrapManager
import com.aman.gigi.db.ReminderRepository
import com.aman.gigi.model.ConnectionRole
import com.aman.gigi.model.RecurrencePattern
import com.aman.gigi.model.Reminder
import com.aman.gigi.model.SharedAlarmMirror
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.repository.SharedAlarmRepository
import com.aman.gigi.utils.Utils
import com.skydoves.cloudy.Cloudy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ==========================================
// 1. VIEW MODEL
// ==========================================

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val repository: ReminderRepository,
    private val sharedAlarmRepository: SharedAlarmRepository,
    private val connectionRepository: ConnectionRepository,
    private val bootstrapManager: ConnectionBootstrapManager,
    private val identityStore: ClientIdentityStore
) : AndroidViewModel(application) {

    val reminderEntries: StateFlow<List<Reminder>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val memberIdentity: StateFlow<com.aman.gigi.model.MemberIdentity?> = bootstrapManager.memberIdentity
    val serverStatus: StateFlow<com.aman.gigi.model.ServerStatus> = bootstrapManager.serverStatus

    // Set when the server rejects an alarm save with PLAN_LIMIT_REACHED (the local
    // gate can be stale); the UI shows the upgrade sheet while non-null.
    private val _planLimitMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val planLimitMessage: StateFlow<String?> = _planLimitMessage

    fun clearPlanLimitMessage() {
        _planLimitMessage.value = null
    }

    val activeConnections = connectionRepository.getActiveConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedSharedAlarmConnectionId = identityStore.selectedAlarmConnectionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sharedAlarmEntries = sharedAlarmRepository.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleSharedAlarms = combine(
        sharedAlarmEntries,
        activeConnections,
        selectedSharedAlarmConnectionId
    ) { alarms, connections, selectedConnectionId ->
        if (connections.isNotEmpty()) {
            val effectiveId = selectedConnectionId
                ?.takeIf { selected -> connections.any { it.connectionId == selected } }
                ?: connections.first().connectionId
            alarms.filter { it.connectionId == effectiveId }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(activeConnections, selectedSharedAlarmConnectionId) { connections, selected ->
                connections to selected
            }.collect { (connections, selected) ->
                val effectiveSelected = when {
                    connections.isEmpty() -> null
                    selected != null && connections.any { it.connectionId == selected } -> selected
                    else -> connections.first().connectionId
                }

                if (effectiveSelected != selected) {
                    bootstrapManager.saveSelectedAlarmConnectionId(effectiveSelected)
                }
                sharedAlarmRepository.refreshSchedules(connections, effectiveSelected)
            }
        }
    }

    fun addNewReminder(reminder: Reminder) {
        viewModelScope.launch {
            val _id = insert(reminder)
            reminder._id = _id
            AlarmUtils.scheduleAlarm(context = application.applicationContext, reminder = reminder)
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            AlarmUtils.cancelAlarm(context = application.applicationContext, reminder = reminder)
            AlarmUtils.scheduleAlarm(context = application.applicationContext, reminder = reminder)
            update(reminder)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            AlarmUtils.cancelAlarm(context = application.applicationContext, reminder = reminder)
            delete(reminder)
        }
    }

    fun selectSharedAlarmConnection(connectionId: String) {
        viewModelScope.launch {
            bootstrapManager.saveSelectedAlarmConnectionId(connectionId)
            sharedAlarmRepository.refreshSchedules(
                activeConnections = activeConnections.value,
                selectedCreatorConnectionId = connectionId
            )
        }
    }

    fun saveSharedAlarm(
        existingAlarm: SharedAlarmMirror?,
        connectionId: String,
        title: String,
        description: String?,
        dueDate: Long,
        recurrencePattern: RecurrencePattern?,
        customIntervalMillis: Long?,
        startH: Int?,
        startM: Int?,
        endH: Int?,
        endM: Int?,
        emoji: String = "??"
    ) {
        val identity = memberIdentity.value ?: return
        if (identity.authToken.isBlank()) return

        viewModelScope.launch {
            val alarm = SharedAlarmMirror(
                alarmId = existingAlarm?.alarmId ?: UUID.randomUUID().toString(),
                connectionId = connectionId,
                title = title,
                note = description,
                dueAt = dueDate,
                recurrencePattern = recurrencePattern?.name,
                customIntervalMillis = customIntervalMillis,
                repeatStartHour = startH,
                repeatStartMinute = startM,
                repeatEndHour = endH,
                repeatEndMinute = endM,
                ownerMemberId = identity.memberId,
                ownerDisplayName = identity.displayName,
                isActive = true,
                updatedAt = System.currentTimeMillis()
            )

            val mirrored = runCatching {
                sharedAlarmRepository.upsertRemoteAlarm(identity.authToken, alarm)
            }.onFailure { error ->
                android.util.Log.e("MainViewModel", "Failed to upsert shared alarm remotely for $connectionId", error)
                if (error is com.aman.gigi.repository.PlanLimitException) {
                    _planLimitMessage.value = error.message
                }
                bootstrapManager.refreshFromServer("shared_alarm_upsert_failed")
            }.getOrNull()

            mirrored?.let {
                sharedAlarmRepository.applyRemoteUpsert(
                    it,
                    activeConnections = activeConnections.value,
                    selectedCreatorConnectionId = selectedSharedAlarmConnectionId.value
                )
                bootstrapManager.refreshFromServer("shared_alarm_upserted")
            }
        }
    }

    fun deleteSharedAlarm(alarm: SharedAlarmMirror) {
        val identity = memberIdentity.value ?: return
        if (identity.authToken.isBlank()) return

        viewModelScope.launch {
            val deleted = runCatching {
                sharedAlarmRepository.deleteRemoteAlarm(
                    authToken = identity.authToken,
                    connectionId = alarm.connectionId,
                    alarmId = alarm.alarmId
                )
            }.onFailure {
                bootstrapManager.refreshFromServer("shared_alarm_delete_failed")
            }.getOrDefault(false)

            if (deleted) {
                sharedAlarmRepository.applyRemoteDelete(
                    alarmId = alarm.alarmId,
                    activeConnections = activeConnections.value,
                    selectedCreatorConnectionId = selectedSharedAlarmConnectionId.value
                )
                bootstrapManager.refreshFromServer("shared_alarm_deleted")
            }
        }
    }

    private suspend fun insert(reminder: Reminder): Long {
        return repository.insert(reminder)
    }

    private suspend fun update(reminder: Reminder) {
        repository.update(reminder)
    }

    private suspend fun delete(reminder: Reminder) {
        repository.delete(reminder)
    }
}

// ==========================================
// 2. MAIN LIST SCREEN
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Reminders(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val reminders by viewModel.reminderEntries.collectAsStateWithLifecycle()
    val memberIdentity by viewModel.memberIdentity.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val activeConnections by viewModel.activeConnections.collectAsStateWithLifecycle()
    val selectedSharedAlarmConnectionId by viewModel.selectedSharedAlarmConnectionId.collectAsStateWithLifecycle()
    val visibleSharedAlarms by viewModel.visibleSharedAlarms.collectAsStateWithLifecycle()
    val screensaverViewModel: ScreensaverViewModel = hiltViewModel()

    var showAddReminderDialog by remember { mutableStateOf(false) }
    var dialogReminderItem by remember { mutableStateOf<Reminder?>(null) }
    var showSharedAlarmDialog by remember { mutableStateOf(false) }
    var editingSharedAlarm by remember { mutableStateOf<SharedAlarmMirror?>(null) }
    var showUpgradeSheet by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val pagerScope = rememberCoroutineScope()

    // Sync immersive mode with bottom nav
    LaunchedEffect(showAddReminderDialog) {
        screensaverViewModel.setComposerMode(showAddReminderDialog)
    }

    val creatorAlarmConnections = remember(activeConnections) {
        activeConnections.filter { it.role.equals(ConnectionRole.CREATOR.name, ignoreCase = true) }
    }
    val partnerAlarmConnections = remember(activeConnections, creatorAlarmConnections) {
        if (creatorAlarmConnections.isNotEmpty()) creatorAlarmConnections else activeConnections
    }
    val effectiveSharedAlarmConnection = remember(partnerAlarmConnections, selectedSharedAlarmConnectionId) {
        when {
            partnerAlarmConnections.isEmpty() -> null
            selectedSharedAlarmConnectionId != null -> {
                partnerAlarmConnections.firstOrNull { it.connectionId == selectedSharedAlarmConnectionId }
                    ?: partnerAlarmConnections.first()
            }
            else -> partnerAlarmConnections.first()
        }
    }
    val resolvedSharedAlarmConnection = effectiveSharedAlarmConnection ?: partnerAlarmConnections.firstOrNull()

    val personalRemindersContent = @Composable {
        Box(modifier = Modifier.fillMaxSize()) {
            if (reminders.isEmpty()) {
                EmptyRemindersView()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = 64.dp, 
                        start = 16.dp, 
                        end = 16.dp, 
                        bottom = 120.dp // Space for floating nav bar
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = "Your Reminders",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6),
                            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                        )
                    }
                    items(
                        items = reminders,
                        key = { it._id }
                    ) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onClick = {
                                dialogReminderItem = reminder
                                showAddReminderDialog = true
                            },
                            onDelete = { viewModel.deleteReminder(reminder) }
                        )
                    }
                }
            }

            // FAB
            if (!showAddReminderDialog) {
                FloatingActionButton(
                    onClick = {
                        val plan = com.aman.gigi.utils.AppConfig.userPlan
                        val currentCount = reminders.size
                        if (plan.maxReminders > 0 && currentCount >= plan.maxReminders && !plan.isPro) {
                            showUpgradeSheet = true
                        } else {
                            dialogReminderItem = null
                            showAddReminderDialog = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 120.dp, end = 24.dp), 
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Animated flowers, hearts, and sparkles
        com.aman.gigi.ui.components.RomanceAmbientDecor(
            modifier = Modifier.fillMaxSize(),
            darkTheme = false
        )
        val content = @Composable {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(48.dp))
                TabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { pagerScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Personal") },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { pagerScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Partner") },
                        icon = { Icon(Icons.Default.People, contentDescription = null) }
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> personalRemindersContent()
                        else -> PartnerAlarmSection(
                            memberIdentity = memberIdentity,
                            serverStatus = serverStatus,
                            activeConnections = partnerAlarmConnections,
                            creatorConnections = creatorAlarmConnections,
                            selectedConnection = resolvedSharedAlarmConnection,
                            sharedAlarms = visibleSharedAlarms,
                            onSelectConnection = viewModel::selectSharedAlarmConnection,
                            onAddAlarm = {
                                editingSharedAlarm = null
                                showSharedAlarmDialog = true
                            },
                            onEditAlarm = {
                                editingSharedAlarm = it
                                showSharedAlarmDialog = true
                            },
                            onDeleteAlarm = viewModel::deleteSharedAlarm
                        )
                    }
                }
            }
        }

        if (showAddReminderDialog || showSharedAlarmDialog) {
            Cloudy(radius = 20) { content() }
        } else {
            content()
        }

        // Add/Edit Dialog Overlay (On top of everything)
        if (showAddReminderDialog) {
            Dialog(
                onDismissRequest = { showAddReminderDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { showAddReminderDialog = false },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(top = 60.dp, bottom = 120.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) {}
                            .clip(RoundedCornerShape(32.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                    ) {
                        Cloudy(radius = 35) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = if (false) Color(0xFF1A1530).copy(alpha = 0.97f) else Color(0xFFE6E0FF).copy(alpha = 0.85f),
                            tonalElevation = 0.dp
                        ) {
                            AddReminder(
                                reminder = dialogReminderItem,
                                onDismiss = { showAddReminderDialog = false },
                                onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM, emoji ->
                                    val reminder = Reminder(
                                        title = title,
                                        description = description,
                                        dueDate = dueDate,
                                        recurrencePattern = recurrencePattern,
                                        customIntervalMillis = customIntervalMillis,
                                        repeatStartHour = startH,
                                        repeatStartMinute = startM,
                                        repeatEndHour = endH,
                                        repeatEndMinute = endM,
                                        emoji = emoji
                                    )

                                    if (dialogReminderItem == null)
                                        viewModel.addNewReminder(reminder = reminder)
                                    else {
                                        reminder._id = dialogReminderItem!!._id
                                        viewModel.updateReminder(reminder)
                                    }
                                    showAddReminderDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }

        val planLimitMessage by viewModel.planLimitMessage.collectAsStateWithLifecycle()
        if (showUpgradeSheet || planLimitMessage != null) {
            com.aman.gigi.ui.components.UpgradeSheet(
                featureName = "More Reminders",
                featureDescription = planLimitMessage ?: "Create unlimited reminders and recurring alarms.",
                onDismiss = {
                    showUpgradeSheet = false
                    viewModel.clearPlanLimitMessage()
                }
            )
        }

        if (showSharedAlarmDialog) {
            Dialog(
                onDismissRequest = { showSharedAlarmDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { showSharedAlarmDialog = false },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(top = 60.dp, bottom = 120.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) {}
                            .clip(RoundedCornerShape(32.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                    ) {
                        Cloudy(radius = 35) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = if (false) Color(0xFF1A1530).copy(alpha = 0.97f) else Color(0xFFE6E0FF).copy(alpha = 0.85f),
                            tonalElevation = 0.dp
                        ) {
                            AddReminder(
                                reminder = editingSharedAlarm?.asReminderStub(resolvedSharedAlarmConnection?.partnerName),
                                onDismiss = { showSharedAlarmDialog = false },
                                onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM, emoji ->
                                    resolvedSharedAlarmConnection?.let { connection ->
                                        viewModel.saveSharedAlarm(
                                            existingAlarm = editingSharedAlarm,
                                            connectionId = connection.connectionId,
                                            title = title,
                                            description = description,
                                            dueDate = dueDate,
                                            recurrencePattern = recurrencePattern,
                                            customIntervalMillis = customIntervalMillis,
                                            startH = startH,
                                            startM = startM,
                                            endH = endH,
                                            endM = endM,
                                            emoji = emoji
                                        )
                                        showSharedAlarmDialog = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyRemindersView(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val typography = MaterialTheme.typography

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(32.dp)
                .clip(shapes.extraLarge)
                .border(1.2.dp, Color.White.copy(alpha = 0.25f), shapes.extraLarge)
        ) {
            Cloudy(radius = 35) {
                Box(modifier = Modifier.matchParentSize().background(colorScheme.surface.copy(alpha = 0.1f)))
            }
            Surface(
                color = colorScheme.surface.copy(alpha = 0.45f),
                shape = shapes.extraLarge
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF6666FF),
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No Reminders Yet",
                        style = typography.headlineSmall,
                        color = Color.Black.copy(alpha = 0.8f),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the + button to create your first reminder",
                        style = typography.bodyLarge,
                        color = Color.Black.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerAlarmSection(
    memberIdentity: com.aman.gigi.model.MemberIdentity?,
    serverStatus: com.aman.gigi.model.ServerStatus,
    activeConnections: List<com.aman.gigi.model.Connection>,
    creatorConnections: List<com.aman.gigi.model.Connection>,
    selectedConnection: com.aman.gigi.model.Connection?,
    sharedAlarms: List<SharedAlarmMirror>,
    onSelectConnection: (String) -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (SharedAlarmMirror) -> Unit,
    onDeleteAlarm: (SharedAlarmMirror) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            memberIdentity == null -> {
                EmptyStateCard(
                    title = "Sign in to sync partner alarms",
                    message = "Partner alarms come from the server after phone login."
                )
            }
            serverStatus.mode == com.aman.gigi.model.ServerMode.MAINTENANCE -> {
                EmptyStateCard(
                    title = "Partner alarms paused",
                    message = serverStatus.message ?: "Server maintenance is on. Personal alarms still work."
                )
            }
            activeConnections.isEmpty() -> {
                EmptyStateCard(
                    title = "No partner links yet",
                    message = "Create or join a partner connection to share alarms together."
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = "Partner Alarms",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                    }

                    if (activeConnections.isNotEmpty()) {
                        item {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedConnection?.partnerName ?: "Choose a partner",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Alarm partner") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    activeConnections.forEach { connection ->
                                        DropdownMenuItem(
                                            text = { Text(connection.partnerName) },
                                            onClick = {
                                                expanded = false
                                                onSelectConnection(connection.connectionId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SafePartnerAlarmBanner(selectedConnection = selectedConnection)
                    }

                    if (sharedAlarms.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No shared alarms yet",
                                message = "Add one and both phones will ring at the same time."
                            )
                        }
                    } else {
                        items(sharedAlarms, key = { it.alarmId }) { alarm ->
                            SharedAlarmCard(
                                alarm = alarm,
                                onEdit = { onEditAlarm(alarm) },
                                onDelete = { onDeleteAlarm(alarm) }
                            )
                        }
                    }
                }
            }
        }

        if (memberIdentity != null && activeConnections.isNotEmpty() && serverStatus.mode == com.aman.gigi.model.ServerMode.ONLINE) {
            FloatingActionButton(
                onClick = onAddAlarm,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add shared alarm")
            }
        }
    }
}

@Composable
private fun SafePartnerAlarmBanner(selectedConnection: com.aman.gigi.model.Connection?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
    ) {
        Cloudy(radius = 18) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
        Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = selectedConnection?.partnerName?.let { "Ringing together with $it" } ?: "Pick a partner board",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Shared alarms sync from the server and ring on both phones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun SharedAlarmCard(
    alarm: SharedAlarmMirror,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dueDateTime = remember(alarm.dueAt) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(alarm.dueAt), ZoneId.systemDefault())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            .clickable(onClick = onEdit)
    ) {
        Cloudy(radius = 18) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
        Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alarm.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5E35B1)
                        )
                        Text(
                            text = "${Utils.formatDate(LocalContext.current, dueDateTime.toLocalDate())} • ${Utils.formatTime(LocalContext.current, dueDateTime.toLocalTime())}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete shared alarm", tint = Color(0xFFD32F2F))
                    }
                }

                alarm.note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.68f)
                    )
                }

                alarm.ownerDisplayName?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Set by $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF7E57C2),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Box(
        modifier = Modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
    ) {
        Cloudy(radius = 22) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
        Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5E35B1),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.58f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var dueDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(reminder.dueDate), ZoneId.systemDefault())

    if (reminder.recurrencePattern != null) {
        val interval = if (reminder.recurrencePattern == RecurrencePattern.CUSTOM) {
            reminder.customIntervalMillis ?: 0L
        } else {
            reminder.recurrencePattern.intervalMillis
        }

        if (interval > 0) {
            while (dueDateTime.isBefore(LocalDateTime.now())) {
                dueDateTime = dueDateTime.plus(interval, ChronoUnit.MILLIS)
                
                // Check time frame window
                if (reminder.repeatStartHour != null && reminder.repeatEndHour != null) {
                    val currentHour = dueDateTime.hour
                    val currentMinute = dueDateTime.minute
                    val startTimeMinutes = reminder.repeatStartHour * 60 + (reminder.repeatStartMinute ?: 0)
                    val endTimeMinutes = reminder.repeatEndHour * 60 + (reminder.repeatEndMinute ?: 0)
                    val currentTimeMinutes = currentHour * 60 + currentMinute
                    
                    if (currentTimeMinutes < startTimeMinutes) {
                        dueDateTime = dueDateTime.withHour(reminder.repeatStartHour).withMinute(reminder.repeatStartMinute ?: 0).withSecond(0).withNano(0)
                    } else if (currentTimeMinutes > endTimeMinutes) {
                        dueDateTime = dueDateTime.plusDays(1).withHour(reminder.repeatStartHour).withMinute(reminder.repeatStartMinute ?: 0).withSecond(0).withNano(0)
                    }
                }
            }
        }
    }

    val isOverdue = reminder.recurrencePattern == null && dueDateTime.isBefore(LocalDateTime.now())
    val colorScheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val typography = MaterialTheme.typography

    val cardColor = when {
        isOverdue -> colorScheme.errorContainer.copy(alpha = 0.4f)
        else -> colorScheme.surface.copy(alpha = 0.55f)
    }
    val borderColor = if (isOverdue) colorScheme.error.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shapes.extraLarge)
            .border(1.2.dp, borderColor, shapes.extraLarge)
    ) {
        Cloudy(radius = 20) {
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.15f)))
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.6f)),
            shape = shapes.extraLarge,
            border = null
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reminder.title,
                        style = typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isOverdue) colorScheme.error else colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                    }
                }

                if (!reminder.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = reminder.description,
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_calendar), null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Utils.parseMillisToDeviceTimeFormat(context, reminder.dueDate),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (reminder.recurrencePattern != null || !isOverdue) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (reminder.recurrencePattern != null) painterResource(R.drawable.ic_repeat) else painterResource(R.drawable.ic_alarm),
                                null, tint = colorScheme.secondary, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = Utils.parseMillisToDeviceTimeFormat(context, dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
                                style = typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (reminder.repeatStartHour != null && reminder.repeatEndHour != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = colorScheme.outline, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = String.format("Only between %02d:%02d - %02d:%02d", reminder.repeatStartHour, reminder.repeatStartMinute ?: 0, reminder.repeatEndHour, reminder.repeatEndMinute ?: 0),
                                style = typography.labelSmall,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. EDIT/ADD REMINDER SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminder(
    reminder: Reminder?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Long, RecurrencePattern?, Long?, Int?, Int?, Int?, Int?, String) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf<String?>(null) }
    var emoji by remember { mutableStateOf("??") }
    var dueDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var recurrencePattern by remember { mutableStateOf<RecurrencePattern?>(null) }
    var showRecurrenceMenu by remember { mutableStateOf(false) }

    var customIntervalQuantity by remember { mutableStateOf("1") }
    var customIntervalUnit by remember { mutableStateOf(ChronoUnit.MINUTES) }
    var showUnitMenu by remember { mutableStateOf(false) }

    var limitTimeFrame by remember { mutableStateOf(false) }
    var startHour by remember { mutableStateOf(9) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(17) }
    var endMinute by remember { mutableStateOf(0) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val timePickerState = rememberTimePickerState(
        initialHour = dueDateTime.hour,
        initialMinute = dueDateTime.minute
    )
    val startTimePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute)
    val endTimePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute)

    val validInput by remember {
        derivedStateOf { dueDateTime > LocalDateTime.now() && title.isNotBlank() }
    }

    val validCustomInterval = remember(customIntervalQuantity) {
        customIntervalQuantity.toLongOrNull()?.let { it > 0 } ?: false
    }

    val finalValidInput by remember {
        derivedStateOf { 
            validInput && (recurrencePattern != RecurrencePattern.CUSTOM || validCustomInterval)
        }
    }

    LaunchedEffect(reminder) {
        if (reminder != null) {
            title = reminder.title
            description = reminder.description
            emoji = reminder.emoji ?: "??" 
            recurrencePattern = reminder.recurrencePattern
            dueDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(reminder.dueDate),
                ZoneId.systemDefault()
            )
            
            if (reminder.recurrencePattern == RecurrencePattern.CUSTOM && reminder.customIntervalMillis != null) {
                // Try to parse mills back to quantity and unit for easier editing
                val millis = reminder.customIntervalMillis
                when {
                    millis % (1000 * 60 * 60 * 24 * 7) == 0L -> {
                        customIntervalQuantity = (millis / (1000 * 60 * 60 * 24 * 7)).toString()
                        customIntervalUnit = ChronoUnit.WEEKS
                    }
                    millis % (1000 * 60 * 60 * 24) == 0L -> {
                        customIntervalQuantity = (millis / (1000 * 60 * 60 * 24)).toString()
                        customIntervalUnit = ChronoUnit.DAYS
                    }
                    millis % (1000 * 60 * 60) == 0L -> {
                        customIntervalQuantity = (millis / (1000 * 60 * 60)).toString()
                        customIntervalUnit = ChronoUnit.HOURS
                    }
                    else -> {
                        customIntervalQuantity = (millis / (1000 * 60)).toString()
                        customIntervalUnit = ChronoUnit.MINUTES
                    }
                }
            }
            
            limitTimeFrame = reminder.repeatStartHour != null
            startHour = reminder.repeatStartHour ?: 9
            startMinute = reminder.repeatStartMinute ?: 0
            endHour = reminder.repeatEndHour ?: 17
            endMinute = reminder.repeatEndMinute ?: 0
        } else {
            title = ""
            description = null
            emoji = "??" 
            recurrencePattern = null
            dueDateTime = LocalDateTime.now()
            customIntervalQuantity = "1"
            customIntervalUnit = ChronoUnit.MINUTES
            limitTimeFrame = false
            startHour = 9
            startMinute = 0
            endHour = 17
            endMinute = 0
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes

    // Back press handler
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (reminder == null) "Add Reminder" else "Edit Reminder",
            style = typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (false) Color(0xFFB39DDB) else Color(0xFF8B5CF6),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Emoji Selector
            var showEmojiMenu by remember { mutableStateOf(false) }
            val emojis = listOf("??", "?", "??", "??", "??", "??", "??", "??", "??", "??", "??")
            
            Box {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp).clickable { showEmojiMenu = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
                
                DropdownMenu(
                    expanded = showEmojiMenu,
                    onDismissRequest = { showEmojiMenu = false }
                ) {
                    emojis.chunked(4).forEach { rowEmojis ->
                        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                            rowEmojis.forEach { e ->
                                IconButton(onClick = { emoji = e; showEmojiMenu = false }) {
                                    Text(text = e, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                value = title,
                onValueChange = { title = it },
                label = { Text("What needs to be done?") },
                singleLine = true,
                shape = shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (false) Color(0xFF2A2440).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                    unfocusedContainerColor = if (false) Color(0xFF221E38).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f),
                    disabledContainerColor = if (false) Color(0xFF1C1830).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.2f),
                    focusedTextColor = if (false) Color(0xFFE8E0FF) else Color.Unspecified,
                    unfocusedTextColor = if (false) Color(0xFFB0A8D0) else Color.Unspecified,
                    focusedLabelColor = if (false) Color(0xFFB39DDB) else Color.Unspecified,
                    unfocusedLabelColor = if (false) Color(0xFF8878A8) else Color.Unspecified,
                    focusedBorderColor = if (false) Color(0xFF8455FF).copy(alpha = 0.7f) else Color.Unspecified,
                    unfocusedBorderColor = if (false) Color(0xFF4A4060).copy(alpha = 0.6f) else Color.Unspecified,
                )
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            value = description ?: "",
            onValueChange = { description = it },
            label = { Text("Details (optional)") },
            maxLines = 3,
            shape = shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.5f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                disabledContainerColor = Color.White.copy(alpha = 0.2f),
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

          // Date Picker Row
          DateTimePickerRow(
              label = "Date",
              value = Utils.parseDateToDeviceFormat(
                  context,
                  dueDateTime.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
              ),
              icon = painterResource(R.drawable.ic_calendar), // Ensure you have this drawable
              onClick = { showDatePicker = true }
          )

        // Time Picker Row
        DateTimePickerRow(
            label = "Time",
            value = Utils.parseTimeToDeviceFormat(context, dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
            icon = painterResource(R.drawable.ic_alarm), // Ensure you have this drawable
            onClick = { showTimePicker = true }
        )

        // Recurrence Row
        DateTimePickerRow(
            label = "Repeat",
            value = if (recurrencePattern == RecurrencePattern.CUSTOM) "Every $customIntervalQuantity ${customIntervalUnit.name.lowercase()}" else recurrencePattern?.displayName ?: "Does not repeat",
            icon = painterResource(R.drawable.ic_repeat), // Ensure you have this drawable
            onClick = { showRecurrenceMenu = true }
        )

        DropdownMenu(
            expanded = showRecurrenceMenu,
            onDismissRequest = { showRecurrenceMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Does not repeat") },
                onClick = {
                    recurrencePattern = null
                    showRecurrenceMenu = false
                }
            )
            RecurrencePattern.entries.forEach { pattern ->
                DropdownMenuItem(
                    text = { Text(pattern.displayName) },
                    onClick = {
                        recurrencePattern = pattern
                        showRecurrenceMenu = false
                    }
                )
            }
        }

        // Custom Recurrence Options
        if (recurrencePattern == RecurrencePattern.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = customIntervalQuantity,
                    onValueChange = { if (it.all { char -> char.isDigit() }) customIntervalQuantity = it },
                    label = { Text("Interval") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    shape = shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                    )
                )

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = customIntervalUnit.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = {
                            IconButton(onClick = { showUnitMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.rotate(45f))
                            }
                        },
                        shape = shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.5f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                        )
                    )
                    
                    DropdownMenu(
                        expanded = showUnitMenu,
                        onDismissRequest = { showUnitMenu = false }
                    ) {
                        listOf(ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.WEEKS).forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    customIntervalUnit = unit
                                    showUnitMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Time Frame Restriction
        if (recurrencePattern != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Limit Time Frame", style = typography.bodyLarge)
                }
                Switch(checked = limitTimeFrame, onCheckedChange = { limitTimeFrame = it })
            }

            if (limitTimeFrame) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DateTimePickerRow(
                            label = "Start",
                            value = String.format("%02d:%02d", startHour, startMinute),
                            icon = painterResource(R.drawable.ic_alarm),
                            onClick = { showStartTimePicker = true }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DateTimePickerRow(
                            label = "End",
                            value = String.format("%02d:%02d", endHour, endMinute),
                            icon = painterResource(R.drawable.ic_alarm),
                            onClick = { showEndTimePicker = true }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val customMillis = if (recurrencePattern == RecurrencePattern.CUSTOM) {
                        val quantity = customIntervalQuantity.toLongOrNull() ?: 1L
                        when (customIntervalUnit) {
                            ChronoUnit.MINUTES -> quantity * 60 * 1000
                            ChronoUnit.HOURS -> quantity * 60 * 60 * 1000
                            ChronoUnit.DAYS -> quantity * 24 * 60 * 60 * 1000
                            ChronoUnit.WEEKS -> quantity * 7 * 24 * 60 * 60 * 1000
                            else -> 0L
                        }
                    } else null

                    onSave(
                        title,
                        description,
                        dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        recurrencePattern,
                        customMillis,
                        if (limitTimeFrame) startHour else null,
                        if (limitTimeFrame) startMinute else null,
                        if (limitTimeFrame) endHour else null,
                        if (limitTimeFrame) endMinute else null,
                        emoji
                    )
                },
                enabled = finalValidInput,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                shape = shapes.large,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text("Save Reminder")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDateMillis = datePickerState.selectedDateMillis
                    if (selectedDateMillis != null) {
                        val selectedDate = Instant.ofEpochMilli(selectedDateMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        dueDateTime = LocalDateTime.of(selectedDate, dueDateTime.toLocalTime())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDateTime = LocalDateTime.of(
                        dueDateTime.toLocalDate(),
                        LocalTime.of(timePickerState.hour, timePickerState.minute)
                    )
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startHour = startTimePickerState.hour
                    startMinute = startTimePickerState.minute
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = startTimePickerState)
        }
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endHour = endTimePickerState.hour
                    endMinute = endTimePickerState.minute
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = endTimePickerState)
        }
    }
}

@Composable
fun DateTimePickerRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(
                if (false) Color(0xFF2A2440).copy(alpha = 0.7f)
                else Color.White.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = content
    )
}
