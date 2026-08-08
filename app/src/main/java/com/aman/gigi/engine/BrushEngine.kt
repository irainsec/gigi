package com.aman.gigi.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import com.aman.gigi.model.BrushType
import com.aman.gigi.model.Point
import com.aman.gigi.model.Stroke as StrokeModel

/**
 * Handles different brush rendering styles
 */
object BrushEngine {

    fun DrawScope.renderStroke(stroke: StrokeModel, canvasWidth: Float, canvasHeight: Float) {
        if (stroke.points.size < 2) return

        when (stroke.brushType) {
            BrushType.MARKER -> renderMarker(stroke, canvasWidth, canvasHeight)
            BrushType.CALLIGRAPHY -> renderCalligraphy(stroke, canvasWidth, canvasHeight)
            BrushType.NEON -> renderNeon(stroke, canvasWidth, canvasHeight)
            BrushType.HIGHLIGHTER -> renderHighlighter(stroke, canvasWidth, canvasHeight)
        }
    }

    private fun DrawScope.renderMarker(stroke: StrokeModel, canvasWidth: Float, canvasHeight: Float) {
        val path = createPath(stroke.points, canvasWidth, canvasHeight)
        drawPath(
            path = path,
            color = Color(stroke.color).copy(alpha = stroke.opacity),
            style = Stroke(
                width = stroke.width,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    private fun DrawScope.renderCalligraphy(stroke: StrokeModel, canvasWidth: Float, canvasHeight: Float) {
        // Calligraphy uses multiple paths or a single path with varying width
        // Simplified version: use a custom path effect or draw segments
        val color = Color(stroke.color).copy(alpha = stroke.opacity)
        
        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            
            // Calculate dynamic width based on angle
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val angle = kotlin.math.atan2(dy, dx)
            
            // Calligraphy effect: thicker on vertical/diagonal, thinner on horizontal
            val weight = kotlin.math.abs(kotlin.math.sin(angle - 0.78f)) // 45 degree angle
            val dynamicWidth = stroke.width * (0.3f + 0.7f * weight)
            
            drawLine(
                color = color,
                start = Offset(p1.x * canvasWidth, p1.y * canvasHeight),
                end = Offset(p2.x * canvasWidth, p2.y * canvasHeight),
                strokeWidth = dynamicWidth,
                cap = StrokeCap.Square
            )
        }
    }

    private fun DrawScope.renderNeon(stroke: StrokeModel, canvasWidth: Float, canvasHeight: Float) {
        val path = createPath(stroke.points, canvasWidth, canvasHeight)
        val baseColor = Color(stroke.color)
        
        // 1. Draw professional diffusion glow using BlurMaskFilter
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = baseColor.toArgb()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = stroke.width * 2.5f
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                maskFilter = android.graphics.BlurMaskFilter(stroke.width * 1.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        }

        // 2. Add a secondary, more intense glow layer
        drawPath(
            path = path,
            color = baseColor.copy(alpha = 0.5f * stroke.opacity),
            style = Stroke(width = stroke.width * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // 3. Draw white-ish hot center (Core)
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.9f * stroke.opacity),
            style = Stroke(width = stroke.width * 0.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    private fun DrawScope.renderHighlighter(stroke: StrokeModel, canvasWidth: Float, canvasHeight: Float) {
        val path = createPath(stroke.points, canvasWidth, canvasHeight)
        
        // Highlighter uses Multiply blend mode and transparency
        drawPath(
            path = path,
            color = Color(stroke.color).copy(alpha = 0.4f * stroke.opacity),
            style = Stroke(
                width = stroke.width * 3f,
                cap = StrokeCap.Square,
                join = StrokeJoin.Bevel
            ),
            blendMode = BlendMode.Multiply
        )
    }

    private fun createPath(points: List<Point>, width: Float, height: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path
        
        path.moveTo(points[0].x * width, points[0].y * height)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x * width, points[i].y * height)
        }
        return path
    }
}
