package com.aman.gigi.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aman.gigi.model.AnimationType
import com.aman.gigi.model.EffectType
import com.aman.gigi.model.Stroke as StrokeModel

/**
 * Handles rendering of special effects on strokes
 */
object EffectsEngine {

    /**
     * Renders effects for a given stroke
     * @param animationProgress 0.0 to 1.0, used for animated effects
     */
    fun DrawScope.renderEffects(
        stroke: StrokeModel,
        canvasWidth: Float,
        canvasHeight: Float,
        animationProgress: Float = 1.0f
    ) {
        stroke.effects.forEach { effect ->
            when (effect) {
                EffectType.GLOW -> renderGlow(stroke, canvasWidth, canvasHeight)
                EffectType.PARTICLES -> renderParticles(stroke, canvasWidth, canvasHeight, animationProgress)
                EffectType.ANIMATE -> { /* Handled by specialized animation render logic */ }
            }
        }
    }

    private fun DrawScope.renderGlow(stroke: StrokeModel, canvasWidth: Float, canvasHeight: Float) {
        val path = createPath(stroke.points, canvasWidth, canvasHeight)
        
        // Draw a larger, more vibrant additive glow
        drawPath(
            path = path,
            color = Color(stroke.color).copy(alpha = 0.5f * stroke.opacity),
            style = Stroke(
                width = stroke.width * 3.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            blendMode = BlendMode.Screen
        )
    }

    private fun DrawScope.renderParticles(
        stroke: StrokeModel,
        canvasWidth: Float,
        canvasHeight: Float,
        progress: Float
    ) {
        if (stroke.points.isEmpty()) return
        
        val color = Color(stroke.color)
        val particleSize = 3f + (Math.random().toFloat() * 3f)
        
        // Denser particles along the stroke
        val pointsToDraw = (stroke.points.size * progress).toInt().coerceIn(0, stroke.points.size)
        
        for (i in 0 until pointsToDraw) {
            val p = stroke.points[i]
            
            // Random jitter for "particles"
            val jitterX = (Math.random().toFloat() - 0.5f) * 25f
            val jitterY = (Math.random().toFloat() - 0.5f) * 25f
            
            drawCircle(
                color = color.copy(alpha = 0.4f * (1f - (i.toFloat() / stroke.points.size))),
                radius = particleSize,
                center = Offset(p.x * canvasWidth + jitterX, p.y * canvasHeight + jitterY)
            )
        }
    }

    private fun createPath(points: List<com.aman.gigi.model.Point>, width: Float, height: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path
        
        path.moveTo(points[0].x * width, points[0].y * height)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x * width, points[i].y * height)
        }
        return path
    }

    /**
     * Applies animation transformation to a path/stroke
     */
    fun applyAnimation(
        type: AnimationType,
        progress: Float,
        drawBlock: (progress: Float) -> Unit
    ) {
        when (type) {
            AnimationType.NONE -> drawBlock(1.0f)
            AnimationType.REVEAL -> drawBlock(progress)
            AnimationType.FADE_IN -> drawBlock(1.0f) // Handled by alpha in drawBlock wrapper if needed
            AnimationType.PULSE -> {
                val scale = 1.0f + 0.1f * kotlin.math.sin(progress * Math.PI * 2).toFloat()
                drawBlock(scale) 
            }
            AnimationType.FLOW -> drawBlock(progress) // Flow would need path dash effect, simplified for now
        }
    }
}
