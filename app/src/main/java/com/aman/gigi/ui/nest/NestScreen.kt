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
    val activeConnection = activeConnections.firstOrNull()

    val musicUiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val isPlayingMusic = musicUiState.isPlaying

    // Own avatar
    val myIdentity by screensaverViewModel.memberIdentity.collectAsStateWithLifecycle()
    val myAvatarUrl = if (myIdentity?.avatarMode == "TWIGI" && !myIdentity?.twigiRenderUrl.isNullOrBlank()) {
        myIdentity?.twigiRenderUrl
    } else {
        myIdentity?.profileEmojiUrl
    }

    val partnerName = activeConnection?.partnerName?.takeIf { it.isNotBlank() } ?: "Partner"
    val partnerAvatarUrl = activeConnection?.partnerTwigiUrl?.takeIf { it.isNotBlank() }
        ?: activeConnection?.partnerEmojiUrl ?: activeConnection?.partnerAvatarUrl

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
            onFloorTapped = { x, y ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                viewModel.moveMyTwigiTo(x, y, TwigiAction.WALK)
            },
            onFurnitureTapped = { f ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                when (f.type) {
                    "fridge" -> viewModel.setFridgeOpen(true)
                    "couch" -> viewModel.moveMyTwigiTo(f.x, f.y, TwigiAction.SIT_COUCH)
                    "bed" -> viewModel.moveMyTwigiTo(f.x, f.y, TwigiAction.SLEEP_BED)
                    "music" -> viewModel.moveMyTwigiTo(f.x, f.y, TwigiAction.JAM_MUSIC)
                    else -> viewModel.moveMyTwigiTo(f.x, f.y, TwigiAction.IDLE)
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
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                // Time & Mood Pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E1436).copy(alpha = 0.88f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🏡", fontSize = 14.sp)
                        Text(
                            text = "${partnerName}'s Nest",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("•", color = Color(0xFFC084FC), fontSize = 10.sp)
                        Text(
                            text = timeOfDay.label,
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Action Buttons Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Decor Shop Button
                    IconButton(
                        onClick = { viewModel.setShopOpen(true) },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E1436).copy(alpha = 0.88f)),
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Decorate",
                            tint = Color(0xFFF472B6),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Fridge Magnet Notes Button
                    val noteCount = roomData?.fridgeNotes?.size ?: 0
                    Box {
                        IconButton(
                            onClick = { viewModel.setFridgeOpen(true) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E1436).copy(alpha = 0.88f)),
                            modifier = Modifier
                                .size(38.dp)
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), CircleShape)
                        ) {
                            Text("🧊", fontSize = 16.sp)
                        }
                        if (noteCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF43F5E),
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$noteCount",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Quick Emote Trigger Button
                    IconButton(
                        onClick = { showEmotePicker = !showEmotePicker },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFEC4899).copy(alpha = 0.88f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Send Emote",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
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
    }
}
