package com.aman.gigi.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.*
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aman.gigi.model.LocalSong
import com.aman.gigi.model.MusicAlbum
import com.aman.gigi.ui.components.GlassNavActionPill
import com.aman.gigi.viewmodel.MusicUiState
import com.aman.gigi.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.aman.gigi.ui.components.RomanceAmbientDecor

private enum class RecordKind {
    CLASSIC_BLACK,
    SPLASH_BLUE,
    SMOKE_MARBLE,
    CHAMPAGNE_MARBLE,
    ROSE_BLUSH
}

private enum class SwipeAxis {
    Horizontal,
    Vertical
}

private data class MusicPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val accent: Color,
    val accentSoft: Color,
    val textStrong: Color,
    val textMuted: Color,
    val recordKind: RecordKind,
    val isDark: Boolean = false
)

private data class ThemeGroup(
    val cuteName: String,
    val description: String,
    val presets: List<PlayerThemePreset>
)

private data class VinylGroup(
    val cuteName: String,
    val description: String,
    val options: List<RecordFinishOption>
)

private enum class PlayerThemePreset(
    val title: String,
    val subtitle: String
) {
    AUTO("Auto", "Follow the album art"),

    // Group 1: 🌸 "四季の詩" (Poetry of Seasons)
    SAKURA_SPRING("Sakura Spring", "Soft pink petals & golden dawn"),
    MIDNIGHT_SUMMER("Midnight Sun", "Amber horizon under violet skylines"),
    MAPLE_WHISPER("Maple Whisper", "Rustic crimson leaves & golden amber"),
    AURORA_FROST("Aurora Frost", "Ethereal cosmic teal & polar snow"),

    // Group 2: 🧸 "Dreamy Hideaway"
    VINTAGE_TEDDY("Vintage Teddy", "Warm cocoa, hazelnut & beige"),
    PASTEL_PAJAMAS("Pastel Pajamas", "Lavender, sweet peach & cream"),
    RAINY_WINDOW("Rainy Window", "Slate blue mist & warm amber light"),

    // Group 3: 🌌 "Aetherial Whispers"
    STARDUST_LULLABY("Stardust Lullaby", "Nebula purple, cobalt & star drift"),
    LUNAR_ECLIPSE("Lunar Eclipse", "Shadowy velvet black & crimson glow"),
    PIXIE_DUST("Pixie Dust", "Glowing emerald green & fairy dust"),

    // Group 4: 🍵 "Tea Time Stories"
    MATCHA_LATTE("Matcha Latte", "Sage green, toasted sesame & cream"),
    EARL_GREY("Earl Grey", "Bergamot slate-blue, lavender & silver"),
    CAMOMILE_MEADOW("Camomile Meadow", "Daisy white, buttercup & wild grass"),

    // Group 5: 🎮 "Retro Arcade"
    NEON_ODYSSEY("Neon Odyssey", "Cyberpunk magenta, cyan & grid scan"),
    DUNGEON_8BIT("8-Bit Dungeon", "Terminal green & retro CRT lines"),
    SYNTHWAVE_HIGHWAY("Synthwave Highway", "Sunset violet, solar orange & grid"),

    // Group 6: 🐚 "Oceanic Secrets"
    DEEP_CORAL("Deep Coral", "Living coral pink, sand & turquoise"),
    ABYSSAL_GLOW("Abyssal Glow", "Midnight navy & bioluminescent green"),
    PEARL_SHELL("Pearl Shell", "Iridescent white, pink & seafoam"),

    // Group 7: 📚 "Library of Winds"
    OLD_PARCHMENT("Old Parchment", "Sepia tones, mahogany & gold leaf"),
    POETS_INK("Poet's Ink", "Obsidian black, typewriter gray & red wax"),
    FOREST_HERBAL("Forest Herbal", "Sage green, pressed fern & moss"),

    // Group 8: 🍭 "Candy Land Carousel"
    COTTON_CANDY("Cotton Candy", "Swirling bubblegum pink & sky blue"),
    SOUR_LEMONADE("Sour Lemonade", "Neon citrus yellow & sharp lime"),
    CHOCO_MINT("Choco Mint", "Creamy dark chocolate & spearmint"),

    // Group 9: 🏜️ "Wandering Dunes"
    SAHARA_SUNSET("Sahara Sunset", "Sand gold, desert rose & crimson sky"),
    OASIS_MIRAGE("Oasis Mirage", "Cool turquoise, palm emerald & sky blue"),
    RED_CANYON("Red Canyon", "Rust orange, sandstone & violet twilight"),

    // Group 10: 🔮 "Mystic Tarot"
    THE_STAR("The Star", "Midnight indigo, silver & white stars"),
    THE_FOOL("The Fool", "Sun-kissed gold, wildflowers & blue sky"),
    THE_MAGICIAN("The Magician", "Velvet purple, metallic gold & crimson")
}

private val themeGroups = listOf(
    ThemeGroup("🌸 四季の詩", "Worldwide seasonal visual journeys", listOf(
        PlayerThemePreset.SAKURA_SPRING,
        PlayerThemePreset.MIDNIGHT_SUMMER,
        PlayerThemePreset.MAPLE_WHISPER,
        PlayerThemePreset.AURORA_FROST
    )),
    ThemeGroup("🧸 Dreamy", "Nostalgic, warm & cozy bedroom aesthetics", listOf(
        PlayerThemePreset.VINTAGE_TEDDY,
        PlayerThemePreset.PASTEL_PAJAMAS,
        PlayerThemePreset.RAINY_WINDOW
    )),
    ThemeGroup("🌌 Cosmic", "Nebulas, magical dusts & stellar fields", listOf(
        PlayerThemePreset.STARDUST_LULLABY,
        PlayerThemePreset.LUNAR_ECLIPSE,
        PlayerThemePreset.PIXIE_DUST
    )),
    ThemeGroup("🍵 Tea Time", "Calming, herbal-toned cafe settings", listOf(
        PlayerThemePreset.MATCHA_LATTE,
        PlayerThemePreset.EARL_GREY,
        PlayerThemePreset.CAMOMILE_MEADOW
    )),
    ThemeGroup("🎮 Arcade", "Nostalgic scanning lines & cyberpunk glows", listOf(
        PlayerThemePreset.NEON_ODYSSEY,
        PlayerThemePreset.DUNGEON_8BIT,
        PlayerThemePreset.SYNTHWAVE_HIGHWAY
    )),
    ThemeGroup("🐚 Marine", "Bioluminescent coral reefs & deep sea pearls", listOf(
        PlayerThemePreset.DEEP_CORAL,
        PlayerThemePreset.ABYSSAL_GLOW,
        PlayerThemePreset.PEARL_SHELL
    )),
    ThemeGroup("📚 Library", "Ancient sepia, red ink & pressed herbal bindings", listOf(
        PlayerThemePreset.OLD_PARCHMENT,
        PlayerThemePreset.POETS_INK,
        PlayerThemePreset.FOREST_HERBAL
    )),
    ThemeGroup("🍭 Carousel", "Vibrant, playful sweets & creamy spearmints", listOf(
        PlayerThemePreset.COTTON_CANDY,
        PlayerThemePreset.SOUR_LEMONADE,
        PlayerThemePreset.CHOCO_MINT
    )),
    ThemeGroup("🏜️ Dunes", "Warm sandstone, wind dunes & canyon solitudes", listOf(
        PlayerThemePreset.SAHARA_SUNSET,
        PlayerThemePreset.OASIS_MIRAGE,
        PlayerThemePreset.RED_CANYON
    )),
    ThemeGroup("🔮 Tarot", "Fortune-telling, celestial stars & esoteric sigils", listOf(
        PlayerThemePreset.THE_STAR,
        PlayerThemePreset.THE_FOOL,
        PlayerThemePreset.THE_MAGICIAN
    ))
)

private enum class RecordFinishOption(
    val title: String,
    val subtitle: String
) {
    AUTO("Auto", "Follow theme style"),

    // Group 1: 🔮 Standard Cuts
    CLASSIC_BLACK("Audiophile Black", "Classic fine groove press"),
    CRYSTAL_TRANSLUCENT("Crystal Ice", "Crystalline clear press"),
    SMOKY_CHARCOAL("Smoky Charcoal", "Dark mist-hazed translucent"),

    // Group 2: 🌌 Nebula Pressings
    GALAXY_DUST("Galaxy Dust", "Indigo base with cosmic splashes"),
    SUPERNOVA("Supernova", "Solar orange flare explosion"),
    COSMIC_AURORA("Cosmic Aurora", "Bioluminescent space violet glow"),

    // Group 3: 🍀 Terrarium Blends
    MOSS_JADE("Moss Jade", "Emerald green with organic veins"),
    AMBER_SAP("Amber Sap", "Honey gold with wooden age lines"),
    AUTUMN_FOREST("Autumn Forest", "Moss green with foliage accents"),

    // Group 4: 🧁 Sweet Confection
    CANDY_SWIRL("Candy Swirl", "Peppermint red & white pinwheel"),
    CHOCO_CARAMEL("Choco Caramel", "Chocolate base with golden caramel"),
    BERRY_SPRINKLES("Berry Sprinkles", "Strawberry pink with sugar sprinkles"),

    // Group 5: ⚡ Cyberpunk Tracks
    HOLOGRAM_DISC("Hologram Disc", "Iridescent rainbow sweep reflection"),
    GRID_LASER("Grid Laser", "Concentric glowing green laser lines"),
    DATA_STREAM("Data Stream", "Radial glowing cyan binary traces"),

    // Group 6: 🐚 Ocean Secrets
    ABYSSAL_CURRENT("Abyssal Current", "Deep navy with bioluminescent rings"),
    PEARL_OYSTER("Pearl Oyster", "Nacreous white with pink reflections"),
    DEEP_REEF("Deep Reef", "Turquoise with golden coral splatters"),

    // Group 7: 🌸 Blossom Pressed
    SAKURA_RESIN("Sakura Resin", "Clear press with pink blossom petals"),
    GOLDEN_GINGKO("Golden Gingko", "Translucent yellow with gold gingko veins"),
    PRESSED_LAVENDER("Pressed Lavender", "Soft lilac base with lavender stalks"),

    // Group 8: ❄️ Glacial Frost
    CRACKED_ICE("Cracked Ice", "Glacial white with crystalline fissures"),
    SNOW_BLIZZARD("Snow Blizzard", "Powder blue with white snow specks"),
    GLACIER_MELT("Glacier Melt", "Deep blue center fading to frost clear"),

    // Group 9: 🏜️ Desert Sands
    SAHARA_DUNE("Sahara Dune", "Gold press with sand wind ripple lines"),
    RED_CANYON("Red Canyon", "Earthy rust orange & canyon sandstone"),
    MIRAGE_BLUE("Mirage Blue", "Heat-wave golden aquamarine mirror"),

    // Group 10: 🃏 Arcane Runes
    TAROT_GOLD("Tarot Gold", "Velvet black with gold astrology sigils"),
    SPELL_CIRCLE("Spell Circle", "Glowing alchemical runic circles"),
    CRIMSON_ELIXIR("Crimson Elixir", "Translucent ruby with transmutation grid"),
    FIRE_VINYL("Fire Vinyl", "Swirling fiery flares & glowing embers")
}

private val vinylGroups = listOf(
    VinylGroup("🔮 Standard", "High-fidelity audiophile standard pressings", listOf(
        RecordFinishOption.CLASSIC_BLACK,
        RecordFinishOption.CRYSTAL_TRANSLUCENT,
        RecordFinishOption.SMOKY_CHARCOAL
    )),
    VinylGroup("🌌 Nebula", "Cosmic splatters & auroral solar flares", listOf(
        RecordFinishOption.GALAXY_DUST,
        RecordFinishOption.SUPERNOVA,
        RecordFinishOption.COSMIC_AURORA
    )),
    VinylGroup("🍀 Terrarium", "Organic woodgrain & moss jade crystalline", listOf(
        RecordFinishOption.MOSS_JADE,
        RecordFinishOption.AMBER_SAP,
        RecordFinishOption.AUTUMN_FOREST
    )),
    VinylGroup("🧁 Sweet", "Delicious peppermint swirls & chocolate Mint", listOf(
        RecordFinishOption.CANDY_SWIRL,
        RecordFinishOption.CHOCO_CARAMEL,
        RecordFinishOption.BERRY_SPRINKLES
    )),
    VinylGroup("⚡ Cyber", "Bioluminescent grids, scan lines & digital laser waves", listOf(
        RecordFinishOption.HOLOGRAM_DISC,
        RecordFinishOption.GRID_LASER,
        RecordFinishOption.DATA_STREAM
    )),
    VinylGroup("🐚 Ocean", "Bioluminescent abyssal teal & pearl shell oyster", listOf(
        RecordFinishOption.ABYSSAL_CURRENT,
        RecordFinishOption.PEARL_OYSTER,
        RecordFinishOption.DEEP_REEF
    )),
    VinylGroup("🌸 Blossom", "Pressed lavender stalks & resin sakura petals", listOf(
        RecordFinishOption.SAKURA_RESIN,
        RecordFinishOption.GOLDEN_GINGKO,
        RecordFinishOption.PRESSED_LAVENDER
    )),
    VinylGroup("❄️ Glacial", "Powder snow flurries, cracked frost & glacial cores", listOf(
        RecordFinishOption.CRACKED_ICE,
        RecordFinishOption.SNOW_BLIZZARD,
        RecordFinishOption.GLACIER_MELT
    )),
    VinylGroup("🏜️ Sand", "Warm sandstone dunes & terracotta canyons", listOf(
        RecordFinishOption.SAHARA_DUNE,
        RecordFinishOption.RED_CANYON,
        RecordFinishOption.MIRAGE_BLUE
    )),
    VinylGroup("🔮 Tarot", "Fortune-telling, celestial stars & esoteric sigils", listOf(
        RecordFinishOption.TAROT_GOLD,
        RecordFinishOption.SPELL_CIRCLE,
        RecordFinishOption.CRIMSON_ELIXIR,
        RecordFinishOption.FIRE_VINYL
    ))
)

private enum class TonearmFinish(
    val title: String,
    val subtitle: String
) {
    SILVER("Silver", "Classic brushed metal"),
    CHAMPAGNE("Champagne", "Warm brass-chrome blend"),
    ROSE_GOLD("Rose Gold", "Soft pink metal sheen"),
    GRAPHITE("Graphite", "Modern darker arm")
}

private enum class LabelTone(
    val title: String,
    val subtitle: String
) {
    IVORY("Ivory", "Paper label"),
    BLUSH("Blush", "Rosy boutique paper"),
    NOIR("Noir", "Dark collector label")
}

private enum class AdvancedEditorTab {
    VINYL,
    EFFECT,
    LABEL
}

private data class VinylThemeConfig(
    val preset: PlayerThemePreset = PlayerThemePreset.AUTO,
    val recordFinish: RecordFinishOption = RecordFinishOption.AUTO,
    val tonearmFinish: TonearmFinish = TonearmFinish.SILVER,
    val labelTone: LabelTone = LabelTone.IVORY,
    val glowAmount: Float = 0.62f,
    val shadowAmount: Float = 0.58f,
    val vinylTint: Color = Color.Transparent,
    val vinylTintAlpha: Float = 0f,
    val effectTint: Color = Color.Transparent,
    val effectTintAlpha: Float = 0f,
    val labelTint: Color = Color.Transparent,
    val labelTintAlpha: Float = 0f,
    val animateEffects: Boolean = true
)

private data class TonearmColors(
    val bodyDark: Color,
    val bodyMid: Color,
    val bodyLight: Color,
    val ring: Color,
    val accent: Color
)

private data class LabelToneColors(
    val base: Color,
    val ring: Color,
    val text: Color,
    val spindleOuter: Color,
    val spindleInner: Color
)

private data class PersistedVinylThemeState(
    val preset: PlayerThemePreset = PlayerThemePreset.AUTO,
    val recordFinish: RecordFinishOption = RecordFinishOption.AUTO,
    val tonearmFinish: TonearmFinish = TonearmFinish.SILVER,
    val labelTone: LabelTone = LabelTone.IVORY,
    val glowAmount: Float = 0.62f,
    val shadowAmount: Float = 0.58f,
    val vinylTintArgb: Int = Color.White.toArgb(),
    val vinylTintAlpha: Float = 0f,
    val effectTintArgb: Int = Color(0xFF6A6A6A).toArgb(),
    val effectTintAlpha: Float = 0f,
    val labelTintArgb: Int = Color(0xFFF7F3EE).toArgb(),
    val labelTintAlpha: Float = 0f,
    val animateEffects: Boolean = true
)

private const val PLAYER_THEME_PREFS_NAME = "music_player_theme"
private const val PREF_THEME_PRESET = "theme_preset"
private const val PREF_RECORD_FINISH = "record_finish"
private const val PREF_TONEARM_FINISH = "tonearm_finish"
private const val PREF_LABEL_TONE = "label_tone"
private const val PREF_GLOW_AMOUNT = "glow_amount"
private const val PREF_SHADOW_AMOUNT = "shadow_amount"
private const val PREF_VINYL_TINT = "vinyl_tint"
private const val PREF_VINYL_TINT_ALPHA = "vinyl_tint_alpha"
private const val PREF_EFFECT_TINT = "effect_tint"
private const val PREF_EFFECT_TINT_ALPHA = "effect_tint_alpha"
private const val PREF_LABEL_TINT = "label_tint"
private const val PREF_LABEL_TINT_ALPHA = "label_tint_alpha"
private const val PREF_ANIMATE_EFFECTS = "animate_effects"
private const val RECORD_SPIN_CYCLE_MS = 4600f

private val musicPalettes = listOf(
    MusicPalette(
        backgroundTop = Color(0xFF404040),
        backgroundBottom = Color(0xFF383838),
        accent = Color(0xFFD4A84E),
        accentSoft = Color(0xFF4A4A4A),
        textStrong = Color(0xFFF0F0F0),
        textMuted = Color(0xFFAAAAAA),
        recordKind = RecordKind.SMOKE_MARBLE,
        isDark = true
    )
)

