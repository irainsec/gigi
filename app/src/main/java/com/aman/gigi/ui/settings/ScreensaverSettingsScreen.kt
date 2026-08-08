package com.aman.gigi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aman.gigi.viewmodel.SettingsViewModel

// ─── Color palette matching the screenshots ───────────────────────────────────
private val BgLavender   = Color(0xFFF0EAFB) // overall page background
private val CardLavender = Color(0xFFF4EEFF) // header card background
private val PinkPill     = Color(0xFFFFD6E7) // "Sweet little settings" pill bg
private val PinkText     = Color(0xFFFF70A6) // pink pill text
private val Purple       = Color(0xFF7C4DFF) // primary purple
private val TextDark     = Color(0xFF1A1040) // near-black for headings
private val TextGrey     = Color(0xFF9E9CB5) // subtext grey
private val CardWhite    = Color(0xFFFAF8FF) // identity card background
private val RowBg        = Color(0xFFF8F5FF) // action row background
private val AccentOrange = Color(0xFFFFA726)
private val AccentGreen  = Color(0xFF66BB6A)
private val AccentRed    = Color(0xFFEF5350)
private val AccentGrey   = Color(0xFF9E9E9E)

@Composable
fun ScreensaverSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val memberIdentity by viewModel.memberIdentity.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(memberIdentity) { mutableStateOf(memberIdentity?.displayName ?: "") }
    var editGender by remember(memberIdentity) { mutableStateOf(memberIdentity?.gender ?: "HER") }
    var syncExpanded by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showLpcStudio    by remember { mutableStateOf(false) }
    var showVrmFullscreen  by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("galaxy_orbits", android.content.Context.MODE_PRIVATE) }
    var currentEmojiSelf by remember { mutableStateOf(prefs.getString("emoji_self", null)) }

    DisposableEffect(context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "emoji_self") currentEmojiSelf = sp.getString("emoji_self", null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val gbLoader = remember {
        coil.ImageLoader.Builder(context).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }

    val isEmojiMode = memberIdentity?.avatarMode != "TWIGI"
    // Header avatar mirrors the ACTIVE identity: the Twigi render when Twigi is on,
    // otherwise the chosen emoji. (The Emoji card below always shows the emoji.)
    val twigiUrl = memberIdentity?.twigiRenderUrl?.takeIf { it.isNotBlank() }
    val avatarUrl = if (!isEmojiMode && twigiUrl != null) twigiUrl
        else currentEmojiSelf?.takeIf { it.isNotBlank() }
            ?: memberIdentity?.avatarUrl?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgLavender),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header card (lavender gradient) ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFFCF5FF), CardLavender))
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Top row: pink pill + Done
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PinkPill
                        ) {
                            Text(
                                text = if (isEditing) "Edit your little corner" else "Sweet little settings",
                                color = PinkText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            text = "Done",
                            color = Purple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { if (isEditing) isEditing = false else onBack() }
                        )
                    }

                    // Profile row: avatar left, name+email right
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                                    imageLoader = gbLoader,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(10.dp)
                                )
                            } else {
                                Text(
                                    text = memberIdentity?.displayName?.take(1)?.uppercase() ?: "?",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp,
                                    color = Purple
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = memberIdentity?.displayName ?: "Your Name",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = TextDark
                            )
                            Text(
                                text = memberIdentity?.googleEmail ?: memberIdentity?.phoneNumber ?: "Not signed in",
                                fontSize = 13.sp,
                                color = TextGrey
                            )
                        }
                    }

                    // Edit profile button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isEditing) {
                                    viewModel.updateProfile(editName, editGender, currentEmojiSelf)
                                    isEditing = false
                                } else {
                                    isEditing = true
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Purple, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isEditing) "Save changes" else "Edit profile",
                                color = Purple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // ── Edit profile expanded section ─────────────────────────────────
            AnimatedVisibility(visible = isEditing, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Your emoji row
                    Text("Your emoji", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(BgLavender),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentEmojiSelf != null) {
                                val parsedModel = com.aman.gigi.utils.ImageUtils.parseEmojiModel(currentEmojiSelf)
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(parsedModel).crossfade(true).build(),
                                    imageLoader = gbLoader,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                )
                            } else {
                                Text("🌻", fontSize = 26.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0xFFDDD8F5)),
                            modifier = Modifier.clickable { showEmojiPicker = true }
                        ) {
                            Text(
                                "Change emoji ✨",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Display name
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFDDD8F5),
                            focusedBorderColor = Purple
                        )
                    )

                    // Greeting style
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Greeting style", color = TextGrey, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("Him", "Her").forEach { option ->
                                val selected = editGender.equals(option, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color(0xFFF0EAFB) else Color.Transparent,
                                    border = BorderStroke(1.5.dp, if (selected) Purple else Color(0xFFDDD8F5)),
                                    modifier = Modifier.weight(1f).clickable { editGender = option.uppercase() }
                                ) {
                                    Text(
                                        text = option,
                                        modifier = Modifier.padding(12.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Purple else TextDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Your identity section ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text("Your identity", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                    Text("Pick what your loved ones see", fontSize = 13.sp, color = TextGrey)
                }

                // Emoji card row
                IdentityCard(
                    modifier = Modifier.fillMaxWidth(),
                    isSelected = isEmojiMode,
                    label = "Emoji Avatar",
                    sublabel = "visible to partners 💜",
                    emoji = currentEmojiSelf,
                    imageUrl = null,
                    gbLoader = gbLoader,
                    context = context,
                    onClick = { viewModel.setAvatarMode("EMOJI"); showEmojiPicker = true }
                )

                // Twigi 3D VRM card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardWhite)
                        .then(
                            if (!isEmojiMode) Modifier.border(2.dp, Purple, RoundedCornerShape(20.dp))
                            else Modifier
                        )
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAvatarMode("LPC")
                                showLpcStudio = true
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🐉", fontSize = 26.sp)
                            Column {
                                Text(
                                    "Twigi Studio ✨",
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    fontSize = 15.sp
                                )
                                Text(
                                    if (memberIdentity?.twigiRenderUrl != null) "Your 3D avatar · tap to edit" else "Build your 3D VRM avatar",
                                    fontSize = 12.sp,
                                    color = TextGrey
                                )
                            }
                        }
                        if (!isEmojiMode) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Purple.copy(alpha = 0.12f)
                            ) {
                                Text("Active ✓", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = Purple, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Native 3D Twigi Studio Card (shown when Twigi mode is active or configured)
                    if (!showLpcStudio && (!isEmojiMode || memberIdentity?.twigiRenderUrl != null)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1E1035),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7C3AED).copy(alpha = 0.4f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF2E1065), Color(0xFF0F172A))
                                        )
                                    )
                                    .padding(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("✨ 3D Twigi Avatar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Native GPU 60fps Character Studio", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.setAvatarMode("LPC")
                                            showLpcStudio = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                        shape = RoundedCornerShape(999.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Open Studio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Account & sync ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { syncExpanded = !syncExpanded }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = BgLavender, modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("⚙️", fontSize = 18.sp)
                            }
                        }
                        Column {
                            Text("Account & sync", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                            Text("Online • ${memberIdentity?.let { "ready" } ?: "not linked"}", fontSize = 12.sp, color = TextGrey)
                        }
                    }
                    Icon(
                        imageVector = if (syncExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextGrey
                    )
                }

                AnimatedVisibility(visible = syncExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Stats row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill("2", "Links", Purple, Modifier.weight(1f))
                            StatPill("Online", "Server", AccentGreen, Modifier.weight(1f))
                            StatPill("Ready", "Profile", Purple, Modifier.weight(1f))
                        }
                        // Email
                        Text(
                            memberIdentity?.googleEmail ?: memberIdentity?.phoneNumber ?: "Not signed in",
                            fontWeight = FontWeight.Bold,
                            color = Purple,
                            fontSize = 14.sp
                        )
                        Text("Server sync is online and ready.", fontSize = 13.sp, color = TextGrey)

                        // ── Permissions & System Optimizations inside Account & sync (hidden when granted) ──
                        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                        val isIgnoringBatteryOpt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                        } else true

                        val npEnabled = runCatching {
                            android.provider.Settings.Secure.getString(
                                context.contentResolver, "enabled_notification_listeners"
                            ).orEmpty().contains(context.packageName)
                        }.getOrDefault(false)

                        if (!npEnabled) {
                            HorizontalDivider(color = Color(0xFFF0EAFB), thickness = 1.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF7F5FF))
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎵", fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Share what you're playing", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        Text("Needs notification permission · tap to grant", fontSize = 11.sp, color = TextGrey)
                                    }
                                    Text("Enable", fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED), fontSize = 12.sp)
                                }
                            }
                        }

                        if (!isIgnoringBatteryOpt) {
                            HorizontalDivider(color = Color(0xFFF0EAFB), thickness = 1.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF7F5FF))
                                    .clickable {
                                        runCatching {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }.onFailure {
                                            runCatching {
                                                context.startActivity(
                                                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚡", fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Ensure Instant Push Delivery", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        Text("Exclude Gigi from battery saver for instant doodle wake-up 💜", fontSize = 11.sp, color = TextGrey)
                                    }
                                    Text("Optimize", fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED), fontSize = 12.sp)
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF0EAFB), thickness = 1.dp)

                        // Action rows inside Account & sync
                        ActionRow(icon = "🔄", label = "Restore Purchases", tint = Purple, onClick = onRestorePurchases)
                        HorizontalDivider(color = Color(0xFFF0EAFB), thickness = 1.dp)
                        ActionRow(icon = "🛡️", label = "Privacy Policy", tint = Purple, onClick = onPrivacyPolicy)
                        HorizontalDivider(color = Color(0xFFF0EAFB), thickness = 1.dp)
                        ActionRow(icon = "👋", label = "Logout from Gigi", tint = AccentOrange, onClick = onLogout)
                        HorizontalDivider(color = Color(0xFFF0EAFB), thickness = 1.dp)
                        ActionRow(icon = "💔", label = "Delete Account", tint = AccentRed, onClick = onDeleteAccount)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

        }
    }

    if (showEmojiPicker) {
        com.aman.gigi.ui.components.AvatarEmojiPickerDialog(
            onDismiss = { showEmojiPicker = false },
            onPickEmoji = {
                viewModel.setProfileEmoji(context, it)
                currentEmojiSelf = it
                showEmojiPicker = false
            },
            title = "Your identity ✨",
            subtitle = "Pick an animated emoji to represent you."
        )
    }

    if (showLpcStudio) {
        com.aman.gigi.ui.twigi.TwigiCreatorScreen(
            initialConfigJson = memberIdentity?.twigiConfigJson,
            saving = false,
            onDismiss = { showLpcStudio = false },
            onSave = { cfgJson ->
                viewModel.saveTwigi(cfgJson)
                viewModel.setAvatarMode("TWIGI")
                showLpcStudio = false
            }
        )
    }

}

@Composable
private fun IdentityCard(
    modifier: Modifier,
    isSelected: Boolean,
    label: String,
    sublabel: String,
    emoji: String?,
    imageUrl: String?,
    gbLoader: coil.ImageLoader,
    context: android.content.Context,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .then(
                if (isSelected) Modifier.border(2.dp, Purple, RoundedCornerShape(20.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(end = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    emoji != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(emoji).crossfade(true).build(),
                            imageLoader = gbLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    imageUrl != null -> {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    else -> {
                        Text(if (label == "Emoji") "🌻" else "🐉", fontSize = 40.sp)
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    tint = Purple
                )
            }
        }
        Text(label, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
        if (sublabel.isNotBlank()) {
            Text(sublabel, fontSize = 11.sp, color = TextGrey, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = BgLavender, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = color, fontSize = 16.sp)
            Text(label, fontSize = 11.sp, color = TextGrey)
        }
    }
}

@Composable
private fun ActionRow(icon: String, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(icon, fontSize = 20.sp)
            Text(label, fontWeight = FontWeight.Medium, color = tint, fontSize = 15.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextGrey, modifier = Modifier.size(20.dp))
    }
}
