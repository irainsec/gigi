package com.aman.gigi.ui.screensaver.connection

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aman.gigi.model.ConnectionMember
import com.aman.gigi.model.ConnectionRole
import com.aman.gigi.viewmodel.GroupManagementViewModel
import kotlinx.coroutines.delay

private val Lavender = Color(0xFF8B5CF6)
private val Ink = Color(0xFF3B2A6B)
private val Muted = Color(0xFF9A8FC0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    connectionId: String,
    onBack: () -> Unit,
    viewModel: GroupManagementViewModel = hiltViewModel()
) {
    val groupConnection by viewModel.groupConnection.collectAsState()
    val members by viewModel.members.collectAsState()
    val leaveComplete by viewModel.leaveComplete.collectAsState()

    val currentDeviceId = viewModel.currentDeviceId
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showLeaveDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<ConnectionMember?>(null) }
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(connectionId) { viewModel.loadGroup(connectionId) }
    LaunchedEffect(leaveComplete) { if (leaveComplete) onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF6F1FF), Color(0xFFEFE7FF), Color(0xFFFBEFF7))
                )
            )
    ) {
        val group = groupConnection
        if (group == null) {
            CircularProgressIndicator(color = Lavender, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val isCreator = group.creatorDeviceId?.equals(currentDeviceId, ignoreCase = true) == true
        var isEditingName by remember { mutableStateOf(false) }
        var editName by remember(group.partnerName) { mutableStateOf(group.partnerName) }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(emoji = "‹", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Text("Group", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val pulse = remember { Animatable(0.85f) }
                        LaunchedEffect(Unit) { pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                        var showEmojiPicker by remember { mutableStateOf(false) }
                        val geCtx = LocalContext.current
                        val geLoader = remember {
                            coil.ImageLoader.Builder(geCtx).components {
                                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                                else add(coil.decode.GifDecoder.Factory())
                            }.build()
                        }
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .graphicsLayer(scaleX = pulse.value, scaleY = pulse.value)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFFF9A8D4), Color(0xFF8B5CF6), Color(0xFF818CF8))))
                                    .border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                    .clickable { showEmojiPicker = true },
                                contentAlignment = Alignment.Center
                            ) {
                                // The group's shared animated emoji (synced to every member).
                                val groupEmojiUrl = group.partnerEmojiUrl
                                if (!groupEmojiUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(geCtx).data(groupEmojiUrl).build(),
                                        imageLoader = geLoader,
                                        contentDescription = "Group emoji",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(0.72f)
                                    )
                                } else {
                                    Text("👯", fontSize = 44.sp)
                                }
                            }
                            // Small edit badge so it's clearly tappable.
                            Surface(
                                onClick = { showEmojiPicker = true },
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Edit, contentDescription = "Change group emoji", tint = Lavender, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        if (showEmojiPicker) {
                            com.aman.gigi.ui.components.AvatarEmojiPickerDialog(
                                onDismiss = { showEmojiPicker = false },
                                onPickEmoji = { url ->
                                    viewModel.setGroupEmoji(url)
                                    showEmojiPicker = false
                                },
                                title = "Group emoji ✨",
                                subtitle = "Everyone in the group will see this."
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (isEditingName) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Lavender, unfocusedBorderColor = Muted.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth(0.8f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { isEditingName = false }) { Text("Cancel", color = Muted) }
                                Button(
                                    onClick = { viewModel.updateGroupName(editName); isEditingName = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Lavender),
                                    shape = RoundedCornerShape(999.dp)
                                ) { Text("Save") }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(group.partnerName.ifBlank { "Group" }, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                                if (isCreator) {
                                    Spacer(Modifier.width(6.dp))
                                    RoundIconButton(icon = Icons.Default.Edit, onClick = { editName = group.partnerName; isEditingName = true }, size = 30.dp)
                                }
                            }
                            Text("${members.size} member${if (members.size == 1) "" else "s"} ✨", fontSize = 13.sp, color = Muted)
                        }
                    }
                }

                // Invite card
                item {
                    Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Invite with code", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    group.connectionCode,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Lavender,
                                    modifier = Modifier.weight(1f)
                                )
                                RoundIconButton(icon = Icons.Default.ContentCopy, onClick = {
                                    clipboardManager.setText(AnnotatedString(group.connectionCode)); showCopied = true
                                })
                                Spacer(Modifier.width(8.dp))
                                RoundIconButton(icon = Icons.Default.Share, onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Join my Gigi group \"${group.partnerName}\"! 💜\nCode: ${group.connectionCode}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share invite code"))
                                })
                            }
                            if (showCopied) {
                                LaunchedEffect(Unit) { delay(1800); showCopied = false }
                                Text("Copied! 💜", fontSize = 12.sp, color = Lavender, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }

                item {
                    Text("Members", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }

                items(members, key = { it.memberDeviceId }) { member ->
                    MemberRow(
                        member = member,
                        isSelf = member.memberDeviceId == currentDeviceId,
                        canRemove = isCreator && member.memberDeviceId != currentDeviceId && member.role != ConnectionRole.CREATOR.name,
                        onRemove = { memberToRemove = member }
                    )
                }

                item { Spacer(Modifier.height(4.dp)) }

                // Leave / delete
                item {
                    Surface(
                        onClick = { showLeaveDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFFECEC),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                                Text("🚪", fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(if (isCreator) "Delete Group" else "Leave Group", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = Color.White,
            title = { Text("Leave group?", fontWeight = FontWeight.Bold, color = Ink) },
            text = { Text("You'll lose access to this group and its history.", color = Muted) },
            confirmButton = {
                TextButton(onClick = { viewModel.leaveGroup(); showLeaveDialog = false }) { Text("Leave", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel", color = Muted) } }
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            containerColor = Color.White,
            title = { Text("Remove member?", fontWeight = FontWeight.Bold, color = Ink) },
            text = { Text("Remove ${member.memberName} from this group?", color = Muted) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeMember(member.memberDeviceId); memberToRemove = null }) { Text("Remove", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text("Cancel", color = Muted) } }
        )
    }
}

@Composable
private fun MemberRow(
    member: ConnectionMember,
    isSelf: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val isAdmin = member.role == ConnectionRole.CREATOR.name
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFEDE4FF), Color(0xFFF9E4F1)))),
                contentAlignment = Alignment.Center
            ) {
                val animatedEmoji = member.emojiUrl
                if (!animatedEmoji.isNullOrBlank()) {
                    val mrCtx = LocalContext.current
                    val mrLoader = remember {
                        coil.ImageLoader.Builder(mrCtx).components {
                            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                            else add(coil.decode.GifDecoder.Factory())
                        }.build()
                    }
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(mrCtx).data(animatedEmoji).build(),
                        imageLoader = mrLoader,
                        contentDescription = member.memberName,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.matchParentSize().padding(6.dp)
                    )
                } else {
                    Text(member.memberEmoji.ifBlank { "🌻" }, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.memberName.ifBlank { "Member" } + if (isSelf) " (you)" else "", fontWeight = FontWeight.Bold, color = Ink)
                    if (isAdmin) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFFFF0C7)) {
                            Text("👑 Admin", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB7791F), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                Text(if (isAdmin) "Created the group" else "Member", fontSize = 12.sp, color = Muted)
            }
            if (canRemove) {
                RoundIconButton(icon = Icons.Default.Close, tint = Color(0xFFD32F2F), bg = Color(0xFFFFECEC), onClick = onRemove, size = 34.dp)
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emoji: String? = null,
    tint: Color = Lavender,
    bg: Color = Color(0xFFF1ECFB),
    size: androidx.compose.ui.unit.Dp = 40.dp,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = CircleShape, color = bg, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            when {
                emoji != null -> Text(emoji, fontSize = 22.sp, color = tint, fontWeight = FontWeight.Bold)
                icon != null -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.45f))
            }
        }
    }
}
