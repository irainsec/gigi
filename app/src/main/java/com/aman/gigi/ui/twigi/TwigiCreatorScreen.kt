package com.aman.gigi.ui.twigi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Ink = Color(0xFF3B2A6B)
private val Lavender = Color(0xFF8B5CF6)
private val Muted = Color(0xFF9A8FC0)
private val StageLight = Color(0xFFB9A9E8)

private data class TwigiOption(val id: String, val thumb: String)
private data class TwigiCatalog(
    val parts: LinkedHashMap<String, List<TwigiOption>>,
    val colors: LinkedHashMap<String, List<String>>,
    val labels: Map<String, String>,
    val order: List<String>,
    val default: Map<String, String>,
    val paywallItems: List<String> = emptyList()
)

private fun serverHttpBase(): String = runCatching {
    val ws = java.net.URI(com.aman.gigi.BuildConfig.SERVER_URL)
    val scheme = if (ws.scheme.equals("wss", true)) "https" else "http"
    java.net.URI(scheme, ws.userInfo, ws.host, ws.port, null, null, null).toString().trimEnd('/')
}.getOrDefault("https://gigi.iamanraj.com")

// Body / Motion / Style are stage quick-pickers, not wardrobe tabs.
private val META_KEYS = setOf("body", "anim", "style")

private fun emojiForTab(key: String): String = when (key) {
    "gallery" -> "🖼️"; "skinColor" -> "🎨"; "hairColor" -> "🌈"; "hair" -> "💇"
    "eyes" -> "👀"; "brows" -> "🤨"; "nose" -> "👃"; "ears" -> "👂"; "beards" -> "🧔"
    "facial" -> "🥸"; "top" -> "👕"; "dress" -> "👗"; "bottom" -> "👖"; "shoes" -> "👟"
    "hat" -> "🎩"; "cape" -> "🦸"; "neck" -> "🧣"; "arms" -> "💪"; "shoulders" -> "🎽"
    "weapon" -> "⚔️"; "tool" -> "🔨"; "shield" -> "🛡️"; "backpack" -> "🎒"
    "quiver" -> "🏹"; else -> "✨"
}

private val ANIM_META = mapOf(
    "walk" to ("🚶" to "Walk"), "alive" to ("✨" to "Alive"), "idle" to ("🧍" to "Idle"),
    "run" to ("🏃" to "Run"), "jump" to ("🦘" to "Jump"), "sit" to ("🪑" to "Sit"),
    "spellcast" to ("🪄" to "Magic"), "slash" to ("⚔️" to "Slash"),
    "thrust" to ("🔱" to "Thrust"), "shoot" to ("🏹" to "Shoot"),
    "climb" to ("🧗" to "Climb"), "hurt" to ("💫" to "Faint"), "emote" to ("😊" to "Emote"),
    "static" to ("⏸️" to "Still"),
)
private val STYLE_META = mapOf("pixel" to ("🟦" to "Pixel"), "smooth" to ("〰️" to "Smooth"))
private val BODY_META = mapOf("male" to ("👦" to "Male"), "female" to ("👧" to "Female"))

private fun metaOf(key: String, id: String): Pair<String, String> = when (key) {
    "anim" -> ANIM_META[id]; "style" -> STYLE_META[id]; else -> BODY_META[id]
} ?: ("🎞️" to id.replaceFirstChar { it.uppercase() })

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
}.getOrDefault(Color.Gray)

private fun configB64(cfg: Map<String, String>): String {
    val obj = JSONObject(); cfg.forEach { (k, v) -> obj.put(k, v) }
    return android.util.Base64.encodeToString(
        obj.toString().toByteArray(Charsets.UTF_8),
        android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
    )
}

/**
 * Twigi Studio — immersive avatar creator. Server-driven catalog (826+ LPC items,
 * 14 motions, 2 render styles), pixel-perfect animated preview, Body/Motion/Style
 * pickers on the stage, and the Credits screen (license requirement) behind ⓘ.
 */