@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel(),
    onBottomNavVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeConnections by viewModel.activeConnections.collectAsStateWithLifecycle(initialValue = emptyList())
    var hasPermission by remember { mutableStateOf(hasMusicLibraryPermission(context)) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showLyricsOverlay by remember { mutableStateOf(false) }
    var showLyricsPostcard by remember { mutableStateOf(false) }
    var isHeartbeatActive by remember { mutableStateOf(false) }
    val isAlbumBrowserOpen by viewModel.isAlbumBrowserOpen.collectAsStateWithLifecycle()
    val isMusicSettingsOpen by viewModel.isMusicSettingsOpen.collectAsStateWithLifecycle()

    BackHandler(enabled = isAlbumBrowserOpen || isMusicSettingsOpen) {
        if (isAlbumBrowserOpen) viewModel.setAlbumBrowserOpen(false)
        if (isMusicSettingsOpen) viewModel.setMusicSettingsOpen(false)
    }

    val activity = context as? Activity
    val isLockscreen = activity?.javaClass?.simpleName == "LockscreenPlayerActivity"
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val permissionToRequest = remember { requiredMusicPermission() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted || hasMusicLibraryPermission(context)
    }

    LaunchedEffect(hasPermission) {
        viewModel.onPermissionStateChanged(hasPermission)
        if (hasPermission) {
            viewModel.scanDeviceMusic()
        }
    }

    DisposableEffect(context) {
        val lifecycle = (context as? androidx.activity.ComponentActivity)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = hasMusicLibraryPermission(context)
            }
        }

        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val librarySongs = uiState.songs
    val albums = uiState.albums
    val activeAlbum = remember(uiState.activeAlbumId, albums) {
        albums.firstOrNull { it.albumId == uiState.activeAlbumId }
    }
    val songs = uiState.playbackQueue
        .takeIf { it.isNotEmpty() }
        ?: activeAlbum?.tracks?.takeIf { it.isNotEmpty() }
        ?: librarySongs
    val fallbackPage = remember(uiState.currentSongId, songs) {
        songs.indexOfFirst { it.id == uiState.currentSongId }.takeIf { it >= 0 } ?: 0
    }

    if (!hasPermission) {
        MusicPermissionGate(
            modifier = modifier,
            onRequestPermission = {
                if (permissionToRequest != null) {
                    permissionLauncher.launch(permissionToRequest)
                } else {
                    hasPermission = hasMusicLibraryPermission(context)
                }
            },
            onOpenSettings = { openAppSettings(context) },
            showRationale = activity?.let {
                permissionToRequest != null && it.shouldShowRequestPermissionRationale(permissionToRequest)
            } == true
        )
        return
    }

    if (uiState.isScanning && librarySongs.isEmpty()) {
        MusicLoadingCard(modifier = modifier)
        return
    }

    if (librarySongs.isEmpty()) {
        EmptyMusicScene(
            modifier = modifier,
            isScanning = uiState.isScanning,
            onRefresh = viewModel::scanDeviceMusic,
            errorMessage = uiState.errorMessage
        )
        return
    }

    val dragOffset = remember { Animatable(0f) }
    val libraryProgress = remember { Animatable(0f) }
    val albumListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val themePreferences = remember(context) {
        context.getSharedPreferences(PLAYER_THEME_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val initialThemeState = remember(themePreferences) {
        loadPersistedVinylThemeState(themePreferences)
    }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var selectedLoadingAlbum by remember { mutableStateOf<MusicAlbum?>(null) }
    var showDeckTools by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isMusicSettingsOpen) {
        showDeckTools = isMusicSettingsOpen
    }
    var showThemeEditor by rememberSaveable { mutableStateOf(false) }
    var showAdvancedThemeEditor by rememberSaveable { mutableStateOf(false) }
    var isLaunchingLandscape by rememberSaveable { mutableStateOf(false) }
    var activeAdvancedTab by rememberSaveable { mutableStateOf(AdvancedEditorTab.VINYL) }
    var themePreset by rememberSaveable { mutableStateOf(initialThemeState.preset) }
    var recordFinish by rememberSaveable { mutableStateOf(initialThemeState.recordFinish) }
    var tonearmFinish by rememberSaveable { mutableStateOf(initialThemeState.tonearmFinish) }
    var labelTone by rememberSaveable { mutableStateOf(initialThemeState.labelTone) }
    var glowAmount by rememberSaveable { mutableStateOf(initialThemeState.glowAmount) }
    var shadowAmount by rememberSaveable { mutableStateOf(initialThemeState.shadowAmount) }
    var vinylTintArgb by rememberSaveable { mutableStateOf(initialThemeState.vinylTintArgb) }
    var vinylTintAlpha by rememberSaveable { mutableStateOf(initialThemeState.vinylTintAlpha) }
    var effectTintArgb by rememberSaveable { mutableStateOf(initialThemeState.effectTintArgb) }
    var effectTintAlpha by rememberSaveable { mutableStateOf(initialThemeState.effectTintAlpha) }
    var labelTintArgb by rememberSaveable { mutableStateOf(initialThemeState.labelTintArgb) }
    var labelTintAlpha by rememberSaveable { mutableStateOf(initialThemeState.labelTintAlpha) }
    var animateEffects by rememberSaveable { mutableStateOf(initialThemeState.animateEffects) }
    val loadAnimProgress = remember { Animatable(0f) }
    var selectedIndex by remember(songs) { mutableIntStateOf(fallbackPage.coerceIn(0, songs.lastIndex)) }
    selectedIndex = selectedIndex.coerceIn(0, songs.lastIndex)
    val controlSong = songs[selectedIndex]

    // "Auto" means follow the song: when Theme is Auto, the theme changes per song; when the
    // Disc is Auto, the pressing changes per song. A manually-picked theme/disc stays fixed.
    val songSeed = controlSong.id
    val effPreset = remember(themePreset, songSeed) {
        if (themePreset == PlayerThemePreset.AUTO) {
            val pool = PlayerThemePreset.values().filter { it != PlayerThemePreset.AUTO }
            pool[(((songSeed % pool.size) + pool.size) % pool.size).toInt()]
        } else themePreset
    }
    val effRecordFinish = remember(recordFinish, songSeed) {
        if (recordFinish == RecordFinishOption.AUTO) {
            val pool = RecordFinishOption.values().filter { it != RecordFinishOption.AUTO }
            pool[(((songSeed % pool.size) + pool.size) % pool.size).toInt()]
        } else recordFinish
    }

    val themeConfig = remember(
        effPreset,
        effRecordFinish,
        tonearmFinish,
        labelTone,
        glowAmount,
        shadowAmount,
        vinylTintArgb,
        vinylTintAlpha,
        effectTintArgb,
        effectTintAlpha,
        labelTintArgb,
        labelTintAlpha,
        animateEffects
    ) {
        VinylThemeConfig(
            preset = effPreset,
            recordFinish = effRecordFinish,
            tonearmFinish = tonearmFinish,
            labelTone = labelTone,
            glowAmount = glowAmount,
            shadowAmount = shadowAmount,
            vinylTint = Color(vinylTintArgb),
            vinylTintAlpha = vinylTintAlpha,
            effectTint = Color(effectTintArgb),
            effectTintAlpha = effectTintAlpha,
            labelTint = Color(labelTintArgb),
            labelTintAlpha = labelTintAlpha,
            animateEffects = animateEffects
        )
    }

    val dynamicPalettes = remember { mutableStateMapOf<Long, MusicPalette>() }

    LaunchedEffect(librarySongs) {
        librarySongs.forEach { song ->
            if (!dynamicPalettes.containsKey(song.id)) {
                val artUri = song.albumArtUri
                if (artUri != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(artUri)?.use { stream ->
                                val bitmap = BitmapFactory.decodeStream(stream)
                                if (bitmap != null) {
                                    val palette = extractPaletteFromBitmap(bitmap)
                                    bitmap.recycle()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        dynamicPalettes[song.id] = palette
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    val basePalette = dynamicPalettes[controlSong.id] ?: DEFAULT_PALETTE
    val activePalette = remember(basePalette, themeConfig) {
        applyVinylTheme(basePalette, themeConfig)
    }

    val backgroundTop by animateColorAsState(activePalette.backgroundTop, label = "musicBgTop")
    val backgroundBottom by animateColorAsState(activePalette.backgroundBottom, label = "musicBgBottom")
    val landscapeLaunchProgress by animateFloatAsState(
        targetValue = if (isLaunchingLandscape) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "landscapeLaunchProgress"
    )

    LaunchedEffect(
        themePreset,
        recordFinish,
        tonearmFinish,
        labelTone,
        glowAmount,
        shadowAmount,
        vinylTintArgb,
        vinylTintAlpha,
        effectTintArgb,
        effectTintAlpha,
        labelTintArgb,
        labelTintAlpha
    ) {
        persistVinylThemeState(
            themePreferences,
            PersistedVinylThemeState(
                preset = themePreset,
                recordFinish = recordFinish,
                tonearmFinish = tonearmFinish,
                labelTone = labelTone,
                glowAmount = glowAmount,
                shadowAmount = shadowAmount,
                vinylTintArgb = vinylTintArgb,
                vinylTintAlpha = vinylTintAlpha,
                effectTintArgb = effectTintArgb,
                effectTintAlpha = effectTintAlpha,
                labelTintArgb = labelTintArgb,
                labelTintAlpha = labelTintAlpha
            )
        )
    }

    LaunchedEffect(uiState.currentSongId, songs) {
        val targetPage = songs.indexOfFirst { it.id == uiState.currentSongId }
        if (targetPage >= 0 && targetPage != selectedIndex && !dragOffset.isRunning) {
            selectedIndex = targetPage
            dragOffset.snapTo(0f)
        }
    }

    LaunchedEffect(isLandscape) {
        if (isLandscape) {
            libraryProgress.snapTo(0f)
            showDeckTools = false
            showThemeEditor = false
            showAdvancedThemeEditor = false
        }
        isLaunchingLandscape = false
    }

    LaunchedEffect(controlSong.id) {
        showDeckTools = false
        showThemeEditor = false
        showAdvancedThemeEditor = false
        showSleepTimerSheet = false
        showLyricsOverlay = false
        showLyricsPostcard = false
    }

    val browserProgress = libraryProgress.value.coerceIn(0f, 1f)
    val shouldHideBottomNav = !isLandscape &&
        browserProgress < 0.08f &&
        (showDeckTools || showThemeEditor || showAdvancedThemeEditor || showSleepTimerSheet || showLyricsOverlay || showLyricsPostcard)

    LaunchedEffect(shouldHideBottomNav) {
        onBottomNavVisibilityChanged(!shouldHideBottomNav)
    }

    DisposableEffect(Unit) {
        onDispose {
            onBottomNavVisibilityChanged(true)
        }
    }

    val currentAlbumIndex = remember(uiState.activeAlbumId, albums) {
        val albumIndex = albums.indexOfFirst { it.albumId == uiState.activeAlbumId }
        if (albumIndex >= 0) albumIndex + 1 else 0
    }

    LaunchedEffect(browserProgress > 0.84f, currentAlbumIndex) {
        if (browserProgress > 0.84f && albums.isNotEmpty()) {
            albumListState.animateScrollToItem(currentAlbumIndex)
        }
    }

    if (showCreateAlbumDialog) {
        CreateCustomAlbumDialog(
            songs = librarySongs,
            onDismiss = { showCreateAlbumDialog = false },
            onCreate = { name, selectedSongIds ->
                showCreateAlbumDialog = false
                viewModel.createCustomAlbum(name, selectedSongIds)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundTop,
                        lerpColor(backgroundTop, backgroundBottom, 0.42f),
                        backgroundBottom
                    )
                )
            )
    ) {
        MusicBackdrop(activePalette = activePalette, modifier = Modifier.fillMaxSize())

        if (isLandscape) {
            val isCurrentSong = uiState.currentSongId == controlSong.id
            val progressFraction = progressFraction(
                progressMs = if (isCurrentSong) uiState.progressMs else 0L,
                durationMs = if (isCurrentSong && uiState.durationMs > 0L) uiState.durationMs else controlSong.durationMs
            )
            LandscapeVinylPlayer(
                modifier = Modifier.fillMaxSize(),
                song = controlSong,
                isPlaying = isCurrentSong && uiState.isPlaying,
                progressMs = if (isCurrentSong) uiState.progressMs else 0L,
                progressFraction = progressFraction,
                palette = activePalette,
                themeConfig = themeConfig,
                canPrevious = songs.isNotEmpty(),
                canNext = songs.isNotEmpty(),
                onPlayPause = {
                    if (isCurrentSong) {
                        viewModel.togglePlayback(isLockscreen)
                    } else {
                        viewModel.playSong(controlSong, activeAlbum?.albumId)
                    }
                },
                onPrevious = {
                    if (songs.isNotEmpty()) {
                        val target = if (selectedIndex > 0) selectedIndex - 1 else songs.lastIndex
                        viewModel.playSong(songs[target], activeAlbum?.albumId)
                    }
                },
                onNext = {
                    if (songs.isNotEmpty()) {
                        val target = if (selectedIndex < songs.lastIndex) selectedIndex + 1 else 0
                        viewModel.playSong(songs[target], activeAlbum?.albumId)
                    }
                },
                onPlaybackStateRequested = { shouldPlay ->
                    if (shouldPlay) {
                        if (isCurrentSong) {
                            if (!uiState.isPlaying) {
                                viewModel.togglePlayback(isLockscreen)
                            }
                        } else {
                            viewModel.playSong(controlSong, activeAlbum?.albumId)
                        }
                    } else if (isCurrentSong && uiState.isPlaying) {
                        viewModel.togglePlayback(isLockscreen)
                    }
                },
                onExitLandscape = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            )
        } else {
            BackHandler(enabled = browserProgress > 0.01f) {
                scope.launch {
                    libraryProgress.animateTo(0f, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 0.dp, end = 0.dp, top = 2.dp, bottom = 12.dp)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val stageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val stageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val browserTravelPx = (stageHeightPx * 0.48f).coerceAtLeast(1f)
                    var swipeAxis by remember { mutableStateOf<SwipeAxis?>(null) }
                    var dragDeltaX by remember { mutableStateOf(0f) }
                    var dragDeltaY by remember { mutableStateOf(0f) }

                    fun animateBrowser(open: Boolean) {
                        scope.launch {
                            libraryProgress.animateTo(
                                targetValue = if (open) 1f else 0f,
                                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
                            )
                        }
                    }

                    val targetBrowserOpen by viewModel.isAlbumBrowserOpen.collectAsStateWithLifecycle()
                    val isBrowserOpen = browserProgress > 0.01f

                    LaunchedEffect(isBrowserOpen) {
                        viewModel.setAlbumBrowserOpen(isBrowserOpen)
                    }

                    LaunchedEffect(targetBrowserOpen) {
                        if (!targetBrowserOpen && libraryProgress.value > 0.01f) {
                            animateBrowser(false)
                        } else if (targetBrowserOpen && libraryProgress.value <= 0.01f) {
                            animateBrowser(true)
                        }
                    }

                    LaunchedEffect(browserProgress > 0.08f) {
                        if (browserProgress > 0.08f && !isMusicSettingsOpen) {
                            showDeckTools = false
                            showThemeEditor = false
                            showAdvancedThemeEditor = false
                        }
                    }

                    fun resetSongOffset() {
                        scope.launch {
                            dragOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                            )
                        }
                    }

                    fun settleToSong(targetIndex: Int) {
                        if (songs.isEmpty() || dragOffset.isRunning) return
                        val safeTarget = when {
                            targetIndex < 0 -> songs.lastIndex
                            targetIndex > songs.lastIndex -> 0
                            else -> targetIndex
                        }
                        if (safeTarget == selectedIndex) return

                        scope.launch {
                            val exitOffset = if (targetIndex > selectedIndex) -stageWidthPx else stageWidthPx
                            dragOffset.animateTo(
                                targetValue = exitOffset,
                                animationSpec = tween(durationMillis = 430, easing = FastOutSlowInEasing)
                            )
                            selectedIndex = safeTarget
                            viewModel.playSong(
                                songs[safeTarget],
                                activeAlbum?.albumId
                            )
                            dragOffset.snapTo(0f)
                        }
                    }

                    val dragProgress = (dragOffset.value / stageWidthPx).coerceIn(-1f, 1f)
                    val currentPresence = (1f - dragProgress.absoluteValue).coerceIn(0f, 1f)
                    val incomingIndex = when {
                        songs.isEmpty() -> null
                        dragProgress < 0f -> if (selectedIndex < songs.lastIndex) selectedIndex + 1 else 0
                        dragProgress > 0f -> if (selectedIndex > 0) selectedIndex - 1 else songs.lastIndex
                        else -> null
                    }

                    val playerScale = lerpValue(1f, 0.88f, browserProgress)
                    val playerAlpha = if (selectedLoadingAlbum != null) {
                        1f
                    } else {
                        lerpValue(1f, 0.06f, browserProgress)
                    }
                    val playerTranslationY = stageHeightPx * browserProgress * 0.15f
                    val controlsAlpha = (1f - browserProgress * 1.3f).coerceIn(0f, 1f)

                    val platterRecordAlpha = if (selectedLoadingAlbum != null) {
                        val animVal = loadAnimProgress.value
                        if (animVal <= 0.85f) {
                            0f
                        } else {
                            ((animVal - 0.85f) / 0.15f).coerceIn(0f, 1f)
                        }
                    } else {
                        1f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = playerScale + (landscapeLaunchProgress * 0.10f)
                                scaleY = playerScale + (landscapeLaunchProgress * 0.10f)
                                alpha = playerAlpha * (1f - landscapeLaunchProgress * 0.18f)
                                translationX = landscapeLaunchProgress * 34f
                                translationY = playerTranslationY - (landscapeLaunchProgress * 26f)
                                transformOrigin = TransformOrigin(0.5f, 0.12f)
                            }
                            .pointerInput(selectedIndex, songs.size, stageWidthPx) {
                                detectDragGestures(
                                    onDragStart = {
                                        swipeAxis = null
                                        dragDeltaX = 0f
                                        dragDeltaY = 0f
                                    },
                                    onDragCancel = {
                                        if (swipeAxis == SwipeAxis.Horizontal) {
                                            resetSongOffset()
                                        } else if (swipeAxis == SwipeAxis.Vertical) {
                                            animateBrowser(libraryProgress.value > 0.34f)
                                        }
                                        swipeAxis = null
                                        dragDeltaX = 0f
                                        dragDeltaY = 0f
                                    },
                                    onDragEnd = {
                                        when (swipeAxis) {
                                            SwipeAxis.Horizontal -> {
                                                val threshold = stageWidthPx * 0.18f
                                                when {
                                                    dragOffset.value < -threshold -> settleToSong(selectedIndex + 1)
                                                    dragOffset.value > threshold -> settleToSong(selectedIndex - 1)
                                                    else -> resetSongOffset()
                                                }
                                            }

                                            SwipeAxis.Vertical -> animateBrowser(libraryProgress.value > 0.34f)
                                            null -> Unit
                                        }
                                        swipeAxis = null
                                        dragDeltaX = 0f
                                        dragDeltaY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDeltaX += dragAmount.x
                                        dragDeltaY += dragAmount.y

                                        if (swipeAxis == null) {
                                            val absX = dragDeltaX.absoluteValue
                                            val absY = dragDeltaY.absoluteValue
                                            if (absX > 16f || absY > 16f) {
                                                swipeAxis = if (absX > absY) SwipeAxis.Horizontal else SwipeAxis.Vertical
                                            }
                                        }

                                        when (swipeAxis) {
                                            SwipeAxis.Horizontal -> {
                                                if (libraryProgress.value > 0.08f) {
                                                    return@detectDragGestures
                                                }
                                                val minOffset = if (selectedIndex < songs.lastIndex) -stageWidthPx else 0f
                                                val maxOffset = if (selectedIndex > 0) stageWidthPx else 0f
                                                scope.launch {
                                                    dragOffset.snapTo((dragOffset.value + dragAmount.x).coerceIn(minOffset, maxOffset))
                                                }
                                            }

                                            SwipeAxis.Vertical -> {
                                                scope.launch {
                                                    val nextProgress = (libraryProgress.value - (dragAmount.y / browserTravelPx)).coerceIn(0f, 1f)
                                                    libraryProgress.snapTo(nextProgress)
                                                }
                                            }

                                            null -> Unit
                                        }
                                    }
                                )
                            }
                    ) {
                        FullScreenSongPage(
                            song = songs[selectedIndex],
                            uiState = uiState,
                            pageOffset = dragProgress,
                            pagePresence = currentPresence,
                            palette = applyVinylTheme(
                                dynamicPalettes[songs[selectedIndex].id] ?: DEFAULT_PALETTE,
                                themeConfig
                            ),
                            themeConfig = themeConfig,
                            songIndex = selectedIndex,
                            songCount = songs.size,
                            recordAlpha = platterRecordAlpha,
                            onPlayPause = {
                                if (uiState.currentSongId == controlSong.id) {
                                    viewModel.togglePlayback(isLockscreen)
                                } else {
                                    viewModel.playSong(controlSong, activeAlbum?.albumId)
                                }
                            },
                            onPrevious = { settleToSong(selectedIndex - 1) },
                            onNext = { settleToSong(selectedIndex + 1) },
                            onSeek = viewModel::seekTo
                        )

                        incomingIndex?.let { page ->
                            val incomingPresence = dragProgress.absoluteValue.coerceIn(0f, 1f)
                            val incomingOffset = if (page > selectedIndex) {
                                1f + dragProgress
                            } else {
                                -1f + dragProgress
                            }
                            FullScreenSongPage(
                                song = songs[page],
                                uiState = uiState,
                                pageOffset = incomingOffset,
                                pagePresence = incomingPresence,
                                palette = applyVinylTheme(
                                    dynamicPalettes[songs[page].id] ?: DEFAULT_PALETTE,
                                    themeConfig
                                ),
                                themeConfig = themeConfig,
                                songIndex = page,
                                songCount = songs.size,
                                recordAlpha = platterRecordAlpha,
                                onPlayPause = {
                                    viewModel.playSong(songs[page], activeAlbum?.albumId)
                                },
                                onPrevious = { settleToSong(page - 1) },
                                onNext = { settleToSong(page + 1) },
                                onSeek = viewModel::seekTo
                            )
                        }
                    }

                        StableMusicControls(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(start = 30.dp, end = 30.dp, bottom = 86.dp)
                                .graphicsLayer {
                                    alpha = controlsAlpha * (1f - landscapeLaunchProgress)
                                    translationY = browserProgress * 94f + (landscapeLaunchProgress * 40f)
                                    scaleX = lerpValue(0.92f, 0.84f, browserProgress) - (landscapeLaunchProgress * 0.05f)
                                    scaleY = lerpValue(0.92f, 0.84f, browserProgress) - (landscapeLaunchProgress * 0.05f)
                                },
                        songIndex = selectedIndex,
                        songCount = songs.size,
                        isPlaying = uiState.isPlaying,
                        palette = activePalette,
                        onPlayPause = {
                            if (uiState.currentSongId == controlSong.id) {
                                viewModel.togglePlayback(isLockscreen)
                            } else {
                                viewModel.playSong(controlSong, activeAlbum?.albumId)
                            }
                        },
                        onPrevious = { settleToSong(selectedIndex - 1) },
                        onNext = { settleToSong(selectedIndex + 1) }
                    )

                    if (browserProgress < 0.08f || showDeckTools || isMusicSettingsOpen) {
                        val isAnyToolVisible = showDeckTools || showThemeEditor || showAdvancedThemeEditor || isMusicSettingsOpen
                        BottomDeckToolStrip(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .zIndex(500f)
                                .then(if (isAnyToolVisible) Modifier.fillMaxHeight() else Modifier.wrapContentHeight()),
                            visible = isAnyToolVisible,

                            onToggleVisibility = {
                                val nextVisible = !showDeckTools
                                showDeckTools = nextVisible
                                viewModel.setMusicSettingsOpen(nextVisible)
                                if (!nextVisible) {
                                    showThemeEditor = false
                                    showAdvancedThemeEditor = false
                                }
                            },
                            onOpenThemeEditor = {
                                showDeckTools = true
                                showThemeEditor = true
                                showAdvancedThemeEditor = false
                                viewModel.setMusicSettingsOpen(true)
                            },
                            palette = activePalette,
                            onOpenSleepTimer = { showSleepTimerSheet = true; showDeckTools = false; viewModel.setMusicSettingsOpen(false) },
                            onOpenLyrics = { showLyricsOverlay = true; showDeckTools = false; viewModel.setMusicSettingsOpen(false) },
                            onShare = {
                                val shareIntent = android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "I'm listening to ${controlSong.title} by ${controlSong.artist} on Gigi!")
                                }, "Share Song")
                                context.startActivity(shareIntent)
                                showDeckTools = false
                                viewModel.setMusicSettingsOpen(false)
                            },
                            isHeartbeatActive = isHeartbeatActive,
                            onToggleHeartbeat = { isHeartbeatActive = !isHeartbeatActive },
                            onOpenLandscape = {
                                showDeckTools = false
                                viewModel.setMusicSettingsOpen(false)
                                showThemeEditor = false
                                showAdvancedThemeEditor = false
                                if (!isLaunchingLandscape) {
                                    isLaunchingLandscape = true
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        )
                    }

                    if ((showThemeEditor || showAdvancedThemeEditor) && browserProgress < 0.08f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = if (showAdvancedThemeEditor) 0.14f else 0.08f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showThemeEditor = false
                                    showAdvancedThemeEditor = false
                                    showDeckTools = false
                                }
                        )
                    }

                    if (showThemeEditor && browserProgress < 0.08f) {
                        VinylThemeEditorSheet(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            palette = activePalette,
                            config = themeConfig,
                            onPresetSelected = {
                                themePreset = it
                                tonearmFinish = defaultTonearmFinishForPreset(it)
                                labelTone = defaultLabelToneForPreset(it)
                            },
                            onRecordFinishSelected = { recordFinish = it },
                            onOpenAdvancedEditor = {
                                activeAdvancedTab = AdvancedEditorTab.VINYL
                                showAdvancedThemeEditor = true
                            },
                            onDismiss = {
                                showThemeEditor = false
                                showAdvancedThemeEditor = false
                                showDeckTools = false
                            },
                            
                        )
                    }

                    if (showAdvancedThemeEditor && browserProgress < 0.08f) {
                        VinylAdvancedEditorSheet(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            activeTab = activeAdvancedTab,
                            onTabSelected = { activeAdvancedTab = it },
                            finish = recordFinish,
                            onFinishChanged = { recordFinish = it },
                            tonearmFinish = tonearmFinish,
                            onTonearmFinishChanged = { tonearmFinish = it },
                            glowAmount = glowAmount,
                            onGlowAmountChanged = { glowAmount = it },
                            shadowAmount = shadowAmount,
                            onShadowAmountChanged = { shadowAmount = it },
                            vinylTint = Color(vinylTintArgb),
                            vinylTintAlpha = vinylTintAlpha,
                            effectTint = Color(effectTintArgb),
                            effectTintAlpha = effectTintAlpha,
                            labelTint = Color(labelTintArgb),
                            labelTintAlpha = labelTintAlpha,
                            onVinylTintChanged = {
                                vinylTintArgb = it.toArgb()
                                if (vinylTintAlpha < 0.12f) vinylTintAlpha = 0.38f
                            },
                            onVinylTintAlphaChanged = { vinylTintAlpha = it },
                            onEffectTintChanged = {
                                effectTintArgb = it.toArgb()
                                if (effectTintAlpha < 0.12f) effectTintAlpha = 0.44f
                            },
                            onEffectTintAlphaChanged = { effectTintAlpha = it },
                            onLabelTintChanged = {
                                labelTintArgb = it.toArgb()
                                if (labelTintAlpha < 0.12f) labelTintAlpha = 0.72f
                            },
                            onLabelTintAlphaChanged = { labelTintAlpha = it },
                            animateEffects = animateEffects,
                            onAnimateEffectsChanged = { animateEffects = it },
                            onDismiss = {
                                showAdvancedThemeEditor = false
                            },
                            onDone = {
                                showAdvancedThemeEditor = false
                            },
                            themeConfig = themeConfig
                            
                        )
                    }

                    if (showSleepTimerSheet && browserProgress < 0.08f) {
                        SleepTimerSheet(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            palette = activePalette,
                            onDismiss = { showSleepTimerSheet = false },
                            onSetTimer = { minutes ->
                                viewModel.setSleepTimer(minutes)
                                showSleepTimerSheet = false
                            },
                            onCancelTimer = {
                                viewModel.cancelSleepTimer()
                                showSleepTimerSheet = false
                            }
                        )
                    }

                    if (showLyricsOverlay && browserProgress < 0.08f) {
                        LyricsOverlay(
                            lyrics = "Lyrics for ${controlSong.title}\n\n(Not available offline yet!)",
                            currentProgressMs = 0L,
                            onDismiss = { showLyricsOverlay = false },
                            onShareLyrics = { 
                                showLyricsOverlay = false
                                showLyricsPostcard = true
                            }
                        )
                    }
                    
                    if (showLyricsPostcard && browserProgress < 0.08f) {
                        LyricsPostcardEditor(
                            lyrics = "Lyrics for ${controlSong.title}\n\n(Not available offline yet!)",
                            palette = activePalette,
                            onDismiss = { showLyricsPostcard = false }
                        )
                    }

                    if (browserProgress > 0.01f) {
                        AlbumBrowserOverlay(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            albums = albums,
                            palette = activePalette,
                            activeAlbumId = uiState.activeAlbumId,
                            totalSongCount = librarySongs.size,
                            dynamicPalettes = dynamicPalettes,
                            listState = albumListState,
                            progress = browserProgress,
                            isSwooping = selectedLoadingAlbum != null,
                            uiState = uiState,
                            onSearchQueryChanged = viewModel::searchYoutube,
                            onDownloadSong = viewModel::downloadAndPlayYoutubeSong,
                            onDragProgress = { deltaY ->
                                scope.launch {
                                    val nextProgress = (libraryProgress.value - (deltaY / browserTravelPx)).coerceIn(0f, 1f)
                                    libraryProgress.snapTo(nextProgress)
                                }
                            },
                            onDragEnd = {
                                animateBrowser(libraryProgress.value > 0.34f)
                            },
                            onRefresh = viewModel::scanDeviceMusic,
                            onCreateAlbum = { showCreateAlbumDialog = true },
                            onPlayShuffledLibrary = {
                                scope.launch {
                                    selectedLoadingAlbum = MusicAlbum(
                                        albumId = -1L,
                                        title = "Shuffle Library",
                                        artist = "Play All Songs",
                                        albumArtUri = null,
                                        tracks = emptyList()
                                    )
                                    scope.launch {
                                        kotlinx.coroutines.delay(400)
                                        viewModel.playShuffledLibrary()
                                        animateBrowser(false)
                                        viewModel.setAlbumBrowserOpen(false)
                                    }
                                    loadAnimProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
                                    )
                                    selectedLoadingAlbum = null
                                    loadAnimProgress.snapTo(0f)
                                }
                            },
                            onPlayAlbum = { album ->
                                scope.launch {
                                    selectedLoadingAlbum = album
                                    scope.launch {
                                        kotlinx.coroutines.delay(400)
                                        viewModel.playAlbum(album)
                                        animateBrowser(false)
                                        viewModel.setAlbumBrowserOpen(false)
                                    }
                                    loadAnimProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
                                    )
                                    selectedLoadingAlbum = null
                                    loadAnimProgress.snapTo(0f)
                                }
                            },
                            onCollapse = { animateBrowser(false) },
                            onDeleteAlbum = viewModel::deleteAlbum,
                            activeConnections = activeConnections,
                            onShareAlbum = viewModel::shareAlbum,
                            onDownloadAlbum = viewModel::downloadSharedAlbum
                        )
                    }

                    if (browserProgress > 0.08f) {
                        val nowPlayingBottomPadding = lerpValue(30f, 96f, browserProgress).dp
                        CollapsedNowPlayingBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 24.dp, end = 24.dp, bottom = nowPlayingBottomPadding),
                            song = controlSong,
                            album = activeAlbum,
                            palette = activePalette,
                            progress = browserProgress,
                            isPlaying = uiState.currentSongId == controlSong.id && uiState.isPlaying,
                            onTogglePlayback = {
                                if (uiState.currentSongId == controlSong.id) {
                                    viewModel.togglePlayback(isLockscreen)
                                } else {
                                    viewModel.playSong(controlSong, activeAlbum?.albumId)
                                }
                            },
                            onExpand = { animateBrowser(false) }
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        color = Color.Black.copy(alpha = 0.34f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                selectedLoadingAlbum?.let { album ->
                    val animVal = loadAnimProgress.value
                    if (animVal > 0f) {
                        val palette = dynamicPalettes[album.tracks.firstOrNull()?.id] ?: DEFAULT_PALETTE
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val stageWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                            val stageHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                            
                            val recSize = stageWidth * 1.44f
                            val targetCenter = Offset(
                                -(recSize * 0.31f) + recSize / 2f,
                                recSize * 0.012f + recSize / 2f
                            )
                            
                            val cardAlpha: Float
                            val cardScale: Float
                            val cardY: Float
                            
                            val discX: Float
                            val discY: Float
                            val discScale: Float
                            val discRotation: Float
                            val discAlpha: Float
                            
                            val discRotX: Float
                            val discRotY: Float
                            val discScaleYMultiplier: Float
                            
                            if (animVal <= 0.35f) {
                                val t1 = (animVal / 0.35f).coerceIn(0f, 1f)
                                cardAlpha = 1f
                                cardScale = lerpValue(0.92f, 1.04f, t1)
                                cardY = stageHeight * 0.38f
                                
                                discX = stageWidth * 0.5f + (stageWidth * 0.36f) * t1
                                discY = cardY
                                discScale = 0.22f * 0.85f
                                discRotation = t1 * 180f
                                discAlpha = t1
                                
                                discRotX = -32f
                                discRotY = 2f
                                discScaleYMultiplier = 0.90f
                            } else if (animVal <= 0.85f) {
                                val t2 = ((animVal - 0.35f) / 0.5f).coerceIn(0f, 1f)
                                cardAlpha = (1f - t2 * 1.8f).coerceIn(0f, 1f)
                                cardScale = lerpValue(1.04f, 0.88f, t2)
                                cardY = stageHeight * 0.38f - t2 * 40f
                                
                                val p0 = Offset(stageWidth * 0.5f + stageWidth * 0.36f, stageHeight * 0.38f)
                                val p1 = Offset(stageWidth * 0.92f, stageHeight * 0.62f)
                                val p2 = targetCenter
                                
                                discX = (1 - t2) * (1 - t2) * p0.x + 2 * (1 - t2) * t2 * p1.x + t2 * t2 * p2.x
                                discY = (1 - t2) * (1 - t2) * p0.y + 2 * (1 - t2) * t2 * p1.y + t2 * t2 * p2.y
                                discScale = lerpValue(0.22f * 0.85f, 1.0f, t2)
                                discRotation = 180f + t2 * 900f
                                discAlpha = 1f
                                
                                discRotX = lerpValue(-32f, 0f, t2)
                                discRotY = lerpValue(2f, 0f, t2)
                                discScaleYMultiplier = lerpValue(0.90f, 1.0f, t2)
                            } else {
                                val t3 = ((animVal - 0.85f) / 0.15f).coerceIn(0f, 1f)
                                cardAlpha = 0f
                                cardScale = 0.88f
                                cardY = stageHeight * 0.38f - 40f
                                
                                discX = targetCenter.x
                                discY = targetCenter.y
                                discScale = 1.0f
                                discRotation = 1080f + t3 * 90f
                                discAlpha = (1f - t3).coerceIn(0f, 1f)
                                
                                discRotX = 0f
                                discRotY = 0f
                                discScaleYMultiplier = 1.0f
                            }
                            
                            // Render Swooping Vinyl Disc (drawn first so it slides OUT from behind the cover)
                            if (discAlpha > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .requiredSize(recSize.dp)
                                        .offset(
                                            x = (discX - recSize / 2f).dp,
                                            y = (discY - recSize / 2f).dp
                                        )
                                        .graphicsLayer {
                                            rotationX = discRotX
                                            rotationY = discRotY
                                            scaleX = discScale
                                            scaleY = discScale * discScaleYMultiplier
                                            rotationZ = discRotation
                                            alpha = discAlpha
                                        }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val centerPoint = Offset(size.width / 2f, size.height / 2f)
                                        val radius = size.width * 0.5f
                                        
                                        drawCircle(
                                            color = Color(0xFF121212),
                                            radius = radius
                                        )
                                        repeat(36) { groove ->
                                            drawCircle(
                                                color = Color.White.copy(alpha = 0.018f),
                                                radius = radius * (0.22f + groove * 0.02f),
                                                style = Stroke(width = 0.8f)
                                            )
                                        }
                                        drawCircle(
                                            color = palette.accentSoft.copy(alpha = 0.96f),
                                            radius = radius * 0.30f
                                        )
                                        drawCircle(
                                            color = palette.textStrong.copy(alpha = 0.22f),
                                            radius = radius * 0.28f,
                                            style = Stroke(width = 1.2f)
                                        )
                                        drawCircle(
                                            color = palette.accent.copy(alpha = 0.32f),
                                            radius = radius * 0.14f,
                                            style = Stroke(width = 1.8f)
                                        )
                                        drawCircle(
                                            color = Color.Black,
                                            radius = radius * 0.04f
                                        )
                                    }
                                }
                            }

                            // Render Album Card (drawn second so it sits on top, sleeve style)
                            if (cardAlpha > 0.01f) {
                                Surface(
                                    modifier = Modifier
                                        .width(280.dp)
                                        .height(154.dp)
                                        .offset(
                                            x = (stageWidth * 0.5f - 140f).dp,
                                            y = (cardY - 77f).dp
                                        )
                                        .graphicsLayer {
                                            rotationX = -32f
                                            rotationY = 2f
                                            scaleX = cardScale
                                            scaleY = cardScale * 0.90f
                                            alpha = cardAlpha
                                            shadowElevation = 18f
                                        },
                                    shape = RoundedCornerShape(26.dp),
                                    color = palette.accentSoft.copy(alpha = 0.96f)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val albumArt = album.tracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
                                        if (albumArt != null) {
                                            coil.compose.AsyncImage(
                                                model = albumArt,
                                                contentDescription = album.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Black.copy(alpha = 0.12f),
                                                                Color.Black.copy(alpha = 0.68f)
                                                            )
                                                        )
                                                    )
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(
                                                                palette.accentSoft,
                                                                palette.accent.copy(alpha = 0.86f),
                                                                palette.textStrong.copy(alpha = 0.28f)
                                                            )
                                                        )
                                                    )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(horizontal = 20.dp, vertical = 14.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = album.title.uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = album.artist.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White.copy(alpha = 0.76f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SwirlData(
    val startAngle: Float,
    val sweepAngle: Float,
    val curveControl: Float,
    val thickness: Float,
    val color: Color
)

@Composable
private fun LandscapeVinylPlayer(
    modifier: Modifier = Modifier,
    song: LocalSong,
    isPlaying: Boolean,
    progressMs: Long,
    progressFraction: Float,
    palette: MusicPalette,
    themeConfig: VinylThemeConfig,
    canPrevious: Boolean,
    canNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlaybackStateRequested: (Boolean) -> Unit,
    onExitLandscape: () -> Unit
) {
    val recordRotation = rememberPlaybackDrivenRotation(
        songId = song.id,
        progressMs = progressMs,
        isPlaying = isPlaying
    )
    val waveAnimTransition = rememberInfiniteTransition(label = "waveAnim")
    val waveAnim by waveAnimTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAnim"
    )
    var dragX by remember(song.id) { mutableStateOf(0f) }
    val animatedDragX by animateFloatAsState(
        targetValue = dragX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "landscapeDiscDragX"
    )
    val labelColors = remember(themeConfig.labelTone, themeConfig.labelTint, themeConfig.labelTintAlpha) {
        applyLabelTint(
            labelToneColorsFor(themeConfig.labelTone),
            themeConfig.labelTint,
            themeConfig.labelTintAlpha
        )
    }
    val shadowStrength = themeConfig.shadowAmount.coerceIn(0.2f, 1f)
    val sleeveTone = remember(labelColors.base, palette.backgroundBottom, palette.isDark) {
        if (palette.isDark) {
            lerpColor(palette.backgroundBottom, labelColors.base, 0.18f)
        } else {
            lerpColor(labelColors.base, Color.White, 0.14f)
        }
    }

    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastInteractionTime) {
        kotlinx.coroutines.delay(3000)
        areControlsVisible = false
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (areControlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "landscapeControlsAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(palette.backgroundTop, palette.backgroundBottom)
                )
            )
            .padding(horizontal = 26.dp, vertical = 16.dp)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                lastInteractionTime = System.currentTimeMillis()
                areControlsVisible = true
            }
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val discSize = (availableHeight * 0.96f).coerceAtMost(availableWidth * 0.55f)
        val labelSize = discSize * 0.31f
        val sleeveSize = (availableHeight * 0.74f).coerceAtMost(availableWidth * 0.42f)
        val discOffsetX = availableWidth * 0.38f
        val discOffsetY = (-10).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(canNext, canPrevious, onNext, onPrevious, onPlayPause) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragX = 0f
                            lastInteractionTime = System.currentTimeMillis()
                            areControlsVisible = true
                        },
                        onDragEnd = {
                            when {
                                dragX < -80f && canNext -> onNext()
                                dragX > 80f && canPrevious -> onPrevious()
                                dragX.absoluteValue < 15f -> onPlayPause()
                            }
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            lastInteractionTime = System.currentTimeMillis()
                            areControlsVisible = true
                            dragX += dragAmount
                        }
                    )
                }
        ) {
            LandscapeAlbumSleeve(
                modifier = Modifier
                    .size(sleeveSize)
                    .align(Alignment.CenterStart)
                    .offset(x = 38.dp, y = (-16).dp),
                song = song,
                palette = palette,
                sleeveTone = sleeveTone,
                labelColors = labelColors
            )

            Box(
                modifier = Modifier
                    .size(discSize)
                    .align(Alignment.TopStart)
                    .offset(x = discOffsetX, y = discOffsetY)
                    .graphicsLayer {
                        translationX = animatedDragX
                        rotationZ = animatedDragX * 0.08f
                        alpha = (1f - (animatedDragX.absoluteValue / 1200f)).coerceIn(0.3f, 1f)
                    }
                    .shadow(
                        elevation = 18.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.12f + 0.20f * shadowStrength),
                        spotColor = Color.Black.copy(alpha = 0.10f + 0.14f * shadowStrength)
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = recordRotation }
                ) {
                    val radius = size.minDimension / 2f
                    drawStyledRecordSurface(
                        finish = themeConfig.recordFinish,
                        fallbackKind = palette.recordKind,
                        centerPoint = center,
                        radius = radius,
                        vinylTint = themeConfig.vinylTint,
                        vinylTintAlpha = themeConfig.vinylTintAlpha,
                        effectTint = themeConfig.effectTint,
                        effectTintAlpha = themeConfig.effectTintAlpha,
                        preset = themeConfig.preset,
                        animValue = waveAnim,
                        animateEffects = themeConfig.animateEffects,
                        
                    )
                }

                Surface(
                    modifier = Modifier.size(labelSize),
                    shape = CircleShape,
                    color = labelColors.base,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = song.album,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "MD VINYL",
                                    color = labelColors.text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "33 1/3 RPM",
                                    color = labelColors.text,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(labelColors.spindleInner)
                                .border(1.2.dp, labelColors.spindleOuter, CircleShape)
                        )
                    }
                }
            }

            LandscapeToneArm(
                modifier = Modifier
                    .fillMaxSize(),
                discOffsetX = discOffsetX,
                discOffsetY = discOffsetY,
                discSize = discSize,
                isPlaying = isPlaying,
                progressFraction = progressFraction,
                tonearmFinish = themeConfig.tonearmFinish,
                shadowStrength = shadowStrength,
                onPlaybackStateRequested = onPlaybackStateRequested
            )

            if (controlsAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .graphicsLayer { alpha = controlsAlpha },
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PlayerPillButton(
                        icon = Icons.Default.SkipPrevious,
                        label = "PREV",
                        onClick = onPrevious,
                        palette = palette,
                        enabled = canPrevious,
                        compact = true
                    )
                    PlayerPillButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = if (isPlaying) "PAUSE" else "PLAY",
                        onClick = onPlayPause,
                        palette = palette,
                        emphasized = true,
                        compact = true
                    )
                    PlayerPillButton(
                        icon = Icons.Default.SkipNext,
                        label = "NEXT",
                        onClick = onNext,
                        palette = palette,
                        enabled = canNext,
                        compact = true
                    )
                }
            }

            if (controlsAlpha > 0.01f) {
                GlassNavActionPill(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 14.dp)
                        .graphicsLayer { alpha = controlsAlpha },
                    label = "Portrait",
                    darkTheme = palette.isDark,
                    width = 112.dp,
                    height = 44.dp,
                    onClick = onExitLandscape,
                    icon = {
                        PortraitLayoutIcon(
                            tint = if (palette.isDark) Color.White.copy(alpha = 0.86f) else Color(0xFF5F35D9),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LandscapeAlbumSleeve(
    modifier: Modifier = Modifier,
    song: LocalSong,
    palette: MusicPalette,
    sleeveTone: Color,
    labelColors: LabelToneColors
) {
    val hasArtwork = song.albumArtUri != null
    val titleColor = if (hasArtwork || palette.isDark) {
        Color.White.copy(alpha = 0.96f)
    } else {
        Color(0xFF2C231D)
    }
    val subtitleColor = if (hasArtwork || palette.isDark) {
        Color.White.copy(alpha = 0.76f)
    } else {
        Color(0xFF6F5B50)
    }
    val badgeBackground = if (hasArtwork || palette.isDark) {
        Color.White.copy(alpha = 0.13f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    val coverShape = RoundedCornerShape(18.dp)
    val artShape = RoundedCornerShape(13.dp)
    val infoPanelColor = if (hasArtwork || palette.isDark) {
        Color.Black.copy(alpha = 0.32f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }
    val infoPanelBorder = if (hasArtwork || palette.isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.68f)
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 10.dp, y = 10.dp)
                .graphicsLayer {
                    rotationZ = -2.6f
                    alpha = 0.68f
                }
                .background(
                    Brush.linearGradient(
                        listOf(
                            lerpColor(sleeveTone, palette.backgroundBottom, 0.28f),
                            lerpColor(sleeveTone, Color.Black, if (palette.isDark) 0.18f else 0.08f)
                        )
                    ),
                    coverShape
                )
        )

        Surface(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = -2.6f
                    shadowElevation = 24f
                },
            shape = coverShape,
            color = sleeveTone
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = if (hasArtwork) {
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.24f)
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    sleeveTone,
                                    palette.backgroundBottom.copy(alpha = 0.16f)
                                )
                            }
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .clip(artShape)
                        .background(
                            if (hasArtwork) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF2A2422),
                                        Color(0xFF0F0E10)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.30f),
                                        sleeveTone,
                                        lerpColor(sleeveTone, palette.backgroundBottom, 0.18f)
                                    )
                                )
                            }
                        )
                        .border(
                            1.dp,
                            if (hasArtwork) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.24f),
                            artShape
                        )
                ) {
                    if (hasArtwork) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = song.album,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (hasArtwork) 0.12f else 0.06f),
                                        Color.Transparent,
                                        if (hasArtwork) Color.Black.copy(alpha = 0.62f) else sleeveTone.copy(alpha = 0.12f)
                                    )
                                )
                            )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(16.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = if (hasArtwork) 0.18f else 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(22.dp)
                    ) {
                        if (!hasArtwork) {
                            drawCircle(
                                color = labelColors.ring.copy(alpha = if (palette.isDark) 0.56f else 0.22f),
                                radius = size.minDimension * 0.34f,
                                center = center,
                                style = Stroke(width = size.minDimension * 0.08f)
                            )
                            drawCircle(
                                color = palette.accent.copy(alpha = if (palette.isDark) 0.14f else 0.08f),
                                radius = size.minDimension * 0.22f,
                                center = center
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.26f),
                                radius = size.minDimension * 0.40f,
                                center = center,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }

                        drawLine(
                            color = Color.White.copy(alpha = if (hasArtwork) 0.16f else 0.10f),
                            start = Offset(size.width * 0.08f, size.height * 0.16f),
                            end = Offset(size.width * 0.42f, size.height * 0.02f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeBackground
                    ) {
                        Text(
                            text = "SIDE A",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = titleColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Text(
                        text = "33 ⅓ RPM",
                        color = subtitleColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = infoPanelColor,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, infoPanelBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = song.title,
                            color = titleColor,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 30.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = subtitleColor,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (song.album.isNotBlank()) song.album else "Gigi Vinyl Sessions",
                            color = subtitleColor.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeToneArm(
    modifier: Modifier = Modifier,
    discOffsetX: androidx.compose.ui.unit.Dp,
    discOffsetY: androidx.compose.ui.unit.Dp,
    discSize: androidx.compose.ui.unit.Dp,
    isPlaying: Boolean,
    progressFraction: Float,
    tonearmFinish: TonearmFinish,
    shadowStrength: Float,
    onPlaybackStateRequested: (Boolean) -> Unit
) {
    val metal = tonearmColorsFor(tonearmFinish)
    val haptic = LocalHapticFeedback.current
    val playbackStateRequester by rememberUpdatedState(onPlaybackStateRequested)
    var dragArmAngle by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val armScale = 0.94f
        val discSizePx = with(density) { discSize.toPx() }
        val discCenter = remember(maxWidth, maxHeight, discOffsetX, discOffsetY, discSize) {
            Offset(
                x = with(density) { discOffsetX.toPx() } + discSizePx / 2f,
                y = with(density) { discOffsetY.toPx() } + discSizePx / 2f
            )
        }
        val discRadius = discSizePx / 2f
        val pivot = Offset(
            with(density) { maxWidth.toPx() } * 0.89f,
            with(density) { maxHeight.toPx() } * 0.14f
        )
        val armTop = Offset(pivot.x, pivot.y + with(density) { 38.dp.toPx() })
        val armElbow = Offset(
            with(density) { maxWidth.toPx() } * 0.89f,
            with(density) { maxHeight.toPx() } * 0.60f
        )
        val trackProgress = progressFraction.coerceIn(0f, 1f)
        val needleOffset = with(density) {
            Offset(6.dp.toPx() * armScale, 22.dp.toPx() * armScale)
        }
        val lowerArmLength = discRadius * 0.91f
        val parkedArmAngle = 140f
        val outerGrooveAngle = 162f
        val innerGrooveAngle = 173.5f
        val minArmAngle = 136f
        val maxArmAngle = 176f
        val restingArmAngle = if (isPlaying) {
            lerpValue(outerGrooveAngle, innerGrooveAngle, trackProgress)
        } else {
            parkedArmAngle
        }
        val displayedArmAngle = dragArmAngle ?: restingArmAngle
        val animatedArmAngle by animateFloatAsState(
            targetValue = displayedArmAngle,
            animationSpec = if (dragArmAngle != null) {
                spring(stiffness = Spring.StiffnessHigh)
            } else {
                tween(durationMillis = 420, easing = FastOutSlowInEasing)
            },
            label = "landscapeArmAngle"
        )
        val animatedCartridgeTarget = landscapeCartridgeCenterForArmAngle(
            armElbow = armElbow,
            armAngle = animatedArmAngle,
            armLength = lowerArmLength
        )
        val animatedNeedleTip = landscapeStylusTipForArmAngle(
            cartridge = animatedCartridgeTarget,
            armAngle = animatedArmAngle,
            needleOffset = needleOffset
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(isPlaying, progressFraction, discCenter, discRadius) {
                    val upperThreshold = with(density) { 52.dp.toPx() }
                    val lowerThreshold = with(density) { 82.dp.toPx() }
                    val stylusThreshold = with(density) { 78.dp.toPx() }
                    val cartridgeThreshold = with(density) { 48.dp.toPx() }

                    fun isTouchingArm(point: Offset, cartridge: Offset, needleTip: Offset): Boolean {
                        return distancePointToSegment(point, pivot, armElbow) <= upperThreshold ||
                            distancePointToSegment(point, armElbow, cartridge) <= lowerThreshold ||
                            distanceBetween(point, cartridge) <= cartridgeThreshold ||
                            distanceBetween(point, needleTip) <= stylusThreshold
                    }

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startArmAngle = dragArmAngle ?: restingArmAngle
                            val startCartridge = landscapeCartridgeCenterForArmAngle(
                                armElbow = armElbow,
                                armAngle = startArmAngle,
                                armLength = lowerArmLength
                            )
                            val startNeedleTip = landscapeStylusTipForArmAngle(
                                cartridge = startCartridge,
                                armAngle = startArmAngle,
                                needleOffset = needleOffset
                            )
                            if (!isTouchingArm(down.position, startCartridge, startNeedleTip)) {
                                continue
                            }

                            down.consume()
                            val angleOffset = startArmAngle - landscapeArmAngleForPoint(down.position, armElbow)
                            dragArmAngle = startArmAngle
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            try {
                                drag(down.id) { change ->
                                    change.consume()
                                    val requestedAngle = landscapeArmAngleForPoint(change.position, armElbow) + angleOffset
                                    dragArmAngle = requestedAngle.coerceIn(minArmAngle, maxArmAngle)
                                }
                            } finally {
                                val finalArmAngle = dragArmAngle ?: startArmAngle
                                val finalCartridge = landscapeCartridgeCenterForArmAngle(
                                    armElbow = armElbow,
                                    armAngle = finalArmAngle,
                                    armLength = lowerArmLength
                                )
                                val finalNeedleTip = landscapeStylusTipForArmAngle(
                                    cartridge = finalCartridge,
                                    armAngle = finalArmAngle,
                                    needleOffset = needleOffset
                                )
                                playbackStateRequester(
                                    isLandscapeStylusOnDisc(
                                        stylus = finalNeedleTip,
                                        discCenter = discCenter,
                                        discRadius = discRadius
                                    )
                                )
                                dragArmAngle = null
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawDetailedToneArm(
                    pivot = pivot,
                    armTop = armTop,
                    armElbow = armElbow,
                    cartridge = animatedCartridgeTarget,
                    armRotation = animatedArmAngle,
                    metal = metal,
                    shadowStrength = shadowStrength,
                    armScale = armScale
                )
            }
        }
    }
}

@Composable
private fun PortraitLayoutIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.12f, h * 0.14f),
                size = Size(w * 0.32f, h * 0.72f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 2.1.dp.toPx())
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.52f, h * 0.34f),
                size = Size(w * 0.32f, h * 0.48f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 2.1.dp.toPx())
            )
        }
    }
}



