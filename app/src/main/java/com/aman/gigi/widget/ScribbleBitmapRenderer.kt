package com.aman.gigi.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import com.aman.gigi.model.BrushType
import com.aman.gigi.model.Scribble

object ScribbleBitmapRenderer {

    fun render(
        scribble: Scribble?,
        width: Int = 480,
        height: Int = 480,
        backgroundColor: Int = Color.TRANSPARENT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        }

        if (scribble == null) return bitmap

        // 1. Photo / Base64 Media Rendering
        val base64 = scribble.mediaBase64
        if (!base64.isNullOrBlank()) {
            runCatching {
                val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val imgBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (imgBitmap != null) {
                    val srcRect = android.graphics.Rect(0, 0, imgBitmap.width, imgBitmap.height)
                    val dstRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(imgBitmap, srcRect, dstRect, paint)
                }
            }
        }

        // 2. Vector Strokes Rendering
        if (scribble.strokes.isNotEmpty()) {
            val scaleFactor = width / 480f

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            scribble.strokes.forEach { stroke ->
                val strokeColor = stroke.color
                val strokeWidth = (stroke.width * scaleFactor).coerceAtLeast(2.5f)

                paint.color = strokeColor
                paint.strokeWidth = strokeWidth

                if (stroke.brushType == BrushType.NEON) {
                    // Soft neon glow underlay
                    val glowPaint = Paint(paint).apply {
                        this.strokeWidth = strokeWidth * 2.2f
                        this.color = (strokeColor and 0x00FFFFFF) or 0x44000000
                    }
                    drawStrokePath(canvas, stroke.points, width, height, glowPaint)
                }

                drawStrokePath(canvas, stroke.points, width, height, paint)
            }
        }

        return bitmap
    }

    private fun drawStrokePath(
        canvas: Canvas,
        points: List<com.aman.gigi.model.Point>,
        w: Int,
        h: Int,
        paint: Paint
    ) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            val pt = points.first()
            val fillPaint = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(pt.x * w, pt.y * h, paint.strokeWidth / 2f, fillPaint)
            return
        }

        val path = Path()
        val first = points.first()
        path.moveTo(first.x * w, first.y * h)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f * w
            val midY = (prev.y + curr.y) / 2f * h
            path.quadTo(prev.x * w, prev.y * h, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * w, last.y * h)

        canvas.drawPath(path, paint)
    }
}
