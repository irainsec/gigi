package com.aman.gigi.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.aman.gigi.db.StrokeListConverter

/**
 * Represents a scribble (drawing) sent or received
 */
@Entity(tableName = "scribbles")
@TypeConverters(StrokeListConverter::class)
data class Scribble(
    @PrimaryKey
    val scribbleId: String,
    val connectionId: String,
    val senderDeviceId: String? = null,
    val strokes: List<Stroke>,
    val isSent: Boolean, // true if sent by this user, false if received
    val status: ScribbleStatus = ScribbleStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val receivedAt: Long? = null,
    val displayedAt: Long? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val mediaBase64: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mood: String? = null,
    val revealType: String? = null,
    val secretMessage: String? = null,
    val meetingDate: Long? = null,
    val anniversaryDate: Long? = null
)

/**
 * Represents a single stroke in a scribble
 */
data class Stroke(
    val points: List<Point>,
    val color: Int, // ARGB color
    val width: Float, // Base stroke width
    val brushType: BrushType = BrushType.MARKER,
    val opacity: Float = 1.0f,
    val effects: List<EffectType> = emptyList(),
    val animationType: AnimationType = AnimationType.NONE,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a point in a stroke
 */
data class Point(
    val x: Float, // Normalized 0-1
    val y: Float, // Normalized 0-1
    val pressure: Float = 1.0f, // Simulated pressure based on velocity
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Brush types for advanced drawing
 */
enum class BrushType {
    MARKER,
    CALLIGRAPHY,
    NEON,
    HIGHLIGHTER
}

/**
 * Effect types for strokes
 */
enum class EffectType {
    GLOW,
    PARTICLES,
    ANIMATE
}

/**
 * Animation types for strokes
 */
enum class AnimationType {
    NONE,
    REVEAL,
    FLOW,
    FADE_IN,
    PULSE
}

/**
 * Status of a scribble
 */
enum class ScribbleStatus {
    PENDING,      // Created, not yet sent
    SENDING,      // Currently uploading
    SENT,         // Successfully sent
    RECEIVED,     // Received from partner
    DISPLAYING,   // Currently being displayed
    DISPLAYED,    // Already shown
    FAILED        // Failed to send
}