@Composable
private fun BottomDeckToolStrip(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onOpenThemeEditor: () -> Unit,
    onOpenLandscape: () -> Unit,
    onOpenSleepTimer: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    onShare: () -> Unit = {},
    isHeartbeatActive: Boolean = false,
    onToggleHeartbeat: () -> Unit = {},
    palette: MusicPalette
) {
    val revealAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "deckToolsAlpha"
    )
    val revealOffset by animateDpAsState(
        targetValue = if (visible) 0.dp else 40.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "deckToolsOffset"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        if (visible) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onToggleVisibility() }
            )
        }
        
        // The expanding sheet
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(36.dp),
                color = palette.backgroundBottom.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Handle
                    Box(modifier = Modifier.width(40.dp).height(4.dp).background(palette.textStrong.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                    
                    Text("Music Options", color = palette.textStrong, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ExpandedActionItem(label = "Sleep", icon = Icons.Default.Timer, onClick = { onToggleVisibility(); onOpenSleepTimer() }, palette = palette)
                        ExpandedActionItem(label = "Lyrics", icon = Icons.Default.Subject, onClick = { onToggleVisibility(); onOpenLyrics() }, palette = palette)
                        ExpandedActionItem(label = "Share", icon = Icons.Default.Share, onClick = { onToggleVisibility(); onShare() }, palette = palette)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ExpandedActionItem(label = "Heart", icon = if (isHeartbeatActive) Icons.Default.Favorite else Icons.Default.FavoriteBorder, onClick = { onToggleHeartbeat() }, palette = palette, tint = if (isHeartbeatActive) Color(0xFFE91E63) else Color.Unspecified)
                        ExpandedActionItem(label = "Theme", icon = Icons.Default.Settings, onClick = { onToggleVisibility(); onOpenThemeEditor() }, palette = palette)
                        ExpandedActionItem(label = "Landscape", icon = null, onClick = { onToggleVisibility(); onOpenLandscape() }, palette = palette, customIcon = { DeckLayoutIcon(tint = palette.textStrong, modifier = Modifier.size(24.dp)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckLayoutIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 2.1.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.10f, size.height * 0.44f),
            size = Size(size.width * 0.46f, size.height * 0.40f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.48f, size.height * 0.14f),
            size = Size(size.width * 0.42f, size.height * 0.68f),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun VinylThemeEditorSheet(
    modifier: Modifier = Modifier,
    palette: MusicPalette,
    config: VinylThemeConfig,
    onPresetSelected: (PlayerThemePreset) -> Unit,
    onRecordFinishSelected: (RecordFinishOption) -> Unit,
    onOpenAdvancedEditor: () -> Unit,
    onDismiss: () -> Unit,
    songId: Long = 0L,
    songTitle: String = ""
) {
    var selectedThemeGroupIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedVinylGroupIndex by rememberSaveable { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.48f)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        color = Color(0xFF161419).copy(alpha = 0.99f),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
        shadowElevation = 32.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(46.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                )

                BottomThemeSectionTitle(
                    title = "Theme Groups",
                    subtitle = "Swipe groups and pick a creative theme mood."
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1: Horizontally Scrollable Theme Groups Selector
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(themeGroups) { idx, group ->
                            val selected = selectedThemeGroupIndex == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                    .border(1.dp, if (selected) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .clickable { selectedThemeGroupIndex = idx }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = group.cuteName,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Row 2: Selected Theme Group's Swatches
                item {
                    val activeGroup = themeGroups.getOrNull(selectedThemeGroupIndex)
                    if (activeGroup != null) {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Show "Auto" option only inside the first or all groups if desired
                            if (selectedThemeGroupIndex == 0) {
                                item {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ThemePaletteSwatch(
                                            selected = config.preset == PlayerThemePreset.AUTO,
                                            swatches = presetPreviewSwatches(PlayerThemePreset.AUTO),
                                            onClick = { onPresetSelected(PlayerThemePreset.AUTO) }
                                        )
                                        Text(
                                            text = "Auto",
                                            color = if (config.preset == PlayerThemePreset.AUTO) Color.White else Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            
                            items(activeGroup.presets) { preset ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ThemePaletteSwatch(
                                        selected = config.preset == preset,
                                        swatches = presetPreviewSwatches(preset),
                                        onClick = { onPresetSelected(preset) }
                                    )
                                    Text(
                                        text = preset.title,
                                        color = if (config.preset == preset) Color.White else Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Title for Vinyl Groups
                item {
                    BottomThemeSectionTitle(
                        title = "Vinyl Pressing Groups",
                        subtitle = "Tap a pressing category and explore 30 custom disc designs."
                    )
                }

                // Row 3: Horizontally Scrollable Vinyl Groups Selector
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(vinylGroups) { idx, group ->
                            val selected = selectedVinylGroupIndex == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                    .border(1.dp, if (selected) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .clickable { selectedVinylGroupIndex = idx }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = group.cuteName,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Row 4: Selected Vinyl Group's Swatches & Designs
                item {
                    val activeGroup = vinylGroups.getOrNull(selectedVinylGroupIndex)
                    if (activeGroup != null) {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Show Auto Finish in standard cuts group
                            if (selectedVinylGroupIndex == 0) {
                                item {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        RecordFinishPreviewButton(
                                            selected = config.recordFinish == RecordFinishOption.AUTO,
                                            onClick = { onRecordFinishSelected(RecordFinishOption.AUTO) },
                                            content = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(54.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.06f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                        )
                                        Text(
                                            text = "Auto",
                                            color = if (config.recordFinish == RecordFinishOption.AUTO) Color.White else Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            items(activeGroup.options) { finish ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RecordFinishPreviewButton(
                                        selected = config.recordFinish == finish,
                                        onClick = { onRecordFinishSelected(finish) },
                                        content = {
                                            RecordPreviewDisc(
                                                modifier = Modifier.size(54.dp),
                                                finish = finish,
                                                kind = resolveRecordKindOverride(DEFAULT_PALETTE.recordKind, finish),
                                                themeConfig = config,
                                                
                                            )
                                        }
                                    )
                                    Text(
                                        text = finish.title,
                                        color = if (config.recordFinish == finish) Color.White else Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Let Advanced Customizer (+) be always accessible at the end of the lists
                            item(key = "record-advanced") {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RecordFinishPreviewButton(
                                        selected = false,
                                        onClick = onOpenAdvancedEditor,
                                        content = {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.08f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "More vinyl controls",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        }
                                    )
                                    Text(
                                        text = "Custom",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomThemeSectionTitle(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f)
        )
    }
}

@Composable
private fun ThemePaletteSwatch(
    selected: Boolean,
    swatches: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(66.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = swatches
                )
            )
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun RecordFinishPreviewButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.10f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun RecordPreviewDisc(
    modifier: Modifier = Modifier,
    finish: RecordFinishOption = RecordFinishOption.AUTO,
    kind: RecordKind = RecordKind.CLASSIC_BLACK,
    themeConfig: VinylThemeConfig,
    songId: Long = 0L,
    songTitle: String = ""
) {
    val waveAnimTransition = rememberInfiniteTransition(label = "previewWaveAnim")
    val waveAnim by waveAnimTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "previewWaveAnim"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val radius = size.minDimension / 2f
            drawStyledRecordSurface(
                finish = finish,
                fallbackKind = kind,
                centerPoint = center,
                radius = radius,
                vinylTint = themeConfig.vinylTint,
                vinylTintAlpha = themeConfig.vinylTintAlpha,
                effectTint = themeConfig.effectTint,
                effectTintAlpha = themeConfig.effectTintAlpha,
                preset = themeConfig.preset,
                animValue = waveAnim,
                animateEffects = themeConfig.animateEffects
            )
            drawCircle(
                color = Color(0xFF1A191C),
                radius = radius * 0.34f
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = radius * 0.08f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.68f),
                radius = radius * 0.03f
            )
        }
    }
}

@Composable
private fun VinylAdvancedEditorSheet(
    modifier: Modifier = Modifier,
    activeTab: AdvancedEditorTab,
    onTabSelected: (AdvancedEditorTab) -> Unit,
    finish: RecordFinishOption,
    onFinishChanged: (RecordFinishOption) -> Unit,
    tonearmFinish: TonearmFinish,
    onTonearmFinishChanged: (TonearmFinish) -> Unit,
    glowAmount: Float,
    onGlowAmountChanged: (Float) -> Unit,
    shadowAmount: Float,
    onShadowAmountChanged: (Float) -> Unit,
    vinylTint: Color,
    vinylTintAlpha: Float,
    effectTint: Color,
    effectTintAlpha: Float,
    labelTint: Color,
    labelTintAlpha: Float,
    onVinylTintChanged: (Color) -> Unit,
    onVinylTintAlphaChanged: (Float) -> Unit,
    onEffectTintChanged: (Color) -> Unit,
    onEffectTintAlphaChanged: (Float) -> Unit,
    onLabelTintChanged: (Color) -> Unit,
    onLabelTintAlphaChanged: (Float) -> Unit,
    animateEffects: Boolean,
    onAnimateEffectsChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    themeConfig: VinylThemeConfig,
    songId: Long = 0L,
    songTitle: String = ""
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.52f)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
        color = Color(0xFF141217).copy(alpha = 0.99f),
        shadowElevation = 32.dp
    ) {
        val currentColor = when (activeTab) {
            AdvancedEditorTab.VINYL -> vinylTint
            AdvancedEditorTab.EFFECT -> effectTint
            AdvancedEditorTab.LABEL -> labelTint
        }
        val currentOpacity = when (activeTab) {
            AdvancedEditorTab.VINYL -> vinylTintAlpha
            AdvancedEditorTab.EFFECT -> effectTintAlpha
            AdvancedEditorTab.LABEL -> labelTintAlpha
        }
        val presetColors = advancedEditorColors(activeTab)

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Full-Width Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Text(
                    text = "VINYL MASTER CUSTOMIZER",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onDone,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Subtle Header Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            // Split Layout below Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 18.dp, end = 18.dp, bottom = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Panel: Dynamic Live Rotating Disc Preview
                Box(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "previewSpin")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(4000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "spinAngle"
                        )

                        RecordPreviewDisc(
                            modifier = Modifier
                                .size(118.dp)
                                .graphicsLayer { rotationZ = rotation },
                            finish = finish,
                            kind = resolveRecordKindOverride(DEFAULT_PALETTE.recordKind, finish),
                            themeConfig = themeConfig,
                            
                        )
                        
                        Text(
                            text = "LIVE DISC PREVIEW",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                // Right Panel: Dashboard Tabs & Sliders Controls
                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Segmented Tab Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AdvancedEditorTab.values().forEach { tab ->
                            val active = tab == activeTab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable { onTabSelected(tab) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (tab) {
                                        AdvancedEditorTab.VINYL -> "Vinyl"
                                        AdvancedEditorTab.EFFECT -> "Effect"
                                        AdvancedEditorTab.LABEL -> "Label"
                                    },
                                    color = if (active) Color.White else Color.White.copy(alpha = 0.62f),
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Scrollable Controls Column
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 10.dp)
                    ) {
                        // Item 1: Curated Color Swatches Row
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Aesthetic Tint Color",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ColorOptionSwatch(
                                        color = currentColor,
                                        selected = currentOpacity == 0f,
                                        outlined = true,
                                        onClick = {
                                            when (activeTab) {
                                                AdvancedEditorTab.VINYL -> onVinylTintAlphaChanged(0f)
                                                AdvancedEditorTab.EFFECT -> onEffectTintAlphaChanged(0f)
                                                AdvancedEditorTab.LABEL -> onLabelTintAlphaChanged(0f)
                                            }
                                        }
                                    )
                                    presetColors.forEach { swatch ->
                                        ColorOptionSwatch(
                                            color = swatch,
                                            selected = currentOpacity > 0f && colorsClose(currentColor, swatch),
                                            onClick = {
                                                when (activeTab) {
                                                    AdvancedEditorTab.VINYL -> onVinylTintChanged(swatch)
                                                    AdvancedEditorTab.EFFECT -> onEffectTintChanged(swatch)
                                                    AdvancedEditorTab.LABEL -> onLabelTintChanged(swatch)
                                                }
                                            }
                                        )
                                    }
                                    RainbowColorWheelSwatch(
                                        selected = false,
                                        onClick = {
                                            val accent = when (activeTab) {
                                                AdvancedEditorTab.VINYL -> Color(0xFFFF007F)
                                                AdvancedEditorTab.EFFECT -> Color(0xFF00FFCC)
                                                AdvancedEditorTab.LABEL -> Color(0xFFFFD54F)
                                            }
                                            when (activeTab) {
                                                AdvancedEditorTab.VINYL -> onVinylTintChanged(accent)
                                                AdvancedEditorTab.EFFECT -> onEffectTintChanged(accent)
                                                AdvancedEditorTab.LABEL -> onLabelTintChanged(accent)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Item 2: Pill Opacity Custom Slider
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Color Mix Intensity",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${(currentOpacity * 100).toInt()}%",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Slider(
                                    value = currentOpacity,
                                    onValueChange = {
                                        when (activeTab) {
                                            AdvancedEditorTab.VINYL -> onVinylTintAlphaChanged(it.coerceIn(0f, 1f))
                                            AdvancedEditorTab.EFFECT -> onEffectTintAlphaChanged(it.coerceIn(0f, 1f))
                                            AdvancedEditorTab.LABEL -> onLabelTintAlphaChanged(it.coerceIn(0f, 1f))
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                                    )
                                )
                            }
                        }

                        // Item 3: Spindle cap / Metal Finish
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Tonearm Metal Finish",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TonearmFinish.values().forEach { finishOpt ->
                                        val selected = tonearmFinish == finishOpt
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                                .border(1.dp, if (selected) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                .clickable { onTonearmFinishChanged(finishOpt) }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = finishOpt.title,
                                                color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Item 4: Glow and Shadow Sliders
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Ambient Deck Glow",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${(glowAmount * 100).toInt()}%",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Slider(
                                        value = glowAmount,
                                        onValueChange = onGlowAmountChanged,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                                        )
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Turntable Depth Shadow",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${(shadowAmount * 100).toInt()}%",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Slider(
                                        value = shadowAmount,
                                        onValueChange = onShadowAmountChanged,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                                        )
                                    )
                                }
                            }
                        }

                        // Item 5: Animate Disc Effects Toggle Row
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Animate Disc Effects",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Enable dynamic theme particle & aura motions",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                    
                                    val trackColor by animateColorAsState(
                                        targetValue = if (animateEffects) Color(0xFFFF007F).copy(alpha = 0.38f) else Color.White.copy(alpha = 0.08f),
                                        label = "switchTrackColor"
                                    )
                                    val borderColor by animateColorAsState(
                                        targetValue = if (animateEffects) Color(0xFFFF007F).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f),
                                        label = "switchBorderColor"
                                    )
                                    val thumbOffset by animateDpAsState(
                                        targetValue = if (animateEffects) 24.dp else 2.dp,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "switchThumbOffset"
                                    )
                                    val thumbColor by animateColorAsState(
                                        targetValue = if (animateEffects) Color.White else Color.White.copy(alpha = 0.6f),
                                        label = "switchThumbColor"
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(width = 50.dp, height = 28.dp)
                                            .clip(CircleShape)
                                            .background(trackColor)
                                            .border(1.dp, borderColor, CircleShape)
                                            .clickable { onAnimateEffectsChanged(!animateEffects) },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = thumbOffset)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.radialGradient(
                                                        colors = listOf(
                                                            thumbColor,
                                                            thumbColor.copy(alpha = 0.8f)
                                                        )
                                                    )
                                                )
                                                .shadow(elevation = 2.dp, shape = CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorOptionSwatch(
    color: Color,
    selected: Boolean,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (outlined) Color.Transparent else color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (outlined) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(2.dp, color, CircleShape)
            )
        }
    }
}

@Composable
private fun RainbowColorWheelSwatch(
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF5F6D),
                        Color(0xFFFFC371),
                        Color(0xFFA8FF78),
                        Color(0xFF43CEA2),
                        Color(0xFF4FACFE),
                        Color(0xFFB76CFD),
                        Color(0xFFFF5F6D)
                    )
                )
            )
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF1D1A20))
        )
    }
}

@Composable
private fun AlbumBrowserOverlay(
    modifier: Modifier = Modifier,
    albums: List<MusicAlbum>,
    palette: MusicPalette,
    activeAlbumId: Long?,
    totalSongCount: Int,
    dynamicPalettes: Map<Long, MusicPalette>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    progress: Float,
    isSwooping: Boolean = false,
    uiState: com.aman.gigi.viewmodel.MusicUiState,
    onSearchQueryChanged: (String) -> Unit,
    onDownloadSong: (com.aman.gigi.data.music.YTSearchResult) -> Unit,
    onStreamSong: (com.aman.gigi.data.music.YTSearchResult) -> Unit = {},
    onDragProgress: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRefresh: () -> Unit,
    onCreateAlbum: () -> Unit,
    onPlayShuffledLibrary: () -> Unit,
    onPlayAlbum: (MusicAlbum) -> Unit,
    onCollapse: () -> Unit,
    onDeleteAlbum: (Long) -> Unit,
    activeConnections: List<com.aman.gigi.model.Connection> = emptyList(),
    onShareAlbum: (MusicAlbum, String) -> Unit = { _, _ -> },
    onDownloadAlbum: (MusicAlbum) -> Unit = {}
) {
    if (progress <= 0.01f) return
 
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = with(density) { 84.dp.toPx() }
 
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSwooping) 0f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "browserContentAlpha"
    )
 
    var activeDetailAlbum by remember { mutableStateOf<MusicAlbum?>(null) }
    var searchQuery by remember { mutableStateOf("") }
 
    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress * contentAlpha
                translationY = lerpValue(260f, 0f, progress)
            },
        color = (if (palette.isDark) Color(0xFF111216) else Color(0xFFF7EFE5)).copy(alpha = lerpValue(0f, 0.96f, progress) * contentAlpha)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (palette.isDark) {
                            listOf(
                                Color(0xFF1F1F24).copy(alpha = 0.96f * contentAlpha),
                                Color(0xFF18181D).copy(alpha = 0.98f * contentAlpha),
                                Color(0xFF121216).copy(alpha = 0.96f * contentAlpha)
                            )
                        } else {
                            listOf(
                                Color(0xFFFFFCF7).copy(alpha = 0.96f * contentAlpha),
                                Color(0xFFF4EADF).copy(alpha = 0.98f * contentAlpha),
                                Color(0xFFEFE1D0).copy(alpha = 0.96f * contentAlpha)
                            )
                        }
                    )
                )
        ) {
            val detailTransition by animateFloatAsState(
                targetValue = if (activeDetailAlbum != null) 1f else 0f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                label = "detailTransition"
            )

            // 1. ALBUM LIST VIEW (OR SEARCH RESULTS)
            if (detailTransition < 0.99f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (1f - detailTransition) * contentAlpha
                            translationX = -detailTransition * 300f
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 254.dp,
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 182.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (searchQuery.isNotEmpty()) 8.dp else -70.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            if (uiState.isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = palette.accent
                                        )
                                    }
                                }
                            } else if (uiState.searchResults.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No results found on YouTube Music",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textMuted
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = uiState.searchResults,
                                    key = { _, result -> result.videoId }
                                ) { _, result ->
                                    YTSearchResultRow(
                                        result = result,
                                        isDownloading = uiState.downloadingVideoIds.contains(result.videoId),
                                        onDownload = { onDownloadSong(result) },
                                        onStream = { onStreamSong(result) },
                                        palette = palette
                                    )
                                }
                            }
                        } else {
                            if (albums.isEmpty()) {
                                item(key = "empty-albums") {
                                    EmptyAlbumBrowserState(onCreateAlbum = onCreateAlbum, palette = palette)
                                }
                            }

                            itemsIndexed(
                                items = albums,
                                key = { _, album -> album.albumId }
                            ) { index, album ->
                                val approximateCenter = listState.firstVisibleItemIndex.toFloat() +
                                    (listState.firstVisibleItemScrollOffset / itemHeightPx)
                                val relative = index.toFloat() - approximateCenter
                                val focus = (1f - (relative.absoluteValue * 0.24f)).coerceIn(0.52f, 1f)
                                AlbumBrowserCard(
                                    album = album,
                                    palette = albumPaletteFor(album, dynamicPalettes),
                                    isActive = album.albumId == activeAlbumId,
                                    focus = focus,
                                    relativeOffset = relative.coerceIn(-2f, 2f),
                                    onClick = { activeDetailAlbum = album }
                                )
                            }
                        }
                    }
                }
            }

            // 2. ALBUM DETAILS VIEW
            if (detailTransition > 0.01f) {
                val detailAlbum = activeDetailAlbum ?: albums.firstOrNull()
                if (detailAlbum != null) {
                    AlbumDetailsView(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .graphicsLayer {
                                alpha = detailTransition * contentAlpha
                                translationX = (1f - detailTransition) * 300f
                            },
                        album = detailAlbum,
                        palette = albumPaletteFor(detailAlbum, dynamicPalettes),
                        onPlayAlbum = { album ->
                            onPlayAlbum(album)
                        },
                        onDeleteAlbum = { albumId ->
                            activeDetailAlbum = null
                            onDeleteAlbum(albumId)
                        },
                        onBack = { activeDetailAlbum = null },
                        activeConnections = activeConnections,
                        downloadingVideoIds = uiState.downloadingVideoIds,
                        onShareAlbum = onShareAlbum,
                        onDownloadAlbum = onDownloadAlbum
                    )
                }
            }

            // 3. HEADER COLUMN (Drag down anywhere on this header to collapse!)
            if (detailTransition < 0.99f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .graphicsLayer {
                            alpha = (1f - detailTransition) * contentAlpha
                            translationY = lerpValue(260f, 0f, progress) - detailTransition * 150f
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragProgress(dragAmount.y)
                                },
                                onDragEnd = {
                                    onDragEnd()
                                }
                            )
                        }
                        .padding(start = 22.dp, end = 22.dp, top = 36.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Your Library",
                                style = MaterialTheme.typography.headlineMedium,
                                color = palette.textStrong,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tap a sleeve to view tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconButton(
                                onClick = onCreateAlbum,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (palette.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.70f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create custom album",
                                    tint = palette.textStrong
                                )
                            }
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (palette.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.70f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh albums",
                                    tint = palette.textStrong
                                )
                            }
                        }
                    }

                    // NOTE: the YouTube Music search/download box lived here. Removed —
                    // extracting audio from YouTube breaks their ToS and is a Play Store
                    // takedown risk. Gigi plays your on-device library; what your people
                    // are listening to is shared via Now Playing instead.

                    if (searchQuery.isEmpty()) {
                        Surface(
                            onClick = onPlayShuffledLibrary,
                            shape = RoundedCornerShape(999.dp),
                            color = (if (palette.isDark) palette.accent.copy(alpha = 0.24f) else Color(0xFFE1D0BD).copy(alpha = 0.86f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Shuffle Play",
                                    tint = palette.textStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Shuffle Play • $totalSongCount songs",
                                    color = palette.textStrong,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YTSearchResultRow(
    result: com.aman.gigi.data.music.YTSearchResult,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onStream: () -> Unit,
    palette: MusicPalette
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = (if (palette.isDark) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                coil.compose.AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = result.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.hasLyrics) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = palette.accent.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "LYRICS",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = palette.accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = palette.accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onStream) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Stream song",
                            tint = palette.textStrong
                        )
                    }
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download song",
                            tint = palette.textStrong
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailsView(
    modifier: Modifier = Modifier,
    album: MusicAlbum,
    palette: MusicPalette,
    onPlayAlbum: (MusicAlbum) -> Unit,
    onDeleteAlbum: (Long) -> Unit,
    onBack: () -> Unit,
    activeConnections: List<com.aman.gigi.model.Connection>,
    downloadingVideoIds: Set<String>,
    onShareAlbum: (MusicAlbum, String) -> Unit,
    onDownloadAlbum: (MusicAlbum) -> Unit
) {
    var showShareDialog by remember { mutableStateOf(false) }

    val isShared = album.sharedByPartnerName != null
    val missingTracks = remember(album.tracks) {
        album.tracks.filter { it.contentUri.toString().startsWith("shared://") }
    }
    val hasMissingTracks = missingTracks.isNotEmpty()

    var isDiscVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        isDiscVisible = true
    }

    val discSlideOffset by animateDpAsState(
        targetValue = if (isDiscVisible) 56.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "discSlideOffset"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (palette.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.50f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.textStrong
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textStrong,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (album.sharedByPartnerName != null) {
                        "${album.trackCount} Songs • Shared by ${album.sharedByPartnerName}"
                    } else {
                        "${album.trackCount} Songs • ${album.artist}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (album.isCustom) {
                if (album.sharedByPartnerName == null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showShareDialog = true },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (palette.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.50f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Album",
                            tint = palette.textStrong
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { onDeleteAlbum(album.albumId) },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (palette.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.50f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Album",
                        tint = palette.accent
                    )
                }
            }
        }

        // Card & Disc Representation (centered horizontally)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // Sliding and rotating Vinyl Disc
            AlbumPreviewDisc(
                modifier = Modifier
                    .size(160.dp)
                    .offset(x = discSlideOffset)
                    .shadow(8.dp, CircleShape),
                album = album,
                palette = palette,
                rotate = true
            )

            // Album Card (Sleeve)
            Surface(
                modifier = Modifier
                    .width(220.dp)
                    .height(132.dp)
                    .graphicsLayer {
                        rotationX = -20f
                        rotationY = 2f
                        cameraDistance = 14f
                        shadowElevation = 14f
                    },
                shape = RoundedCornerShape(22.dp),
                color = palette.accentSoft.copy(alpha = 0.96f),
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val albumArt = album.tracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
                    if (albumArt != null) {
                        AsyncImage(
                            model = albumArt,
                            contentDescription = album.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.12f),
                                            Color.Black.copy(alpha = 0.62f)
                                        )
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            palette.accentSoft,
                                            palette.accent.copy(alpha = 0.86f),
                                            palette.textStrong.copy(alpha = 0.28f)
                                        )
                                    )
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = album.title.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onPlayAlbum(album) },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(27.dp),
                        ambientColor = palette.accent.copy(alpha = 0.4f),
                        spotColor = palette.accent.copy(alpha = 0.6f)
                    ),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (palette.isDark) palette.accent else Color(0xFF2D221C),
                    contentColor = if (palette.isDark) Color(0xFF241712) else Color.White
                )
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY",
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }

            if (isShared && hasMissingTracks) {
                val isDownloading = remember(album.tracks, downloadingVideoIds) {
                    missingTracks.any { downloadingVideoIds.contains(it.id.toString()) }
                }

                Button(
                    onClick = { onDownloadAlbum(album) },
                    enabled = !isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(27.dp),
                            ambientColor = palette.textStrong.copy(alpha = 0.1f),
                            spotColor = palette.textStrong.copy(alpha = 0.2f)
                        ),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (palette.isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFF0E5D8),
                        contentColor = palette.textStrong,
                        disabledContainerColor = if (palette.isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFE5D8C9),
                        disabledContentColor = palette.textMuted
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = palette.accent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DOWNLOADING...",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DOWNLOAD ALL",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (showShareDialog) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                title = {
                    Text(
                        text = "Share Album",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textStrong
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeConnections.isEmpty()) {
                            Text(
                                text = "No partners connected yet. Connect with a partner to share albums!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textMuted
                            )
                        } else {
                            Text(
                                text = "Select a partner to share this album with:",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            activeConnections.forEach { connection ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (palette.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
                                        .clickable {
                                            onShareAlbum(album, connection.connectionId)
                                            showShareDialog = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = connection.partnerEmoji,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Text(
                                        text = connection.partnerName.ifBlank { "Partner" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.textStrong
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text(text = "CANCEL", color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = if (palette.isDark) Color(0xFF1D1B20) else Color(0xFFF7EFE5),
                shape = RoundedCornerShape(28.dp)
            )
        }
 
        // Song List Header
        Text(
            text = "TRACKS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textMuted,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
 
        // Song List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(album.tracks) { idx, song ->
                SongRowItem(index = idx + 1, song = song, palette = palette)
            }
        }
    }
}

@Composable
private fun SongRowItem(
    index: Int,
    song: LocalSong,
    palette: MusicPalette
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = (if (palette.isDark) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = String.format("%02d", index),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textMuted
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = formatSongDuration(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted
            )
        }
    }
}

@Composable
private fun AlbumBrowserCard(
    album: MusicAlbum,
    palette: MusicPalette,
    isActive: Boolean,
    focus: Float,
    relativeOffset: Float,
    onClick: () -> Unit
) {
    val cardScale = lerpValue(0.86f, 1f, focus)
    val cardAlpha = lerpValue(0.85f, 1f, focus)
    val liftY = lerpValue(28f, 0f, focus) + (relativeOffset * -24f)
    
    val tiltX = -32f + (relativeOffset * -6f)
    val tiltY = 2f + (relativeOffset * 4f)
    val twistZ = relativeOffset * -4f
    val shiftX = relativeOffset * 12f
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                rotationZ = twistZ
                scaleX = cardScale
                scaleY = cardScale * 0.90f
                cameraDistance = 14f
                translationY = liftY
                translationX = shiftX
                shadowElevation = lerpValue(12f, 26f, focus)
                alpha = cardAlpha
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = palette.accentSoft.copy(alpha = 0.96f),
        shadowElevation = 14.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val albumArt = album.tracks.firstOrNull { it.albumArtUri != null }?.albumArtUri
            if (albumArt != null) {
                AsyncImage(
                    model = albumArt,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Black.copy(alpha = 0.68f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    palette.accentSoft,
                                    palette.accent.copy(alpha = 0.86f),
                                    palette.textStrong.copy(alpha = 0.28f)
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .background(Color.White.copy(alpha = 0.15f))
                    .align(Alignment.CenterStart)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = album.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.22f)
            ) {
                Text(
                    text = "${album.trackCount} TRKS",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            if (focus > 0.85f) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 24.dp)
                        .graphicsLayer {
                            rotationZ = relativeOffset * 45f
                        }
                        .background(Color(0xFF111111), shape = CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(palette.accentSoft, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShuffleLibraryCard(
    songCount: Int,
    isActive: Boolean,
    focus: Float,
    relativeOffset: Float,
    onClick: () -> Unit
) {
    val cardScale = lerpValue(0.86f, 1f, focus)
    val cardAlpha = lerpValue(0.85f, 1f, focus)
    val liftY = lerpValue(28f, 0f, focus) + (relativeOffset * -24f)
    
    val tiltX = -32f + (relativeOffset * -6f)
    val tiltY = 2f + (relativeOffset * 4f)
    val twistZ = relativeOffset * -4f
    val shiftX = relativeOffset * 12f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                rotationZ = twistZ
                scaleX = cardScale
                scaleY = cardScale * 0.90f
                cameraDistance = 14f
                translationY = liftY
                translationX = shiftX
                shadowElevation = lerpValue(12f, 26f, focus)
                alpha = cardAlpha
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF1E1B18),
        shadowElevation = 14.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2C2421),
                                Color(0xFF1E1513),
                                Color(0xFF140D0B)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .background(Color.White.copy(alpha = 0.1f))
                    .align(Alignment.CenterStart)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "SHUFFLE LIBRARY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "PLAY ALL SONGS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFFA07A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "$songCount TRACKS",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            if (focus > 0.85f) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 24.dp)
                        .graphicsLayer {
                            rotationZ = relativeOffset * 45f
                        }
                        .background(Color(0xFF111111), shape = CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFFFFA07A), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAlbumBrowserState(
    onCreateAlbum: () -> Unit,
    palette: MusicPalette
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        color = (if (palette.isDark) Color(0xFF1E1E22) else Color(0xFFFFFBF7)),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "No manual albums yet",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.textStrong,
                fontWeight = FontWeight.Light
            )
            Text(
                text = "Create your own album by choosing songs from the phone library. After that, it will appear here in the swipe-up deck.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted
            )
            Button(
                onClick = onCreateAlbum,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (palette.isDark) palette.accent else Color(0xFF2D221C),
                    contentColor = if (palette.isDark) Color(0xFF241712) else Color(0xFFFFFBF7)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create album")
            }
        }
    }
}

@Composable
private fun AlbumPreviewDisc(
    modifier: Modifier = Modifier,
    album: MusicAlbum,
    palette: MusicPalette,
    rotate: Boolean
) {
    val rotation = remember(album.albumId) { Animatable(0f) }

    LaunchedEffect(rotate, album.albumId) {
        if (rotate) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 5200, easing = LinearEasing)
                )
            }
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = rotation.value
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A2A2A),
                        Color(0xFF0E0E0E),
                        Color.Black
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
            repeat(42) { groove ->
                drawCircle(
                    color = Color.White.copy(alpha = if (groove % 4 == 0) 0.06f else 0.025f),
                    radius = radius * (0.22f + groove * 0.016f),
                    style = Stroke(width = if (groove % 5 == 0) 1.6f else 0.8f)
                )
            }
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -88f,
                sweepAngle = 56f,
                useCenter = false,
                style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius * 0.90f, center.y - radius * 0.90f),
                size = Size(radius * 1.8f, radius * 1.8f)
            )
        }

        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = Color(0xFFF6F3EB)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val albumArt = album.albumArtUri
                if (albumArt != null) {
                    AsyncImage(
                        model = albumArt,
                        contentDescription = album.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = album.title.take(1).uppercase(),
                        color = palette.textStrong,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B1B1B))
                )
            }
        }
    }
}

