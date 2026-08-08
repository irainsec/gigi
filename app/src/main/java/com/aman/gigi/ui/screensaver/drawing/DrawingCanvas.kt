package com.aman.gigi.ui.screensaver.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.aman.gigi.engine.ScribbleEngine
import com.aman.gigi.model.Point
import com.aman.gigi.model.Stroke as StrokeModel

/**
 * Drawing canvas composable for scribble creation
 */
@Composable
fun DrawingCanvas(
    strokes: List<StrokeModel>,
    currentStroke: StrokeModel?,
    onStrokeStart: (Float, Float) -> Unit,
    onStrokeMove: (Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val normalizedX = offset.x / size.width
                        val normalizedY = offset.y / size.height
                        onStrokeStart(normalizedX, normalizedY)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val normalizedX = change.position.x / size.width
                        val normalizedY = change.position.y / size.height
                        onStrokeMove(normalizedX, normalizedY)
                    },
                    onDragEnd = {
                        onStrokeEnd()
                    }
                )
            }
    ) {
        // Draw completed strokes using ScribbleEngine
        strokes.forEach { stroke ->
            with(ScribbleEngine) {
                drawStroke(stroke, size.width, size.height)
            }
        }
        
        // Draw current stroke being drawn using ScribbleEngine
        currentStroke?.let { stroke ->
            with(ScribbleEngine) {
                drawStroke(stroke, size.width, size.height)
            }
        }
    }
}