@OptIn(FlowPreview::class)
@Composable
fun TwigiCreatorScreen(
    initialConfigJson: String?,
    saving: Boolean,
    isSubscribed: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (configJson: String) -> Unit
) {
    val context = LocalContext.current
    val base = remember { serverHttpBase() }
    val animLoader = remember {
        ImageLoader.Builder(context).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }
    var catalog by remember { mutableStateOf<TwigiCatalog?>(null) }
    var loadErrorMsg by remember { mutableStateOf<String?>(null) }
    val cfg = remember { mutableStateMapOf<String, String>() }
    var selectedTab by remember { mutableStateOf<String?>(null) }
    var staticUrl by remember { mutableStateOf<String?>(null) }
    var animUrl by remember { mutableStateOf<String?>(null) }
    var previewPx by remember { mutableStateOf(320) }
    var showCredits by remember { mutableStateOf(false) }
    var pickerKey by remember { mutableStateOf<String?>(null) }
    var showPaywallSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (parsed, isNetworkError) = withContext(Dispatchers.IO) {
            val netResult = runCatching { parseCatalog(java.net.URL("$base/twigi/catalog.json").readText()) }
            val netParsed = netResult.getOrNull()
            if (netParsed != null) {
                netParsed to false
            } else {
                val assetParsed = runCatching {
                    context.assets.open("twigi_catalog.json").bufferedReader().use { parseCatalog(it.readText()) }
                }.getOrNull()
                assetParsed to true
            }
        }
        if (parsed == null) {
            loadErrorMsg = "Couldn't reach Twigi Studio.\nCheck your connection."
            return@LaunchedEffect
        }
        if (parsed.parts.isEmpty()) {
            loadErrorMsg = if (isNetworkError) "Couldn't reach Twigi Studio.\nCheck your connection."
            else "Twigi assets aren't installed on the server."
            return@LaunchedEffect
        }
        loadErrorMsg = null
        catalog = parsed
        val seed = runCatching { initialConfigJson?.let { JSONObject(it) } }.getOrNull()
        parsed.default.forEach { (k, v) -> cfg[k] = v }
        seed?.keys()?.forEach { k -> cfg[k] = seed.optString(k) }
        selectedTab = parsed.order.firstOrNull { it !in META_KEYS }
    }

    LaunchedEffect(catalog) {
        if (catalog == null) return@LaunchedEffect
        snapshotFlow { cfg.toMap() to previewPx }.debounce(250).collect { (c, px) ->
            val b64 = configB64(c)
            staticUrl = "$base/twigi/preview?c=$b64&size=$px"
            animUrl = "$base/twigi/anim?c=$b64&size=$px"
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF241B45), Color(0xFF352866), Color(0xFF4A3585))))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircle(onClick = onDismiss) { Text("✕", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Twigi Studio", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("your tiny twin ✨", fontSize = 12.sp, color = StageLight)
                }
                GlassCircle(onClick = { showCredits = true }) { Text("ⓘ", color = Color.White, fontSize = 16.sp) }
                Spacer(Modifier.width(8.dp))
                catalog?.let { cat ->
                    GlassCircle(onClick = { cfg.putAll(randomConfig(cat)) }) { Text("🎲", fontSize = 17.sp) }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val availPx = with(density) { minOf(maxWidth, maxHeight).toPx() } * 0.92f
                val px = ((availPx / 64f).toInt()).coerceIn(3, 8) * 64
                SideEffect { if (previewPx != px) previewPx = px }
                val charSize = with(density) { px.toDp() }

                Box(
                    modifier = Modifier.size(320.dp).background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent))
                    )
                )
                when {
                    loadErrorMsg != null -> Text(loadErrorMsg!!,
                        color = StageLight, fontSize = 14.sp, textAlign = TextAlign.Center)
                    staticUrl != null -> Box(modifier = Modifier.size(charSize), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            modifier = Modifier.offset(y = 7.dp).size(charSize * 0.5f, 16.dp)
                                .background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)))
                        )
                        val isAnim = cfg["anim"] != "static" && animUrl != null
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (isAnim) animUrl else staticUrl)
                                .crossfade(true).build(),
                            imageLoader = animLoader,
                            contentDescription = "Twigi preview",
                            filterQuality = FilterQuality.None,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> CircularProgressIndicator(color = StageLight)
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaChip(emoji = metaOf("body", cfg["body"] ?: "male").first, label = "Body") { pickerKey = "body" }
                    MetaChip(emoji = metaOf("anim", cfg["anim"] ?: "walk").first, label = "Motion") { pickerKey = "anim" }
                    MetaChip(emoji = metaOf("style", cfg["style"] ?: "pixel").first, label = "Style") { pickerKey = "style" }
                }
            }

            catalog?.let { cat ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(Color.White).padding(top = 10.dp, bottom = 16.dp)
                ) {
                    val pickableTabs = cat.order.filter { it !in META_KEYS }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pickableTabs, key = { it }) { key ->
                            val active = (selectedTab ?: pickableTabs.firstOrNull()) == key
                            val label = cat.labels[key] ?: key
                            Surface(
                                onClick = { selectedTab = key },
                                shape = RoundedCornerShape(999.dp),
                                color = if (active) Lavender else Color(0xFFF3EEFF)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(emojiForTab(key), fontSize = 13.sp)
                                    Spacer(Modifier.width(5.dp))
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else Ink)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.height(258.dp)) {
                        when {
                            cat == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (loadErrorMsg == null) CircularProgressIndicator(color = Lavender, modifier = Modifier.size(28.dp))
                            }
                            else -> {
                                val key = selectedTab ?: cat.order.first { it !in META_KEYS }
                                val partOptions = cat.parts[key]
                                if (partOptions != null) OptionGrid(base, partOptions, cfg[key], cat.paywallItems) { cfg[key] = it }
                                else SwatchGrid(cat.colors[key].orEmpty(), cfg[key]) { cfg[key] = it }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val equippedVip = cfg.values.filter { it in (catalog?.paywallItems ?: emptyList()) }
                            if (equippedVip.isNotEmpty() && !isSubscribed) {
                                showPaywallSheet = true
                            } else {
                                val obj = JSONObject(); cfg.forEach { (k, v) -> obj.put(k, v) }; onSave(obj.toString())
                            }
                        },
                        enabled = !saving && catalog != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Lavender),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp)
                    ) {
                        if (saving) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp)); Text("Saving your twin…", fontWeight = FontWeight.Bold)
                        } else Text("Save my Twigi 💜", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (showCredits) CreditsDialog(base) { showCredits = false }
        if (showPaywallSheet) {
            com.aman.gigi.ui.components.UpgradeSheet(
                featureName = "Twigi VIP Outfits 👑",
                featureDescription = "Equipping VIP crowns, wizard robes, or jetpacks requires a Gigi Plus subscription 👑",
                onDismiss = { showPaywallSheet = false }
            )
        }
        pickerKey?.let { key ->
            catalog?.let { cat ->
                MetaPickerDialog(
                    title = cat.labels[key] ?: key,
                    key = key,
                    ids = cat.parts[key]?.map { it.id }.orEmpty(),
                    current = cfg[key],
                    onPick = { cfg[key] = it; pickerKey = null },
                    onClose = { pickerKey = null }
                )
            }
        }
    }
}