@Composable
private fun CollapsedNowPlayingBar(
    modifier: Modifier = Modifier,
    song: LocalSong,
    album: MusicAlbum?,
    palette: MusicPalette,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onExpand: () -> Unit
) {
    if (progress <= 0.08f) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = lerpValue(140f, 0f, progress)
            }
            .clickable(onClick = onExpand),
        color = (if (palette.isDark) Color(0xFF1F1F24) else Color(0xFFFFFBF6)).copy(alpha = 0.96f),
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumPreviewDisc(
                modifier = Modifier.size(56.dp),
                album = album ?: MusicAlbum(song.albumId, song.album, song.artist, song.albumArtUri, listOf(song)),
                palette = palette,
                rotate = isPlaying
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = song.title,
                    color = palette.textStrong,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album?.title ?: song.album,
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = palette.textStrong
                )
            }
        }
    }
}

@Composable
private fun CreateCustomAlbumDialog(
    songs: List<LocalSong>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Long>) -> Unit
) {
    var albumName by remember { mutableStateOf("") }
    var selectedSongIds by remember { mutableStateOf(setOf<Long>()) }
    val sortedSongs = remember(songs) { songs.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() })) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onCreate(albumName, selectedSongIds.toList()) },
                enabled = albumName.isNotBlank() && selectedSongIds.isNotEmpty()
            ) {
                Text("Create album")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = "Create custom album",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Album name") }
                )

                Text(
                    text = "Add songs from your phone library",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF5E4A43)
                )

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFFF8F2EA)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        itemsIndexed(sortedSongs, key = { _, song -> song.id }) { _, song ->
                            val selected = song.id in selectedSongIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSongIds = if (selected) {
                                            selectedSongIds - song.id
                                        } else {
                                            selectedSongIds + song.id
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        selectedSongIds = if (checked) {
                                            selectedSongIds + song.id
                                        } else {
                                            selectedSongIds - song.id
                                        }
                                    }
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(0xFF231A16),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${song.artist} • ${song.album}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF7F6D66),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun MusicTopBar(
    songCount: Int,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    palette: MusicPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Music",
                style = MaterialTheme.typography.headlineLarge,
                color = palette.textStrong,
                fontWeight = FontWeight.Light
            )
            Text(
                text = if (songCount > 0) {
                    "Swipe left or right to change songs from this phone."
                } else {
                    "Scan this phone and load your local tracks."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textMuted
            )
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.2.dp,
                    color = palette.accent
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh songs",
                    tint = palette.textStrong,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun FullScreenSongPage(
    song: LocalSong,
    uiState: MusicUiState,
    pageOffset: Float,
    pagePresence: Float,
    palette: MusicPalette,
    themeConfig: VinylThemeConfig,
    songIndex: Int,
    songCount: Int,
    recordAlpha: Float = 1f,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val absolutePageOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val isCurrentSong = uiState.currentSongId == song.id
    val isPlaying = isCurrentSong && uiState.isPlaying

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp, vertical = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val heroHeight = maxHeight * 0.86f

            HeroTurntable(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .align(Alignment.TopCenter),
                song = song,
                progressMs = if (isCurrentSong) uiState.progressMs else 0L,
                progressFraction = progressFraction(
                    progressMs = if (isCurrentSong) uiState.progressMs else 0L,
                    durationMs = if (isCurrentSong && uiState.durationMs > 0L) uiState.durationMs else song.durationMs
                ),
                isPlaying = isPlaying,
                pageOffset = pageOffset,
                pagePresence = (1f - absolutePageOffset).coerceIn(0f, 1f),
                palette = palette,
                themeConfig = themeConfig,
                recordAlpha = recordAlpha,
                onPlayPause = onPlayPause,
                onSeek = { fraction ->
                    val duration = if (isCurrentSong && uiState.durationMs > 0L) uiState.durationMs else song.durationMs
                    if (duration > 0L) {
                        val targetMs = (fraction * duration).toLong()
                        onSeek(targetMs)
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(start = 38.dp, end = 34.dp, bottom = 192.dp)
                    .graphicsLayer {
                        alpha = (pagePresence * 1.08f).coerceIn(0f, 1f)
                        translationY = absolutePageOffset * 30f
                    },
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.5.sp,
                    color = palette.textStrong,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.5.sp,
                    color = palette.textMuted,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrentSong && uiState.isPreparing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = palette.accent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Loading track...",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StableMusicControls(
    modifier: Modifier = Modifier,
    songIndex: Int,
    songCount: Int,
    isPlaying: Boolean,
    palette: MusicPalette,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            key("prev") {
                PlayerPillButton(
                    icon = Icons.Default.SkipPrevious,
                    label = "PREV",
                    onClick = onPrevious,
                    palette = palette,
                    enabled = songCount > 0
                )
            }
            key("play") {
                PlayerPillButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = "PLAY",
                    onClick = onPlayPause,
                    palette = palette,
                    emphasized = true
                )
            }
            key("next") {
                PlayerPillButton(
                    icon = Icons.Default.SkipNext,
                    label = "NEXT",
                    onClick = onNext,
                    palette = palette,
                    enabled = songCount > 0
                )
            }
        }
    }
}

@Composable
private fun HeroTurntable(
    modifier: Modifier = Modifier,
    song: LocalSong,
    progressMs: Long,
    progressFraction: Float,
    isPlaying: Boolean,
    pageOffset: Float,
    pagePresence: Float,
    palette: MusicPalette,
    themeConfig: VinylThemeConfig,
    recordAlpha: Float = 1f,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentProgressFraction by rememberUpdatedState(progressFraction)
    val currentPageOffset by rememberUpdatedState(pageOffset)
    val recordRotation = rememberPlaybackDrivenRotation(
        songId = song.id,
        progressMs = progressMs,
        isPlaying = isPlaying
    )
    val waveAnimTransition = rememberInfiniteTransition(label = "portraitWaveAnim")
    val waveAnim by waveAnimTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "portraitWaveAnim"
    )
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var dragAngle by remember { mutableStateOf<Float?>(null) }
    val metal = remember(themeConfig.tonearmFinish) { tonearmColorsFor(themeConfig.tonearmFinish) }
    val labelColors = remember(themeConfig.labelTone, themeConfig.labelTint, themeConfig.labelTintAlpha) {
        applyLabelTint(
            labelToneColorsFor(themeConfig.labelTone),
            themeConfig.labelTint,
            themeConfig.labelTintAlpha
        )
    }
    val glowStrength = themeConfig.glowAmount.coerceIn(0.2f, 1f)
    val shadowStrength = themeConfig.shadowAmount.coerceIn(0.2f, 1f)

    val armAngle by animateFloatAsState(
        targetValue = when {
            dragAngle != null -> dragAngle!!
            pageOffset.absoluteValue > 0.05f -> 92f
            isPlaying -> 118f + (progressFraction * 10f)
            else -> 92f
        },
        animationSpec = if (dragAngle != null) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "armAngle"
    )
    val glowTransition = rememberInfiniteTransition(label = "musicGlow")
    val glowScale by glowTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = (0.08f + pagePresence * 0.92f).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.TopStart
    ) {
        val recordSize = maxWidth * 1.44f

        val glowSize = recordSize * 1.02f
        val labelSize = recordSize * 0.30f

        val toneArmWidth = maxWidth * 0.65f // expanded Canvas container width to completely avoid clipping
        val toneArmHeight = recordSize * 1.15f // expanded Canvas container height to completely avoid clipping
        val origArmWidth = maxWidth * 0.37f
        val origArmHeight = recordSize * 0.74f

        val recordOffsetX = -(recordSize * 0.31f)
        val recordOffsetY = recordSize * 0.012f

        val toneArmOffsetX = maxWidth * 0.55f
        val toneArmOffsetY = maxHeight * 0.02f
        val swipeLift = pageOffset.absoluteValue.coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .requiredSize(glowSize)
                .offset(x = recordOffsetX + 18.dp, y = recordOffsetY + 22.dp)
                .graphicsLayer {
                    scaleX = glowScale - (swipeLift * 0.04f)
                    scaleY = glowScale - (swipeLift * 0.04f)
                    alpha = (if (isPlaying) 0.18f else 0.12f) * glowStrength
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f * glowStrength),
                            palette.accent.copy(alpha = 0.07f * glowStrength),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .requiredSize(recordSize)
                .offset(x = recordOffsetX, y = recordOffsetY)
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.18f + 0.27f * shadowStrength),
                    spotColor = Color.Black.copy(alpha = 0.16f + 0.19f * shadowStrength)
                )
                .graphicsLayer {
                    translationX = pageOffset * 640f
                    translationY = swipeLift * 16f
                    rotationZ = pageOffset * 122f
                    scaleX = 1f - (swipeLift * 0.10f)
                    scaleY = 1f - (swipeLift * 0.10f)
                    alpha = ((1f - swipeLift * 0.72f).coerceIn(0f, 1f)) * recordAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = recordRotation + (pageOffset * 188f)
                    }
            ) {
                val radius = size.minDimension / 2f
                val centerPoint = center
                drawStyledRecordSurface(
                    finish = themeConfig.recordFinish,
                    fallbackKind = palette.recordKind,
                    centerPoint = centerPoint,
                    radius = radius,
                    vinylTint = themeConfig.vinylTint,
                    vinylTintAlpha = themeConfig.vinylTintAlpha,
                    effectTint = themeConfig.effectTint,
                    effectTintAlpha = themeConfig.effectTintAlpha,
                    preset = themeConfig.preset,
                    animValue = waveAnim,
                    animateEffects = themeConfig.animateEffects
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .pointerInput(song.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = kotlin.math.min(size.width, size.height) / 2f
                                val touchRange = 50.dp.toPx()
                                
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val dx = down.position.x - cx
                                val dy = down.position.y - cy
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                
                                if (kotlin.math.abs(dist - radius) < touchRange) {
                                    val angleRad = kotlin.math.atan2(dy, dx)
                                    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                                    if (angleDeg < -180f) angleDeg += 360f
                                    if (angleDeg > 180f) angleDeg -= 360f
                                    
                                    if (angleDeg in -90f..10f) {
                                        down.consume()
                                        val frac = ((angleDeg - (-58f)) / 42f).coerceIn(0f, 1f)
                                        onSeek(frac)
                                        
                                        var pointerId = down.id
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pointer = event.changes.firstOrNull { it.id == pointerId } ?: break
                                            if (!pointer.pressed) break
                                            
                                            pointer.consume()
                                            val px = pointer.position.x - cx
                                            val py = pointer.position.y - cy
                                            val pAngleRad = kotlin.math.atan2(py, px)
                                            var pAngleDeg = Math.toDegrees(pAngleRad.toDouble()).toFloat()
                                            if (pAngleDeg < -180f) pAngleDeg += 360f
                                            if (pAngleDeg > 180f) pAngleDeg -= 360f
                                            
                                            val pFrac = ((pAngleDeg - (-58f)) / 42f).coerceIn(0f, 1f)
                                            onSeek(pFrac)
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                val radius = size.minDimension / 2f
                drawArc(
                    brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFD4A84E),
                                Color(0xFF8B6F2E),
                                Color(0xFFD4A84E)
                            ),
                        center = center
                    ),
                    startAngle = -92f,
                    sweepAngle = 34f + (progressFraction.coerceIn(0f, 1f) * 42f),
                    useCenter = false,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f)
                )
            }

            Box(
                modifier = Modifier
                    .size(labelSize)
                    .graphicsLayer {
                        rotationZ = recordRotation + (pageOffset * 188f)
                    }
                    .clip(CircleShape)
                    .background(labelColors.base)
                    .border(
                        5.dp,
                        labelColors.ring,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    // Spindle hole overlay
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(labelColors.spindleInner)
                            .border(1.5.dp, labelColors.spindleOuter, CircleShape)
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Spindle center dot
                        drawCircle(
                            color = labelColors.spindleOuter,
                            radius = 6.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = labelColors.spindleInner,
                            radius = 3.5.dp.toPx(),
                            center = center
                        )

                        val radius = size.minDimension / 2f
                        val pathRadius = radius * 0.86f

                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas

                            val topPath = android.graphics.Path().apply {
                                addArc(
                                    android.graphics.RectF(
                                        center.x - pathRadius, center.y - pathRadius,
                                        center.x + pathRadius, center.y + pathRadius
                                    ),
                                    -165f, 150f
                                )
                            }

                            val bottomPath = android.graphics.Path().apply {
                                addArc(
                                    android.graphics.RectF(
                                        center.x - pathRadius, center.y - pathRadius,
                                        center.x + pathRadius, center.y + pathRadius
                                    ),
                                    165f, -150f
                                )
                            }

                            val textPaint = android.graphics.Paint().apply {
                                color = labelColors.text.toArgb()
                                textSize = 3.6.dp.toPx()
                                isAntiAlias = true
                                letterSpacing = 0.08f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
                            }

                            nativeCanvas.drawTextOnPath(
                                "Developed remotely by Wi-Fi   •   Made in Hangzhou, China",
                                topPath,
                                0f,
                                0f,
                                textPaint
                            )

                            nativeCanvas.drawTextOnPath(
                                "Designed by Nikki with her purple computer   •   Special thanks to Sleeping dogs",
                                bottomPath,
                                0f,
                                0f,
                                textPaint
                            )

                            val labelPaint = android.graphics.Paint().apply {
                                color = labelColors.text.toArgb()
                                textSize = 3.0.dp.toPx()
                                isAntiAlias = true
                                letterSpacing = 0.04f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            nativeCanvas.drawText("© MD Studio 2026", center.x, center.y - radius * 0.52f, labelPaint)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "MD VINYL",
                            color = labelColors.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "33 ⅓ RPM",
                            color = labelColors.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // High-fidelity Static 3D Specular Light reflections Canvas (Floats over spinning vinyl)
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val radius = size.minDimension / 2f
                val centerPoint = center

                // Concentric polished specular highlight bands (3D Vinyl Sheen)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = centerPoint
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    alpha = 0.76f
                )

                // 1. High-contrast 3D physical bow-tie radial shimmers (real groove light reflections)
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = centerPoint
                    ),
                    radius = radius
                )

                // 2. Realistic 3D rim bevel highlight catching light from top-left
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = radius - 1.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // Realistic 3D rim bevel shadow catching depth on bottom-right
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    radius = radius - 2.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 3. 3D paper label indentation depth ring (makes the central label look sunken)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.40f),
                    radius = radius * 0.38f,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = radius * 0.38f + 1.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        val origArmWidthPx = with(density) { origArmWidth.toPx() }
        val origArmHeightPx = with(density) { origArmHeight.toPx() }
        val toneArmOffsetXPx = with(density) { toneArmOffsetX.toPx() }
        val toneArmOffsetYPx = with(density) { toneArmOffsetY.toPx() }
        val pivotPx = Offset(toneArmOffsetXPx + origArmWidthPx * 0.68f, toneArmOffsetYPx + origArmHeightPx * 0.12f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = swipeLift * 24f
                    alpha = 0.92f
                }
                .pointerInput(song.id) {
                    val distanceToSegment = { p: Offset, a: Offset, b: Offset ->
                        val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
                        if (l2 == 0f) {
                            val dx = p.x - a.x
                            val dy = p.y - a.y
                            Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        } else {
                            var t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2
                            t = t.coerceIn(0f, 1f)
                            val px = a.x + t * (b.x - a.x)
                            val py = a.y + t * (b.y - a.y)
                            val dx = p.x - px
                            val dy = p.y - py
                            Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        }
                    }

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val dx = down.position.x - pivotPx.x
                            val dy = down.position.y - pivotPx.y
                            val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            
                            if (angle in 86f..148f) {
                                val currentAngle = when {
                                    currentPageOffset.absoluteValue > 0.05f -> 92f
                                    currentIsPlaying -> 118f + (currentProgressFraction * 10f)
                                    else -> 92f
                                }
                                val currentAngleRad = Math.toRadians(currentAngle.toDouble())
                                val armElbow = Offset(pivotPx.x, toneArmOffsetYPx + origArmHeightPx * 0.50f)
                                val armLength = origArmHeightPx * 0.50f
                                val currentCartridge = Offset(
                                    armElbow.x + armLength * Math.cos(currentAngleRad).toFloat(),
                                    armElbow.y + armLength * Math.sin(currentAngleRad).toFloat()
                                )

                                val distToLower = distanceToSegment(down.position, armElbow, currentCartridge)
                                val distToUpper = distanceToSegment(down.position, pivotPx, armElbow)

                                val touchThresholdLower = 56.dp.toPx()
                                val touchThresholdUpper = 36.dp.toPx()

                                if (distToLower < touchThresholdLower || distToUpper < touchThresholdUpper) {
                                    down.consume()
                                    dragAngle = angle
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    
                                    try {
                                        drag(down.id) { change ->
                                            change.consume()
                                            val dragDx = change.position.x - pivotPx.x
                                            val dragDy = change.position.y - pivotPx.y
                                            val dragAngleVal = Math.toDegrees(Math.atan2(dragDy.toDouble(), dragDx.toDouble())).toFloat()
                                            if (dragAngleVal in 86f..148f) {
                                                dragAngle = dragAngleVal
                                            }
                                        }
                                    } finally {
                                        dragAngle?.let { finalAngle ->
                                            if (finalAngle > 109f) {
                                                if (!currentIsPlaying) {
                                                    currentOnPlayPause()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            } else {
                                                if (currentIsPlaying) {
                                                    currentOnPlayPause()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        }
                                        dragAngle = null
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val pivot = pivotPx
            val armTop = Offset(pivot.x, pivot.y + 20.dp.toPx())
            val armElbow = Offset(pivot.x, toneArmOffsetYPx + origArmHeightPx * 0.50f)
            val armLength = origArmHeightPx * 0.50f
            val armRotation = armAngle + (pageOffset * 5f)
            val angleRad = Math.toRadians(armRotation.toDouble())
            val cartridge = Offset(
                armElbow.x + armLength * Math.cos(angleRad).toFloat(),
                armElbow.y + armLength * Math.sin(angleRad).toFloat()
            )
            drawDetailedToneArm(
                pivot = pivot,
                armTop = armTop,
                armElbow = armElbow,
                cartridge = cartridge,
                armRotation = armRotation,
                metal = metal,
                shadowStrength = shadowStrength
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDetailedToneArm(
    pivot: Offset,
    armTop: Offset,
    armElbow: Offset,
    cartridge: Offset,
    armRotation: Float,
    metal: TonearmColors,
    shadowStrength: Float,
    armScale: Float = 1f
) {
    val vX = cartridge.x - armElbow.x
    val vY = cartridge.y - armElbow.y
    val vLen = Math.sqrt((vX * vX + vY * vY).toDouble()).toFloat().coerceAtLeast(1f)
    val tX = vX / vLen
    val tY = vY / vLen
    val nX = -tY
    val nY = tX

    val curveOffset = 38.dp.toPx() * armScale
    val c1X = armElbow.x + vX * 0.35f + nX * curveOffset
    val c1Y = armElbow.y + vY * 0.35f + nY * curveOffset
    val c2X = armElbow.x + vX * 0.65f - nX * curveOffset
    val c2Y = armElbow.y + vY * 0.65f - nY * curveOffset

    val curvePath = Path().apply {
        moveTo(armElbow.x, armElbow.y)
        cubicTo(c1X, c1Y, c2X, c2Y, cartridge.x, cartridge.y)
    }

    val baseArmWidth = 22.dp.toPx() * armScale
    val shadowOffset = Offset(5.5.dp.toPx() * armScale, 9.dp.toPx() * armScale)
    val shadowColor = Color.Black.copy(alpha = 0.06f + 0.16f * shadowStrength)

    drawCircle(
        color = shadowColor,
        radius = 44.dp.toPx() * armScale,
        center = pivot + shadowOffset
    )
    drawLine(
        color = shadowColor,
        start = armTop + shadowOffset,
        end = armElbow + shadowOffset,
        strokeWidth = baseArmWidth,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = shadowColor,
        radius = 20.dp.toPx() * armScale,
        center = armElbow + shadowOffset
    )

    val shadowPath = Path().apply {
        moveTo(armElbow.x + shadowOffset.x, armElbow.y + shadowOffset.y)
        cubicTo(
            c1X + shadowOffset.x, c1Y + shadowOffset.y,
            c2X + shadowOffset.x, c2Y + shadowOffset.y,
            cartridge.x + shadowOffset.x, cartridge.y + shadowOffset.y
        )
    }
    drawPath(
        path = shadowPath,
        color = shadowColor,
        style = Stroke(width = baseArmWidth * 0.85f, cap = StrokeCap.Round)
    )
    rotate(armRotation - 99f, cartridge) {
        drawRoundRect(
            color = shadowColor,
            topLeft = Offset(
                cartridge.x - 28.dp.toPx() * armScale,
                cartridge.y - 20.dp.toPx() * armScale
            ) + shadowOffset,
            size = Size(56.dp.toPx() * armScale, 40.dp.toPx() * armScale),
            cornerRadius = CornerRadius(6.dp.toPx() * armScale, 6.dp.toPx() * armScale)
        )
    }

    drawCircle(
        color = metal.bodyMid,
        radius = 44.dp.toPx() * armScale,
        center = pivot
    )
    drawCircle(
        color = metal.bodyLight,
        radius = 40.dp.toPx() * armScale,
        center = pivot
    )
    drawCircle(
        color = metal.ring,
        radius = 40.dp.toPx() * armScale,
        center = pivot,
        style = Stroke(width = 1.5.dp.toPx() * armScale)
    )
    drawCircle(
        color = metal.bodyLight.copy(alpha = 0.92f),
        radius = 16.dp.toPx() * armScale,
        center = pivot
    )
    drawCircle(
        color = metal.bodyDark,
        radius = 10.dp.toPx() * armScale,
        center = pivot
    )
    drawCircle(
        color = Color(0xFF1E1E1E),
        radius = 5.dp.toPx() * armScale,
        center = pivot
    )

    drawLine(
        color = metal.bodyDark,
        start = armTop,
        end = armElbow,
        strokeWidth = baseArmWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = metal.bodyMid,
        start = armTop,
        end = armElbow,
        strokeWidth = baseArmWidth * 0.70f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = metal.bodyLight,
        start = armTop + Offset(-1.5.dp.toPx() * armScale, 0f),
        end = armElbow + Offset(-1.5.dp.toPx() * armScale, 0f),
        strokeWidth = baseArmWidth * 0.28f,
        cap = StrokeCap.Round
    )

    drawCircle(
        color = metal.accent,
        radius = 16.dp.toPx() * armScale,
        center = armElbow
    )
    drawCircle(
        color = metal.bodyLight,
        radius = 13.dp.toPx() * armScale,
        center = armElbow
    )
    drawCircle(
        color = metal.ring,
        radius = 13.dp.toPx() * armScale,
        center = armElbow,
        style = Stroke(width = 1.dp.toPx() * armScale)
    )
    drawCircle(
        color = metal.bodyDark,
        radius = 5.dp.toPx() * armScale,
        center = armElbow
    )

    drawPath(
        path = curvePath,
        color = metal.bodyDark,
        style = Stroke(width = baseArmWidth * 0.85f, cap = StrokeCap.Round)
    )
    drawPath(
        path = curvePath,
        color = metal.bodyMid,
        style = Stroke(width = baseArmWidth * 0.60f, cap = StrokeCap.Round)
    )
    drawPath(
        path = curvePath,
        color = metal.bodyLight,
        style = Stroke(width = baseArmWidth * 0.22f, cap = StrokeCap.Round)
    )

    rotate(armRotation - 99f, cartridge) {
        drawRoundRect(
            color = metal.bodyDark.copy(alpha = 0.72f),
            topLeft = Offset(
                cartridge.x - 26.dp.toPx() * armScale,
                cartridge.y - 17.dp.toPx() * armScale
            ),
            size = Size(50.dp.toPx() * armScale, 36.dp.toPx() * armScale),
            cornerRadius = CornerRadius(6.dp.toPx() * armScale, 6.dp.toPx() * armScale)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    metal.bodyLight,
                    metal.bodyMid,
                    metal.bodyMid.copy(alpha = 0.92f)
                )
            ),
            topLeft = Offset(
                cartridge.x - 25.dp.toPx() * armScale,
                cartridge.y - 16.dp.toPx() * armScale
            ),
            size = Size(48.dp.toPx() * armScale, 32.dp.toPx() * armScale),
            cornerRadius = CornerRadius(5.dp.toPx() * armScale, 5.dp.toPx() * armScale)
        )
        drawRect(
            color = metal.accent,
            topLeft = Offset(
                cartridge.x - 16.dp.toPx() * armScale,
                cartridge.y - 16.dp.toPx() * armScale
            ),
            size = Size(6.dp.toPx() * armScale, 32.dp.toPx() * armScale)
        )
        drawRoundRect(
            color = metal.ring,
            topLeft = Offset(
                cartridge.x - 25.dp.toPx() * armScale,
                cartridge.y - 16.dp.toPx() * armScale
            ),
            size = Size(48.dp.toPx() * armScale, 32.dp.toPx() * armScale),
            cornerRadius = CornerRadius(5.dp.toPx() * armScale, 5.dp.toPx() * armScale),
            style = Stroke(width = 1.dp.toPx() * armScale)
        )
        drawLine(
            color = metal.bodyDark,
            start = Offset(
                cartridge.x + 3.dp.toPx() * armScale,
                cartridge.y + 10.dp.toPx() * armScale
            ),
            end = Offset(
                cartridge.x + 6.dp.toPx() * armScale,
                cartridge.y + 22.dp.toPx() * armScale
            ),
            strokeWidth = 2.5.dp.toPx() * armScale,
            cap = StrokeCap.Round
        )
        drawLine(
            color = metal.ring,
            start = Offset(
                cartridge.x - 19.dp.toPx() * armScale,
                cartridge.y - 12.dp.toPx() * armScale
            ),
            end = Offset(
                cartridge.x - 26.dp.toPx() * armScale,
                cartridge.y - 19.dp.toPx() * armScale
            ),
            strokeWidth = 2.dp.toPx() * armScale,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MiniCapsule(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    palette: MusicPalette
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = palette.textStrong,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PlayerPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    palette: MusicPalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    compact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val pressedOffset = if (compact) 3.dp else 4.dp
    val translationY by animateDpAsState(
        targetValue = if (isPressed) pressedOffset else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "btnPressY"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (enabled) 0.90f else 0.40f,
        animationSpec = tween(durationMillis = 200),
        label = "iconAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (enabled) 0.70f else 0.35f,
        animationSpec = tween(durationMillis = 200),
        label = "textAlpha"
    )

    val buttonBrush = if (emphasized) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFF0F5),
                Color(0xFFFFD7E5),
                Color(0xFFFFB3D1)
            )
        )
    } else if (palette.isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF323238),
                Color(0xFF24242A),
                Color(0xFF1C1C22)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White,
                Color(0xFFFAF9F6),
                Color(0xFFEDEDE6)
            )
        )
    }

    val borderColor = if (emphasized) {
        Color(0xFFFF9BC0)
    } else if (palette.isDark) {
        Color(0xFF3F3F46)
    } else {
        Color(0xFFE5E2DA)
    }
    val outerWidth = if (compact) 64.dp else 84.dp
    val depthHeight = if (compact) 34.dp else 44.dp
    val cornerRadius = if (compact) 9.dp else 12.dp
    val innerCornerRadius = if (compact) 7.dp else 9.dp
    val iconSize = if (compact) 16.dp else 20.dp
    val labelHeight = if (compact) 12.dp else 18.dp
    val labelSpacing = if (compact) 5.dp else 10.dp
    val labelLetterSpacing = if (compact) 1.sp else 1.6.sp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(labelSpacing)
    ) {
        // Container Box holding the 3D button assembly
        Box(
            modifier = Modifier.size(width = outerWidth, height = depthHeight + 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 1. The 3D Depth Side Body (sits at the bottom of the slot)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(depthHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        if (emphasized) Color(0xFFFFC0D3)
                        else if (palette.isDark) Color(0xFF141416)
                        else Color(0xFFC5C0B2), 
                        RoundedCornerShape(cornerRadius)
                    )
                    .border(
                        1.dp,
                        if (emphasized) Color(0xFFFFA3C1)
                        else if (palette.isDark) Color(0xFF24242A)
                        else Color(0xFFB0AB9F),
                        RoundedCornerShape(cornerRadius)
                    )
            )

            // 2. The sliding mechanical key cap face
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(depthHeight)
                    .offset(y = translationY)
                    .background(
                        buttonBrush,
                        RoundedCornerShape(cornerRadius)
                    )
                    .border(
                        1.dp,
                        borderColor,
                        RoundedCornerShape(cornerRadius)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null, 
                        enabled = enabled,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .border(
                            0.8.dp,
                            if (emphasized) Color(0xFFFFF0F5)
                            else if (palette.isDark) Color(0xFF44444C)
                            else Color(0xFFF7F5EE),
                            RoundedCornerShape(innerCornerRadius)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (emphasized) {
                            Color(0xFF641B3F).copy(alpha = iconAlpha)
                        } else {
                            palette.textStrong.copy(alpha = iconAlpha)
                        },
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
        
        // Render a solid, static chassis marking text label below the keycap!
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(labelHeight)
        ) {
            Text(
                text = label.uppercase(),
                color = palette.textStrong.copy(alpha = textAlpha),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = labelLetterSpacing
            )
        }
    }
}

@Composable
private fun EqualizerHint(
    isPlaying: Boolean,
    palette: MusicPalette,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "musicEqualizer")
    val first by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val second by transition.animateFloat(
        initialValue = 0.44f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val third by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )
    val fourth by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.84f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq4"
    )
    val fifth by transition.animateFloat(
        initialValue = 0.36f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq5"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(first, second, third, fourth, fifth).forEach { value ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((24f + 36f * if (isPlaying) value else 0.24f).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.accentSoft,
                                palette.accent
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun MusicBackdrop(
    activePalette: MusicPalette,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "musicBackdrop")
    val drift by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdropDrift"
    )

    Box(modifier = modifier) {
        // Base background gradient using palette colors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            activePalette.backgroundTop,
                            lerpColor(activePalette.backgroundTop, activePalette.backgroundBottom, 0.42f),
                            activePalette.backgroundBottom
                        )
                    )
                )
        )
        RomanceAmbientDecor(
            darkTheme = activePalette.isDark,
            modifier = Modifier.fillMaxSize()
        )
        // Very subtle grain/noise texture
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(42)
            val grainColor = if (activePalette.isDark) Color.White else Color.Black
            repeat(800) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val opacity = random.nextFloat() * 0.018f + 0.004f
                val r = random.nextFloat() * 0.6f + 0.2f
                drawCircle(
                    color = grainColor.copy(alpha = opacity),
                    radius = r,
                    center = Offset(x, y)
                )
            }
        }
        // Subtle ambient glow from album art color
        Box(
            modifier = Modifier
                .padding(top = 80.dp, start = 18.dp)
                .size(320.dp)
                .graphicsLayer {
                    translationX = drift * 0.45f
                    translationY = drift * 0.24f
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activePalette.accentSoft.copy(alpha = if (activePalette.isDark) 0.08f else 0.20f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun MusicPermissionGate(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    showRationale: Boolean
) {
    val isDark = false
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) listOf(Color(0xFF0D0F1A), Color(0xFF16102A), Color(0xFF1A1225))
                    else listOf(Color(0xFFF0F4F8), Color(0xFFE6E0FF), Color(0xFFF3E5F5))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        RomanceAmbientDecor(
            darkTheme = isDark,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            modifier = Modifier.padding(horizontal = 22.dp),
            shape = RoundedCornerShape(34.dp),
            color = if (isDark) Color(0xFF1E1A2E).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.70f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD7E5),
                                    Color(0xFFFF9BC0)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = Color(0xFF641B3F),
                        modifier = Modifier.size(42.dp)
                    )
                }
                Text(
                    text = "Allow music access",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFE8BACC) else Color(0xFF4E1430)
                )
                Text(
                    text = if (showRationale) {
                        "Gigi needs audio permission so it can scan local songs and open the full-screen vinyl player."
                    } else {
                        "Turn on audio access to scan songs stored on this phone."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDark) Color(0xFFB89AA8) else Color(0xFF8A6B78),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF8455FF) else Color(0xFFFF97BF),
                        contentColor = Color.White
                    )
                ) {
                    Text("Allow music access", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isDark) Color(0xFFB89AA8) else Color(0xFF4E1430)
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open app settings")
                }
            }
        }
    }
}

