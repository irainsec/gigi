package com.aman.gigi.data.nest

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class FurnitureItem(
    val id: String,
    val name: String,
    val type: String, // 'bed', 'couch', 'music', 'fridge', 'plant', 'rug', 'lamp'
    val x: Float,
    val y: Float,
    val rotation: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("rotation", rotation)
    }

    companion object {
        fun fromJson(j: JSONObject): FurnitureItem = FurnitureItem(
            id = j.optString("id", "f_${System.currentTimeMillis()}"),
            name = j.optString("name", "Furniture"),
            type = j.optString("type", "rug"),
            x = j.optDouble("x", 0.5).toFloat(),
            y = j.optDouble("y", 0.5).toFloat(),
            rotation = j.optInt("rotation", 0)
        )
    }
}

data class FridgeNote(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val drawingUrl: String? = null,
    val color: String = "#FEF08A",
    val createdAt: String = ""
) {
    companion object {
        fun fromJson(j: JSONObject): FridgeNote = FridgeNote(
            id = j.optString("id", ""),
            authorId = j.optString("authorId", ""),
            authorName = j.optString("authorName", "Partner"),
            text = j.optString("text", ""),
            drawingUrl = j.optString("drawingUrl").takeIf { !it.isNullOrBlank() },
            color = j.optString("color", "#FEF08A"),
            createdAt = j.optString("createdAt", "")
        )
    }
}

data class PetState(
    val name: String = "Mochi",
    val type: String = "cat", // 'cat' or 'dog'
    val happiness: Int = 100,
    val hunger: Int = 80,
    val x: Float = 0.55f,
    val y: Float = 0.65f,
    val isSleeping: Boolean = false
) {
    companion object {
        fun fromJson(j: JSONObject?): PetState {
            if (j == null) return PetState()
            return PetState(
                name = j.optString("name", "Mochi"),
                type = j.optString("type", "cat"),
                happiness = j.optInt("happiness", 100),
                hunger = j.optInt("hunger", 80),
                x = 0.55f,
                y = 0.65f,
                isSleeping = false
            )
        }
    }
}

data class NestRoomData(
    val connectionCode: String,
    val wallpaper: String = "lavender_stars",
    val flooring: String = "warm_oak",
    val roomMood: String = "cozy",
    val furniture: List<FurnitureItem> = defaultFurnitureList(),
    val fridgeNotes: List<FridgeNote> = emptyList(),
    val pet: PetState = PetState()
) {
    companion object {
        fun defaultFurnitureList(): List<FurnitureItem> = listOf(
            FurnitureItem("f_bed_1", "Cozy Canopy Bed", "bed", 0.22f, 0.38f),
            FurnitureItem("f_couch_1", "Sweetheart Loveseat", "couch", 0.72f, 0.52f),
            FurnitureItem("f_vinyl_1", "Vintage Turntable", "music", 0.82f, 0.28f),
            FurnitureItem("f_fridge_1", "Pastel Mini-Fridge", "fridge", 0.38f, 0.24f),
            FurnitureItem("f_plant_1", "Lucky Bonsai", "plant", 0.12f, 0.65f),
            FurnitureItem("f_rug_1", "Heart Cloud Rug", "rug", 0.50f, 0.62f)
        )

        fun fromJson(j: JSONObject): NestRoomData {
            val fArr = j.optJSONArray("furniture") ?: JSONArray()
            val fList = mutableListOf<FurnitureItem>()
            for (i in 0 until fArr.length()) {
                fArr.optJSONObject(i)?.let { fList.add(FurnitureItem.fromJson(it)) }
            }
            if (fList.isEmpty()) fList.addAll(defaultFurnitureList())

            val nArr = j.optJSONArray("fridgeNotes") ?: JSONArray()
            val nList = mutableListOf<FridgeNote>()
            for (i in 0 until nArr.length()) {
                nArr.optJSONObject(i)?.let { nList.add(FridgeNote.fromJson(it)) }
            }

            return NestRoomData(
                connectionCode = j.optString("connectionCode", ""),
                wallpaper = j.optString("wallpaper", "lavender_stars"),
                flooring = j.optString("flooring", "warm_oak"),
                roomMood = j.optString("roomMood", "cozy"),
                furniture = fList,
                fridgeNotes = nList,
                pet = PetState.fromJson(j.optJSONObject("pet"))
            )
        }
    }
}

enum class TwigiAction {
    IDLE,
    WALK,
    SIT_COUCH,
    SLEEP_BED,
    JAM_MUSIC
}

data class TwigiRoomPosition(
    val x: Float,
    val y: Float,
    val targetX: Float = x,
    val targetY: Float = y,
    val facingLeft: Boolean = false,
    val action: TwigiAction = TwigiAction.IDLE,
    val emote: String? = null
)

enum class TimeOfDay(val label: String, val skyColorTop: Color, val skyColorBottom: Color, val ambientTint: Color) {
    SUNRISE("Sunrise 🌅", Color(0xFFFDBA74), Color(0xFFF472B6), Color(0xFFFFEDD5).copy(alpha = 0.12f)),
    DAY("Daytime ☀️", Color(0xFF38BDF8), Color(0xFF93C5FD), Color.Transparent),
    SUNSET("Golden Dusk 🌇", Color(0xFFFB923C), Color(0xFF7C3AED), Color(0xFFFDE047).copy(alpha = 0.16f)),
    NIGHT("Starlit Night 🌙", Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF1E1B4B).copy(alpha = 0.35f))
}

data class WallpaperOption(val id: String, val name: String, val primaryColor: Color, val patternEmoji: String)
data class FlooringOption(val id: String, val name: String, val primaryColor: Color, val grainColor: Color)
