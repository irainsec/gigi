package com.aman.gigi.utils

import kotlin.math.*

object LocationUtils {
    /**
     * Calculates distance between two points in kilometers using Haversine formula
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Radius of the earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Formats distance for display
     */
    fun formatDistance(km: Double): String {
        return when {
            km < 1 -> "${(km * 1000).toInt()}m away"
            km > 1000 -> "${String.format("%.1f", km / 1000)}k km away"
            else -> "${km.toInt()}km away"
        }
    }
}
