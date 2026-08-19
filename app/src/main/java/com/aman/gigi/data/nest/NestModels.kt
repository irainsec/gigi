package com.aman.gigi.data.nest

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

enum class FacingDirection(val angle: Float) {
    DOWN(0f),
    UP(180f),
    LEFT(270f),
    RIGHT(90f)
}

enum class TwigiAction {
    IDLE,
    WALK,
    SIT_COUCH,
    SIT_DESK,
    SLEEP_BED,
    JAM_MUSIC
}

data class FurnitureItem(
    val id: String,
    val name: String,
    val type: String, // 'desk_computer', 'office_chair', 'bookshelf_large', 'cozy_bed', 'nightstand_lamp', 'sweetheart_sofa', 'coffee_table', 'turntable_station', 'heart_rug', 'mini_fridge', 'potted_plant', 'ac_unit', 'bulletin_board', 'wall_clock'
    val x: Float,
    val y: Float,
    val widthDp: Float = 60f,
    val heightDp: Float = 40f,
    val rotation: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("widthDp", widthDp.toDouble())
        put("heightDp", heightDp.toDouble())
        put("rotation", rotation)
    }

    companion object {
        fun fromJson(j: JSONObject): FurnitureItem = FurnitureItem(
            id = j.optString("id", "f_${System.currentTimeMillis()}"),
            name = j.optString("name", "Furniture"),
            type = j.optString("type", "heart_rug"),
            x = j.optDouble("x", 0.5).toFloat(),
            y = j.optDouble("y", 0.5).toFloat(),
            widthDp = j.optDouble("widthDp", 60.0).toFloat(),
            heightDp = j.optDouble("heightDp", 40.0).toFloat(),
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
    val x: Float = 0.52f,
    val y: Float = 0.72f,
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
                x = 0.52f,
                y = 0.72f,
                isSleeping = false
            )
        }
    }
}

data class NestRoomData(
    val connectionCode: String,
    val wallpaper: String = "apartment_light",
    val flooring: String = "office_grid",
    val roomMood: String = "cozy",
    val furniture: List<FurnitureItem> = defaultFurnitureList(),
    val fridgeNotes: List<FridgeNote> = emptyList(),
    val pet: PetState = PetState()
) {
    companion object {
        fun defaultFurnitureList(): List<FurnitureItem> = listOf(
            // Top-Left Zone: Work & Study Station
            FurnitureItem("f_desk", "Dual Monitor Workstation", "desk_computer", 0.26f, 0.32f, 90f, 48f),
            FurnitureItem("f_chair", "Ergonomic Swivel Chair", "office_chair", 0.26f, 0.38f, 32f, 32f),
            FurnitureItem("f_bookshelf", "Packed Library Bookshelf", "bookshelf_large", 0.08f, 0.24f, 48f, 60f),
            FurnitureItem("f_bulletin", "Bulletin Pinboard", "bulletin_board", 0.26f, 0.16f, 54f, 30f),

            // Top-Right Zone: Bedroom Rest Nook
            FurnitureItem("f_bed", "Cozy Canopy Bed", "cozy_bed", 0.80f, 0.28f, 75f, 70f),
            FurnitureItem("f_nightstand", "Bedside Lamp Stand", "nightstand_lamp", 0.62f, 0.24f, 28f, 36f),
            FurnitureItem("f_ac", "Wall Air Conditioner", "ac_unit", 0.50f, 0.14f, 56f, 22f),

            // Bottom-Left Zone: Living Lounge & Hearth
            FurnitureItem("f_rug", "Cozy Hearth Rug", "heart_rug", 0.30f, 0.70f, 100f, 60f),
            FurnitureItem("f_sofa", "Sweetheart Loveseat", "sweetheart_sofa", 0.30f, 0.66f, 85f, 42f),
            FurnitureItem("f_table", "Coffee Table with Mugs", "coffee_table", 0.30f, 0.75f, 52f, 26f),
            FurnitureItem("f_turntable", "Vintage Vinyl Station", "turntable_station", 0.08f, 0.60f, 36f, 42f),
            FurnitureItem("f_plant", "Leafy Monstera Pot", "potted_plant", 0.08f, 0.82f, 32f, 44f),

            // Bottom-Right Zone: Kitchenette & Snacks
            FurnitureItem("f_fridge", "Retro Pastel Mini-Fridge", "mini_fridge", 0.84f, 0.60f, 42f, 56f),
            FurnitureItem("f_dining", "Snack Counter Table", "coffee_table", 0.74f, 0.76f, 48f, 32f),
            FurnitureItem("f_clock", "Wall Clock", "wall_clock", 0.84f, 0.46f, 24f, 24f)
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
                wallpaper = j.optString("wallpaper", "apartment_light"),
                flooring = j.optString("flooring", "office_grid"),
                roomMood = j.optString("roomMood", "cozy"),
                furniture = fList,
                fridgeNotes = nList,
                pet = PetState.fromJson(j.optJSONObject("pet"))
            )
        }
    }
}

data class TwigiRoomPosition(
    val x: Float,
    val y: Float,
    val targetX: Float = x,
    val targetY: Float = y,
    val facing: FacingDirection = FacingDirection.DOWN,
    val isWalking: Boolean = false,
    val action: TwigiAction = TwigiAction.IDLE,
    val emote: String? = null
)

enum class TimeOfDay(val label: String, val skyColorTop: Color, val skyColorBottom: Color, val ambientTint: Color) {
    SUNRISE("Sunrise 🌅", Color(0xFFFDBA74), Color(0xFFF472B6), Color(0xFFFFEDD5).copy(alpha = 0.08f)),
    DAY("Daytime ☀️", Color(0xFF38BDF8), Color(0xFF93C5FD), Color.Transparent),
    SUNSET("Golden Dusk 🌇", Color(0xFFFB923C), Color(0xFF7C3AED), Color(0xFFFDE047).copy(alpha = 0.12f)),
    NIGHT("Starlit Night 🌙", Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF1E1B4B).copy(alpha = 0.28f))
}

data class WallpaperOption(val id: String, val name: String, val primaryColor: Color, val patternEmoji: String)
data class FlooringOption(val id: String, val name: String, val primaryColor: Color, val grainColor: Color)
