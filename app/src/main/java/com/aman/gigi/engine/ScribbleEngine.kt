package com.aman.gigi.engine

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aman.gigi.model.Stroke as StrokeModel

/**
 * Main engine for orchestrating scribble rendering and effects
 */
object ScribbleEngine {

    /**
     * Renders a single stroke with its brush and all attached effects
     */
    fun DrawScope.drawStroke(
        stroke: StrokeModel,
        canvasWidth: Float,
        canvasHeight: Float,
        animationProgress: Float = 1.0f
    ) {
        // 1. Apply animation logic if needed
        EffectsEngine.applyAnimation(stroke.animationType, animationProgress) { actualProgress ->
            
            // 2. Render backing effects (those that go behind the stroke, like Glow)
            with(EffectsEngine) {
                renderEffects(stroke, canvasWidth, canvasHeight, actualProgress)
            }
            
            // 3. Render the core brush stroke
            with(BrushEngine) {
                renderStroke(stroke, canvasWidth, canvasHeight)
            }
        }
    }
    
    /**
     * Multi-stroke rendering for full canvas redraws
     */
    fun DrawScope.drawCanvas(
        strokes: List<StrokeModel>,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        strokes.forEach { stroke ->
            drawStroke(stroke, canvasWidth, canvasHeight)
        }
    }
}