@Composable
private fun MusicLoadingCard(modifier: Modifier = Modifier) {
    val isDark = false
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) listOf(Color(0xFF0D0F1A), Color(0xFF16102A), Color(0xFF1A1225))
                    else listOf(Color(0xFFF0F4F8), Color(0xFFE6E0FF), Color(0xFFF3E5F5))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        RomanceAmbientDecor(
            darkTheme = isDark,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            modifier = Modifier.padding(horizontal = 22.dp),
            shape = RoundedCornerShape(30.dp),
            color = if (isDark) Color(0xFF1E1A2E).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.70f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = if (isDark) Color(0xFF8455FF) else Color(0xFFFF97BF))
                Text(
                    text = "Scanning your phone for music...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFE8BACC) else Color(0xFF4E1430),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "The full-screen player will be ready when your local tracks load.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDark) Color(0xFFB89AA8) else Color(0xFF8A6B78),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyMusicScene(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    errorMessage: String?
) {
    val isDark = false
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) listOf(Color(0xFF0D0F1A), Color(0xFF16102A), Color(0xFF1A1225))
                    else listOf(Color(0xFFF0F4F8), Color(0xFFE6E0FF), Color(0xFFF3E5F5))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        RomanceAmbientDecor(
            darkTheme = isDark,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            modifier = Modifier.padding(horizontal = 22.dp),
            shape = RoundedCornerShape(34.dp),
            color = if (isDark) Color(0xFF1E1A2E).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.70f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF8455FF) else Color(0xFFFF97BF),
                    modifier = Modifier.size(50.dp)
                )
                Text(
                    text = "No local songs found yet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFE8BACC) else Color(0xFF4E1430),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = errorMessage ?: "Refresh after the device library finishes indexing, or after adding songs to the phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDark) Color(0xFFB89AA8) else Color(0xFF8A6B78),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRefresh,
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF8455FF) else Color(0xFFFF97BF),
                        contentColor = Color.White
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan again", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val dynamicFinishes = listOf(
    RecordFinishOption.CLASSIC_BLACK,
    RecordFinishOption.CRYSTAL_TRANSLUCENT,
    RecordFinishOption.SMOKY_CHARCOAL,
    RecordFinishOption.GALAXY_DUST,
    RecordFinishOption.SUPERNOVA,
    RecordFinishOption.COSMIC_AURORA,
    RecordFinishOption.MOSS_JADE,
    RecordFinishOption.AMBER_SAP,
    RecordFinishOption.AUTUMN_FOREST,
    RecordFinishOption.CANDY_SWIRL,
    RecordFinishOption.CHOCO_CARAMEL,
    RecordFinishOption.BERRY_SPRINKLES,
    RecordFinishOption.HOLOGRAM_DISC,
    RecordFinishOption.GRID_LASER,
    RecordFinishOption.DATA_STREAM,
    RecordFinishOption.ABYSSAL_CURRENT,
    RecordFinishOption.PEARL_OYSTER,
    RecordFinishOption.DEEP_REEF,
    RecordFinishOption.SAKURA_RESIN,
    RecordFinishOption.GOLDEN_GINGKO,
    RecordFinishOption.PRESSED_LAVENDER,
    RecordFinishOption.CRACKED_ICE,
    RecordFinishOption.SNOW_BLIZZARD,
    RecordFinishOption.GLACIER_MELT,
    RecordFinishOption.SAHARA_DUNE,
    RecordFinishOption.RED_CANYON,
    RecordFinishOption.MIRAGE_BLUE,
    RecordFinishOption.TAROT_GOLD,
    RecordFinishOption.SPELL_CIRCLE,
    RecordFinishOption.CRIMSON_ELIXIR,
    RecordFinishOption.FIRE_VINYL
)

private fun getDynamicFinishForSong(songId: Long, songTitle: String): RecordFinishOption {
    val rawHash = songId.hashCode() xor songTitle.hashCode()
    val hash = if (rawHash == Int.MIN_VALUE) Int.MAX_VALUE else if (rawHash < 0) -rawHash else rawHash
    val index = hash % dynamicFinishes.size
    return dynamicFinishes[index]
}

private fun applyVinylTheme(
    basePalette: MusicPalette,
    config: VinylThemeConfig
): MusicPalette {
    val presetPalette = when (config.preset) {
        PlayerThemePreset.AUTO -> basePalette

        // Group 1: 🌸 "四季の詩" (Poetry of Seasons)
        PlayerThemePreset.SAKURA_SPRING -> MusicPalette(
            backgroundTop = Color(0xFFFFF0F5),
            backgroundBottom = Color(0xFFFFE4E1),
            accent = Color(0xFFFF69B4),
            accentSoft = Color(0xFFFFF0F3),
            textStrong = Color(0xFF8B008B),
            textMuted = Color(0xFFC71585),
            recordKind = RecordKind.ROSE_BLUSH,
            isDark = false
        )
        PlayerThemePreset.MIDNIGHT_SUMMER -> MusicPalette(
            backgroundTop = Color(0xFF1F1235),
            backgroundBottom = Color(0xFF0F071A),
            accent = Color(0xFFFF8C00),
            accentSoft = Color(0xFF2E194B),
            textStrong = Color(0xFFFFF3D1),
            textMuted = Color(0xFFB392AC),
            recordKind = RecordKind.CLASSIC_BLACK,
            isDark = true
        )
        PlayerThemePreset.MAPLE_WHISPER -> MusicPalette(
            backgroundTop = Color(0xFF3D1F16),
            backgroundBottom = Color(0xFF200C07),
            accent = Color(0xFFCD5C5C),
            accentSoft = Color(0xFF592D21),
            textStrong = Color(0xFFFFCC99),
            textMuted = Color(0xFFB57C5E),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = true
        )
        PlayerThemePreset.AURORA_FROST -> MusicPalette(
            backgroundTop = Color(0xFF0C2540),
            backgroundBottom = Color(0xFF05101C),
            accent = Color(0xFF00FFCC),
            accentSoft = Color(0xFF14395E),
            textStrong = Color(0xFFE0FFFF),
            textMuted = Color(0xFF80CBC4),
            recordKind = RecordKind.SPLASH_BLUE,
            isDark = true
        )

        // Group 2: 🧸 "Dreamy Hideaway"
        PlayerThemePreset.VINTAGE_TEDDY -> MusicPalette(
            backgroundTop = Color(0xFFF5EBE6),
            backgroundBottom = Color(0xFFE3D5CA),
            accent = Color(0xFFD5BFAF),
            accentSoft = Color(0xFFFDFBF7),
            textStrong = Color(0xFF4E3D30),
            textMuted = Color(0xFF8E7A6B),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )
        PlayerThemePreset.PASTEL_PAJAMAS -> MusicPalette(
            backgroundTop = Color(0xFFF3E8FF),
            backgroundBottom = Color(0xFFFFECEC),
            accent = Color(0xFFFFB7B7),
            accentSoft = Color(0xFFFFF5F5),
            textStrong = Color(0xFF6B4E71),
            textMuted = Color(0xFF9A7B9F),
            recordKind = RecordKind.ROSE_BLUSH,
            isDark = false
        )
        PlayerThemePreset.RAINY_WINDOW -> MusicPalette(
            backgroundTop = Color(0xFF2C3E50),
            backgroundBottom = Color(0xFF1A252F),
            accent = Color(0xFFF39C12),
            accentSoft = Color(0xFF34495E),
            textStrong = Color(0xFFECF0F1),
            textMuted = Color(0xFFBDC3C7),
            recordKind = RecordKind.SMOKE_MARBLE,
            isDark = true
        )

        // Group 3: 🌌 "Aetherial Whispers"
        PlayerThemePreset.STARDUST_LULLABY -> MusicPalette(
            backgroundTop = Color(0xFF1A1B35),
            backgroundBottom = Color(0xFF0C0D1C),
            accent = Color(0xFF8A2BE2),
            accentSoft = Color(0xFF23254A),
            textStrong = Color(0xFFE6E6FA),
            textMuted = Color(0xFF9370DB),
            recordKind = RecordKind.SMOKE_MARBLE,
            isDark = true
        )
        PlayerThemePreset.LUNAR_ECLIPSE -> MusicPalette(
            backgroundTop = Color(0xFF111111),
            backgroundBottom = Color(0xFF050505),
            accent = Color(0xFFFF2A2A),
            accentSoft = Color(0xFF221111),
            textStrong = Color(0xFFE0E0E0),
            textMuted = Color(0xFF888888),
            recordKind = RecordKind.CLASSIC_BLACK,
            isDark = true
        )
        PlayerThemePreset.PIXIE_DUST -> MusicPalette(
            backgroundTop = Color(0xFF0B2B1E),
            backgroundBottom = Color(0xFF04140E),
            accent = Color(0xFF39FF14),
            accentSoft = Color(0xFF0D3E2B),
            textStrong = Color(0xFFF3FFD7),
            textMuted = Color(0xFF4AC27E),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = true
        )

        // Group 4: 🍵 "Tea Time Stories"
        PlayerThemePreset.MATCHA_LATTE -> MusicPalette(
            backgroundTop = Color(0xFFE8EFE9),
            backgroundBottom = Color(0xFFD2DFD4),
            accent = Color(0xFF8A9A86),
            accentSoft = Color(0xFFF2F6F3),
            textStrong = Color(0xFF2F3E32),
            textMuted = Color(0xFF6B7E6F),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )
        PlayerThemePreset.EARL_GREY -> MusicPalette(
            backgroundTop = Color(0xFFEAEFF2),
            backgroundBottom = Color(0xFFD3DFE5),
            accent = Color(0xFF9AAEC4),
            accentSoft = Color(0xFFF3F6F8),
            textStrong = Color(0xFF384351),
            textMuted = Color(0xFF708090),
            recordKind = RecordKind.SMOKE_MARBLE,
            isDark = false
        )
        PlayerThemePreset.CAMOMILE_MEADOW -> MusicPalette(
            backgroundTop = Color(0xFFFFFDF0),
            backgroundBottom = Color(0xFFFFF9D9),
            accent = Color(0xFFFFD700),
            accentSoft = Color(0xFFFFFEEB),
            textStrong = Color(0xFF5D5A43),
            textMuted = Color(0xFF9E9875),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )

        // Group 5: 🎮 "Retro Arcade"
        PlayerThemePreset.NEON_ODYSSEY -> MusicPalette(
            backgroundTop = Color(0xFF1D003E),
            backgroundBottom = Color(0xFF080016),
            accent = Color(0xFFFF007F),
            accentSoft = Color(0xFF2D0060),
            textStrong = Color(0xFF00FFFF),
            textMuted = Color(0xFF8B00FF),
            recordKind = RecordKind.SMOKE_MARBLE,
            isDark = true
        )
        PlayerThemePreset.DUNGEON_8BIT -> MusicPalette(
            backgroundTop = Color(0xFF08180A),
            backgroundBottom = Color(0xFF020603),
            accent = Color(0xFF39FF14),
            accentSoft = Color(0xFF0C2610),
            textStrong = Color(0xFF39FF14),
            textMuted = Color(0xFF1F802C),
            recordKind = RecordKind.CLASSIC_BLACK,
            isDark = true
        )
        PlayerThemePreset.SYNTHWAVE_HIGHWAY -> MusicPalette(
            backgroundTop = Color(0xFF2B0938),
            backgroundBottom = Color(0xFF0D031A),
            accent = Color(0xFFFF5E00),
            accentSoft = Color(0xFF450D59),
            textStrong = Color(0xFFFF007F),
            textMuted = Color(0xFFD400FF),
            recordKind = RecordKind.SMOKE_MARBLE,
            isDark = true
        )

        // Group 6: 🐚 "Oceanic Secrets"
        PlayerThemePreset.DEEP_CORAL -> MusicPalette(
            backgroundTop = Color(0xFFFFECE5),
            backgroundBottom = Color(0xFFFFD4C4),
            accent = Color(0xFFFF6F61),
            accentSoft = Color(0xFFFFF5F2),
            textStrong = Color(0xFF5A312B),
            textMuted = Color(0xFF9E655C),
            recordKind = RecordKind.ROSE_BLUSH,
            isDark = false
        )
        PlayerThemePreset.ABYSSAL_GLOW -> MusicPalette(
            backgroundTop = Color(0xFF041029),
            backgroundBottom = Color(0xFF010612),
            accent = Color(0xFF00FF88),
            accentSoft = Color(0xFF0B2147),
            textStrong = Color(0xFFD1E8FF),
            textMuted = Color(0xFF5F8BB7),
            recordKind = RecordKind.SPLASH_BLUE,
            isDark = true
        )
        PlayerThemePreset.PEARL_SHELL -> MusicPalette(
            backgroundTop = Color(0xFFF9F7F5),
            backgroundBottom = Color(0xFFEEEAE5),
            accent = Color(0xFFD1BCA6),
            accentSoft = Color(0xFFFFFDFB),
            textStrong = Color(0xFF4E453E),
            textMuted = Color(0xFF968B80),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )

        // Group 7: 📚 "Library of Winds"
        PlayerThemePreset.OLD_PARCHMENT -> MusicPalette(
            backgroundTop = Color(0xFFEFE8DA),
            backgroundBottom = Color(0xFFDCD2BD),
            accent = Color(0xFF8B5E3C),
            accentSoft = Color(0xFFF6F3EC),
            textStrong = Color(0xFF3E2D20),
            textMuted = Color(0xFF7F6A5A),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )
        PlayerThemePreset.POETS_INK -> MusicPalette(
            backgroundTop = Color(0xFF1D2024),
            backgroundBottom = Color(0xFF0F1012),
            accent = Color(0xFFA62B2B),
            accentSoft = Color(0xFF2D3138),
            textStrong = Color(0xFFF5EFE6),
            textMuted = Color(0xFF8A929E),
            recordKind = RecordKind.CLASSIC_BLACK,
            isDark = true
        )
        PlayerThemePreset.FOREST_HERBAL -> MusicPalette(
            backgroundTop = Color(0xFFE8EEE5),
            backgroundBottom = Color(0xFFCDD9CA),
            accent = Color(0xFF6B8E23),
            accentSoft = Color(0xFFF3F7F2),
            textStrong = Color(0xFF2C3E2B),
            textMuted = Color(0xFF617F60),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )

        // Group 8: 🍭 "Candy Land Carousel"
        PlayerThemePreset.COTTON_CANDY -> MusicPalette(
            backgroundTop = Color(0xFFFFECF2),
            backgroundBottom = Color(0xFFE0F7FF),
            accent = Color(0xFFFFA2C4),
            accentSoft = Color(0xFFFFF7FA),
            textStrong = Color(0xFF4E7FA5),
            textMuted = Color(0xFF80A9C9),
            recordKind = RecordKind.ROSE_BLUSH,
            isDark = false
        )
        PlayerThemePreset.SOUR_LEMONADE -> MusicPalette(
            backgroundTop = Color(0xFFFFFEE0),
            backgroundBottom = Color(0xFFF1FED1),
            accent = Color(0xFFCBE300),
            accentSoft = Color(0xFFFFFFF2),
            textStrong = Color(0xFF55601F),
            textMuted = Color(0xFF90A145),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )
        PlayerThemePreset.CHOCO_MINT -> MusicPalette(
            backgroundTop = Color(0xFF2C1E18),
            backgroundBottom = Color(0xFF18100C),
            accent = Color(0xFF00FFCC),
            accentSoft = Color(0xFF442D23),
            textStrong = Color(0xFFF0E5DE),
            textMuted = Color(0xFF9E8D82),
            recordKind = RecordKind.CLASSIC_BLACK,
            isDark = true
        )

        // Group 9: 🏜️ "Wandering Dunes"
        PlayerThemePreset.SAHARA_SUNSET -> MusicPalette(
            backgroundTop = Color(0xFF4C2A1E),
            backgroundBottom = Color(0xFF261009),
            accent = Color(0xFFE67E22),
            accentSoft = Color(0xFF6A3A2A),
            textStrong = Color(0xFFFFCC99),
            textMuted = Color(0xFFC08A75),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = true
        )
        PlayerThemePreset.OASIS_MIRAGE -> MusicPalette(
            backgroundTop = Color(0xFFE5F7F6),
            backgroundBottom = Color(0xFFCCEFEB),
            accent = Color(0xFF00A896),
            accentSoft = Color(0xFFF2FBFA),
            textStrong = Color(0xFF1B4947),
            textMuted = Color(0xFF5B8A88),
            recordKind = RecordKind.SPLASH_BLUE,
            isDark = false
        )
        PlayerThemePreset.RED_CANYON -> MusicPalette(
            backgroundTop = Color(0xFF3E1F1F),
            backgroundBottom = Color(0xFF1F0E0E),
            accent = Color(0xFFD35400),
            accentSoft = Color(0xFF5C2C2C),
            textStrong = Color(0xFFF39C12),
            textMuted = Color(0xFFB87333),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = true
        )

        // Group 10: 🔮 "Mystic Tarot"
        PlayerThemePreset.THE_STAR -> MusicPalette(
            backgroundTop = Color(0xFF0E1128),
            backgroundBottom = Color(0xFF050612),
            accent = Color(0xFFE6E6FA),
            accentSoft = Color(0xFF161C40),
            textStrong = Color(0xFFE6E6FA),
            textMuted = Color(0xFF7B8CB6),
            recordKind = RecordKind.SPLASH_BLUE,
            isDark = true
        )
        PlayerThemePreset.THE_FOOL -> MusicPalette(
            backgroundTop = Color(0xFFFFFEEB),
            backgroundBottom = Color(0xFFFFEEB2),
            accent = Color(0xFFFFB800),
            accentSoft = Color(0xFFFFFFFA),
            textStrong = Color(0xFF5E543D),
            textMuted = Color(0xFF9E9273),
            recordKind = RecordKind.CHAMPAGNE_MARBLE,
            isDark = false
        )
        PlayerThemePreset.THE_MAGICIAN -> MusicPalette(
            backgroundTop = Color(0xFF26103A),
            backgroundBottom = Color(0xFF11051D),
            accent = Color(0xFFFFD700),
            accentSoft = Color(0xFF3F1B5F),
            textStrong = Color(0xFFF3A4B5),
            textMuted = Color(0xFFB57AA6),
            recordKind = RecordKind.SMOKE_MARBLE,
            isDark = true
        )
    }

    return presetPalette.copy(recordKind = resolveRecordKindOverride(presetPalette.recordKind, config.recordFinish))
}

private fun resolveRecordKindOverride(
    fallback: RecordKind,
    finish: RecordFinishOption
): RecordKind {
    return when (finish) {
        RecordFinishOption.AUTO -> fallback
        RecordFinishOption.CLASSIC_BLACK -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.CRYSTAL_TRANSLUCENT -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.SMOKY_CHARCOAL -> RecordKind.SMOKE_MARBLE
        
        RecordFinishOption.GALAXY_DUST -> RecordKind.SMOKE_MARBLE
        RecordFinishOption.SUPERNOVA -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.COSMIC_AURORA -> RecordKind.SMOKE_MARBLE
        
        RecordFinishOption.MOSS_JADE -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.AMBER_SAP -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.AUTUMN_FOREST -> RecordKind.SMOKE_MARBLE
        
        RecordFinishOption.CANDY_SWIRL -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.CHOCO_CARAMEL -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.BERRY_SPRINKLES -> RecordKind.ROSE_BLUSH
        
        RecordFinishOption.HOLOGRAM_DISC -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.GRID_LASER -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.DATA_STREAM -> RecordKind.CLASSIC_BLACK
        
        RecordFinishOption.ABYSSAL_CURRENT -> RecordKind.SPLASH_BLUE
        RecordFinishOption.PEARL_OYSTER -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.DEEP_REEF -> RecordKind.SPLASH_BLUE
        
        RecordFinishOption.SAKURA_RESIN -> RecordKind.ROSE_BLUSH
        RecordFinishOption.GOLDEN_GINGKO -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.PRESSED_LAVENDER -> RecordKind.ROSE_BLUSH
        
        RecordFinishOption.CRACKED_ICE -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.SNOW_BLIZZARD -> RecordKind.SPLASH_BLUE
        RecordFinishOption.GLACIER_MELT -> RecordKind.SPLASH_BLUE
        
        RecordFinishOption.SAHARA_DUNE -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.RED_CANYON -> RecordKind.CHAMPAGNE_MARBLE
        RecordFinishOption.MIRAGE_BLUE -> RecordKind.SPLASH_BLUE
        
        RecordFinishOption.TAROT_GOLD -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.SPELL_CIRCLE -> RecordKind.CLASSIC_BLACK
        RecordFinishOption.CRIMSON_ELIXIR -> RecordKind.ROSE_BLUSH
        RecordFinishOption.FIRE_VINYL -> RecordKind.CLASSIC_BLACK
    }
}

private fun tonearmColorsFor(finish: TonearmFinish): TonearmColors {
    return when (finish) {
        TonearmFinish.SILVER -> TonearmColors(
            bodyDark = Color(0xFF8B8B84),
            bodyMid = Color(0xFFD4D0C5),
            bodyLight = Color.White,
            ring = Color(0xFFB8B5AD),
            accent = Color(0xFFC7B184)
        )

        TonearmFinish.CHAMPAGNE -> TonearmColors(
            bodyDark = Color(0xFF9B8666),
            bodyMid = Color(0xFFE0CDAA),
            bodyLight = Color(0xFFFFF4E2),
            ring = Color(0xFFC6AF86),
            accent = Color(0xFFD1B26D)
        )

        TonearmFinish.ROSE_GOLD -> TonearmColors(
            bodyDark = Color(0xFF98716C),
            bodyMid = Color(0xFFE6C3BE),
            bodyLight = Color(0xFFFFF0ED),
            ring = Color(0xFFC79E98),
            accent = Color(0xFFD7A79B)
        )

        TonearmFinish.GRAPHITE -> TonearmColors(
            bodyDark = Color(0xFF5F646A),
            bodyMid = Color(0xFFAEB5BC),
            bodyLight = Color(0xFFE8EDF1),
            ring = Color(0xFF7D838B),
            accent = Color(0xFFB7A373)
        )
    }
}

private fun labelToneColorsFor(tone: LabelTone): LabelToneColors {
    return when (tone) {
        LabelTone.IVORY -> LabelToneColors(
            base = Color(0xFFFAF7F0),
            ring = Color(0xFFEDE8DF),
            text = Color(0xFF8E7A6B),
            spindleOuter = Color(0xFFC5C2BA),
            spindleInner = Color(0xFF1E1E1E)
        )

        LabelTone.BLUSH -> LabelToneColors(
            base = Color(0xFFFFF4F6),
            ring = Color(0xFFF2DCE3),
            text = Color(0xFFB06F86),
            spindleOuter = Color(0xFFE1B9C8),
            spindleInner = Color(0xFF442733)
        )

        LabelTone.NOIR -> LabelToneColors(
            base = Color(0xFF2A2727),
            ring = Color(0xFF484040),
            text = Color(0xFFF0DED2),
            spindleOuter = Color(0xFF8A7B73),
            spindleInner = Color(0xFFF7F3EE)
        )
    }
}

