package com.aman.gigi.ui.sparkle

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.model.Scribble
import com.aman.gigi.model.ScribbleSummary
import com.aman.gigi.ui.theme.RemindMeTheme
import com.aman.gigi.utils.SparkleMedia
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SparkleRevealActivity : ComponentActivity() {

    @Inject
    lateinit var scribbleRepository: com.aman.gigi.repository.ScribbleRepository

    @Inject
    lateinit var syncManager: com.aman.gigi.data.sync.ScribbleSyncManager

    /**
     * The activity is `singleInstance`, so a second sparkle arriving while it is open is
     * delivered through onNewIntent rather than a fresh onCreate. Routing the arguments
     * through state means the new sparkle actually replaces the stale one on screen.
     */
    private val arrivedScribbleId = MutableStateFlow<String?>(null)
    private val connectionId = MutableStateFlow<String?>(null)
    private val partnerName = MutableStateFlow("Partner")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        readIntent(intent)

        setContent {
            RemindMeTheme {
                val arrivedId by arrivedScribbleId.collectAsState()
                val connId by connectionId.collectAsState()
                val partner by partnerName.collectAsState()

                // Every photo memory shared with this connection, newest first.
                // Summaries only — the full payload is fetched one at a time so browsing
                // a long history never drags every base64 blob into memory at once.
                val history by produceState(initialValue = emptyList<ScribbleSummary>(), connId, arrivedId) {
                    val id = connId
                    value = if (id.isNullOrBlank()) {
                        emptyList()
                    } else {
                        scribbleRepository.getSparkleSummaries(id)
                    }
                }

                // Which sparkle we're looking at. Starts on the one that just arrived and
                // moves back through the history as the user taps "previous".
                var cursor by remember(arrivedId, history.size) {
                    mutableStateOf(history.indexOfFirst { it.scribbleId == arrivedId }.coerceAtLeast(0))
                }

                val currentId = history.getOrNull(cursor)?.scribbleId ?: arrivedId
                val isArrival = currentId != null && currentId == arrivedId

                val scribble by produceState<Scribble?>(initialValue = null, currentId) {
                    val id = currentId
                    value = if (id.isNullOrBlank()) null else scribbleRepository.getScribbleById(id)
                }

                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val loaded = scribble
                    if (loaded != null) {
                        SparkleRevealContent(
                            scribble = loaded,
                            partnerName = partner,
                            position = if (history.isEmpty()) 0 else cursor,
                            total = history.size,
                            // Only the sparkle that just landed is worth locking behind a
                            // scratch/tap reveal — revisiting an older one shows it straight away.
                            locked = isArrival,
                            onPrevious = { if (cursor < history.lastIndex) cursor++ },
                            onNext = { if (cursor > 0) cursor-- },
                            onClose = {
                                arrivedId?.let { syncManager.cancelNotification(it) }
                                finish()
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(source: Intent?) {
        arrivedScribbleId.value = source?.getStringExtra("scribble_id")
        connectionId.value = source?.getStringExtra("connection_id")
        partnerName.value = source?.getStringExtra("partner_name") ?: "Partner"
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SparkleRevealContent(
    scribble: Scribble,
    partnerName: String,
    onClose: () -> Unit,
    position: Int = 0,
    total: Int = 0,
    locked: Boolean = true,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {}
) {
    val context = LocalContext.current

    var bitmap by remember(scribble.scribbleId) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(scribble.scribbleId) {
        val model = SparkleMedia.resolve(scribble)
        if (model != null) {
            val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    when (model) {
                        is ByteArray ->
                            android.graphics.BitmapFactory.decodeByteArray(model, 0, model.size)?.asImageBitmap()
                        is java.io.File ->
                            java.io.FileInputStream(model).use {
                                android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                            }
                        is String ->
                            java.net.URL(model).openStream().use {
                                android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                            }
                        else -> null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SparkleReveal", "Error decoding sparkle media", e)
                    null
                }
            }
            bitmap = decoded
        }
    }

    var isRevealed by remember(scribble.scribbleId) { mutableStateOf(!locked) }
    val stamp = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = currentBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (locked) {
            when (scribble.revealType) {
                "SCRATCH" -> ScratchOffOverlay(onRevealComplete = { if (!isRevealed) isRevealed = true })
                "SECRET" -> SecretRevealOverlay(
                    message = scribble.secretMessage ?: "I love you!",
                    onRevealed = { if (!isRevealed) isRevealed = true }
                )
                else -> LaunchedEffect(scribble.scribbleId) { isRevealed = true }
            }
        }

        // Who + when, so an older sparkle you scrolled back to still has context.
        if (isRevealed) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "${if (scribble.isSent) "You" else partnerName} · ${stamp.format(Date(scribble.createdAt))}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 14.dp)
                    .size(38.dp)
                    .clickable { onClose() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Caption for a secret note once it has been opened
        if (isRevealed && !scribble.secretMessage.isNullOrBlank() && scribble.revealType != "SECRET") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
            ) {
                Text(
                    text = "💌 ${scribble.secretMessage}",
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (isRevealed) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Browse earlier sparkles ──
                if (total > 1) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val hasNewer = position > 0
                            val hasOlder = position < total - 1

                            IconButton(onClick = onNext, enabled = hasNewer, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Newer sparkle",
                                    tint = if (hasNewer) Color.White else Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${position + 1} of $total",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onPrevious, enabled = hasOlder, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Earlier sparkle",
                                    tint = if (hasOlder) Color.White else Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // All memories
                    Button(
                        onClick = {
                            val intent = Intent(context, com.aman.gigi.ui.MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                action = "ACTION_OPEN_MEMORIES"
                                putExtra("connection_id", scribble.connectionId)
                            }
                            context.startActivity(intent)
                            onClose()
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Memories", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    var showReplyPicker by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showReplyPicker = true },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reply", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    if (showReplyPicker) {
                        androidx.compose.ui.window.Dialog(onDismissRequest = { showReplyPicker = false }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color.White.copy(alpha = 0.94f))
                                    .padding(24.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Reply to $partnerName",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.Black
                                    )
                                    Spacer(Modifier.height(24.dp))

                                    Button(
                                        onClick = {
                                            val intent = Intent(context, com.aman.gigi.ui.MainActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                action = "ACTION_REPLY_SCRIBBLE"
                                                putExtra("connection_id", scribble.connectionId)
                                            }
                                            context.startActivity(intent)
                                            showReplyPicker = false
                                            onClose()
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                                    ) {
                                        Icon(Icons.Default.Brush, contentDescription = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Scribble Reply", fontSize = 16.sp)
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val intent = Intent(context, com.aman.gigi.ui.MainActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                action = "ACTION_REPLY_SPARKLE"
                                                putExtra("connection_id", scribble.connectionId)
                                            }
                                            context.startActivity(intent)
                                            showReplyPicker = false
                                            onClose()
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Sparkle Reply", fontSize = 16.sp)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScratchOffOverlay(onRevealComplete: () -> Unit) {
    val paths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            currentPath = Path().apply { moveTo(event.x, event.y) }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            currentPath?.lineTo(event.x, event.y)
                            // Hack to trigger refresh
                            paths.add(Path())
                            paths.removeAt(paths.size - 1)
                        }
                        MotionEvent.ACTION_UP -> {
                            currentPath?.let { paths.add(it) }
                            currentPath = null
                            if (paths.size > 5) onRevealComplete()
                        }
                    }
                    true
                }
        ) {
            // Foil (Native Canvas for Eraser)
            drawContext.canvas.nativeCanvas.saveLayer(null, null)

            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#C0C0C0")
            }
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)

            // Eraser paint
            val clearPaint = android.graphics.Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                strokeWidth = 100f
                style = android.graphics.Paint.Style.STROKE
                strokeJoin = android.graphics.Paint.Join.ROUND
                strokeCap = android.graphics.Paint.Cap.ROUND
                isAntiAlias = true
            }

            paths.forEach { path ->
                drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), clearPaint)
            }
            currentPath?.let {
                drawContext.canvas.nativeCanvas.drawPath(it.asAndroidPath(), clearPaint)
            }

            drawContext.canvas.nativeCanvas.restore()
        }

        Text(
            text = "Scratch to Reveal! ✨",
            color = Color.DarkGray,
            modifier = Modifier.align(Alignment.Center).alpha(if (paths.size > 2) 0f else 1f),
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
    }
}

@Composable
fun SecretRevealOverlay(message: String, onRevealed: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (revealed) 1f else 0f, animationSpec = tween(1000), label = "secretAlpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (revealed) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.9f))
            .clickable {
                revealed = true
                onRevealed()
            },
        contentAlignment = Alignment.Center
    ) {
        if (!revealed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Text("Secret Message inside...", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Tap to reveal", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        } else {
            Text(
                text = message,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(32.dp).alpha(alpha)
            )
        }
    }
}
