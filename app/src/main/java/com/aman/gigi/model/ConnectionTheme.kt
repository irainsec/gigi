package com.aman.gigi.model

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Relationship type enum
// ---------------------------------------------------------------------------
enum class RelationshipType {
    ROMANTIC,
    BESTIE,
    FRIENDSHIP,
    FAMILY,
    GROUP;

    companion object {
        fun fromString(value: String?): RelationshipType {
            if (value.equals("BEST_FRIEND", ignoreCase = true) || value.equals("BESTIE", ignoreCase = true)) return BESTIE
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ROMANTIC
        }
    }
}

// ---------------------------------------------------------------------------
// Theme data class
// ---------------------------------------------------------------------------
data class ConnectionTheme(
    val type: RelationshipType,
    val primaryColor: Color,
    val accentColor: Color,
    val softColor: Color,                  // Lighter tint for glass surfaces
    val backgroundColors: List<Color>,
    val darkBackgroundColors: List<Color>, // Dark mode background variant
    val orbColor1: Color,
    val orbColor2: Color,
    val orbColor3: Color,
    val centerIconEmoji: String,           // Emoji for the center icon card
    val centerCardLabel: String,           // "with Love 💕" / "Besties Forever 🌟" / etc.
    val timerLabel: String,               // "Together for" / "Besties for" / "Family for"
    val anniversaryLabel: String,         // "Anniversary in" / "Besties since" / "Together since"
    val doodleCardTitle: String,
    val doodleCardSubtitle: String,
    val sparkleCardTitle: String,
    val sparkleCardSubtitle: String,
    val connectedLabel: String,           // "Connected with" / "Chatting with" / "Bestie Hub"
    val displayName: String,              // Human readable "Romantic", "Bestie", "Friendship", "Family"
    val badgeEmoji: String,               // Small badge emoji for partner list
)

// ---------------------------------------------------------------------------
// Theme Definitions
// ---------------------------------------------------------------------------

val RomanticTheme = ConnectionTheme(
    type = RelationshipType.ROMANTIC,
    primaryColor = Color(0xFFE91E63),
    accentColor = Color(0xFFFF80AB),
    softColor = Color(0xFFFCE4EC),
    backgroundColors = listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD9), Color(0xFFFFF9C4)),
    darkBackgroundColors = listOf(Color(0xFF1A0A12), Color(0xFF2A0D1E), Color(0xFF1C1528)),
    orbColor1 = Color(0xFFE91E63).copy(alpha = 0.15f),
    orbColor2 = Color(0xFFFF80AB).copy(alpha = 0.12f),
    orbColor3 = Color(0xFFFF4081).copy(alpha = 0.10f),
    centerIconEmoji = "❤️",
    centerCardLabel = "with Love 💕",
    timerLabel = "Together for",
    anniversaryLabel = "Anniversary in",
    doodleCardTitle = "Doodle of Love",
    doodleCardSubtitle = "Send a cute sketch",
    sparkleCardTitle = "Share a Sparkle",
    sparkleCardSubtitle = "Videos & Selfies",
    connectedLabel = "Connected with",
    displayName = "Romantic 💕",
    badgeEmoji = "❤️",
)

val BestieTheme = ConnectionTheme(
    type = RelationshipType.BESTIE,
    primaryColor = Color(0xFFF59E0B),
    accentColor = Color(0xFFFBBF24),
    softColor = Color(0xFFFEF3C7),
    backgroundColors = listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A), Color(0xFFFEF9C3)),
    darkBackgroundColors = listOf(Color(0xFF1E1504), Color(0xFF2A1C08), Color(0xFF1F1B0B)),
    orbColor1 = Color(0xFFF59E0B).copy(alpha = 0.18f),
    orbColor2 = Color(0xFFFBBF24).copy(alpha = 0.15f),
    orbColor3 = Color(0xFFD97706).copy(alpha = 0.12f),
    centerIconEmoji = "🌟",
    centerCardLabel = "Besties Forever 🌟",
    timerLabel = "Besties for",
    anniversaryLabel = "Besties since",
    doodleCardTitle = "Bestie Doodle",
    doodleCardSubtitle = "Send a fun sketch",
    sparkleCardTitle = "Send Bestie Sparkle",
    sparkleCardSubtitle = "Crazy Clips & Selfies",
    connectedLabel = "Bestie Hub",
    displayName = "Bestie 🌟",
    badgeEmoji = "🌟",
)