private fun presetPreviewSwatches(preset: PlayerThemePreset): List<Color> {
    return when (preset) {
        PlayerThemePreset.AUTO -> listOf(Color(0xFFFFFEEB), Color(0xFFFFB800), Color(0xFF2B201B))
        
        // Group 1: 🌸 "四季の詩" (Poetry of Seasons)
        PlayerThemePreset.SAKURA_SPRING -> listOf(Color(0xFFFFF0F5), Color(0xFFFF69B4), Color(0xFF8B008B))
        PlayerThemePreset.MIDNIGHT_SUMMER -> listOf(Color(0xFF1F1235), Color(0xFFFF8C00), Color(0xFFB392AC))
        PlayerThemePreset.MAPLE_WHISPER -> listOf(Color(0xFF3D1F16), Color(0xFFCD5C5C), Color(0xFFFFCC99))
        PlayerThemePreset.AURORA_FROST -> listOf(Color(0xFF0C2540), Color(0xFF00FFCC), Color(0xFFE0FFFF))
        
        // Group 2: 🧸 "Dreamy Hideaway"
        PlayerThemePreset.VINTAGE_TEDDY -> listOf(Color(0xFFF5EBE6), Color(0xFFD5BFAF), Color(0xFF4E3D30))
        PlayerThemePreset.PASTEL_PAJAMAS -> listOf(Color(0xFFF3E8FF), Color(0xFFFFB7B7), Color(0xFF6B4E71))
        PlayerThemePreset.RAINY_WINDOW -> listOf(Color(0xFF2C3E50), Color(0xFFF39C12), Color(0xFFECF0F1))
        
        // Group 3: 🌌 "Aetherial Whispers"
        PlayerThemePreset.STARDUST_LULLABY -> listOf(Color(0xFF1A1B35), Color(0xFF8A2BE2), Color(0xFFE6E6FA))
        PlayerThemePreset.LUNAR_ECLIPSE -> listOf(Color(0xFF111111), Color(0xFFFF2A2A), Color(0xFFE0E0E0))
        PlayerThemePreset.PIXIE_DUST -> listOf(Color(0xFF0B2B1E), Color(0xFF39FF14), Color(0xFFF3FFD7))
        
        // Group 4: 🍵 "Tea Time Stories"
        PlayerThemePreset.MATCHA_LATTE -> listOf(Color(0xFFE8EFE9), Color(0xFF8A9A86), Color(0xFF2F3E32))
        PlayerThemePreset.EARL_GREY -> listOf(Color(0xFFEAEFF2), Color(0xFF9AAEC4), Color(0xFF384351))
        PlayerThemePreset.CAMOMILE_MEADOW -> listOf(Color(0xFFFFFDF0), Color(0xFFFFD700), Color(0xFF5D5A43))
        
        // Group 5: 🎮 "Retro Arcade"
        PlayerThemePreset.NEON_ODYSSEY -> listOf(Color(0xFF1D003E), Color(0xFFFF007F), Color(0xFF00FFFF))
        PlayerThemePreset.DUNGEON_8BIT -> listOf(Color(0xFF08180A), Color(0xFF39FF14), Color(0xFF1F802C))
        PlayerThemePreset.SYNTHWAVE_HIGHWAY -> listOf(Color(0xFF2B0938), Color(0xFFFF5E00), Color(0xFFFF007F))
        
        // Group 6: 🐚 "Oceanic Secrets"
        PlayerThemePreset.DEEP_CORAL -> listOf(Color(0xFFFFECE5), Color(0xFFFF6F61), Color(0xFF5A312B))
        PlayerThemePreset.ABYSSAL_GLOW -> listOf(Color(0xFF041029), Color(0xFF00FF88), Color(0xFFD1E8FF))
        PlayerThemePreset.PEARL_SHELL -> listOf(Color(0xFFF9F7F5), Color(0xFFD1BCA6), Color(0xFF4E453E))
        
        // Group 7: 📚 "Library of Winds"
        PlayerThemePreset.OLD_PARCHMENT -> listOf(Color(0xFFEFE8DA), Color(0xFF8B5E3C), Color(0xFF3E2D20))
        PlayerThemePreset.POETS_INK -> listOf(Color(0xFF1D2024), Color(0xFFA62B2B), Color(0xFFF5EFE6))
        PlayerThemePreset.FOREST_HERBAL -> listOf(Color(0xFFE8EEE5), Color(0xFF6B8E23), Color(0xFF2C3E2B))
        
        // Group 8: 🍭 "Candy Land Carousel"
        PlayerThemePreset.COTTON_CANDY -> listOf(Color(0xFFFFECF2), Color(0xFFFFA2C4), Color(0xFF4E7FA5))
        PlayerThemePreset.SOUR_LEMONADE -> listOf(Color(0xFFFFFEE0), Color(0xFFCBE300), Color(0xFF55601F))
        PlayerThemePreset.CHOCO_MINT -> listOf(Color(0xFF2C1E18), Color(0xFF00FFCC), Color(0xFFF0E5DE))
        
        // Group 9: 🏜️ "Wandering Dunes"
        PlayerThemePreset.SAHARA_SUNSET -> listOf(Color(0xFF4C2A1E), Color(0xFFE67E22), Color(0xFFFFCC99))
        PlayerThemePreset.OASIS_MIRAGE -> listOf(Color(0xFFE5F7F6), Color(0xFF00A896), Color(0xFF1B4947))
        PlayerThemePreset.RED_CANYON -> listOf(Color(0xFF3E1F1F), Color(0xFFD35400), Color(0xFFF39C12))
        
        // Group 10: 🔮 "Mystic Tarot"
        PlayerThemePreset.THE_STAR -> listOf(Color(0xFF0E1128), Color(0xFFE6E6FA), Color(0xFF7B8CB6))
        PlayerThemePreset.THE_FOOL -> listOf(Color(0xFFFFFEEB), Color(0xFFFFB800), Color(0xFF5E543D))
        PlayerThemePreset.THE_MAGICIAN -> listOf(Color(0xFF26103A), Color(0xFFFFD700), Color(0xFFF3A4B5))
    }
}

private fun recordFinishPreviewSwatches(finish: RecordFinishOption): List<Color> {
    return when (finish) {
        RecordFinishOption.AUTO -> listOf(Color(0xFFEDE3D6), Color(0xFFC59B6E), Color(0xFF3D342E))
        
        // Group 1: Standard Cuts
        RecordFinishOption.CLASSIC_BLACK -> listOf(Color(0xFF070707), Color(0xFF3C3C3C), Color(0xFFD0C8B8))
        RecordFinishOption.CRYSTAL_TRANSLUCENT -> listOf(Color(0xFFFFFFFF).copy(alpha = 0.8f), Color(0xFFE0E0E0), Color(0xFFB5A89E))
        RecordFinishOption.SMOKY_CHARCOAL -> listOf(Color(0xFF2A2A2A), Color(0xFF4E4E4E), Color(0xFF908075))
        
        // Group 2: Nebula
        RecordFinishOption.GALAXY_DUST -> listOf(Color(0xFF1A003E), Color(0xFFFF007F), Color(0xFF00FFFF))
        RecordFinishOption.SUPERNOVA -> listOf(Color(0xFFFF3D00), Color(0xFFFFD600), Color(0xFF3E2723))
        RecordFinishOption.COSMIC_AURORA -> listOf(Color(0xFF4A148C), Color(0xFF00E5FF), Color(0xFF1A0033))
        
        // Group 3: Terrarium
        RecordFinishOption.MOSS_JADE -> listOf(Color(0xFF004D40), Color(0xFF00E676), Color(0xFF81C784))
        RecordFinishOption.AMBER_SAP -> listOf(Color(0xFFFF8F00), Color(0xFFFFD54F), Color(0xFF5D4037))
        RecordFinishOption.AUTUMN_FOREST -> listOf(Color(0xFF2E7D32), Color(0xFFEF6C00), Color(0xFF8D6E63))
        
        // Group 4: Sweet
        RecordFinishOption.CANDY_SWIRL -> listOf(Color(0xFFD32F2F), Color(0xFFFFFFFF), Color(0xFFFFCDD2))
        RecordFinishOption.CHOCO_CARAMEL -> listOf(Color(0xFF3E2723), Color(0xFFFFB300), Color(0xFFD7CCC8))
        RecordFinishOption.BERRY_SPRINKLES -> listOf(Color(0xFFF06292), Color(0xFF00E5FF), Color(0xFFFFD54F))
        
        // Group 5: Cyber
        RecordFinishOption.HOLOGRAM_DISC -> listOf(Color(0xFF2979FF), Color(0xFFFF1744), Color(0xFF00E676))
        RecordFinishOption.GRID_LASER -> listOf(Color(0xFF000000), Color(0xFF00FF00), Color(0xFF003300))
        RecordFinishOption.DATA_STREAM -> listOf(Color(0xFF07070F), Color(0xFF00E5FF), Color(0xFF002233))
        
        // Group 6: Ocean
        RecordFinishOption.ABYSSAL_CURRENT -> listOf(Color(0xFF000A23), Color(0xFF00FFCC), Color(0xFF0D47A1))
        RecordFinishOption.PEARL_OYSTER -> listOf(Color(0xFFECEFF1), Color(0xFFF8BBD0), Color(0xFFD1C4E9))
        RecordFinishOption.DEEP_REEF -> listOf(Color(0xFF00E5FF), Color(0xFFFF7043), Color(0xFFFFCC80))
        
        // Group 7: Blossom
        RecordFinishOption.SAKURA_RESIN -> listOf(Color(0xFFFFF0F5), Color(0xFFFF8DA1), Color(0xFFF48FB1))
        RecordFinishOption.GOLDEN_GINGKO -> listOf(Color(0xFFFFFDE7), Color(0xFFFFD54F), Color(0xFFFFB300))
        RecordFinishOption.PRESSED_LAVENDER -> listOf(Color(0xFFEDE7F6), Color(0xFFB39DDB), Color(0xFF7E57C2))
        
        // Group 8: Glacial
        RecordFinishOption.CRACKED_ICE -> listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFFFFFFFF))
        RecordFinishOption.SNOW_BLIZZARD -> listOf(Color(0xFFE1F5FE), Color(0xFFFFFFFF), Color(0xFF81D4FA))
        RecordFinishOption.GLACIER_MELT -> listOf(Color(0xFF01579B), Color(0xFFB3E5FC), Color(0xFFFFFFFF))
        
        // Group 9: Desert
        RecordFinishOption.SAHARA_DUNE -> listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFF8D6E63))
        RecordFinishOption.RED_CANYON -> listOf(Color(0xFFD84315), Color(0xFFFF8A65), Color(0xFF4E342E))
        RecordFinishOption.MIRAGE_BLUE -> listOf(Color(0xFF00ACC1), Color(0xFFFFD54F), Color(0xFF006064))
        
        // Group 10: Tarot
        RecordFinishOption.TAROT_GOLD -> listOf(Color(0xFF1A1A1A), Color(0xFFFFD700), Color(0xFF333333))
        RecordFinishOption.SPELL_CIRCLE -> listOf(Color(0xFF0A0210), Color(0xFFBA68C8), Color(0xFF4A148C))
        RecordFinishOption.CRIMSON_ELIXIR -> listOf(Color(0xFFB71C1C), Color(0xFFFFD700), Color(0xFF3E2723))
        RecordFinishOption.FIRE_VINYL -> listOf(Color(0xFF150805), Color(0xFFFF3D00), Color(0xFFFF9100))
    }
}

private fun tonearmPreviewSwatches(finish: TonearmFinish): List<Color> {
    val colors = tonearmColorsFor(finish)
    return listOf(colors.bodyDark, colors.bodyMid, colors.accent)
}

private fun labelTonePreviewSwatches(tone: LabelTone): List<Color> {
    val colors = labelToneColorsFor(tone)
    return listOf(colors.base, colors.ring, colors.text)
}

private fun defaultTonearmFinishForPreset(preset: PlayerThemePreset): TonearmFinish {
    return when (preset) {
        PlayerThemePreset.AUTO -> TonearmFinish.SILVER
        
        // Silver arm
        PlayerThemePreset.AURORA_FROST, PlayerThemePreset.RAINY_WINDOW, 
        PlayerThemePreset.EARL_GREY, PlayerThemePreset.NEON_ODYSSEY,
        PlayerThemePreset.THE_STAR -> TonearmFinish.SILVER
        
        // Rose gold arm
        PlayerThemePreset.SAKURA_SPRING, PlayerThemePreset.PASTEL_PAJAMAS,
        PlayerThemePreset.COTTON_CANDY, PlayerThemePreset.DEEP_CORAL,
        PlayerThemePreset.PEARL_SHELL -> TonearmFinish.ROSE_GOLD
        
        // Graphite arm
        PlayerThemePreset.MIDNIGHT_SUMMER, PlayerThemePreset.STARDUST_LULLABY,
        PlayerThemePreset.LUNAR_ECLIPSE, PlayerThemePreset.ABYSSAL_GLOW,
        PlayerThemePreset.POETS_INK, PlayerThemePreset.CHOCO_MINT,
        PlayerThemePreset.THE_MAGICIAN -> TonearmFinish.GRAPHITE
        
        // Champagne arm
        PlayerThemePreset.MAPLE_WHISPER, PlayerThemePreset.VINTAGE_TEDDY,
        PlayerThemePreset.PIXIE_DUST, PlayerThemePreset.MATCHA_LATTE,
        PlayerThemePreset.CAMOMILE_MEADOW, PlayerThemePreset.DUNGEON_8BIT,
        PlayerThemePreset.SYNTHWAVE_HIGHWAY, PlayerThemePreset.OLD_PARCHMENT,
        PlayerThemePreset.FOREST_HERBAL, PlayerThemePreset.SOUR_LEMONADE,
        PlayerThemePreset.SAHARA_SUNSET, PlayerThemePreset.OASIS_MIRAGE,
        PlayerThemePreset.RED_CANYON, PlayerThemePreset.THE_FOOL -> TonearmFinish.CHAMPAGNE
    }
}

private fun defaultLabelToneForPreset(preset: PlayerThemePreset): LabelTone {
    return when (preset) {
        PlayerThemePreset.AUTO -> LabelTone.IVORY
        
        // Ivory Label
        PlayerThemePreset.VINTAGE_TEDDY, PlayerThemePreset.MATCHA_LATTE,
        PlayerThemePreset.CAMOMILE_MEADOW, PlayerThemePreset.OLD_PARCHMENT,
        PlayerThemePreset.THE_FOOL, PlayerThemePreset.FOREST_HERBAL -> LabelTone.IVORY
        
        // Blush Label
        PlayerThemePreset.SAKURA_SPRING, PlayerThemePreset.PASTEL_PAJAMAS,
        PlayerThemePreset.DEEP_CORAL, PlayerThemePreset.PEARL_SHELL,
        PlayerThemePreset.COTTON_CANDY, PlayerThemePreset.SOUR_LEMONADE -> LabelTone.BLUSH
        
        // Noir Label
        PlayerThemePreset.MIDNIGHT_SUMMER, PlayerThemePreset.MAPLE_WHISPER,
        PlayerThemePreset.AURORA_FROST, PlayerThemePreset.RAINY_WINDOW,
        PlayerThemePreset.STARDUST_LULLABY, PlayerThemePreset.LUNAR_ECLIPSE,
        PlayerThemePreset.PIXIE_DUST, PlayerThemePreset.EARL_GREY,
        PlayerThemePreset.NEON_ODYSSEY, PlayerThemePreset.DUNGEON_8BIT,
        PlayerThemePreset.SYNTHWAVE_HIGHWAY, PlayerThemePreset.ABYSSAL_GLOW,
        PlayerThemePreset.POETS_INK, PlayerThemePreset.CHOCO_MINT,
        PlayerThemePreset.SAHARA_SUNSET, PlayerThemePreset.OASIS_MIRAGE,
        PlayerThemePreset.RED_CANYON, PlayerThemePreset.THE_STAR,
        PlayerThemePreset.THE_MAGICIAN -> LabelTone.NOIR
    }
}

private fun advancedEditorColors(tab: AdvancedEditorTab): List<Color> {
    return when (tab) {
        AdvancedEditorTab.VINYL -> listOf(
            Color.White,
            Color.Black,
            Color(0xFFF0C15A),
            Color(0xFF5E5E5E)
        )

        AdvancedEditorTab.EFFECT -> listOf(
            Color(0xFF3C3C3C),
            Color.White,
            Color(0xFF81C784),
            Color(0xFF8E71F5)
        )

        AdvancedEditorTab.LABEL -> listOf(
            Color(0xFFF7F3EE),
            Color.White,
            Color.Black,
            Color(0xFFF0D7B3)
        )
    }
}

private fun colorsClose(first: Color, second: Color): Boolean {
    return kotlin.math.abs(first.red - second.red) < 0.04f &&
        kotlin.math.abs(first.green - second.green) < 0.04f &&
        kotlin.math.abs(first.blue - second.blue) < 0.04f
}

private fun perceivedLuminance(color: Color): Float {
    return (color.red * 0.299f) + (color.green * 0.587f) + (color.blue * 0.114f)
}

private fun applyLabelTint(
    base: LabelToneColors,
    tint: Color,
    opacity: Float
): LabelToneColors {
    val safeOpacity = opacity.coerceIn(0f, 1f)
    if (safeOpacity <= 0.001f) return base

    val tintedBase = lerpColor(base.base, tint, safeOpacity * 0.92f)
    val tintedRing = lerpColor(base.ring, tint, safeOpacity * 0.72f)
    val textColor = if (perceivedLuminance(tintedBase) > 0.52f) {
        Color(0xFF2B211D)
    } else {
        Color(0xFFF5E6D8)
    }

    return LabelToneColors(
        base = tintedBase,
        ring = tintedRing,
        text = textColor,
        spindleOuter = lerpColor(base.spindleOuter, tint, safeOpacity * 0.36f),
        spindleInner = if (perceivedLuminance(tintedBase) > 0.52f) Color(0xFF1B1B1B) else Color(0xFFF4F0EC)
    )
}

