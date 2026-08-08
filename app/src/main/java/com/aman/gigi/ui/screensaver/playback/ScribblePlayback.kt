package com.aman.gigi.ui.screensaver.playback

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aman.gigi.model.Scribble
import com.aman.gigi.viewmodel.PlaybackViewModel
import com.aman.gigi.engine.ScribbleEngine
import kotlinx.coroutines.launch

/**
 * Scribble playback screen with animations
 */
@Composable
fun ScribblePlayback(
    scribble: Scribble,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animator = rememberStrokeAnimator()
    var playbackState by remember { mutableStateOf<StrokeAnimator.PlaybackState>(StrokeAnimator.PlaybackState.Idle) }
    var currentStrokeIndex by remember { mutableIntStateOf(0) }
    var currentPointIndex by remember { mutableIntStateOf(0) }
    var alpha by remember { mutableFloatStateOf(1f) }
    
    val scope = rememberCoroutineScope()
    
    // Start animation on mount
    LaunchedEffect(scribble.scribbleId) {
        scope.launch {
            animator.animateStrokes(
                strokes = scribble.strokes,
                onStateChange = { state ->
                    playbackState = state
                    when (state) {
                        is StrokeAnimator.PlaybackState.FadingOut -> {
                            alpha = state.alpha
                        }
                        is StrokeAnimator.PlaybackState.Complete -> {
                            onComplete()
                        }
                        else -> {}
                    }
                },
                onStrokeProgress = { strokeIndex, pointIndex, _ ->
                    currentStrokeIndex = strokeIndex
                    currentPointIndex = pointIndex
                }
            )
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFF0F3460)
        )
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Drawing canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Draw strokes up to current progress using ScribbleEngine
            scribble.strokes.forEachIndexed { strokeIndex, stroke ->
                if (strokeIndex < currentStrokeIndex) {
                    // Fully completed strokes
                    with(ScribbleEngine) {
                        drawStroke(
                            stroke = stroke.copy(color = Color(stroke.color).copy(alpha = alpha).toArgb()),
                            canvasWidth = size.width,
                            canvasHeight = size.height
                        )
                    }
                } else if (strokeIndex == currentStrokeIndex && currentPointIndex > 0) {
                    // Stroke currently being animated
                    val animatingStroke = stroke.copy(
                        points = stroke.points.take(currentPointIndex + 1),
                        color = Color(stroke.color).copy(alpha = alpha).toArgb()
                    )
                    with(ScribbleEngine) {
                        drawStroke(
                            stroke = animatingStroke,
                            canvasWidth = size.width,
                            canvasHeight = size.height
                        )
                    }
                }
            }
        }
        
        // Progress indicator (optional)
        if (playbackState is StrokeAnimator.PlaybackState.Playing) {
            val progress = animator.calculateProgress(
                currentStrokeIndex,
                currentPointIndex,
                scribble.strokes
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(
                        Color.White.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Drawing... ${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Playback screen with queue management
 */
@Composable
fun PlaybackScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val currentScribble by viewModel.currentScribble.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val queueSize = viewModel.getQueueSize()
    
    // Load scribbles on mount
    LaunchedEffect(Unit) {
        viewModel.loadReceivedScribbles()
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Animated flowers, hearts, and sparkles
        com.aman.gigi.ui.components.RomanceAmbientDecor(
            modifier = Modifier.fillMaxSize(),
            darkTheme = true
        )
        currentScribble?.let { scribble ->
            ScribblePlayback(
                scribble = scribble,
                onComplete = {
                    viewModel.completeCurrentScribble()
                }
            )
        }
        
        // Queue indicator
        if (queueSize > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(
                        Color.White.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$queueSize scribbles in queue",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}
