package com.aman.gigi.ui.sparkle

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import com.aman.gigi.model.Scribble
import com.aman.gigi.ui.theme.RemindMeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class SparkleRevealActivity : ComponentActivity() {

    @Inject
    lateinit var scribbleRepository: com.aman.gigi.repository.ScribbleRepository

    @Inject
    lateinit var syncManager: com.aman.gigi.data.sync.ScribbleSyncManager

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
        
        val scribbleId = intent.getStringExtra("scribble_id")
        val partnerName = intent.getStringExtra("partner_name") ?: "Partner"

        setContent {
            RemindMeTheme {
                var scribble by remember { mutableStateOf<Scribble?>(null) }
                
                LaunchedEffect(scribbleId) {
                    if (scribbleId != null) {
                        scribble = scribbleRepository.getScribbleById(scribbleId)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    if (scribble != null) {
                        SparkleRevealContent(
                            scribble = scribble!!,
                            partnerName = partnerName,
                            onClose = { 
                                scribbleId?.let { syncManager.cancelNotification(it) }
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
}

@OptIn(ExperimentalComposeUiApi::class, androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun SparkleRevealContent(
    scribble: Scribble,
    partnerName: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(scribble.mediaBase64) {
        if (scribble.mediaBase64 != null) {
            val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bytes = android.util.Base64.decode(scribble.mediaBase64, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (e: Exception) {
                    android.util.Log.e("SparkleReveal", "Error decoding bitmap", e)
                    null
                }
            }
            bitmap = decoded
        }
    }

    var isRevealed by remember { mutableStateOf(false) }
    
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

        when (scribble.revealType) {
            "SCRATCH" -> {
                ScratchOffOverlay(
                    onRevealComplete = { 
                        if (!isRevealed) {
                            isRevealed = true
                        }
                    }
                )
            }
            "SECRET" -> {
                SecretRevealOverlay(
                    message = scribble.secretMessage ?: "I love you!",
                    onRevealed = {
                         if (!isRevealed) {
                            isRevealed = true
                        }
                    }
                )
            }
            else -> {
                LaunchedEffect(Unit) { isRevealed = true }
            }
        }

        // Reply & Dismiss Buttons
        if (isRevealed) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dismiss Button
                Button(
                    onClick = onClose,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.5f))
                ) {
                    Text("Dismiss", color = Color.Black)
                }
                
                // Reply Button
                val context = androidx.compose.ui.platform.LocalContext.current
                var showReplyPicker by remember { mutableStateOf(false) }

                Button(
                    onClick = { showReplyPicker = true },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Reply, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Reply", color = Color.White)
                }

                if (showReplyPicker) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showReplyPicker = false }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White.copy(alpha = 0.9f))
                                .padding(24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Reply to $partnerName",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.Black
                                )
                                Spacer(Modifier.height(24.dp))
                                
                                // Scribble Reply
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(context, com.aman.gigi.ui.MainActivity::class.java).apply {
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
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
                                    Icon(androidx.compose.material.icons.Icons.Default.Brush, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Scribble Reply", fontSize = 16.sp)
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // Sparkle Reply
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(context, com.aman.gigi.ui.MainActivity::class.java).apply {
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
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
                                    Icon(androidx.compose.material.icons.Icons.Default.PhotoCamera, contentDescription = null)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScratchOffOverlay(onRevealComplete: () -> Unit) {
    val paths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    
    val foilColor = Color(0xFFC0C0C0) 

    Box(modifier = Modifier.fillMaxSize()) {
        // Transparent Overlay with Foil? No, foil on top.
        // For real scratch off, we need PorterDuff on Native Canvas.
        // Let's use a simpler "tap to reveal" for now if MVP, or use Native Canvas.
        
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
                            paths.removeAt(paths.size-1)
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
    val alpha by animateFloatAsState(targetValue = if (revealed) 1f else 0f, animationSpec = tween(1000))

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