private fun tintPreservingAlpha(
    base: Color,
    tint: Color,
    amount: Float
): Color {
    if (amount <= 0.001f) return base
    val blended = lerpColor(base.copy(alpha = 1f), tint.copy(alpha = 1f), amount.coerceIn(0f, 1f))
    return blended.copy(alpha = base.alpha)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasFlower(
    center: Offset,
    size: Float,
    color: Color
) {
    val petalCount = 5
    val path = Path()
    repeat(petalCount) { idx ->
        val angle = idx * (360f / petalCount)
        rotate(angle, center) {
            path.reset()
            path.moveTo(center.x, center.y)
            path.cubicTo(
                center.x - size * 0.45f, center.y - size * 0.35f,
                center.x - size * 0.35f, center.y - size * 0.85f,
                center.x - size * 0.12f, center.y - size * 0.95f
            )
            path.lineTo(center.x, center.y - size * 0.82f)
            path.lineTo(center.x + size * 0.12f, center.y - size * 0.95f)
            path.cubicTo(
                center.x + size * 0.35f, center.y - size * 0.85f,
                center.x + size * 0.45f, center.y - size * 0.35f,
                center.x, center.y
            )
            path.close()
            drawPath(path = path, color = color)
        }
    }
    drawCircle(
        color = Color(0xFFFFF176),
        radius = size * 0.18f,
        center = center
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasMapleLeaf(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y)
        lineTo(center.x - size * 0.12f, center.y - size * 0.32f)
        lineTo(center.x - size * 0.05f, center.y - size * 0.42f)
        lineTo(center.x - size * 0.22f, center.y - size * 0.52f)
        lineTo(center.x, center.y - size)
        lineTo(center.x + size * 0.22f, center.y - size * 0.52f)
        lineTo(center.x + size * 0.05f, center.y - size * 0.42f)
        lineTo(center.x + size * 0.12f, center.y - size * 0.32f)
        lineTo(center.x - size * 0.32f, center.y - size * 0.22f)
        lineTo(center.x - size * 0.52f, center.y - size * 0.42f)
        lineTo(center.x - size * 0.72f, center.y - size * 0.18f)
        lineTo(center.x - size * 0.42f, center.y - size * 0.08f)
        lineTo(center.x + size * 0.32f, center.y - size * 0.22f)
        lineTo(center.x + size * 0.52f, center.y - size * 0.42f)
        lineTo(center.x + size * 0.72f, center.y - size * 0.18f)
        lineTo(center.x + size * 0.42f, center.y - size * 0.08f)
        close()
    }
    drawPath(path = path, color = color)
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x, center.y + size * 0.28f),
        strokeWidth = size * 0.06f,
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasSnowflake(
    center: Offset,
    size: Float,
    color: Color
) {
    repeat(6) { branch ->
        rotate(branch * 60f, center) {
            drawLine(
                color = color,
                start = center,
                end = Offset(center.x, center.y - size),
                strokeWidth = size * 0.07f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(center.x, center.y - size * 0.42f),
                end = Offset(center.x - size * 0.24f, center.y - size * 0.65f),
                strokeWidth = size * 0.05f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(center.x, center.y - size * 0.42f),
                end = Offset(center.x + size * 0.24f, center.y - size * 0.65f),
                strokeWidth = size * 0.05f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(center.x, center.y - size * 0.72f),
                end = Offset(center.x - size * 0.16f, center.y - size * 0.88f),
                strokeWidth = size * 0.05f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(center.x, center.y - size * 0.72f),
                end = Offset(center.x + size * 0.16f, center.y - size * 0.88f),
                strokeWidth = size * 0.05f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyledRecordSurface(
    finish: RecordFinishOption = RecordFinishOption.AUTO,
    fallbackKind: RecordKind = RecordKind.CLASSIC_BLACK,
    centerPoint: Offset,
    radius: Float,
    vinylTint: Color = Color.Transparent,
    vinylTintAlpha: Float = 0f,
    effectTint: Color = Color.Transparent,
    effectTintAlpha: Float = 0f,
    preset: PlayerThemePreset = PlayerThemePreset.AUTO,
    animValue: Float = 0f,
    animateEffects: Boolean = true
) {
    val vinylBlend = vinylTintAlpha.coerceIn(0f, 1f)
    val effectBlend = effectTintAlpha.coerceIn(0f, 1f)

    if (finish == RecordFinishOption.AUTO) {
        if (preset == PlayerThemePreset.AUTO) {
            // Render fallbackKind base shaders
            when (fallbackKind) {
                RecordKind.CLASSIC_BLACK -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                lerpColor(Color(0xFF242423), vinylTint, vinylBlend * 0.88f),
                                lerpColor(Color(0xFF0B0B0B), vinylTint, vinylBlend * 0.82f),
                                lerpColor(Color(0xFF000000), vinylTint, vinylBlend * 0.76f)
                            ),
                            center = centerPoint,
                            radius = radius
                        ),
                        radius = radius
                    )
                    drawCircle(
                        color = lerpColor(Color.Black, vinylTint, vinylBlend * 0.55f).copy(alpha = 0.78f),
                        radius = radius * 0.96f
                    )
                    repeat(42) { groove ->
                        drawCircle(
                            color = Color.White.copy(alpha = if (groove % 6 == 0) 0.08f else 0.03f),
                            radius = radius * (0.18f + groove * 0.018f),
                            style = Stroke(width = if (groove % 5 == 0) 1.5f else 0.8f)
                        )
                    }
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                tintPreservingAlpha(Color(0xFFD5D0BF).copy(alpha = 0.34f), effectTint, effectBlend * 0.42f),
                                Color.Transparent,
                                tintPreservingAlpha(Color.White.copy(alpha = 0.12f), effectTint, effectBlend * 0.28f),
                                Color.Transparent,
                                tintPreservingAlpha(Color(0xFF404040).copy(alpha = 0.28f), effectTint, effectBlend * 0.32f)
                            ),
                            center = centerPoint
                        ),
                        startAngle = 206f,
                        sweepAngle = 118f,
                        useCenter = false,
                        style = Stroke(width = radius * 0.125f, cap = StrokeCap.Round),
                        topLeft = Offset(centerPoint.x - radius * 0.76f, centerPoint.y - radius * 0.76f),
                        size = Size(radius * 1.52f, radius * 1.52f)
                    )
                }
                RecordKind.SMOKE_MARBLE -> drawMarbledRecordSurface(
                    centerPoint = centerPoint,
                    radius = radius,
                    baseColors = listOf(Color(0xFF191919), Color(0xFF111111), Color(0xFF090909)),
                    grooveHighlight = Color.White.copy(alpha = 0.03f),
                    veinColor = Color.White.copy(alpha = 0.085f),
                    splashColor = Color.White.copy(alpha = 0.022f),
                    rimColor = Color.White.copy(alpha = 0.10f),
                    seed = 1337L,
                    vinylTint = vinylTint,
                    vinylTintAlpha = vinylBlend,
                    effectTint = effectTint,
                    effectTintAlpha = effectBlend
                )
                RecordKind.CHAMPAGNE_MARBLE -> drawMarbledRecordSurface(
                    centerPoint = centerPoint,
                    radius = radius,
                    baseColors = listOf(Color(0xFFF4E7C8), Color(0xFFE2D0AB), Color(0xFFD2BD93)),
                    grooveHighlight = Color.White.copy(alpha = 0.08f),
                    veinColor = Color(0xFF886C43).copy(alpha = 0.11f),
                    splashColor = Color(0xFFAD8A58).copy(alpha = 0.032f),
                    rimColor = Color.White.copy(alpha = 0.28f),
                    seed = 2404L,
                    vinylTint = vinylTint,
                    vinylTintAlpha = vinylBlend,
                    effectTint = effectTint,
                    effectTintAlpha = effectBlend
                )
                RecordKind.ROSE_BLUSH -> drawMarbledRecordSurface(
                    centerPoint = centerPoint,
                    radius = radius,
                    baseColors = listOf(Color(0xFFFFE7EF), Color(0xFFF7C7D7), Color(0xFFE8A7BF)),
                    grooveHighlight = Color.White.copy(alpha = 0.10f),
                    veinColor = Color(0xFFA55A72).copy(alpha = 0.11f),
                    splashColor = Color(0xFFD98BA6).copy(alpha = 0.034f),
                    rimColor = Color.White.copy(alpha = 0.28f),
                    seed = 8612L,
                    vinylTint = vinylTint,
                    vinylTintAlpha = vinylBlend,
                    effectTint = effectTint,
                    effectTintAlpha = effectBlend
                )
                RecordKind.SPLASH_BLUE -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                lerpColor(Color(0xFFEAF7FF), vinylTint, vinylBlend * 0.86f),
                                lerpColor(Color(0xFF8CB9D9), vinylTint, vinylBlend * 0.78f),
                                lerpColor(Color(0xFF6387A7), vinylTint, vinylBlend * 0.66f)
                            ),
                            center = centerPoint,
                            radius = radius
                        ),
                        radius = radius
                    )
                    repeat(18) { groove ->
                        drawCircle(
                            color = Color.White.copy(alpha = if (groove % 3 == 0) 0.12f else 0.06f),
                            radius = radius * (0.28f + groove * 0.034f),
                            style = Stroke(width = 1.0f)
                        )
                    }
                }
            }
            return
        }

        // Bespoke Theme Presets Canvas Shaders
        when (preset) {
            PlayerThemePreset.SAKURA_SPRING -> {
                drawCircle(color = Color(0xFFFFF0F5).copy(alpha = 0.88f), radius = radius)
                drawCircle(color = Color(0xFFFFB7C5).copy(alpha = 0.55f), radius = radius * 0.98f, style = Stroke(width = 4.dp.toPx()))
                if (animateEffects) {
                    val flowRandom = java.util.Random(88L)
                    repeat(5) { flow ->
                        val angle = flow * 72f + animValue * 0.20f + flowRandom.nextFloat() * 5f
                        val dist = radius * (0.34f + flowRandom.nextFloat() * 0.42f)
                        val size = (12f + flowRandom.nextFloat() * 6f).dp.toPx()
                        rotate(angle, centerPoint) {
                            drawCanvasFlower(
                                center = Offset(centerPoint.x, centerPoint.y - dist),
                                size = size,
                                color = Color(0xFFFF8DA1).copy(alpha = 0.88f)
                            )
                        }
                    }
                }
                repeat(24) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.16f), radius = radius * (0.22f + g * 0.03f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.MIDNIGHT_SUMMER -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3F1975), Color(0xFF1B0C30), Color(0xFF0F071A)),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius
                )
                if (animateEffects) {
                    repeat(16) { idx ->
                        val angle = idx * 22.5f + animValue * 0.25f
                        rotate(angle, centerPoint) {
                            val pulseScale = 1.0f + 0.08f * Math.sin(Math.toRadians((idx * 20f + animValue * 2.0f).toDouble())).toFloat()
                            val path = Path().apply {
                                moveTo(centerPoint.x, centerPoint.y - radius * 0.28f)
                                quadraticTo(
                                    centerPoint.x - radius * 0.08f * pulseScale, centerPoint.y - radius * 0.6f,
                                    centerPoint.x, centerPoint.y - radius * 0.94f * pulseScale
                                )
                                quadraticTo(
                                    centerPoint.x + radius * 0.08f * pulseScale, centerPoint.y - radius * 0.6f,
                                    centerPoint.x, centerPoint.y - radius * 0.28f
                                )
                                close()
                            }
                            drawPath(path = path, color = Color(0xFFFF8C00).copy(alpha = 0.22f))
                        }
                    }
                }
                repeat(26) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * (0.24f + g * 0.026f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.MAPLE_WHISPER -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3D1F16), Color(0xFF28110A), Color(0xFF180805)),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius
                )
                if (animateEffects) {
                    val mapRandom = java.util.Random(444L)
                    repeat(5) { idx ->
                        val angle = idx * 72f + animValue * 0.20f + mapRandom.nextFloat() * 4f
                        val dist = radius * (0.35f + mapRandom.nextFloat() * 0.4f)
                        val size = (14f + mapRandom.nextFloat() * 6f).dp.toPx()
                        rotate(angle, centerPoint) {
                            drawCanvasMapleLeaf(
                                center = Offset(centerPoint.x, centerPoint.y - dist),
                                size = size,
                                color = if (idx % 2 == 0) Color(0xFFCD5C5C).copy(alpha = 0.86f) else Color(0xFFCD853F).copy(alpha = 0.86f)
                            )
                        }
                    }
                }
                repeat(30) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.AURORA_FROST -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0D2845), Color(0xFF051221), Color(0xFF02070D)),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius
                )
                if (animateEffects) {
                    val widthPhase = Math.sin(Math.toRadians(animValue.toDouble())).toFloat() * 0.12f
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color(0xFF00FFCC).copy(alpha = 0.16f), Color.Transparent, Color(0xFF00E5FF).copy(alpha = 0.12f), Color.Transparent)),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = radius * (0.28f + widthPhase))
                    )
                    val snowRandom = java.util.Random(999L)
                    repeat(6) { idx ->
                        val angle = idx * 60f + animValue * 0.5f
                        val dist = radius * (0.36f + snowRandom.nextFloat() * 0.42f)
                        val size = (10f + snowRandom.nextFloat() * 5f).dp.toPx()
                        rotate(angle, centerPoint) {
                            drawCanvasSnowflake(
                                center = Offset(centerPoint.x, centerPoint.y - dist),
                                size = size,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }
                    }
                }
                repeat(24) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.07f), radius = radius * (0.22f + g * 0.03f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.VINTAGE_TEDDY -> {
                drawCircle(color = Color(0xFF4E3D30), radius = radius)
                if (animateEffects) {
                    repeat(8) { idx ->
                        val angle = idx * 45f + animValue * 0.25f
                        rotate(angle, centerPoint) {
                            val dist = radius * 0.62f
                            drawCircle(color = Color(0xFF8E7A6B).copy(alpha = 0.45f), radius = 6.dp.toPx(), center = Offset(centerPoint.x, centerPoint.y - dist))
                            drawCircle(color = Color(0xFF8E7A6B).copy(alpha = 0.45f), radius = 2.5.dp.toPx(), center = Offset(centerPoint.x - 6.dp.toPx(), centerPoint.y - dist - 6.dp.toPx()))
                            drawCircle(color = Color(0xFF8E7A6B).copy(alpha = 0.45f), radius = 2.5.dp.toPx(), center = Offset(centerPoint.x, centerPoint.y - dist - 9.dp.toPx()))
                            drawCircle(color = Color(0xFF8E7A6B).copy(alpha = 0.45f), radius = 2.5.dp.toPx(), center = Offset(centerPoint.x + 6.dp.toPx(), centerPoint.y - dist - 6.dp.toPx()))
                        }
                    }
                }
                repeat(32) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.PASTEL_PAJAMAS -> {
                drawCircle(color = Color(0xFFF3E8FF), radius = radius)
                if (animateEffects) {
                    val cloudRandom = java.util.Random(12L)
                    repeat(6) { idx ->
                        val angle = idx * 60f + animValue * 0.5f
                        val dist = radius * (0.35f + cloudRandom.nextFloat() * 0.45f)
                        rotate(angle, centerPoint) {
                            val cCenter = Offset(centerPoint.x, centerPoint.y - dist)
                            drawCircle(color = Color.White.copy(alpha = 0.65f), radius = 12.dp.toPx(), center = cCenter)
                            drawCircle(color = Color.White.copy(alpha = 0.65f), radius = 8.dp.toPx(), center = Offset(cCenter.x - 10.dp.toPx(), cCenter.y))
                            drawCircle(color = Color.White.copy(alpha = 0.65f), radius = 8.dp.toPx(), center = Offset(cCenter.x + 10.dp.toPx(), cCenter.y))
                        }
                    }
                }
                repeat(24) { g ->
                    drawCircle(color = Color(0xFF6B4E71).copy(alpha = 0.06f), radius = radius * (0.24f + g * 0.03f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.RAINY_WINDOW -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2C3E50), Color(0xFF1F2C39), Color(0xFF121B24)),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius
                )
                if (animateEffects) {
                    val rainRandom = java.util.Random(808L)
                    val progress = animValue / 360f
                    val slideProgress = (progress * 4f) % 1f
                    val slide = slideProgress * 40.dp.toPx()
                    repeat(24) { idx ->
                        val angle = idx * 15f + rainRandom.nextFloat() * 5f
                        val dist = radius * (0.22f + rainRandom.nextFloat() * 0.7f)
                        rotate(angle, centerPoint) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.18f),
                                start = Offset(centerPoint.x, centerPoint.y - dist - slide),
                                end = Offset(centerPoint.x, centerPoint.y - dist - slide - 12.dp.toPx()),
                                strokeWidth = 1.0f
                            )
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.STARDUST_LULLABY -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1A1B35), Color(0xFF0C0D1C), Color(0xFF05050D)),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius
                )
                if (animateEffects) {
                    val starRandom = java.util.Random(111L)
                    repeat(20) { star ->
                        val angle = star * 18f
                        val dist = radius * (0.3f + starRandom.nextFloat() * 0.6f)
                        val pulse = 1f + 0.35f * Math.sin(Math.toRadians((star * 40f + animValue * 2f).toDouble())).toFloat()
                        rotate(angle, centerPoint) {
                            val c = Offset(centerPoint.x, centerPoint.y - dist)
                            drawLine(Color.White.copy(alpha = 0.62f), Offset(c.x, c.y - 3.dp.toPx() * pulse), Offset(c.x, c.y + 3.dp.toPx() * pulse), strokeWidth = 0.8f)
                            drawLine(Color.White.copy(alpha = 0.62f), Offset(c.x - 3.dp.toPx() * pulse, c.y), Offset(c.x + 3.dp.toPx() * pulse, c.y), strokeWidth = 0.8f)
                        }
                    }
                }
                repeat(30) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.LUNAR_ECLIPSE -> {
                drawCircle(color = Color(0xFF0F0F0F), radius = radius)
                val glowWidth = if (animateEffects) radius * (0.04f + 0.015f * Math.sin(Math.toRadians(animValue.toDouble())).toFloat()) else radius * 0.04f
                drawCircle(
                    color = Color(0xFFFF2A2A).copy(alpha = 0.62f),
                    radius = radius * 0.85f,
                    style = Stroke(width = glowWidth)
                )
                if (animateEffects) {
                    rotate(animValue * 1f, centerPoint) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.16f),
                            startAngle = -45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                repeat(34) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius * (0.2f + g * 0.022f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.PIXIE_DUST -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0B2B1E), Color(0xFF04140E), Color(0xFF010503)),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius
                )
                if (animateEffects) {
                    val pixRandom = java.util.Random(72L)
                    repeat(32) { idx ->
                        val angle = idx * 11.25f
                        val dist = radius * (0.25f + pixRandom.nextFloat() * 0.65f)
                        val pulse = 1f + 0.3f * Math.sin(Math.toRadians((idx * 30f + animValue * 2.0f).toDouble())).toFloat()
                        rotate(angle, centerPoint) {
                            drawCircle(
                                color = Color(0xFF39FF14).copy(alpha = 0.42f),
                                radius = (2f + pixRandom.nextFloat() * 3f).dp.toPx() * pulse,
                                center = Offset(centerPoint.x, centerPoint.y - dist)
                            )
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.MATCHA_LATTE -> {
                drawCircle(color = Color(0xFFE8EFE9), radius = radius)
                if (animateEffects) {
                    repeat(3) { idx ->
                        rotate(idx * 120f + animValue * (1f / 3f), centerPoint) {
                            drawArc(
                                color = Color(0xFF8A9A86).copy(alpha = 0.28f),
                                startAngle = 0f,
                                sweepAngle = 90f,
                                useCenter = false,
                                style = Stroke(width = radius * 0.16f, cap = StrokeCap.Round),
                                topLeft = Offset(centerPoint.x - radius * 0.6f, centerPoint.y - radius * 0.6f),
                                size = Size(radius * 1.2f, radius * 1.2f)
                            )
                        }
                    }
                }
                repeat(24) { g ->
                    drawCircle(color = Color.Black.copy(alpha = 0.05f), radius = radius * (0.24f + g * 0.03f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.EARL_GREY -> {
                drawCircle(color = Color(0xFFEAEFF2), radius = radius)
                if (animateEffects) {
                    val leafRandom = java.util.Random(91L)
                    repeat(8) { leaf ->
                        val angle = leaf * 45f + animValue * 0.25f
                        val dist = radius * (0.42f + leafRandom.nextFloat() * 0.45f)
                        rotate(angle, centerPoint) {
                            val leafCenter = Offset(centerPoint.x, centerPoint.y - dist)
                            val path = Path().apply {
                                moveTo(leafCenter.x, leafCenter.y)
                                quadraticTo(leafCenter.x - 6.dp.toPx(), leafCenter.y - 3.dp.toPx(), leafCenter.x, leafCenter.y - 12.dp.toPx())
                                quadraticTo(leafCenter.x + 6.dp.toPx(), leafCenter.y - 3.dp.toPx(), leafCenter.x, leafCenter.y)
                            }
                            drawPath(path = path, color = Color(0xFF9AAEC4).copy(alpha = 0.22f))
                        }
                    }
                }
                repeat(30) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.CAMOMILE_MEADOW -> {
                drawCircle(color = Color(0xFFFFFDF0), radius = radius)
                if (animateEffects) {
                    val daisyRandom = java.util.Random(404L)
                    repeat(6) { flow ->
                        val angle = flow * 60f + animValue * 0.5f
                        val dist = radius * (0.4f + daisyRandom.nextFloat() * 0.4f)
                        rotate(angle, centerPoint) {
                            drawCanvasFlower(
                                center = Offset(centerPoint.x, centerPoint.y - dist),
                                size = 8.dp.toPx(),
                                color = Color.White
                            )
                        }
                    }
                }
                repeat(22) { g ->
                    drawCircle(color = Color(0xFF5D5A43).copy(alpha = 0.05f), radius = radius * (0.26f + g * 0.032f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.NEON_ODYSSEY -> {
                drawCircle(color = Color(0xFF120024), radius = radius)
                if (animateEffects) {
                    val scanPos = radius * (0.2f + 0.75f * Math.abs(Math.sin(Math.toRadians(animValue.toDouble() * 2.0))).toFloat())
                    drawCircle(
                        color = Color(0xFFFF007F).copy(alpha = 0.65f),
                        radius = scanPos,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                        radius = scanPos * 0.92f,
                        style = Stroke(width = 1.0.dp.toPx())
                    )
                }
                repeat(36) { g ->
                    drawCircle(color = Color(0xFF00E5FF).copy(alpha = 0.08f), radius = radius * (0.18f + g * 0.02f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.DUNGEON_8BIT -> {
                drawCircle(color = Color(0xFF050F09), radius = radius)
                if (animateEffects) {
                    val screenRandom = java.util.Random(888L)
                    repeat(24) { block ->
                        val angle = block * 15f
                        val dist = radius * (0.28f + screenRandom.nextFloat() * 0.64f)
                        val flicker = 1f + 0.4f * Math.sin(Math.toRadians((block * 45f + animValue * 4.0f).toDouble())).toFloat()
                        rotate(angle, centerPoint) {
                            drawRect(
                                color = Color(0xFF39FF14).copy(alpha = 0.34f * flicker),
                                topLeft = Offset(centerPoint.x - 2.dp.toPx(), centerPoint.y - dist),
                                size = Size(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }
                repeat(40) { g ->
                    drawCircle(color = Color(0xFF39FF14).copy(alpha = 0.06f), radius = radius * (0.2f + g * 0.018f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.SYNTHWAVE_HIGHWAY -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3E1F54), Color(0xFF1E0B28), Color(0xFF08020D)),
                        center = centerPoint,
                        radius = radius
                    ),
                    radius = radius
                )
                if (animateEffects) {
                    repeat(12) { line ->
                        val angle = line * 30f + animValue * 0.25f
                        rotate(angle, centerPoint) {
                            drawLine(
                                color = Color(0xFFFF8C00).copy(alpha = 0.16f),
                                start = Offset(centerPoint.x, centerPoint.y - radius * 0.26f),
                                end = Offset(centerPoint.x, centerPoint.y - radius * 0.98f),
                                strokeWidth = 1.0f
                            )
                        }
                    }
                }
                repeat(32) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.DEEP_CORAL -> {
                drawCircle(color = Color(0xFF00838F), radius = radius)
                if (animateEffects) {
                    repeat(6) { branch ->
                        rotate(branch * 60f + animValue * 0.5f, centerPoint) {
                            val dist = radius * 0.58f
                            drawLine(
                                color = Color(0xFFFF7043).copy(alpha = 0.36f),
                                start = Offset(centerPoint.x, centerPoint.y - dist + 10.dp.toPx()),
                                end = Offset(centerPoint.x, centerPoint.y - dist - 12.dp.toPx()),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color(0xFFFF7043).copy(alpha = 0.36f),
                                start = Offset(centerPoint.x, centerPoint.y - dist - 2.dp.toPx()),
                                end = Offset(centerPoint.x - 6.dp.toPx(), centerPoint.y - dist - 8.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color(0xFFFF7043).copy(alpha = 0.36f),
                                start = Offset(centerPoint.x, centerPoint.y - dist - 6.dp.toPx()),
                                end = Offset(centerPoint.x + 6.dp.toPx(), centerPoint.y - dist - 12.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.ABYSSAL_GLOW -> {
                drawCircle(color = Color(0xFF050C1B), radius = radius)
                if (animateEffects) {
                    val waterPulse = radius * (0.05f + 0.012f * Math.sin(Math.toRadians(animValue.toDouble() * 2.0)).toFloat())
                    drawCircle(
                        color = Color(0xFF39FF14).copy(alpha = 0.28f),
                        radius = radius * 0.74f,
                        style = Stroke(width = waterPulse)
                    )
                }
                repeat(34) { g ->
                    drawCircle(color = Color(0xFF00E5FF).copy(alpha = 0.08f), radius = radius * (0.2f + g * 0.022f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.PEARL_SHELL -> {
                drawCircle(color = Color(0xFFECEFF1), radius = radius)
                if (animateEffects) {
                    repeat(4) { spiral ->
                        rotate(spiral * 90f + animValue * 0.25f, centerPoint) {
                            drawArc(
                                color = Color(0xFFFF8DA1).copy(alpha = 0.24f),
                                startAngle = 0f,
                                sweepAngle = 90f,
                                useCenter = false,
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = Offset(centerPoint.x - radius * 0.72f, centerPoint.y - radius * 0.72f),
                                size = Size(radius * 1.44f, radius * 1.44f)
                            )
                        }
                    }
                }
                repeat(30) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.24f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.OLD_PARCHMENT -> {
                drawCircle(color = Color(0xFFF5EBE6), radius = radius)
                if (animateEffects) {
                    val scrollRandom = java.util.Random(555L)
                    repeat(8) { idx ->
                        val angle = idx * 45f + animValue * 0.25f
                        val dist = radius * (0.35f + scrollRandom.nextFloat() * 0.45f)
                        rotate(angle, centerPoint) {
                            val pCenter = Offset(centerPoint.x, centerPoint.y - dist)
                            val path = Path().apply {
                                moveTo(pCenter.x - 8.dp.toPx(), pCenter.y)
                                quadraticTo(pCenter.x, pCenter.y - 12.dp.toPx(), pCenter.x + 8.dp.toPx(), pCenter.y)
                                quadraticTo(pCenter.x, pCenter.y + 12.dp.toPx(), pCenter.x - 8.dp.toPx(), pCenter.y)
                            }
                            drawPath(path = path, color = Color(0xFF8B5A2B).copy(alpha = 0.22f))
                        }
                    }
                }
                repeat(38) { g ->
                    drawCircle(
                        color = Color(0xFFD5BFAF).copy(alpha = if (g % 8 == 0) 0.16f else 0.05f),
                        radius = radius * (0.18f + g * 0.019f),
                        style = Stroke(width = if (g % 8 == 0) 1.5f else 0.6f)
                    )
                }
            }
            PlayerThemePreset.POETS_INK -> {
                drawCircle(color = Color(0xFF0A0A0F), radius = radius)
                if (animateEffects) {
                    val inkRandom = java.util.Random(909L)
                    repeat(8) { idx ->
                        val angle = idx * 45f + animValue * 0.25f
                        val dist = radius * (0.35f + inkRandom.nextFloat() * 0.45f)
                        rotate(angle, centerPoint) {
                            drawCircle(
                                color = Color(0xFFB71C1C).copy(alpha = 0.36f),
                                radius = (3f + inkRandom.nextFloat() * 5f).dp.toPx(),
                                center = Offset(centerPoint.x, centerPoint.y - dist)
                            )
                        }
                    }
                }
                repeat(34) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.2f + g * 0.022f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.FOREST_HERBAL -> {
                drawCircle(color = Color(0xFF1E3F20), radius = radius)
                if (animateEffects) {
                    val herbRandom = java.util.Random(82L)
                    repeat(6) { idx ->
                        val angle = idx * 60f + animValue * 0.5f
                        val dist = radius * (0.42f + herbRandom.nextFloat() * 0.4f)
                        rotate(angle, centerPoint) {
                            val fCenter = Offset(centerPoint.x, centerPoint.y - dist)
                            drawLine(Color(0xFF81C784).copy(alpha = 0.22f), Offset(fCenter.x, fCenter.y - 12.dp.toPx()), Offset(fCenter.x, fCenter.y + 12.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                            drawLine(Color(0xFF81C784).copy(alpha = 0.22f), fCenter, Offset(fCenter.x - 6.dp.toPx(), fCenter.y - 4.dp.toPx()), strokeWidth = 1.0.dp.toPx())
                            drawLine(Color(0xFF81C784).copy(alpha = 0.22f), fCenter, Offset(fCenter.x + 6.dp.toPx(), fCenter.y - 4.dp.toPx()), strokeWidth = 1.0.dp.toPx())
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.COTTON_CANDY -> {
                drawCircle(color = Color(0xFFFFB7B7), radius = radius)
                if (animateEffects) {
                    repeat(4) { swirl ->
                        rotate(swirl * 90f + animValue * 0.25f, centerPoint) {
                            drawArc(
                                color = Color(0xFF80DEEA).copy(alpha = 0.32f),
                                startAngle = 0f,
                                sweepAngle = 72f,
                                useCenter = false,
                                style = Stroke(width = radius * 0.18f, cap = StrokeCap.Round),
                                topLeft = Offset(centerPoint.x - radius * 0.65f, centerPoint.y - radius * 0.65f),
                                size = Size(radius * 1.3f, radius * 1.3f)
                            )
                        }
                    }
                }
                repeat(24) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.16f), radius = radius * (0.24f + g * 0.03f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.SOUR_LEMONADE -> {
                drawCircle(color = Color(0xFFFFD54F), radius = radius)
                if (animateEffects) {
                    repeat(8) { idx ->
                        rotate(idx * 45f + animValue * 0.25f, centerPoint) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.34f),
                                start = Offset(centerPoint.x, centerPoint.y - radius * 0.28f),
                                end = Offset(centerPoint.x, centerPoint.y - radius * 0.96f),
                                strokeWidth = 1.2.dp.toPx()
                            )
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.18f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.CHOCO_MINT -> {
                drawCircle(color = Color(0xFF80CBC4), radius = radius)
                if (animateEffects) {
                    val chipRandom = java.util.Random(99L)
                    repeat(24) { chip ->
                        val angle = chip * 15f + animValue * 0.25f
                        val dist = radius * (0.34f + chipRandom.nextFloat() * 0.56f)
                        rotate(angle, centerPoint) {
                            drawCircle(
                                color = Color(0xFF3E2723).copy(alpha = 0.45f),
                                radius = (3f + chipRandom.nextFloat() * 3.5f).dp.toPx(),
                                center = Offset(centerPoint.x, centerPoint.y - dist)
                            )
                        }
                    }
                }
                repeat(26) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.12f), radius = radius * (0.24f + g * 0.028f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.SAHARA_SUNSET -> {
                drawCircle(color = Color(0xFFFFB300), radius = radius)
                if (animateEffects) {
                    val sandRandom = java.util.Random(77L)
                    repeat(5) { flow ->
                        val angle = flow * 72f + animValue * 0.2f
                        val dist = radius * (0.4f + sandRandom.nextFloat() * 0.42f)
                        rotate(angle, centerPoint) {
                            drawCanvasFlower(
                                center = Offset(centerPoint.x, centerPoint.y - dist),
                                size = 9.dp.toPx(),
                                color = Color(0xFFD84315)
                            )
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.Black.copy(alpha = 0.05f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.OASIS_MIRAGE -> {
                drawCircle(color = Color(0xFF00ACC1), radius = radius)
                if (animateEffects) {
                    repeat(4) { palm ->
                        rotate(palm * 90f + animValue * 0.25f, centerPoint) {
                            val fCenter = Offset(centerPoint.x, centerPoint.y - radius * 0.62f)
                            drawLine(Color(0xFFFFF9D9).copy(alpha = 0.28f), Offset(fCenter.x, fCenter.y - 10.dp.toPx()), Offset(fCenter.x, fCenter.y + 10.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                            drawArc(
                                color = Color(0xFFFFF9D9).copy(alpha = 0.28f),
                                startAngle = -45f,
                                sweepAngle = 90f,
                                useCenter = false,
                                style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = Offset(fCenter.x - 8.dp.toPx(), fCenter.y - 12.dp.toPx()),
                                size = Size(16.dp.toPx(), 16.dp.toPx())
                            )
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.RED_CANYON -> {
                drawCircle(color = Color(0xFFD84315), radius = radius)
                repeat(6) { canyon ->
                    val strataPulse = if (animateEffects) radius * (0.42f + canyon * 0.085f + 0.012f * Math.sin(Math.toRadians((canyon * 30f + animValue * 2.0f).toDouble())).toFloat()) else radius * (0.42f + canyon * 0.085f)
                    drawCircle(
                        color = Color(0xFF3E2723).copy(alpha = 0.16f),
                        radius = strataPulse,
                        style = Stroke(width = 6.dp.toPx())
                    )
                }
                repeat(32) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.7f))
                }
            }
            PlayerThemePreset.THE_STAR -> {
                drawCircle(color = Color(0xFF0D1B2A), radius = radius)
                if (animateEffects) {
                    val pulse = 1f + 0.2f * Math.sin(Math.toRadians(animValue.toDouble() * 2f)).toFloat()
                    val size = 28.dp.toPx() * pulse
                    val starCenter = centerPoint
                    val path = Path().apply {
                        moveTo(starCenter.x, starCenter.y - size)
                        quadraticTo(starCenter.x, starCenter.y, starCenter.x + size, starCenter.y)
                        quadraticTo(starCenter.x, starCenter.y, starCenter.x, starCenter.y + size)
                        quadraticTo(starCenter.x, starCenter.y, starCenter.x - size, starCenter.y)
                        quadraticTo(starCenter.x, starCenter.y, starCenter.x, starCenter.y - size)
                        close()
                    }
                    drawPath(path = path, color = Color.White.copy(alpha = 0.28f))
                    rotate(45f, starCenter) {
                        val path2 = Path().apply {
                            moveTo(starCenter.x, starCenter.y - size * 0.65f)
                            quadraticTo(starCenter.x, starCenter.y, starCenter.x + size * 0.65f, starCenter.y)
                            quadraticTo(starCenter.x, starCenter.y, starCenter.x, starCenter.y + size * 0.65f)
                            quadraticTo(starCenter.x, starCenter.y, starCenter.x - size * 0.65f, starCenter.y)
                            quadraticTo(starCenter.x, starCenter.y, starCenter.x, starCenter.y - size * 0.65f)
                            close()
                        }
                        drawPath(path = path2, color = Color.White.copy(alpha = 0.22f))
                    }
                }
                repeat(30) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius * (0.22f + g * 0.024f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.THE_FOOL -> {
                drawCircle(color = Color(0xFFFFD54F), radius = radius)
                if (animateEffects) {
                    val foolRandom = java.util.Random(999L)
                    repeat(12) { flow ->
                        val angle = flow * 30f + animValue * 0.25f
                        val dist = radius * (0.35f + foolRandom.nextFloat() * 0.45f)
                        rotate(angle, centerPoint) {
                            drawCircle(
                                color = when (foolRandom.nextInt(3)) {
                                    0 -> Color(0xFFFF8A65).copy(alpha = 0.48f)
                                    1 -> Color(0xFFF06292).copy(alpha = 0.48f)
                                    else -> Color(0xFF4DD0E1).copy(alpha = 0.48f)
                                },
                                radius = (4f + foolRandom.nextFloat() * 4.5f).dp.toPx(),
                                center = Offset(centerPoint.x, centerPoint.y - dist)
                            )
                        }
                    }
                }
                repeat(28) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.16f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.THE_MAGICIAN -> {
                drawCircle(color = Color(0xFF311B92), radius = radius)
                if (animateEffects) {
                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = 0.24f),
                        radius = radius * 0.74f,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    repeat(3) { tri ->
                        rotate(tri * 120f + animValue * (1f / 3f), centerPoint) {
                            drawLine(
                                color = Color(0xFFFFD700).copy(alpha = 0.16f),
                                start = Offset(centerPoint.x, centerPoint.y - radius * 0.74f),
                                end = Offset(centerPoint.x - radius * 0.64f, centerPoint.y + radius * 0.37f),
                                strokeWidth = 1.0f
                            )
                            drawLine(
                                color = Color(0xFFFFD700).copy(alpha = 0.16f),
                                start = Offset(centerPoint.x - radius * 0.64f, centerPoint.y + radius * 0.37f),
                                end = Offset(centerPoint.x + radius * 0.64f, centerPoint.y + radius * 0.37f),
                                strokeWidth = 1.0f
                            )
                        }
                    }
                }
                repeat(30) { g ->
                    drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius * (0.22f + g * 0.024f), style = Stroke(width = 0.8f))
                }
            }
            PlayerThemePreset.AUTO -> {}
        }
        return
    }

    // Render one of the 30 programmatic designs
    when (finish) {
        RecordFinishOption.AUTO -> {} // Already handled above

        // Group 1: 🔮 Standard Cuts
        RecordFinishOption.CLASSIC_BLACK -> {
            drawCircle(
                color = lerpColor(Color(0xFF111111), vinylTint, vinylBlend),
                radius = radius
            )
            repeat(45) { groove ->
                drawCircle(
                    color = Color.White.copy(alpha = if (groove % 5 == 0) 0.09f else 0.04f),
                    radius = radius * (0.18f + groove * 0.017f),
                    style = Stroke(width = if (groove % 5 == 0) 1.4f else 0.7f)
                )
            }
        }
        RecordFinishOption.CRYSTAL_TRANSLUCENT -> {
            drawCircle(
                color = lerpColor(Color.White.copy(alpha = 0.12f), vinylTint, vinylBlend),
                radius = radius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = radius * 0.98f,
                style = Stroke(width = 4f)
            )
            repeat(20) { groove ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius * (0.25f + groove * 0.035f),
                    style = Stroke(width = 0.8f)
                )
            }
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent)),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = radius * 0.4f)
            )
        }
        RecordFinishOption.SMOKY_CHARCOAL -> {
            drawMarbledRecordSurface(
                centerPoint = centerPoint,
                radius = radius,
                baseColors = listOf(Color(0xFF2C2C2C), Color(0xFF1E1E1E), Color(0xFF121212)),
                grooveHighlight = Color.White.copy(alpha = 0.04f),
                veinColor = Color.White.copy(alpha = 0.10f),
                splashColor = Color.White.copy(alpha = 0.03f),
                rimColor = Color.White.copy(alpha = 0.12f),
                seed = 505L,
                vinylTint = vinylTint,
                vinylTintAlpha = vinylBlend,
                effectTint = effectTint,
                effectTintAlpha = effectBlend
            )
        }

        // Group 2: 🌌 Nebula Pressings
        RecordFinishOption.GALAXY_DUST -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF1B003E), Color(0xFF0C021F)), center = centerPoint, radius = radius),
                radius = radius
            )
            repeat(16) {
                rotate(it * 22.5f, centerPoint) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color(0xFFFF007F).copy(alpha = 0.24f), Color(0xFF00FFFF).copy(alpha = 0.18f), Color.Transparent)),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = Offset(centerPoint.x - radius * 0.9f, centerPoint.y - radius * 0.9f),
                        size = Size(radius * 1.8f, radius * 1.8f)
                    )
                }
            }
            repeat(25) { groove ->
                drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius * (0.2f + groove * 0.03f), style = Stroke(width = 0.8f))
            }
        }
        RecordFinishOption.SUPERNOVA -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFCC00), Color(0xFFFF3300), Color(0xFF330000)), center = centerPoint, radius = radius),
                radius = radius
            )
            repeat(24) { idx ->
                val angle = idx * 15f
                rotate(angle, centerPoint) {
                    drawRoundRect(
                        color = Color(0xFFFFD700).copy(alpha = 0.35f),
                        topLeft = Offset(centerPoint.x - 3.dp.toPx(), centerPoint.y - radius * 0.95f),
                        size = Size(6.dp.toPx(), radius * 0.5f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
            repeat(20) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * (0.22f + g * 0.035f), style = Stroke(width = 0.9f))
            }
        }
        RecordFinishOption.COSMIC_AURORA -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF3A0D66), Color(0xFF1D003E), Color(0xFF000000)), center = centerPoint, radius = radius),
                radius = radius
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF00FFCC).copy(alpha = 0.28f), Color(0xFF00E5FF).copy(alpha = 0.18f), Color.Transparent)),
                startAngle = 45f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = radius * 0.35f, cap = StrokeCap.Round),
                topLeft = Offset(centerPoint.x - radius * 0.8f, centerPoint.y - radius * 0.8f),
                size = Size(radius * 1.6f, radius * 1.6f)
            )
            repeat(30) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.8f))
            }
        }

        // Group 3: 🍀 Terrarium Blends
        RecordFinishOption.MOSS_JADE -> {
            drawMarbledRecordSurface(
                centerPoint = centerPoint,
                radius = radius,
                baseColors = listOf(Color(0xFF004D40), Color(0xFF00695C), Color(0xFF003F35)),
                grooveHighlight = Color(0xFF81C784).copy(alpha = 0.15f),
                veinColor = Color(0xFF4DB6AC).copy(alpha = 0.16f),
                splashColor = Color(0xFFE8F5E9).copy(alpha = 0.05f),
                rimColor = Color(0xFF81C784).copy(alpha = 0.35f),
                seed = 72L,
                vinylTint = vinylTint,
                vinylTintAlpha = vinylBlend,
                effectTint = effectTint,
                effectTintAlpha = effectBlend
            )
        }
        RecordFinishOption.AMBER_SAP -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFA000), Color(0xFFE65100), Color(0xFF3E2723)), center = centerPoint, radius = radius),
                radius = radius
            )
            repeat(45) { ring ->
                drawCircle(
                    color = Color(0xFFFFD54F).copy(alpha = if (ring % 6 == 0) 0.18f else 0.07f),
                    radius = radius * (0.18f + ring * 0.017f),
                    style = Stroke(width = if (ring % 6 == 0) 1.8f else 0.6f)
                )
            }
        }
        RecordFinishOption.AUTUMN_FOREST -> {
            drawMarbledRecordSurface(
                centerPoint = centerPoint,
                radius = radius,
                baseColors = listOf(Color(0xFF1E3F20), Color(0xFF142B15), Color(0xFF09140A)),
                grooveHighlight = Color.White.copy(alpha = 0.03f),
                veinColor = Color(0xFFE65100).copy(alpha = 0.15f),
                splashColor = Color(0xFFFFB300).copy(alpha = 0.08f),
                rimColor = Color(0xFF81C784).copy(alpha = 0.18f),
                seed = 918L,
                vinylTint = vinylTint,
                vinylTintAlpha = vinylBlend,
                effectTint = effectTint,
                effectTintAlpha = effectBlend
            )
        }

        // Group 4: 🧁 Sweet Confection
        RecordFinishOption.CANDY_SWIRL -> {
            drawCircle(color = Color.White, radius = radius)
            repeat(8) { swirl ->
                rotate(swirl * 45f, centerPoint) {
                    drawArc(
                        color = Color(0xFFD32F2F),
                        startAngle = 0f,
                        sweepAngle = 22.5f,
                        useCenter = true,
                        topLeft = Offset(centerPoint.x - radius, centerPoint.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                }
            }
            repeat(30) { groove ->
                drawCircle(color = Color.Black.copy(alpha = 0.08f), radius = radius * (0.22f + groove * 0.024f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.CHOCO_CARAMEL -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF3E2723), Color(0xFF271714)), center = centerPoint, radius = radius),
                radius = radius
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFFFFB300).copy(alpha = 0.4f), Color.Transparent, Color(0xFFFFE082).copy(alpha = 0.3f), Color.Transparent)),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = radius * 0.28f)
            )
            repeat(32) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.8f))
            }
        }
        RecordFinishOption.BERRY_SPRINKLES -> {
            drawCircle(color = Color(0xFFF06292), radius = radius)
            val sprRandom = java.util.Random(404L)
            repeat(32) { idx ->
                val angle = idx * (360f / 32f) + sprRandom.nextFloat() * 8f
                val dist = radius * (0.35f + sprRandom.nextFloat() * 0.5f)
                val sprColor = when (sprRandom.nextInt(3)) {
                    0 -> Color(0xFF00E5FF)
                    1 -> Color(0xFFFFD54F)
                    else -> Color(0xFFFFFFFF)
                }
                rotate(angle, centerPoint) {
                    drawRoundRect(
                        color = sprColor,
                        topLeft = Offset(centerPoint.x - 2.dp.toPx(), centerPoint.y - dist),
                        size = Size(4.dp.toPx(), 9.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
            repeat(18) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.12f), radius = radius * (0.28f + g * 0.038f), style = Stroke(width = 0.8f))
            }
        }

        // Group 5: ⚡ Cyberpunk Tracks
        RecordFinishOption.HOLOGRAM_DISC -> {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF2979FF), Color(0xFFFF1744), Color(0xFFFFEA00),
                        Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFFD500F9), Color(0xFF2979FF)
                    ),
                    center = centerPoint
                ),
                radius = radius
            )
            drawCircle(color = Color.Black.copy(alpha = 0.18f), radius = radius * 0.98f, style = Stroke(width = 2.dp.toPx()))
            repeat(40) { groove ->
                drawCircle(color = Color.White.copy(alpha = 0.14f), radius = radius * (0.2f + groove * 0.019f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.GRID_LASER -> {
            drawCircle(color = Color.Black, radius = radius)
            repeat(14) { ring ->
                drawCircle(
                    color = Color(0xFF00FF00).copy(alpha = if (ring % 3 == 0) 0.68f else 0.28f),
                    radius = radius * (0.25f + ring * 0.054f),
                    style = Stroke(width = if (ring % 3 == 0) 1.8f else 0.8f)
                )
            }
            repeat(12) { line ->
                rotate(line * 30f, centerPoint) {
                    drawLine(
                        color = Color(0xFF00FF00).copy(alpha = 0.12f),
                        start = Offset(centerPoint.x, centerPoint.y - radius * 0.25f),
                        end = Offset(centerPoint.x, centerPoint.y - radius * 0.98f),
                        strokeWidth = 1.0f
                    )
                }
            }
        }
        RecordFinishOption.DATA_STREAM -> {
            drawCircle(color = Color(0xFF07070F), radius = radius)
            repeat(20) { g ->
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = if (g % 4 == 0) 0.58f else 0.16f),
                    radius = radius * (0.2f + g * 0.038f),
                    style = Stroke(width = if (g % 4 == 0) 1.6f else 0.6f)
                )
            }
            val datRandom = java.util.Random(999L)
            repeat(40) { idx ->
                val angle = idx * 9f
                val dist = radius * (0.3f + datRandom.nextFloat() * 0.6f)
                rotate(angle, centerPoint) {
                    drawRect(
                        color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                        topLeft = Offset(centerPoint.x - 1.dp.toPx(), centerPoint.y - dist),
                        size = Size(2.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        // Group 6: 🐚 Ocean Secrets
        RecordFinishOption.ABYSSAL_CURRENT -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF010A1C), Color(0xFF00050F)), center = centerPoint, radius = radius),
                radius = radius
            )
            drawCircle(
                color = Color(0xFF00FFCC).copy(alpha = 0.28f),
                radius = radius * 0.72f,
                style = Stroke(width = radius * 0.08f)
            )
            repeat(28) { g ->
                drawCircle(color = Color(0xFF00E5FF).copy(alpha = 0.09f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.PEARL_OYSTER -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFAFAFA), Color(0xFFECEFF1), Color(0xFFCFD8DC)), center = centerPoint, radius = radius),
                radius = radius
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFFFFC1E3).copy(alpha = 0.35f), Color(0xFFE8D5FF).copy(alpha = 0.28f), Color.Transparent)),
                startAngle = -45f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = radius * 0.42f)
            )
            repeat(30) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.22f), radius = radius * (0.22f + g * 0.024f), style = Stroke(width = 0.8f))
            }
        }
        RecordFinishOption.DEEP_REEF -> {
            drawMarbledRecordSurface(
                centerPoint = centerPoint,
                radius = radius,
                baseColors = listOf(Color(0xFF00E5FF), Color(0xFF0097A7), Color(0xFF006064)),
                grooveHighlight = Color.White.copy(alpha = 0.12f),
                veinColor = Color(0xFFFF7043).copy(alpha = 0.18f),
                splashColor = Color(0xFFFFCC80).copy(alpha = 0.09f),
                rimColor = Color.White.copy(alpha = 0.35f),
                seed = 101L,
                vinylTint = vinylTint,
                vinylTintAlpha = vinylBlend,
                effectTint = effectTint,
                effectTintAlpha = effectBlend
            )
        }

        // Group 7: 🌸 Blossom Pressed
        RecordFinishOption.SAKURA_RESIN -> {
            drawCircle(color = Color(0xFFFFF0F5).copy(alpha = 0.24f), radius = radius)
            drawCircle(color = Color(0xFFFFB7C5).copy(alpha = 0.12f), radius = radius, style = Stroke(width = 3.dp.toPx()))
            val flowRandom = java.util.Random(88L)
            repeat(16) { flow ->
                val angle = flow * 22.5f + flowRandom.nextFloat() * 10f
                val dist = radius * (0.35f + flowRandom.nextFloat() * 0.5f)
                rotate(angle, centerPoint) {
                    drawCircle(
                        color = Color(0xFFFF8DA1).copy(alpha = 0.45f),
                        radius = 6.dp.toPx(),
                        center = Offset(centerPoint.x, centerPoint.y - dist)
                    )
                }
            }
            repeat(20) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.15f), radius = radius * (0.25f + g * 0.035f), style = Stroke(width = 0.8f))
            }
        }
        RecordFinishOption.GOLDEN_GINGKO -> {
            drawCircle(color = Color(0xFFFFFDE7).copy(alpha = 0.28f), radius = radius)
            val ginRandom = java.util.Random(199L)
            repeat(12) { leaf ->
                val angle = leaf * 30f + ginRandom.nextFloat() * 8f
                val dist = radius * (0.4f + ginRandom.nextFloat() * 0.45f)
                rotate(angle, centerPoint) {
                    val path = Path().apply {
                        moveTo(centerPoint.x, centerPoint.y - dist)
                        quadraticTo(centerPoint.x - 12.dp.toPx(), centerPoint.y - dist - 6.dp.toPx(), centerPoint.x, centerPoint.y - dist - 18.dp.toPx())
                        quadraticTo(centerPoint.x + 12.dp.toPx(), centerPoint.y - dist - 6.dp.toPx(), centerPoint.x, centerPoint.y - dist)
                    }
                    drawPath(path = path, color = Color(0xFFFFB300).copy(alpha = 0.35f))
                }
            }
            repeat(18) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.15f), radius = radius * (0.28f + g * 0.036f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.PRESSED_LAVENDER -> {
            drawCircle(color = Color(0xFFF3E8FF).copy(alpha = 0.26f), radius = radius)
            val lavRandom = java.util.Random(32L)
            repeat(14) { stalk ->
                val angle = stalk * (360f / 14f) + lavRandom.nextFloat() * 6f
                val dist = radius * (0.35f + lavRandom.nextFloat() * 0.45f)
                rotate(angle, centerPoint) {
                    drawLine(
                        color = Color(0xFF81C784).copy(alpha = 0.28f),
                        start = Offset(centerPoint.x, centerPoint.y - dist + 12.dp.toPx()),
                        end = Offset(centerPoint.x, centerPoint.y - dist - 12.dp.toPx()),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawCircle(color = Color(0xFFB39DDB).copy(alpha = 0.48f), radius = 3.dp.toPx(), center = Offset(centerPoint.x - 3.dp.toPx(), centerPoint.y - dist))
                    drawCircle(color = Color(0xFF7E57C2).copy(alpha = 0.48f), radius = 3.dp.toPx(), center = Offset(centerPoint.x + 3.dp.toPx(), centerPoint.y - dist - 4.dp.toPx()))
                }
            }
            repeat(20) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.12f), radius = radius * (0.25f + g * 0.034f), style = Stroke(width = 0.8f))
            }
        }

        // Group 8: Glacial Frost
        RecordFinishOption.CRACKED_ICE -> {
            drawCircle(color = Color(0xFFE0F7FA).copy(alpha = 0.45f), radius = radius)
            val crackRandom = java.util.Random(901L)
            repeat(12) {
                val angle = it * 30f
                rotate(angle, centerPoint) {
                    val crackPath = Path().apply {
                        moveTo(centerPoint.x, centerPoint.y - radius * 0.25f)
                        lineTo(centerPoint.x + (crackRandom.nextFloat() * 12.dp.toPx() - 6.dp.toPx()), centerPoint.y - radius * 0.55f)
                        lineTo(centerPoint.x + (crackRandom.nextFloat() * 16.dp.toPx() - 8.dp.toPx()), centerPoint.y - radius * 0.96f)
                    }
                    drawPath(path = crackPath, color = Color.White.copy(alpha = 0.48f), style = Stroke(width = 1.5.dp.toPx()))
                }
            }
            repeat(24) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.14f), radius = radius * (0.25f + g * 0.029f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.SNOW_BLIZZARD -> {
            drawCircle(color = Color(0xFFE1F5FE), radius = radius)
            val snowRandom = java.util.Random(111L)
            repeat(45) { flake ->
                val angle = flake * 8f
                val dist = radius * (0.2f + snowRandom.nextFloat() * 0.75f)
                val flakeSize = (2f + snowRandom.nextFloat() * 4.5f).dp.toPx()
                rotate(angle, centerPoint) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.62f),
                        radius = flakeSize,
                        center = Offset(centerPoint.x, centerPoint.y - dist)
                    )
                }
            }
            repeat(20) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.10f), radius = radius * (0.24f + g * 0.034f), style = Stroke(width = 0.8f))
            }
        }
        RecordFinishOption.GLACIER_MELT -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF01579B), Color(0xFF0288D1), Color(0xFFE1F5FE)),
                    center = centerPoint,
                    radius = radius
                ),
                radius = radius
            )
            repeat(25) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.15f), radius = radius * (0.2f + g * 0.03f), style = Stroke(width = 0.8f))
            }
        }

        // Group 9: Desert Sands
        RecordFinishOption.SAHARA_DUNE -> {
            drawCircle(color = Color(0xFFFFE082), radius = radius)
            repeat(5) { dune ->
                drawArc(
                    color = Color(0xFFFFB300).copy(alpha = 0.14f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx()),
                    topLeft = Offset(centerPoint.x - radius * (0.3f + dune * 0.15f), centerPoint.y - radius * (0.2f + dune * 0.1f)),
                    size = Size(radius * (0.6f + dune * 0.3f), radius * (0.4f + dune * 0.2f))
                )
            }
            repeat(28) { g ->
                drawCircle(color = Color.Black.copy(alpha = 0.04f), radius = radius * (0.22f + g * 0.026f), style = Stroke(width = 0.8f))
            }
        }
        RecordFinishOption.RED_CANYON -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD84315), Color(0xFFFF8A65), Color(0xFF3E2723)),
                    center = centerPoint,
                    radius = radius
                ),
                radius = radius
            )
            repeat(6) { canyon ->
                drawCircle(
                    color = Color(0xFF4E342E).copy(alpha = 0.16f),
                    radius = radius * (0.4f + canyon * 0.09f),
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            repeat(32) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * (0.2f + g * 0.024f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.MIRAGE_BLUE -> {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFF00ACC1), Color(0xFFFFD54F), Color(0xFF00ACC1)),
                    center = centerPoint
                ),
                radius = radius
            )
            repeat(22) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.15f), radius = radius * (0.25f + g * 0.03f), style = Stroke(width = 0.8f))
            }
        }

        // Group 10: Tarot Magic
        RecordFinishOption.TAROT_GOLD -> {
            drawCircle(color = Color(0xFF151515), radius = radius)
            val tarRandom = java.util.Random(7L)
            repeat(16) { star ->
                val angle = star * 22.5f + tarRandom.nextFloat() * 8f
                val dist = radius * (0.35f + tarRandom.nextFloat() * 0.5f)
                rotate(angle, centerPoint) {
                    drawLine(
                        color = Color(0xFFFFD700).copy(alpha = 0.45f),
                        start = Offset(centerPoint.x, centerPoint.y - dist - 4.dp.toPx()),
                        end = Offset(centerPoint.x, centerPoint.y - dist + 4.dp.toPx()),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color(0xFFFFD700).copy(alpha = 0.45f),
                        start = Offset(centerPoint.x - 4.dp.toPx(), centerPoint.y - dist),
                        end = Offset(centerPoint.x + 4.dp.toPx(), centerPoint.y - dist),
                        strokeWidth = 1f
                    )
                }
            }
            repeat(30) { g ->
                drawCircle(color = Color(0xFFFFD700).copy(alpha = 0.09f), radius = radius * (0.22f + g * 0.024f), style = Stroke(width = 0.8f))
            }
        }
        RecordFinishOption.SPELL_CIRCLE -> {
            drawCircle(color = Color(0xFF0A0210), radius = radius)
            drawCircle(
                color = Color(0xFFBA68C8).copy(alpha = 0.65f),
                radius = radius * 0.82f,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFBA68C8).copy(alpha = 0.45f),
                radius = radius * 0.74f,
                style = Stroke(width = 1.dp.toPx())
            )
            repeat(18) { rune ->
                rotate(rune * 20f, centerPoint) {
                    drawRect(
                        color = Color(0xFFBA68C8).copy(alpha = 0.4f),
                        topLeft = Offset(centerPoint.x - 3.dp.toPx(), centerPoint.y - radius * 0.78f),
                        size = Size(6.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
            repeat(20) { g ->
                drawCircle(color = Color(0xFFBA68C8).copy(alpha = 0.08f), radius = radius * (0.24f + g * 0.024f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.CRIMSON_ELIXIR -> {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF1D002B)), center = centerPoint, radius = radius),
                radius = radius
            )
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = 0.45f),
                radius = radius * 0.75f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            repeat(3) { tri ->
                rotate(tri * 120f, centerPoint) {
                    drawLine(
                        color = Color(0xFFFFD700).copy(alpha = 0.3f),
                        start = Offset(centerPoint.x, centerPoint.y - radius * 0.75f),
                        end = Offset(centerPoint.x - radius * 0.65f, centerPoint.y + radius * 0.37f),
                        strokeWidth = 1.2f
                    )
                    drawLine(
                        color = Color(0xFFFFD700).copy(alpha = 0.3f),
                        start = Offset(centerPoint.x - radius * 0.65f, centerPoint.y + radius * 0.37f),
                        end = Offset(centerPoint.x + radius * 0.65f, centerPoint.y + radius * 0.37f),
                        strokeWidth = 1.2f
                    )
                }
            }
            repeat(24) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.12f), radius = radius * (0.25f + g * 0.029f), style = Stroke(width = 0.7f))
            }
        }
        RecordFinishOption.FIRE_VINYL -> {
            drawCircle(color = lerpColor(Color(0xFF150805), vinylTint, vinylBlend), radius = radius)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFFE65100).copy(alpha = 0.8f), Color(0xFFFFB300).copy(alpha = 0.6f), Color(0xFFFF3D00).copy(alpha = 0.7f), Color(0xFF150805)),
                    center = centerPoint
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = radius * 0.45f)
            )
            if (animateEffects) {
                val emberRandom = java.util.Random(999L)
                repeat(28) { idx ->
                    val offsetPhase = (idx * 13f + animValue * 2f) % 360f
                    val phaseRad = Math.toRadians(offsetPhase.toDouble()).toFloat()
                    val radiusFluctuation = radius * (0.22f + emberRandom.nextFloat() * 0.65f) + Math.sin(phaseRad.toDouble()).toFloat() * 12.dp.toPx()
                    val angle = idx * 12.8f + (animValue * 0.15f)
                    
                    rotate(angle, centerPoint) {
                        drawCircle(
                            color = when (emberRandom.nextInt(3)) {
                                0 -> Color(0xFFFF9100).copy(alpha = 0.78f)
                                1 -> Color(0xFFFF3D00).copy(alpha = 0.85f)
                                else -> Color(0xFFFFEA00).copy(alpha = 0.72f)
                            },
                            radius = (3f + emberRandom.nextFloat() * 4.5f).dp.toPx() * (1f + 0.25f * Math.sin(phaseRad.toDouble()).toFloat()),
                            center = Offset(centerPoint.x, centerPoint.y - radiusFluctuation)
                        )
                    }
                }
            }
            repeat(30) { g ->
                drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * (0.22f + g * 0.024f), style = Stroke(width = 0.7f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarbledRecordSurface(
    centerPoint: Offset,
    radius: Float,
    baseColors: List<Color>,
    grooveHighlight: Color,
    veinColor: Color,
    splashColor: Color,
    rimColor: Color,
    seed: Long,
    vinylTint: Color = Color.Transparent,
    vinylTintAlpha: Float = 0f,
    effectTint: Color = Color.Transparent,
    effectTintAlpha: Float = 0f
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = baseColors.map { lerpColor(it, vinylTint, vinylTintAlpha * 0.84f) },
            center = centerPoint,
            radius = radius
        ),
        radius = radius
    )

    repeat(48) { groove ->
        drawCircle(
            color = grooveHighlight.copy(alpha = grooveHighlight.alpha * if (groove % 5 == 0) 1.8f else 1f),
            radius = radius * (0.18f + groove * 0.0168f),
            style = Stroke(width = if (groove % 4 == 0) 1.4f else 0.7f)
        )
    }

    val random = java.util.Random(seed)
    repeat(34) {
        val baseAngle = it * (360f / 34f)
        val spread = random.nextFloat() * 26f - 13f
        val angle = baseAngle + spread
        val startR = radius * (0.08f + random.nextFloat() * 0.22f)
        val endR = radius * (0.48f + random.nextFloat() * 0.45f)
        val strokeW = (0.8f + random.nextFloat() * 3.8f).dp.toPx()
        val alpha = 0.035f + random.nextFloat() * 0.10f
        val cx1 = random.nextFloat() * 52.dp.toPx() - 26.dp.toPx()
        val cy1 = random.nextFloat() * 26.dp.toPx() - 13.dp.toPx()
        val cx2 = random.nextFloat() * 52.dp.toPx() - 26.dp.toPx()
        val cy2 = random.nextFloat() * 26.dp.toPx() - 13.dp.toPx()
        rotate(angle, centerPoint) {
            val marblePath = Path().apply {
                moveTo(centerPoint.x, centerPoint.y + startR)
                cubicTo(
                    centerPoint.x + cx1 * 0.4f,
                    centerPoint.y + startR + (endR - startR) * 0.3f,
                    centerPoint.x + cx2 * 0.7f,
                    centerPoint.y + startR + (endR - startR) * 0.65f,
                    centerPoint.x + cx1 * 0.25f,
                    centerPoint.y + endR
                )
            }
            drawPath(
                path = marblePath,
                color = tintPreservingAlpha(veinColor.copy(alpha = alpha), effectTint, effectTintAlpha * 0.72f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }

    val splashRandom = java.util.Random(seed + 77L)
    repeat(18) {
        val angle = it * 20f + splashRandom.nextFloat() * 8f
        val startR = radius * (0.05f + splashRandom.nextFloat() * 0.14f)
        val endR = radius * (0.68f + splashRandom.nextFloat() * 0.24f)
        val strokeW = (2.2f + splashRandom.nextFloat() * 6.8f).dp.toPx()
        val alpha = 0.015f + splashRandom.nextFloat() * 0.032f
        val curveX = splashRandom.nextFloat() * 60.dp.toPx() - 30.dp.toPx()
        rotate(angle, centerPoint) {
            val broadPath = Path().apply {
                moveTo(centerPoint.x, centerPoint.y + startR)
                quadraticTo(
                    centerPoint.x + curveX,
                    centerPoint.y + startR + (endR - startR) * 0.5f,
                    centerPoint.x + curveX * 0.5f,
                    centerPoint.y + endR
                )
            }
            drawPath(
                path = broadPath,
                color = tintPreservingAlpha(splashColor.copy(alpha = alpha), effectTint, effectTintAlpha * 0.64f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }

    drawCircle(
        color = tintPreservingAlpha(rimColor, effectTint, effectTintAlpha * 0.25f),
        radius = radius,
        style = Stroke(width = 1.5.dp.toPx())
    )
    drawCircle(
        color = Color.White.copy(alpha = rimColor.alpha * 0.52f),
        radius = radius * 0.975f,
        style = Stroke(width = 0.8.dp.toPx())
    )
}

private val DEFAULT_PALETTE = MusicPalette(
    backgroundTop = Color(0xFFF8F3EA),
    backgroundBottom = Color(0xFFF1E7DA),
    accent = Color(0xFFB58B70),
    accentSoft = Color(0xFFF8F0E7),
    textStrong = Color(0xFF2B201B),
    textMuted = Color(0xFF867266),
    recordKind = RecordKind.CLASSIC_BLACK,
    isDark = false
)

private fun extractPaletteFromBitmap(bitmap: Bitmap): MusicPalette {
    val scaled = Bitmap.createScaledBitmap(bitmap, 12, 12, true)
    var totalR = 0f
    var totalG = 0f
    var totalB = 0f
    var count = 0
    
    val pixels = IntArray(12 * 12)
    scaled.getPixels(pixels, 0, 12, 0, 0, 12, 12)
    
    for (color in pixels) {
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        val a = AndroidColor.alpha(color)
        if (a > 200) {
            totalR += r
            totalG += g
            totalB += b
            count++
        }
    }
    
    if (scaled != bitmap) {
        scaled.recycle()
    }
    
    val avgR = if (count > 0) (totalR / count).toInt() else 240
    val avgG = if (count > 0) (totalG / count).toInt() else 238
    val avgB = if (count > 0) (totalB / count).toInt() else 230
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.RGBToHSL(avgR, avgG, avgB, hsl)
    
    val bgHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.04f, 0.22f), 0.96f)
    val bgHex = androidx.core.graphics.ColorUtils.HSLToColor(bgHsl)
    val bgTop = Color(bgHex)
    
    val bgBottomHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.04f, 0.22f), 0.93f)
    val bgBottom = Color(androidx.core.graphics.ColorUtils.HSLToColor(bgBottomHsl))
    
    val accentHsl = floatArrayOf(hsl[0], hsl[1].coerceAtLeast(0.40f), 0.38f)
    val accent = Color(androidx.core.graphics.ColorUtils.HSLToColor(accentHsl))
    
    val accentSoftHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.06f, 0.18f), 0.98f)
    val accentSoft = Color(androidx.core.graphics.ColorUtils.HSLToColor(accentSoftHsl))
    
    val textStrongHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.06f, 0.20f), 0.18f)
    val textStrong = Color(androidx.core.graphics.ColorUtils.HSLToColor(textStrongHsl))
    
    val textMutedHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.06f, 0.20f), 0.54f)
    val textMuted = Color(androidx.core.graphics.ColorUtils.HSLToColor(textMutedHsl))
    
    val recordKind = if (hsl[1] > 0.34f && hsl[0] in 175f..260f) {
        RecordKind.SPLASH_BLUE
    } else {
        RecordKind.CLASSIC_BLACK
    }

    return MusicPalette(
        backgroundTop = bgTop,
        backgroundBottom = bgBottom,
        accent = accent,
        accentSoft = accentSoft,
        textStrong = textStrong,
        textMuted = textMuted,
        recordKind = recordKind,
        isDark = false
    )
}