val FriendshipTheme = ConnectionTheme(
    type = RelationshipType.FRIENDSHIP,
    primaryColor = Color(0xFF1976D2),
    accentColor = Color(0xFF64B5F6),
    softColor = Color(0xFFE3F2FD),
    backgroundColors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFFE8F5E9)),
    darkBackgroundColors = listOf(Color(0xFF080F1A), Color(0xFF0D1829), Color(0xFF0A1520)),
    orbColor1 = Color(0xFF1976D2).copy(alpha = 0.15f),
    orbColor2 = Color(0xFF64B5F6).copy(alpha = 0.12f),
    orbColor3 = Color(0xFF03A9F4).copy(alpha = 0.10f),
    centerIconEmoji = "🤝",
    centerCardLabel = "Best Friends 🤝",
    timerLabel = "Friends for",
    anniversaryLabel = "Friendiversary in",
    doodleCardTitle = "Doodle for You",
    doodleCardSubtitle = "Send a fun sketch",
    sparkleCardTitle = "Send a Smile",
    sparkleCardSubtitle = "Videos & Funny Clips",
    connectedLabel = "Chatting with",
    displayName = "Friendship 🤝",
    badgeEmoji = "🤝",
)

val FamilyTheme = ConnectionTheme(
    type = RelationshipType.FAMILY,
    primaryColor = Color(0xFF388E3C),
    accentColor = Color(0xFF81C784),
    softColor = Color(0xFFE8F5E9),
    backgroundColors = listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9), Color(0xFFE0F2F1)),
    darkBackgroundColors = listOf(Color(0xFF080F09), Color(0xFF0D1A0E), Color(0xFF091512)),
    orbColor1 = Color(0xFF388E3C).copy(alpha = 0.15f),
    orbColor2 = Color(0xFF81C784).copy(alpha = 0.12f),
    orbColor3 = Color(0xFF66BB6A).copy(alpha = 0.10f),
    centerIconEmoji = "🏠",
    centerCardLabel = "Family Time 🏠",
    timerLabel = "Family for",
    anniversaryLabel = "Together since",
    doodleCardTitle = "Family Doodle",
    doodleCardSubtitle = "Send a warm sketch",
    sparkleCardTitle = "Share a Memory",
    sparkleCardSubtitle = "Photos & Videos",
    connectedLabel = "Family:",
    displayName = "Family 🏠",
    badgeEmoji = "🏠",
)

// ---------------------------------------------------------------------------
// Extension + CompositionLocal
// ---------------------------------------------------------------------------

val GroupTheme = ConnectionTheme(
    type = RelationshipType.GROUP,
    primaryColor = Color(0xFF7C4DFF),
    accentColor = Color(0xFFB388FF),
    softColor = Color(0xFFEDE7F6),
    backgroundColors = listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9), Color(0xFFE8EAF6)),
    darkBackgroundColors = listOf(Color(0xFF0D0A1A), Color(0xFF160D2A), Color(0xFF0F0D20)),
    orbColor1 = Color(0xFF7C4DFF).copy(alpha = 0.15f),
    orbColor2 = Color(0xFFB388FF).copy(alpha = 0.12f),
    orbColor3 = Color(0xFF651FFF).copy(alpha = 0.10f),
    centerIconEmoji = "👥",
    centerCardLabel = "in the group ✨",
    timerLabel = "Active together",
    anniversaryLabel = "Group since",
    doodleCardTitle = "Group Doodle",
    doodleCardSubtitle = "Doodle to everyone",
    sparkleCardTitle = "Share a Sparkle",
    sparkleCardSubtitle = "Send to the group",
    connectedLabel = "Group Hub",
    displayName = "Group 👥",
    badgeEmoji = "👥",
)

fun RelationshipType.toTheme(): ConnectionTheme = when (this) {
    RelationshipType.ROMANTIC -> RomanticTheme
    RelationshipType.BESTIE -> BestieTheme
    RelationshipType.FRIENDSHIP -> FriendshipTheme
    RelationshipType.FAMILY -> FamilyTheme
    RelationshipType.GROUP -> GroupTheme
}

fun String?.toConnectionTheme(): ConnectionTheme =
    RelationshipType.fromString(this).toTheme()

val LocalConnectionTheme = compositionLocalOf { RomanticTheme }
