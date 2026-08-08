package com.aman.gigi.ui.screensaver.playback

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.aman.gigi.model.Stroke
import kotlinx.coroutines.delay

/**
 * Animator for stroke-by-stroke playback
 */
class StrokeAnimator {
    
    /**
     * Playback configuration
     */
    data class PlaybackConfig(
        val strokeDelay: Long = 300L,      // Delay between strokes (ms)
        val pointDelay: Long = 5L,         // Delay between points in a stroke (ms)
        val fadeOutDelay: Long = 3000L,    // Delay before fade-out starts (ms)
        val fadeOutDuration: Long = 2000L  // Duration of fade-out animation (ms)
    )
    
    /**
     * Playback state
     */
    sealed class PlaybackState {
        object Idle : PlaybackState()
        data class Playing(
            val currentStrokeIndex: Int,
            val currentPointIndex: Int,
            val totalStrokes: Int
        ) : PlaybackState()
        data class FadingOut(val alpha: Float) : PlaybackState()
        object Complete : PlaybackState()
    }
    
    /**
     * Animate strokes with timing control
     */
    suspend fun animateStrokes(
        strokes: List<Stroke>,
        config: PlaybackConfig = PlaybackConfig(),
        onStateChange: (PlaybackState) -> Unit,
        onStrokeProgress: (strokeIndex: Int, pointIndex: Int, totalPoints: Int) -> Unit
    ) {
        if (strokes.isEmpty()) {
            onStateChange(PlaybackState.Complete)
            return
        }
        
        // Play each stroke
        strokes.forEachIndexed { strokeIndex, stroke ->
            onStateChange(
                PlaybackState.Playing(
                    currentStrokeIndex = strokeIndex,
                    currentPointIndex = 0,
                    totalStrokes = strokes.size
                )
            )
            
            // Animate points in stroke
            stroke.points.forEachIndexed { pointIndex, _ ->
                onStrokeProgress(strokeIndex, pointIndex, stroke.points.size)
                delay(config.pointDelay)
            }
            
            // Delay between strokes
            if (strokeIndex < strokes.size - 1) {
                delay(config.strokeDelay)
            }
        }
        
        // Hold before fade-out
        delay(config.fadeOutDelay)
        
        // Fade-out animation
        val fadeSteps = 20
        val fadeStepDuration = config.fadeOutDuration / fadeSteps
        
        for (step in 0..fadeSteps) {
            val alpha = 1f - (step.toFloat() / fadeSteps)
            onStateChange(PlaybackState.FadingOut(alpha))
            delay(fadeStepDuration)
        }
        
        onStateChange(PlaybackState.Complete)
    }
    
    /**
     * Calculate progress percentage
     */
    fun calculateProgress(
        currentStrokeIndex: Int,
        currentPointIndex: Int,
        strokes: List<Stroke>
    ): Float {
        if (strokes.isEmpty()) return 0f
        
        var totalPoints = 0
        var completedPoints = 0
        
        strokes.forEachIndexed { strokeIndex, stroke ->
            totalPoints += stroke.points.size
            
            if (strokeIndex < currentStrokeIndex) {
                completedPoints += stroke.points.size
            } else if (strokeIndex == currentStrokeIndex) {
                completedPoints += currentPointIndex
            }
        }
        
        return if (totalPoints > 0) {
            completedPoints.toFloat() / totalPoints.toFloat()
        } else {
            0f
        }
    }
}

/**
 * Composable hook for stroke animation
 */
@Composable
fun rememberStrokeAnimator(): StrokeAnimator {
    return remember { StrokeAnimator() }
}