private fun paletteFor(index: Int): MusicPalette {
    return DEFAULT_PALETTE
}

private fun albumPaletteFor(
    album: MusicAlbum,
    dynamicPalettes: Map<Long, MusicPalette>
): MusicPalette {
    return album.tracks.firstNotNullOfOrNull { dynamicPalettes[it.id] } ?: DEFAULT_PALETTE
}

private fun lerpValue(start: Float, end: Float, progress: Float): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    return start + (end - start) * safeProgress
}

private fun lerpColor(start: Color, end: Color, progress: Float): Color {
    val safeProgress = progress.coerceIn(0f, 1f)
    return Color(
        red = lerpValue(start.red, end.red, safeProgress),
        green = lerpValue(start.green, end.green, safeProgress),
        blue = lerpValue(start.blue, end.blue, safeProgress),
        alpha = lerpValue(start.alpha, end.alpha, safeProgress)
    )
}

private fun distanceBetween(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

private fun distancePointToSegment(point: Offset, start: Offset, end: Offset): Float {
    val segmentLengthSquared =
        (end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)

    if (segmentLengthSquared == 0f) {
        return distanceBetween(point, start)
    }

    val projection = (
        ((point.x - start.x) * (end.x - start.x)) +
            ((point.y - start.y) * (end.y - start.y))
        ) / segmentLengthSquared
    val clampedProjection = projection.coerceIn(0f, 1f)
    val projectedPoint = Offset(
        x = start.x + clampedProjection * (end.x - start.x),
        y = start.y + clampedProjection * (end.y - start.y)
    )
    return distanceBetween(point, projectedPoint)
}

private fun rotateOffset(offset: Offset, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val cosValue = Math.cos(radians).toFloat()
    val sinValue = Math.sin(radians).toFloat()
    return Offset(
        x = offset.x * cosValue - offset.y * sinValue,
        y = offset.x * sinValue + offset.y * cosValue
    )
}

private fun landscapeCartridgeCenterForNeedleTip(
    needleTip: Offset,
    armElbow: Offset,
    needleOffset: Offset
): Offset {
    val armAngle = Math.toDegrees(
        Math.atan2(
            (needleTip.y - armElbow.y).toDouble(),
            (needleTip.x - armElbow.x).toDouble()
        )
    ).toFloat()
    val rotatedNeedleOffset = rotateOffset(needleOffset, armAngle - 99f)
    return needleTip - rotatedNeedleOffset
}

private fun landscapeArmAngleForPoint(
    point: Offset,
    armElbow: Offset
): Float {
    return Math.toDegrees(
        Math.atan2(
            (point.y - armElbow.y).toDouble(),
            (point.x - armElbow.x).toDouble()
        )
    ).toFloat()
}

private fun landscapeCartridgeCenterForArmAngle(
    armElbow: Offset,
    armAngle: Float,
    armLength: Float
): Offset {
    val radians = Math.toRadians(armAngle.toDouble())
    return Offset(
        x = armElbow.x + armLength * Math.cos(radians).toFloat(),
        y = armElbow.y + armLength * Math.sin(radians).toFloat()
    )
}

private fun landscapeStylusTipForArmAngle(
    cartridge: Offset,
    armAngle: Float,
    needleOffset: Offset
): Offset {
    return cartridge + rotateOffset(needleOffset, armAngle - 99f)
}

private fun solveLandscapeArmAngle(
    targetStylus: Offset,
    armElbow: Offset,
    armLength: Float,
    needleOffset: Offset,
    minAngle: Float,
    maxAngle: Float
): Float {
    var bestAngle = minAngle
    var bestDistance = Float.MAX_VALUE
    var angle = minAngle
    while (angle <= maxAngle) {
        val cartridge = landscapeCartridgeCenterForArmAngle(
            armElbow = armElbow,
            armAngle = angle,
            armLength = armLength
        )
        val stylus = landscapeStylusTipForArmAngle(
            cartridge = cartridge,
            armAngle = angle,
            needleOffset = needleOffset
        )
        val distance = distanceBetween(stylus, targetStylus)
        if (distance < bestDistance) {
            bestDistance = distance
            bestAngle = angle
        }
        angle += 0.25f
    }
    return bestAngle
}

private fun isLandscapeStylusOnDisc(
    stylus: Offset,
    discCenter: Offset,
    discRadius: Float
): Boolean {
    val distanceFromCenter = distanceBetween(stylus, discCenter)
    return distanceFromCenter in (discRadius * 0.22f)..(discRadius * 1.02f)
}

@Composable
private fun rememberPlaybackDrivenRotation(
    songId: Long,
    progressMs: Long,
    isPlaying: Boolean
): Float {
    var rotation by rememberSaveable(songId) { mutableStateOf((progressMs.toFloat() * 360f / RECORD_SPIN_CYCLE_MS) % 360f) }
    var previousProgressMs by remember { mutableLongStateOf(progressMs) }
    var lastUpdateMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(progressMs) {
        if (kotlin.math.abs(progressMs - previousProgressMs) > 1500L) {
            rotation = (progressMs.toFloat() * 360f / RECORD_SPIN_CYCLE_MS) % 360f
        }
        previousProgressMs = progressMs
    }

    LaunchedEffect(songId, isPlaying) {
        lastUpdateMs = SystemClock.elapsedRealtime()
        if (isPlaying) {
            while (isActive) {
                withFrameNanos { }
                val now = SystemClock.elapsedRealtime()
                val delta = (now - lastUpdateMs).coerceAtLeast(0L)
                lastUpdateMs = now
                rotation = (rotation + (delta.toFloat() * 360f / RECORD_SPIN_CYCLE_MS)) % 360f
            }
        }
    }

    return rotation
}

private fun loadPersistedVinylThemeState(sharedPreferences: SharedPreferences): PersistedVinylThemeState {
    return PersistedVinylThemeState(
        preset = sharedPreferences
            .getString(PREF_THEME_PRESET, PlayerThemePreset.AUTO.name)
            ?.let { runCatching { PlayerThemePreset.valueOf(it) }.getOrNull() }
            ?: PlayerThemePreset.AUTO,
        recordFinish = sharedPreferences
            .getString(PREF_RECORD_FINISH, RecordFinishOption.AUTO.name)
            ?.let { runCatching { RecordFinishOption.valueOf(it) }.getOrNull() }
            ?: RecordFinishOption.AUTO,
        tonearmFinish = sharedPreferences
            .getString(PREF_TONEARM_FINISH, TonearmFinish.SILVER.name)
            ?.let { runCatching { TonearmFinish.valueOf(it) }.getOrNull() }
            ?: TonearmFinish.SILVER,
        labelTone = sharedPreferences
            .getString(PREF_LABEL_TONE, LabelTone.IVORY.name)
            ?.let { runCatching { LabelTone.valueOf(it) }.getOrNull() }
            ?: LabelTone.IVORY,
        glowAmount = sharedPreferences.getFloat(PREF_GLOW_AMOUNT, 0.62f),
        shadowAmount = sharedPreferences.getFloat(PREF_SHADOW_AMOUNT, 0.58f),
        vinylTintArgb = sharedPreferences.getInt(PREF_VINYL_TINT, Color.White.toArgb()),
        vinylTintAlpha = sharedPreferences.getFloat(PREF_VINYL_TINT_ALPHA, 0f),
        effectTintArgb = sharedPreferences.getInt(PREF_EFFECT_TINT, Color(0xFF6A6A6A).toArgb()),
        effectTintAlpha = sharedPreferences.getFloat(PREF_EFFECT_TINT_ALPHA, 0f),
        labelTintArgb = sharedPreferences.getInt(PREF_LABEL_TINT, Color(0xFFF7F3EE).toArgb()),
        labelTintAlpha = sharedPreferences.getFloat(PREF_LABEL_TINT_ALPHA, 0f),
        animateEffects = sharedPreferences.getBoolean(PREF_ANIMATE_EFFECTS, true)
    )
}

private fun persistVinylThemeState(
    sharedPreferences: SharedPreferences,
    state: PersistedVinylThemeState
) {
    sharedPreferences.edit()
        .putString(PREF_THEME_PRESET, state.preset.name)
        .putString(PREF_RECORD_FINISH, state.recordFinish.name)
        .putString(PREF_TONEARM_FINISH, state.tonearmFinish.name)
        .putString(PREF_LABEL_TONE, state.labelTone.name)
        .putFloat(PREF_GLOW_AMOUNT, state.glowAmount)
        .putFloat(PREF_SHADOW_AMOUNT, state.shadowAmount)
        .putInt(PREF_VINYL_TINT, state.vinylTintArgb)
        .putFloat(PREF_VINYL_TINT_ALPHA, state.vinylTintAlpha)
        .putInt(PREF_EFFECT_TINT, state.effectTintArgb)
        .putFloat(PREF_EFFECT_TINT_ALPHA, state.effectTintAlpha)
        .putInt(PREF_LABEL_TINT, state.labelTintArgb)
        .putFloat(PREF_LABEL_TINT_ALPHA, state.labelTintAlpha)
        .putBoolean(PREF_ANIMATE_EFFECTS, state.animateEffects)
        .apply()
}

private fun progressFraction(progressMs: Long, durationMs: Long): Float {
    val safeDuration = durationMs.coerceAtLeast(1L)
    return (progressMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
}

private fun formatSongDuration(durationMs: Long): String {
    val safeSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun requiredMusicPermission(): String? {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_AUDIO
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> Manifest.permission.READ_EXTERNAL_STORAGE
        else -> null
    }
}

private fun hasMusicLibraryPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        return true
    }

    val permission = requiredMusicPermission() ?: return true
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
private fun LyricsOverlay(
    lyrics: String,
    currentProgressMs: Long,
    onDismiss: () -> Unit,
    onShareLyrics: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Lyrics", color = Color.White, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.lazy.LazyColumn {
                item {
                    Text(lyrics, color = Color.LightGray, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = { onShareLyrics(lyrics) }) {
                Text("Share Lyrics")
            }
        }
    }
}

@Composable
private fun LyricsPostcardEditor(
    lyrics: String,
    palette: MusicPalette,
    onDismiss: () -> Unit
) {
    var backgroundColor by remember { mutableStateOf(palette.backgroundBottom) }
    var textColor by remember { mutableStateOf(Color.White) }
    
    // Very simple sticker logic
    var stickers by remember { mutableStateOf(listOf<String>()) }
    val availableStickers = listOf("❤️", "✨", "🔥", "🎶", "🎵", "🥺", "🌸", "⭐", "🦋", "🦄")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(onClick = onDismiss)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // The postcard
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clickable { /* prevent dismiss */ },
            shape = RoundedCornerShape(24.dp),
            color = backgroundColor,
            shadowElevation = 16.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                // Background decoration stickers
                stickers.forEachIndexed { index, sticker ->
                    // Just scatter them randomly based on index
                    val xOffset = ((index * 47) % 200 - 100).dp
                    val yOffset = ((index * 83) % 300 - 150).dp
                    Text(
                        text = sticker,
                        fontSize = 32.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = xOffset, y = yOffset)
                            .graphicsLayer { rotationZ = (index * 25).toFloat() }
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "“",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor.copy(alpha = 0.3f),
                        modifier = Modifier.offset(y = 16.dp)
                    )
                    Text(
                        text = lyrics,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )
                    Text(
                        text = "”",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor.copy(alpha = 0.3f),
                        modifier = Modifier.offset(y = (-16).dp)
                    )
                }
            }
        }
        
        // Editor controls at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clickable { /* prevent dismiss */ },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Colors
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val colors = listOf(Color(0xFF2C2C2C), Color(0xFF6A1B9A), Color(0xFFAD1457), Color(0xFF00695C), Color(0xFF1565C0), Color(0xFFF9A825))
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(color, RoundedCornerShape(16.dp))
                            .border(2.dp, if (backgroundColor == color) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { backgroundColor = color }
                    )
                }
            }
            // Stickers
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(availableStickers) { sticker ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.clickable { stickers = stickers + sticker }
                    ) {
                        Text(sticker, modifier = Modifier.padding(12.dp), fontSize = 24.sp)
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.material3.Button(
                    onClick = { stickers = emptyList() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)
                ) {
                    Text("Clear Stickers")
                }
                
                androidx.compose.material3.Button(
                    onClick = { onDismiss() /* In a real app we'd share the bitmap */ },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Image", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ExpandedActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    palette: MusicPalette,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    customIcon: @Composable (() -> Unit)? = null
) {
    val actualTint = if (tint == androidx.compose.ui.graphics.Color.Unspecified) palette.textStrong else tint
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.size(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = palette.textStrong.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.textStrong.copy(alpha = 0.2f))
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center, modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                if (customIcon != null) {
                    customIcon()
                } else if (icon != null) {
                    androidx.compose.material3.Icon(imageVector = icon, contentDescription = label, tint = actualTint, modifier = androidx.compose.ui.Modifier.size(24.dp))
                }
            }
        }
        androidx.compose.material3.Text(text = label, color = palette.textMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}


@Composable
private fun SleepTimerSheet(
    modifier: Modifier = Modifier,
    palette: MusicPalette,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume clicks so they don't dismiss
                ),
            shape = RoundedCornerShape(28.dp),
            color = palette.backgroundBottom.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).background(palette.textStrong.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                
                Text("Sleep Timer", color = palette.textStrong, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    androidx.compose.material3.OutlinedButton(onClick = { onSetTimer(15) }) { Text("15m", color = palette.textStrong) }
                    androidx.compose.material3.OutlinedButton(onClick = { onSetTimer(30) }) { Text("30m", color = palette.textStrong) }
                    androidx.compose.material3.OutlinedButton(onClick = { onSetTimer(60) }) { Text("60m", color = palette.textStrong) }
                }
                
                androidx.compose.material3.TextButton(onClick = onCancelTimer) {
                    Text("Turn Off Timer", color = palette.textMuted)
                }
            }
        }
    }
}
