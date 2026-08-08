package com.aman.gigi.db

import androidx.room.TypeConverter
import com.aman.gigi.model.Point
import com.aman.gigi.model.Stroke
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converter for Room to handle List<Stroke>
 */
class StrokeListConverter {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStrokeList(strokes: List<Stroke>): String {
        return gson.toJson(strokes)
    }
    
    @TypeConverter
    fun toStrokeList(json: String): List<Stroke> {
        val type = object : TypeToken<List<Stroke>>() {}.type
        return gson.fromJson(json, type)
    }
}

/**
 * Type converter for Point list (if needed separately)
 */
class PointListConverter {
    private val gson = Gson()
    
    @TypeConverter
    fun fromPointList(points: List<Point>): String {
        return gson.toJson(points)
    }
    
    @TypeConverter
    fun toPointList(json: String): List<Point> {
        val type = object : TypeToken<List<Point>>() {}.type
        return gson.fromJson(json, type)
    }
}
