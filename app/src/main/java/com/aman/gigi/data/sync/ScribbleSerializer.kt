package com.aman.gigi.data.sync

import com.aman.gigi.model.Point
import com.aman.gigi.model.Scribble
import com.aman.gigi.model.Stroke
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

/**
 * Serializer for converting scribbles to/from JSON
 */
object ScribbleSerializer {
    
    private val gson = Gson()
    
    /**
     * Serialize scribble to JSON string
     */
    fun serialize(scribble: Scribble): String {
        return gson.toJson(scribble)
    }
    
    /**
     * Deserialize JSON string to scribble
     */
    fun deserialize(json: String): Scribble? {
        return try {
            gson.fromJson(json, Scribble::class.java)
        } catch (e: Exception) {
            android.util.Log.e("ScribbleSerializer", "Error deserializing scribble", e)
            null
        }
    }

    /**
     * Deserialize from an InputStream (memory optimization).
     * This avoids creating a massive intermediate JSON string.
     */
    fun deserializeFromStream(inputStream: java.io.InputStream): Scribble? {
        return try {
            val reader = com.google.gson.stream.JsonReader(inputStream.bufferedReader())
            val scribble = gson.fromJson<Scribble>(reader, Scribble::class.java)
            reader.close()
            scribble
        } catch (e: Exception) {
            android.util.Log.e("ScribbleSerializer", "Error deserializing from stream", e)
            null
        }
    }
    
    /**
     * Serialize to compact JSON (no whitespace)
     */
    fun serializeCompact(scribble: Scribble): String {
        return serialize(scribble)
    }
}