@Composable
private fun GlassCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.14f), modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun MetaChip(emoji: String, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.14f)) {
        Column(
            modifier = Modifier.width(56.dp).padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// Full-choice picker for Body / Motion / Style — every option visible with a label.
@Composable
private fun MetaPickerDialog(
    title: String, key: String, ids: List<String>, current: String?,
    onPick: (String) -> Unit, onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
                    Surface(onClick = onClose, shape = CircleShape, color = Color(0xFFF3EEFF), modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("✕", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    items(ids, key = { it }) { id ->
                        val active = id == current
                        val (emoji, label) = metaOf(key, id)
                        Surface(
                            onClick = { onPick(id) }, shape = RoundedCornerShape(16.dp),
                            color = if (active) Color(0xFFEDE4FF) else Color(0xFFF7F4FF),
                            border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Lavender else Color(0xFFE3DAF7))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(emoji, fontSize = 22.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (active) Ink else Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionGrid(
    base: String,
    options: List<TwigiOption>,
    selected: String?,
    paywallItems: List<String> = emptyList(),
    onPick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.fillMaxWidth()
    ) {
        items(options, key = { it.id }) { opt ->
            val active = opt.id == selected
            val isVip = opt.id in paywallItems
            Surface(
                onClick = { onPick(opt.id) }, shape = RoundedCornerShape(18.dp),
                color = if (active) Color(0xFFEDE4FF) else Color(0xFFF7F4FF),
                shadowElevation = if (active) 6.dp else 0.dp,
                border = BorderStroke(
                    if (active) 2.5.dp else 1.dp,
                    if (active) Lavender else if (isVip) Color(0xFFF472B6) else Color(0xFFE3DAF7)
                )
            ) {
                Box(modifier = Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (opt.id == "none") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🚫", fontSize = 22.sp)
                            Text("None", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        AsyncImage(
                            model = "$base${opt.thumb}", contentDescription = opt.id,
                            contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(0.92f).padding(4.dp)
                        )
                    }
                    if (isVip) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF472B6),
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        ) {
                            Text(
                                "👑 VIP",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchGrid(colors: List<String>, selected: String?, onPick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), modifier = Modifier.fillMaxWidth()
    ) {
        items(colors, key = { it }) { hex ->
            val active = hex.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier.aspectRatio(1f).clip(CircleShape).background(parseColor(hex))
                    .border(if (active) 3.5.dp else 1.dp, if (active) Lavender else Color.Black.copy(alpha = 0.1f), CircleShape)
                    .clickable { onPick(hex) }
            )
        }
    }
}

@Composable
private fun CreditsDialog(base: String, onClose: () -> Unit) {
    var sections by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val o = JSONObject(java.net.URL("$base/twigi/credits").readText())
                val arr = o.getJSONArray("sections")
                (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i); s.getString("title") to s.getString("text")
                }
            }.getOrNull()
        }
        if (loaded == null) failed = true else sections = loaded
    }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(modifier = Modifier.padding(20.dp).heightIn(max = 560.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Credits & Licenses", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                        color = Ink, modifier = Modifier.weight(1f))
                    Surface(onClick = onClose, shape = CircleShape, color = Color(0xFFF3EEFF), modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("✕", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                when {
                    failed -> Text("Couldn't load credits — check your connection.", color = Muted, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp))
                    sections == null -> Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Lavender, modifier = Modifier.size(26.dp))
                    }
                    else -> LazyColumn {
                        items(sections!!) { (title, body) ->
                            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Lavender,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                            Text(body, fontSize = 11.sp, color = Ink.copy(alpha = 0.85f), lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun parseCatalog(json: String): TwigiCatalog {
    val o = JSONObject(json)
    val parts = LinkedHashMap<String, List<TwigiOption>>()
    o.optJSONObject("parts")?.let { p ->
        p.keys().forEach { key ->
            val arr = p.getJSONArray(key)
            parts[key] = (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i); TwigiOption(e.getString("id"), e.optString("thumb"))
            }
        }
    }
    val colors = LinkedHashMap<String, List<String>>()
    o.optJSONObject("colors")?.let { c ->
        c.keys().forEach { key ->
            val arr = c.getJSONArray(key); colors[key] = (0 until arr.length()).map { arr.getString(it) }
        }
    }
    val labels = HashMap<String, String>()
    o.optJSONObject("labels")?.let { l -> l.keys().forEach { labels[it] = l.optString(it) } }
    val order = o.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.getString(it) } }
        ?: (parts.keys + colors.keys).toList()
    val def = HashMap<String, String>()
    o.optJSONObject("default")?.let { d -> d.keys().forEach { def[it] = d.optString(it) } }
    val paywallItems = o.optJSONArray("paywallItems")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    return TwigiCatalog(parts, colors, labels, order, def, paywallItems)
}

private val RANDOM_EXTRA_CHANCE = mapOf(
    "hat" to 0.25, "beards" to 0.15, "facial" to 0.12, "eyes" to 0.20, "neck" to 0.15,
    "cape" to 0.08, "arms" to 0.10, "shoulders" to 0.10, "weapon" to 0.12,
    "shield" to 0.08, "backpack" to 0.10, "dress" to 0.0, "tool" to 0.06,
    "quiver" to 0.04, "brows" to 0.15, "nose" to 0.10, "ears" to 0.06,
)

private fun randomConfig(cat: TwigiCatalog): Map<String, String> {
    val m = HashMap<String, String>()
    val essentials = setOf("hair", "top", "bottom", "shoes")
    cat.parts.forEach { (k, v) ->
        if (v.isEmpty()) return@forEach
        val real = v.filter { it.id != "none" }
        when {
            k == "gallery" -> m[k] = "none"
            k == "anim" || k == "style" -> Unit
            k == "body" -> m[k] = v.random().id
            k in essentials -> real.randomOrNull()?.let { m[k] = it.id }
            else -> m[k] = if (Math.random() < (RANDOM_EXTRA_CHANCE[k] ?: 0.1))
                real.randomOrNull()?.id ?: "none" else "none"
        }
    }
    cat.colors.forEach { (k, v) -> if (v.isNotEmpty()) m[k] = v.random() }
    return m
}
