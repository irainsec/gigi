package com.aman.gigi.ui.nest

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.gigi.data.client.ClientIdentityStore
import com.aman.gigi.data.nest.TwigiAction
import com.aman.gigi.viewmodel.MusicViewModel
import com.aman.gigi.viewmodel.NestViewModel
import com.aman.gigi.viewmodel.ScreensaverViewModel
import kotlinx.coroutines.delay

@Composable
fun NestScreen(
    modifier: Modifier = Modifier,
    viewModel: NestViewModel = hiltViewModel(),
    screensaverViewModel: ScreensaverViewModel,
    musicViewModel: MusicViewModel
) {
    val haptic = LocalHapticFeedback.current
    val roomData by viewModel.roomState.collectAsStateWithLifecycle()
    val timeOfDay by viewModel.timeOfDay.collectAsStateWithLifecycle()
    val myTwigiPos by viewModel.myTwigiState.collectAsStateWithLifecycle()
    val partnerTwigiPos by viewModel.partnerTwigiState.collectAsStateWithLifecycle()
    val isFridgeOpen by viewModel.isFridgeOpen.collectAsStateWithLifecycle()
    val isShopOpen by viewModel.isShopOpen.collectAsStateWithLifecycle()
    val activeEmote by viewModel.activeEmote.collectAsStateWithLifecycle()

    val activeConnections by screensaverViewModel.activeConnections.collectAsStateWithLifecycle()
    var selectedConnectionIndex by remember { mutableIntStateOf(0) }
    val activeConnection = activeConnections.getOrNull(selectedConnectionIndex) ?: activeConnections.firstOrNull()
    var showConnectionMenu by remember { mutableStateOf(false) }

    val musicUiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val isPlayingMusic = musicUiState.isPlaying

    // Own avatar
    val myIdentity by screensaverViewModel.memberIdentity.collectAsStateWithLifecycle()
    val hasTwigi = !myIdentity?.twigiConfigJson.isNullOrBlank() || (!myIdentity?.twigiRenderUrl.isNullOrBlank() && myIdentity?.avatarMode == "TWIGI")
    var showLpcStudio by remember { mutableStateOf(false) }

    val myAvatarUrl = if (myIdentity?.avatarMode == "TWIGI" && !myIdentity?.twigiRenderUrl.isNullOrBlank()) {
        myIdentity?.twigiRenderUrl
    } else {
        null
    }

    val partnerName = activeConnection?.partnerName?.takeIf { it.isNotBlank() } ?: "Partner"
    val partnerAvatarUrl = activeConnection?.partnerTwigiUrl?.takeIf { it.isNotBlank() }
        ?: activeConnection?.partnerEmojiUrl?.takeIf { it.isNotBlank() }
        ?: activeConnection?.partnerAvatarUrl?.takeIf { it.isNotBlank() }

    LaunchedEffect(activeConnection) {
        val code = activeConnection?.connectionId
        if (!code.isNullOrBlank()) {
            viewModel.setConnection(code, partnerName)
        }
    }

    LaunchedEffect(isPlayingMusic) {
        viewModel.updateNowPlayingBehavior(isPlayingMusic)
    }

    var showEmotePicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0A1E))
    ) {
        // Main Room Canvas
        NestRoomCanvas(
            modifier = Modifier.fillMaxSize(),
            roomData = roomData,
            timeOfDay = timeOfDay,
            myTwigiPos = myTwigiPos,
            partnerTwigiPos = partnerTwigiPos,
            myAvatarUrl = myAvatarUrl,
            partnerAvatarUrl = partnerAvatarUrl,
            partnerName = partnerName,
            isPlayingMusic = isPlayingMusic,
            onMoveTarget = { x, y, facing, isWalking ->
                viewModel.moveMyTwigiTo(targetX = x, targetY = y, facing = facing, isWalking = isWalking)
            },
            onFurnitureTapped = { f ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                val key = (f.type + "_" + f.id + "_" + f.name).lowercase()
                when {
                    key.contains("fridge") -> viewModel.setFridgeOpen(true)
                    key.contains("sofa") || key.contains("couch") -> viewModel.moveMyTwigiTo(f.x, f.y, action = TwigiAction.SIT_COUCH)
                    key.contains("desk") || key.contains("chair") -> viewModel.moveMyTwigiTo(f.x, f.y, action = TwigiAction.SIT_DESK)
                    key.contains("bed") -> viewModel.moveMyTwigiTo(f.x, f.y, action = TwigiAction.SLEEP_BED)
                    key.contains("turntable") || key.contains("vinyl") -> viewModel.moveMyTwigiTo(f.x, f.y, action = TwigiAction.JAM_MUSIC)
                    else -> viewModel.moveMyTwigiTo(f.x, f.y, action = TwigiAction.IDLE)
                }
            },
            onPetTapped = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                viewModel.interactPet("PET")
                viewModel.sendEmote("🐾💕")
            }
        )

        // ── Top Control HUD ──
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(top = 6.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection Selector / Nest Title Pill
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF1E1436).copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.clickable {
                            if (activeConnections.size > 1) {
                                showConnectionMenu = true
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("🏡", fontSize = 13.sp)
                            Text(
                                text = "${partnerName}'s Nest",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (activeConnections.size > 1) {
                                Text("▾", color = Color(0xFFC084FC), fontSize = 11.sp)
                            }
                            Text("•", color = Color(0xFFC084FC), fontSize = 10.sp)
                            Text(
                                text = timeOfDay.label,
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    // Multi-connection dropdown menu
                    DropdownMenu(
                        expanded = showConnectionMenu,
                        onDismissRequest = { showConnectionMenu = false }
                    ) {
                        activeConnections.forEachIndexed { idx, conn ->
                            val name = conn.partnerName.takeIf { it.isNotBlank() } ?: "Partner ${idx + 1}"
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🏡", fontSize = 14.sp)
                                        Text(name, fontWeight = if (idx == selectedConnectionIndex) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                onClick = {
                                    selectedConnectionIndex = idx
                                    showConnectionMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))

                // Unified Action Buttons Glass Pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E1436).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Twigi Customizer Button
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { showLpcStudio = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧙", fontSize = 15.sp)
                        }

                        // Decor Shop Button
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.setShopOpen(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Decorate",
                                tint = Color(0xFFF472B6),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Fridge Magnet Notes Button
                        val noteCount = roomData?.fridgeNotes?.size ?: 0
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.setFridgeOpen(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧊", fontSize = 14.sp)
                            if (noteCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF43F5E),
                                    modifier = Modifier
                                        .size(11.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$noteCount",
                                            color = Color.White,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Emote Trigger Button
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEC4899))
                                .clickable { showEmotePicker = !showEmotePicker },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Send Emote",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Quick Emote Picker Dropdown
            AnimatedVisibility(
                visible = showEmotePicker,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E1436).copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF472B6).copy(alpha = 0.45f)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val emotes = listOf("💖", "🫂", "💋", "👋", "☕", "💤", "🐾")
                        emotes.forEach { em ->
                            Text(
                                text = em,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        viewModel.sendEmote(em)
                                        showEmotePicker = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Twigi Creation Guide Overlay for First-Time Users ──
        if (!hasTwigi) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E1035),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC084FC).copy(alpha = 0.6f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("🏡 ✨", fontSize = 38.sp)
                        Text(
                            text = "Create Your Twigi to enter the Nest!",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Only Twigi pixel characters live inside the Nest. Design your custom RPG character to walk, decorate, and hang out with ${partnerName}.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = { showLpcStudio = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC084FC)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("🎨 Create My Twigi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // ── Bottom Sheets ──
        if (isFridgeOpen) {
            FridgeNotesSheet(
                notes = roomData?.fridgeNotes ?: emptyList(),
                onDismiss = { viewModel.setFridgeOpen(false) },
                onAddNote = { text, color ->
                    viewModel.addFridgeNote(text, color = color)
                },
                onDeleteNote = { noteId ->
                    viewModel.deleteFridgeNote(noteId)
                }
            )
        }

        if (isShopOpen) {
            RoomDecorShopSheet(
                currentWallpaper = roomData?.wallpaper ?: "lavender_stars",
                currentFlooring = roomData?.flooring ?: "warm_oak",
                onDismiss = { viewModel.setShopOpen(false) },
                onSelectWallpaper = { viewModel.updateWallpaper(it) },
                onSelectFlooring = { viewModel.updateFlooring(it) }
            )
        }

        // ── Twigi Character Studio Dialog ──
        if (showLpcStudio) {
            com.aman.gigi.ui.twigi.TwigiCreatorScreen(
                initialConfigJson = myIdentity?.twigiConfigJson,
                saving = false,
                onDismiss = { showLpcStudio = false },
                onSave = { cfgJson ->
                    screensaverViewModel.saveTwigi(cfgJson)
                    screensaverViewModel.setAvatarMode("TWIGI")
                    showLpcStudio = false
                }
            )
        }
    }
}
