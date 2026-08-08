package com.aman.gigi.ui.screensaver.drawing

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aman.gigi.viewmodel.DrawingViewModel
import com.aman.gigi.viewmodel.SendStatus
import com.aman.gigi.ui.screensaver.components.GifPickerTray
import com.aman.gigi.ui.components.UpgradeSheet
import com.aman.gigi.utils.AppConfig
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon

/**
 * Full drawing screen with canvas and tools
 */
@Composable
fun DrawingScreen(
    connectionId: String,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DrawingViewModel = hiltViewModel()
) {
    // Keep screen on while drawing
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        viewModel.notifyDrawingState(connectionId, true)
        onDispose {
            view.keepScreenOn = false
            viewModel.notifyDrawingState(connectionId, false)
        }
    }
    val strokes by viewModel.strokes.collectAsState()
    val currentStroke by viewModel.currentStroke.collectAsState()
    val selectedColorInt by viewModel.selectedColor.collectAsState()
    val isEraser by viewModel.isEraser.collectAsState()
    val recentGifs by viewModel.recentGifs.collectAsState()
    
    // Advanced states
    val selectedBrush by viewModel.selectedBrush.collectAsState()
    val selectedEffects by viewModel.selectedEffects.collectAsState()
    val selectedAnimation by viewModel.selectedAnimation.collectAsState()
    val selectedOpacity by viewModel.selectedOpacity.collectAsState()
    val showSettingsPanel by viewModel.showSettingsPanel.collectAsState()
    val strokeWidth by viewModel.strokeWidth.collectAsState()
    val isDrawing by viewModel.isDrawing.collectAsState()
    
    val selectedColor = Color(selectedColorInt)
    val strokeCount = viewModel.getStrokeCount()
    val plan by AppConfig.planFlow.collectAsState()
    val maxStrokes = plan.maxStrokes

    var showClearDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showUpgradeSheet by remember { mutableStateOf(false) }
    
    val sendStatus by viewModel.sendStatus.collectAsState()
    val errorReason by viewModel.errorReason.collectAsState()

    // Handle auto-exit on successful send
    LaunchedEffect(sendStatus) {
        if (sendStatus == SendStatus.SENT) {
            delay(1500) // Show "Sent!" for 1.5s
            viewModel.clearAllStrokes()
            viewModel.resetSendStatus()
            onSend()
        }
    }

    // Background brush
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF0F4F8),
            Color(0xFFE6E0FF),
            Color(0xFFF3E5F5)
        )
    )
    
    val isToolPanelExpanded by viewModel.isToolPanelExpanded.collectAsState()

    // Auto-collapse when drawing starts
    LaunchedEffect(isDrawing) {
        if (isDrawing) {
            viewModel.toggleToolPanel(false)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Animated flowers, hearts, and sparkles
        com.aman.gigi.ui.components.RomanceAmbientDecor(
            modifier = Modifier.fillMaxSize(),
            darkTheme = false
        )
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        
        // Drawing canvas (Full Screen)
        DrawingCanvas(
            strokes = strokes,
            currentStroke = currentStroke,
            onStrokeStart = { x, y ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                viewModel.startStroke(x, y)
            },
            onStrokeMove = { x, y ->
                viewModel.addPointToStroke(x, y)
            },
            onStrokeEnd = {
                viewModel.finishStroke()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Stroke limit banner
        if (viewModel.isAtStrokeLimit()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp)
                    .padding(horizontal = 24.dp)
                    .background(
                        Color(0xFF1F1F2E),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .align(Alignment.TopCenter)
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stroke limit reached ($maxStrokes)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (plan.isFree || plan.isPlus) {
                        androidx.compose.material3.TextButton(onClick = { showUpgradeSheet = true }) {
                            Text("Upgrade", color = Color(0xFF58A6FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showUpgradeSheet) {
            UpgradeSheet(
                featureName = "Unlimited Drawing",
                featureDescription = "Remove the stroke limit and draw as much as you want.",
                onDismiss = { showUpgradeSheet = false }
            )
        }
            
        // 🔴 BOTTOM TOOLS: Collapsible Bottom Sheet
        DrawingTools(
            selectedColor = selectedColor,
            availableColors = viewModel.availableColors,
            isEraser = isEraser,
            strokeCount = strokeCount,
            maxStrokes = maxStrokes,
            onColorSelected = { viewModel.selectColor(it) },
            onEraserToggle = { viewModel.toggleEraser() },
            onUndo = { viewModel.undoLastStroke() },
            onClear = { showClearDialog = true },
            onSend = { viewModel.sendScribble(connectionId) },
            onCancel = { if (strokeCount > 0) showCancelDialog = true else onCancel() },
            onGifClick = { showGifPicker = true },
            selectedBrush = selectedBrush,
            selectedEffects = selectedEffects,
            onBrushSelected = { viewModel.selectBrush(it) },
            onEffectToggled = { viewModel.toggleEffect(it) },
            onLongPress = { viewModel.toggleSettingsPanel(true) },
            strokeWidth = strokeWidth,
            onStrokeWidthChange = { viewModel.setStrokeWidth(it) },
            opacity = selectedOpacity,
            onOpacityChange = { viewModel.setOpacity(it) },
            isExpanded = isToolPanelExpanded,
            onToggleExpand = { viewModel.toggleToolPanel() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 0.dp) // Flush with bottom
        )

        if (showGifPicker) {
            GifPickerTray(
                onGifSelected = { url ->
                    viewModel.sendGif(connectionId, url)
                    showGifPicker = false
                },
                onLocalGifSelected = { uri ->
                    viewModel.sendLocalGif(connectionId, uri)
                    showGifPicker = false
                },
                onDismiss = { showGifPicker = false },
                recentGifs = recentGifs
            )
        }

        // --- Sending Overlay ---
        if (sendStatus == SendStatus.SENDING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures { } }, // Block clicks
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sending To Partner...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }

        // --- Sent/Queued Overlay ---
        if (sendStatus == SendStatus.SENT || sendStatus == SendStatus.QUEUED) {
            val isQueued = sendStatus == SendStatus.QUEUED
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isQueued) Icons.Default.CloudQueue else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isQueued) Color.Gray else Color(0xFF00C853),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isQueued) "Saved to Outbox" else "Sent!",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                    Text(
                        text = if (isQueued) "Will send when connected" else "Partner received it!",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Auto dismiss
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    viewModel.resetSendStatus()
                    viewModel.clearAllStrokes()
                    onCancel()
                }
            }
        }
    }
    
    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Canvas?") },
            text = { Text("This will remove all strokes. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllStrokes()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error Dialog (Local to this screen)
    if (sendStatus == SendStatus.ERROR) {
        AlertDialog(
            onDismissRequest = { viewModel.resetSendStatus() },
            title = { Text("Not Sent") },
            text = { Text(errorReason ?: "Scribble not sent. Partner might be offline.") },
            confirmButton = {
                TextButton(
                    onClick = { 
                        viewModel.resetSendStatus() 
                    }
                ) {
                    Text("Retry")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetSendStatus() }) {
                    Text("Dismiss")
                }
            }
        )
    }
    

    // Cancel confirmation dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Discard Scribble?") },
            text = { Text("Your scribble will be lost if you cancel.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllStrokes()
                        showCancelDialog = false
                        onCancel()
                    }
                ) {
                    Text("Discard", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Drawing")
                }
            }
        )
    }
}
