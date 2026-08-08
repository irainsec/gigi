package com.aman.gigi.utils

import androidx.compose.ui.geometry.Offset
import com.aman.gigi.model.Point

/**
 * Utility for smoothing strokes using Catmull-Rom spline interpolation
 */
object StrokeSmoothing {
    
    /**
     * Smooth a list of points using Catmull-Rom spline
     * 
     * @param points Raw input points
     * @param segmentsPerPoint Number of interpolated points between each pair
     * @return Smoothed list of points
     */
    fun smoothPoints(points: List<Point>, segmentsPerPoint: Int = 4): List<Point> {
        if (points.size < 2) return points
        if (points.size == 2) return points
        
        val smoothed = mutableListOf<Point>()
        
        // Add first point
        smoothed.add(points[0])
        
        // Interpolate between points
        for (i in 0 until points.size - 1) {
            val p0 = if (i == 0) points[0] else points[i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]
            
            // Generate interpolated points
            for (t in 0..segmentsPerPoint) {
                val tNorm = t.toFloat() / segmentsPerPoint
                val interpolated = catmullRomInterpolate(p0, p1, p2, p3, tNorm)
                smoothed.add(interpolated)
            }
        }
        
        // Add last point
        smoothed.add(points.last())
        
        return smoothed
    }
    
    /**
     * Catmull-Rom spline interpolation
     */
    private fun catmullRomInterpolate(
        p0: Point,
        p1: Point,
        p2: Point,
        p3: Point,
        t: Float
    ): Point {
        val t2 = t * t
        val t3 = t2 * t
        
        val x = 0.5f * (
            (2f * p1.x) +
            (-p0.x + p2.x) * t +
            (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
            (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
        )
        
        val y = 0.5f * (
            (2f * p1.y) +
            (-p0.y + p2.y) * t +
            (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
            (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
        )
        
        // Interpolate pressure as well
        val pressure = lerp(p1.pressure, p2.pressure, t)
        
        return Point(x, y, pressure, System.currentTimeMillis())
    }
    
    /**
     * Linear interpolation
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
    
    /**
     * Calculate simulated pressure based on velocity
     * Faster movement = lighter pressure
     */
    fun calculatePressure(velocity: Float, maxVelocity: Float = 5000f): Float {
        val normalizedVelocity = (velocity / maxVelocity).coerceIn(0f, 1f)
        return 1f - (normalizedVelocity * 0.5f) // Range: 0.5 to 1.0
    }
    
    /**
     * Calculate velocity between two points
     */
    fun calculateVelocity(p1: Point, p2: Point): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val timeDelta = (p2.timestamp - p1.timestamp).toFloat().coerceAtLeast(1f)
        return distance / timeDelta * 1000f // pixels per second
    }
}
