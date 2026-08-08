package com.aman.gigi.ui.sparkle

import android.content.Context
import java.util.*
import java.text.SimpleDateFormat
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.compose.animation.core.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.aman.gigi.utils.toBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun SparkleCameraScreen(
    onClose: () -> Unit,
    onCapture: () -> Unit, // Legacy, unused
    viewModel: com.aman.gigi.viewmodel.ScreensaverViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }

    // Contextual camera permission: ask the moment the Sparkle camera opens, with the
    // animated rationale popup, instead of an up-front wall.
    val permissionFlow = com.aman.gigi.ui.LocalPermissionFlow.current
    var hasCameraPermission by remember {
        mutableStateOf(com.aman.gigi.ui.isFeatureGranted(context, com.aman.gigi.ui.FeaturePermission.CAMERA))
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionFlow.request(com.aman.gigi.ui.FeaturePermission.CAMERA) {
                hasCameraPermission = true
            }
        }
    }
    
    // Image Capture Use Case
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build() 
    }
    
    // Face State
    var faces by remember { mutableStateOf<List<com.google.mlkit.vision.face.Face>>(emptyList()) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }
    
    // Filter State
    var selectedFilter by remember { mutableStateOf(SparkleFilter.NORMAL) }
    var secretMessageText by remember { mutableStateOf("") }
    var showSecretDialog by remember { mutableStateOf(false) }

    
    val effectsEngine = remember {
        SparkleEffectsEngine { detectedFaces ->
            faces = detectedFaces
        }
    }
    
    LaunchedEffect(cameraSelector, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProvider = context.getCameraProvider()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    // Capture dimensions for scaling overlay
                    if (imageWidth == 0) {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        if (rotation == 90 || rotation == 270) {
                            imageWidth = imageProxy.height
                            imageHeight = imageProxy.width
                        } else {
                            imageWidth = imageProxy.width
                            imageHeight = imageProxy.height
                        }
                    }
                    effectsEngine.analyze(imageProxy)
                }
            }
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
                imageCapture // Bind Capture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // GIGI_FIX: Refresh location to fix "Connecting..." badge. Location powers the partner
    // distance badge, so ask for it contextually (once) after the camera is sorted, then refresh.
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            permissionFlow.requestOnce(com.aman.gigi.ui.FeaturePermission.LOCATION) {
                viewModel.refreshLocations()
            }
        }
    }
    
    // Hide System Bars (Immersive Mode)
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val insetsController = if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView) else null
        
        insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        onDispose {
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        // Face Overlay
            val myLoc by viewModel.myLocation.collectAsState()
            val partnerLoc by viewModel.partnerLocation.collectAsState()
            val activeConn by viewModel.activeConnections.collectAsState()
            val anniversaryDate = activeConn.firstOrNull()?.anniversaryDate

            // ALWAYS Render Overlay (for Global filters like Polaroid), even if no faces detected
            FaceOverlay(
                faces = faces,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                filter = selectedFilter,
                myLocation = myLoc,
                partnerLocation = partnerLoc,
                anniversaryDate = anniversaryDate
            )
        
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(), // Handle status bar
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Camera",
                    tint = Color.White
                )
            }
            
            // Flip Camera
            IconButton(onClick = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Flip Camera",
                    tint = Color.White
                )
            }
        }
        
        // Bottom Controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            // Shutter Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        // CAPTURE PHOTO
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = image.toBitmap()
                                    if (bitmap == null) {
                                        image.close()
                                        return
                                    }

                                    // Correct Rotation & Mirroring
                                    val matrix = android.graphics.Matrix()
                                    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                                    if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                                        matrix.postScale(-1f, 1f)
                                    }
                                    
                                    val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                    )
                                    
                                    // Process for Effects
                                    effectsEngine.detectInImage(com.google.mlkit.vision.common.InputImage.fromBitmap(rotatedBitmap, 0))
                                        .addOnSuccessListener { detectedFaces ->
                                            val mutableBitmap = rotatedBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                                            val canvas = android.graphics.Canvas(mutableBitmap)
                                            val paint = android.graphics.Paint().apply {
                                                color = android.graphics.Color.RED
                                                style = android.graphics.Paint.Style.STROKE
                                                strokeWidth = 5f
                                            }
                                            
                                            // Draw effects on detected faces
                                            // Scale factor logic: For captured image, coordinates are ALREADY correct relative to bitmap
                                            // BUT ML Kit might have analyzed a downscaled version if not careful.
                                            // Actually, we detected on 'rotatedBitmap' (the full res capture), 
                                            // so bounds are perfect. NO SCALING NEEDED.
                                            
                                            detectedFaces.forEach { face ->
                                                drawFilterOnCanvas(
                                                    canvas = canvas,
                                                    face = face,
                                                    filter = selectedFilter,
                                                    scale = 1.0f,
                                                    myLocation = viewModel.myLocation.value,
                                                    partnerLocation = viewModel.partnerLocation.value,
                                                    anniversaryDate = viewModel.activeConnections.value.firstOrNull()?.anniversaryDate
                                                )
                                            }
                                            
                                            // Draw Global Filters (like Polaroid) ONCE
                                            if (selectedFilter == SparkleFilter.POLAROID) {
                                                drawGlobalFilter(
                                                    canvas = canvas,
                                                    filter = selectedFilter,
                                                    myLocation = viewModel.myLocation.value,
                                                    partnerLocation = viewModel.partnerLocation.value,
                                                    width = mutableBitmap.width.toFloat(),
                                                    height = mutableBitmap.height.toFloat()
                                                )
                                            }
                                            
                                            // Handle Mystery Types
                                            val revealType = when(selectedFilter) {
                                                SparkleFilter.SCRATCH -> "SCRATCH"
                                                SparkleFilter.SECRET -> "SECRET"
                                                else -> null
                                            }
                                            
                                            // Send processed bitmap
                                            val partnerId = viewModel.partnerConnectionId.value
                                            val activeConnection = if (partnerId != null) {
                                                viewModel.activeConnections.value.find { it.connectionId == partnerId }
                                            } else {
                                                viewModel.activeConnections.value.firstOrNull()
                                            }
                                            
                                            if (activeConnection != null) {
                                                viewModel.sendSparkle(
                                                    mutableBitmap, 
                                                    connectionId = activeConnection.connectionId,
                                                    revealType = revealType,
                                                    secretMessage = if (selectedFilter == SparkleFilter.SECRET) secretMessageText else null
                                                )
                                            } else {
                                                android.widget.Toast.makeText(context, "No active connection", android.widget.Toast.LENGTH_SHORT).show()
                                                viewModel.resetSparkleStatus()
                                            }
                                            
                                            image.close()
                                        }
                                        .addOnFailureListener {
                                            image.close()
                                        }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite, // Heart/Shutter
                    contentDescription = "Capture",
                    tint = Color.Red,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // --- Filter Selector ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(SparkleFilter.entries.toList()) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(if (isSelected) 56.dp else 44.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f))
                            .border(2.dp, if (isSelected) Color.Red else Color.White, CircleShape)
                            .clickable { 
                                selectedFilter = filter 
                                if (filter == SparkleFilter.SECRET) {
                                    showSecretDialog = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when(filter) {
                                SparkleFilter.NORMAL -> Icons.Default.Close
                                SparkleFilter.HEART -> Icons.Default.Favorite
                                SparkleFilter.SPARKLES -> Icons.Default.Star
                                SparkleFilter.CAT_EARS -> Icons.Default.Face
                                SparkleFilter.STAR_CROWN -> Icons.Default.AutoAwesome
                                SparkleFilter.POLAROID -> Icons.Default.PhotoCamera
                                SparkleFilter.MISS_YOU -> Icons.Default.FavoriteBorder
                                SparkleFilter.ANNIVERSARY -> Icons.Default.Cake
                                SparkleFilter.MOOD_HAPPY -> Icons.Default.SentimentVerySatisfied
                                SparkleFilter.MOOD_SHY -> Icons.Default.SentimentSatisfied
                                SparkleFilter.MOOD_SILLY -> Icons.Default.SentimentVeryDissatisfied
                                SparkleFilter.SCRATCH -> Icons.Default.Brush // Placeholder for Scratch
                                SparkleFilter.SECRET -> Icons.Default.Email // Placeholder for Secret
                            },
                            contentDescription = filter.name,
                            tint = if (isSelected) Color.Red else Color.White,
                            modifier = Modifier.size(if (isSelected) 30.dp else 24.dp)
                        )
                    }
                }
            }
        }
        
        // --- Status Overlays ---
        val sendStatus by viewModel.sparkleSendStatus.collectAsState()
        
        LaunchedEffect(sendStatus) {
            if (sendStatus == com.aman.gigi.viewmodel.SendStatus.SENT) {
                delay(1500)
                onClose() // Navigate back
                viewModel.resetSparkleStatus()
            } else if (sendStatus == com.aman.gigi.viewmodel.SendStatus.ERROR) {
                android.widget.Toast.makeText(context, "Failed to send Sparkle", android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetSparkleStatus()
            }
        }

        if (sendStatus == com.aman.gigi.viewmodel.SendStatus.SENDING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sending Sparkle...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }
        
        if (sendStatus == com.aman.gigi.viewmodel.SendStatus.SENT) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sparkle Sent!",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                }
            }
        }

        // --- Secret Message Dialog ---
        if (showSecretDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showSecretDialog = false 
                    if (secretMessageText.isEmpty()) selectedFilter = SparkleFilter.NORMAL
                },
                title = { Text("Write a Secret Message") },
                text = {
                    TextField(
                        value = secretMessageText,
                        onValueChange = { secretMessageText = it },
                        placeholder = { Text("Tap to reveal...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showSecretDialog = false }) {
                        Text("Save")
                    }
                }
            )
        }
    }
}

@Composable
fun FaceOverlay(
    faces: List<com.google.mlkit.vision.face.Face>,
    imageWidth: Int,
    imageHeight: Int,
    filter: SparkleFilter,
    myLocation: Pair<Double, Double>? = null,
    partnerLocation: Pair<Double, Double>? = null,
    anniversaryDate: Long? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        if (imageWidth > 0 && imageHeight > 0) {
            val scaleX = size.width / imageWidth
            val scaleY = size.height / imageHeight
            val scale = maxOf(scaleX, scaleY)
            
            // Center the overlay if aspects differ (letterboxing)
            val offsetX = (size.width - imageWidth * scale) / 2
            val offsetY = (size.height - imageHeight * scale) / 2

            faces.forEach { face ->
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.translate(offsetX, offsetY)
                drawFilterOnCanvas(
                    drawContext.canvas.nativeCanvas, 
                    face, 
                    filter, 
                    scale, 
                    previewPulse = pulseScale,
                    myLocation = myLocation,
                    partnerLocation = partnerLocation,
                    anniversaryDate = anniversaryDate
                )
                drawContext.canvas.nativeCanvas.restore()
            }
            
            // Draw Global Filters (like Polaroid) ONCE after faces
            if (filter == SparkleFilter.POLAROID) {
                drawGlobalFilter(
                    canvas = drawContext.canvas.nativeCanvas,
                    filter = filter,
                    myLocation = myLocation,
                    partnerLocation = partnerLocation,
                    width = size.width,
                    height = size.height
                )
            }
        }
    }
}

enum class SparkleFilter {
    NORMAL, HEART, SPARKLES, CAT_EARS, STAR_CROWN, POLAROID,
    MISS_YOU, ANNIVERSARY, MOOD_HAPPY, MOOD_SHY, MOOD_SILLY,
    SCRATCH, SECRET
}

fun drawFilterOnCanvas(
    canvas: android.graphics.Canvas,
    face: com.google.mlkit.vision.face.Face,
    filter: SparkleFilter,
    scale: Float,
    previewPulse: Float = 1.0f,
    myLocation: Pair<Double, Double>? = null,
    partnerLocation: Pair<Double, Double>? = null,
    anniversaryDate: Long? = null
) {
    val bounds = face.boundingBox
    val centerX = bounds.centerX().toFloat() * scale
    val centerY = bounds.centerY().toFloat() * scale
    val faceWidth = bounds.width().toFloat() * scale
    
    when (filter) {
        SparkleFilter.NORMAL -> { /* Do nothing - original photo */ }
        SparkleFilter.MISS_YOU -> {
            drawMissYouPulseEffect(canvas, face, scale, previewPulse)
        }
        SparkleFilter.ANNIVERSARY -> {
            drawAnniversaryCountdownEffect(canvas, face, scale, anniversaryDate)
        }
        SparkleFilter.MOOD_HAPPY, SparkleFilter.MOOD_SHY, SparkleFilter.MOOD_SILLY -> {
            drawMoodParticles(canvas, face, scale, filter)
        }
        SparkleFilter.POLAROID -> { /* Handled globally in drawGlobalFilter */ }
        SparkleFilter.HEART -> {
            val heartSize = faceWidth * 0.8f * previewPulse
            val path = android.graphics.Path()
            path.moveTo(centerX, centerY + (heartSize * 0.3f))
            path.cubicTo(
                centerX - heartSize, centerY - (heartSize * 0.5f),
                centerX - heartSize, centerY - heartSize,
                centerX, centerY - (heartSize * 0.4f)
            )
            path.cubicTo(
                centerX + heartSize, centerY - heartSize,
                centerX + heartSize, centerY - (heartSize * 0.5f),
                centerX, centerY + (heartSize * 0.3f)
            )
            
            val fillPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                style = android.graphics.Paint.Style.FILL
                alpha = 200
            }
            canvas.drawPath(path, fillPaint)
            
            val strokePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 8.0f * scale
                isAntiAlias = true
            }
            canvas.drawPath(path, strokePaint)
        }
        SparkleFilter.SPARKLES -> {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.YELLOW
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val starCount = 6
            for (i in 0 until starCount) {
                val angle = (i * 360 / starCount).toDouble()
                val radius = faceWidth * 0.7f * previewPulse
                val sx = centerX + (Math.cos(Math.toRadians(angle)) * radius).toFloat()
                val sy = centerY + (Math.sin(Math.toRadians(angle)) * radius).toFloat()
                drawStar(canvas, sx, sy, 15f * scale, paint)
            }
        }
        SparkleFilter.CAT_EARS -> {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            // Left Ear
            val leftEarPath = android.graphics.Path()
            leftEarPath.moveTo(centerX - faceWidth * 0.3f, centerY - faceWidth * 0.4f)
            leftEarPath.lineTo(centerX - faceWidth * 0.5f, centerY - faceWidth * 0.7f)
            leftEarPath.lineTo(centerX - faceWidth * 0.1f, centerY - faceWidth * 0.5f)
            canvas.drawPath(leftEarPath, paint)
            
            // Right Ear
            val rightEarPath = android.graphics.Path()
            rightEarPath.moveTo(centerX + faceWidth * 0.3f, centerY - faceWidth * 0.4f)
            rightEarPath.lineTo(centerX + faceWidth * 0.5f, centerY - faceWidth * 0.7f)
            rightEarPath.lineTo(centerX + faceWidth * 0.1f, centerY - faceWidth * 0.5f)
            canvas.drawPath(rightEarPath, paint)
        }
        SparkleFilter.STAR_CROWN -> {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(255, 215, 0) // Gold
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val crownRadius = faceWidth * 0.55f
            val starCount = 7
            for (i in 0 until starCount) {
                val angle = -180 + (i * 180 / (starCount - 1))
                val sx = centerX + (Math.cos(Math.toRadians(angle.toDouble())) * crownRadius).toFloat()
                val sy = centerY + (Math.sin(Math.toRadians(angle.toDouble())) * crownRadius * 0.5f).toFloat() - (faceWidth * 0.4f)
                drawStar(canvas, sx, sy, 20f * scale * previewPulse, paint)
            }
        }
        SparkleFilter.SCRATCH -> { /* Handled in SparkleRevealActivity on receiver side */ }
        SparkleFilter.SECRET -> { /* Handled in SparkleRevealActivity on receiver side */ }
    }
}

fun drawDistanceBadgeEffect(
    canvas: android.graphics.Canvas,
    face: com.google.mlkit.vision.face.Face,
    scale: Float,
    myLocation: Pair<Double, Double>?,
    partnerLocation: Pair<Double, Double>?
) {
    val bounds = face.boundingBox
    val centerX = bounds.centerX().toFloat() * scale
    val centerY = bounds.centerY().toFloat() * scale
    val faceWidth = bounds.width().toFloat() * scale
    
    // Calculate distance text
    val distanceText = if (myLocation != null && partnerLocation != null) {
        val dist = com.aman.gigi.utils.LocationUtils.calculateDistance(
            myLocation.first, myLocation.second,
            partnerLocation.first, partnerLocation.second
        )
        com.aman.gigi.utils.LocationUtils.formatDistance(dist)
    } else {
        "Connecting..."
    }

    // Draw Badge Background
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        alpha = 180
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 36f * scale
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    val padding = 20f * scale
    val textWidth = textPaint.measureText(distanceText)
    val rect = android.graphics.RectF(
        centerX - (textWidth / 2) - padding,
        centerY + (faceWidth * 0.6f),
        centerX + (textWidth / 2) + padding,
        centerY + (faceWidth * 0.6f) + (60f * scale)
    )
    
    canvas.drawRoundRect(rect, 30f * scale, 30f * scale, paint)
    canvas.drawText(distanceText, centerX, rect.bottom - (20f * scale), textPaint)
}

fun drawGlobalFilter(
    canvas: android.graphics.Canvas,
    filter: SparkleFilter,
    myLocation: Pair<Double, Double>?,
    partnerLocation: Pair<Double, Double>?,
    width: Float,
    height: Float
) {
    if (filter != SparkleFilter.POLAROID) return
    
    // Save state to avoid side effects
    canvas.save()
    
    // 1. Draw White Polaroid Frame using absolute Proportions
    val framePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    
    // Proportions tuned for perfect Polaroid aesthetic
    val sideBorder = width * 0.05f     // 5% of width
    val topBorder = width * 0.05f      // 5% of width 
    val bottomBorder = height * 0.30f  // 30% of height (Deep bottom for authenticity & UI clearance)
    
    // Frame borders
    canvas.drawRect(0f, 0f, sideBorder, height, framePaint) // Left
    canvas.drawRect(width - sideBorder, 0f, width, height, framePaint) // Right
    canvas.drawRect(0f, 0f, width, topBorder, framePaint) // Top
    canvas.drawRect(0f, height - bottomBorder, width, height, framePaint) // Bottom

    // 2. Draw Distance Text (Premium Look)
    // Position it proportionally within the bottom white border (top 20% of border area)
    // Formula: Top of Border + (Border Height * 0.20)
    // This ensures it clears the UI controls which are lower down
    val textY = (height - bottomBorder) + (bottomBorder * 0.20f)
    
    val distanceText = if (myLocation != null && partnerLocation != null) {
        val dist = com.aman.gigi.utils.LocationUtils.calculateDistance(
            myLocation.first, myLocation.second,
            partnerLocation.first, partnerLocation.second
        )
        com.aman.gigi.utils.LocationUtils.formatDistance(dist)
    } else {
        "Connecting..."
    }

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#333333") // Elegant Dark Grey
        textSize = width * 0.045f // 4.5% of width - Perfect legible size
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }

    canvas.drawText(distanceText, width / 2f, textY, textPaint)
    
    canvas.restore()
}

fun drawDistanceBadgeInFrame(
    canvas: android.graphics.Canvas,
    densityScale: Float,
    myLocation: Pair<Double, Double>?,
    partnerLocation: Pair<Double, Double>?,
    textY: Float
) {
    // Deprecated for Global Filter but kept for API safety
}

fun drawMissYouPulseEffect(
    canvas: android.graphics.Canvas,
    face: com.google.mlkit.vision.face.Face,
    scale: Float,
    pulse: Float
) {
    val bounds = face.boundingBox
    val centerX = bounds.centerX().toFloat() * scale
    val centerY = bounds.centerY().toFloat() * scale
    val faceWidth = bounds.width().toFloat() * scale
    
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.RED
        alpha = (255 * (1.0f - (pulse - 1.0f) * 2f)).toInt().coerceIn(0, 255)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 10f * scale
        isAntiAlias = true
    }

    // Multiple pulsing rings
    for (i in 1..3) {
        val ringPulse = (pulse + i * 0.2f) % 0.5f + 1.0f
        canvas.drawCircle(centerX, centerY, (faceWidth * 0.6f) * ringPulse, paint)
    }
    
    // Core heartbeat icon
    val path = android.graphics.Path()
    val heartSize = faceWidth * 0.2f * pulse
    path.moveTo(centerX, centerY + (heartSize / 4))
    path.lineTo(centerX - heartSize, centerY - heartSize)
    path.arcTo(centerX - heartSize, centerY - heartSize * 1.5f, centerX, centerY - heartSize / 2, -180f, 180f, false)
    path.arcTo(centerX, centerY - heartSize * 1.5f, centerX + heartSize, centerY - heartSize / 2, -180f, 180f, false)
    path.close()
    
    paint.style = android.graphics.Paint.Style.FILL
    paint.alpha = 200
    canvas.drawPath(path, paint)
}

fun drawAnniversaryCountdownEffect(
    canvas: android.graphics.Canvas,
    face: com.google.mlkit.vision.face.Face,
    scale: Float,
    anniversaryDate: Long?
) {
    val bounds = face.boundingBox
    val centerX = bounds.centerX().toFloat() * scale
    val centerY = bounds.centerY().toFloat() * scale
    val faceWidth = bounds.width().toFloat() * scale
    
    // Use synced anniversaryDate or fallback to demo (1 year from now)
    val targetDate = anniversaryDate ?: (System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
    
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = targetDate }
    
    // Adjust target to current or next year to make it recurring
    target.set(Calendar.YEAR, now.get(Calendar.YEAR))
    if (target.before(now)) {
        target.add(Calendar.YEAR, 1)
    }
    
    val diff = target.timeInMillis - now.timeInMillis
    val days = (diff / (24 * 60 * 60 * 1000))
    
    val text = when {
        days == 0L -> "Happy Anniversary! ❤️"
        days == 1L -> "Anniversary Tomorrow! 💍"
        else -> "$days days until Anniversary"
    }
    
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FFD700") // Gold
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 32f * scale
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    val padding = 25f * scale
    val textWidth = textPaint.measureText(text)
    val rect = android.graphics.RectF(
        centerX - (textWidth / 2) - padding,
        centerY - (faceWidth * 0.9f),
        centerX + (textWidth / 2) + padding,
        centerY - (faceWidth * 0.9f) + (60f * scale)
    )
    
    // Golden Badge
    canvas.drawRoundRect(rect, 15f * scale, 15f * scale, paint)
    
    // Text on Badge
    textPaint.color = android.graphics.Color.BLACK
    canvas.drawText(text, centerX, rect.bottom - (18f * scale), textPaint)
    
    // Royal Frame Corner accents
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 8f * scale
    val offset = 50f * scale
    canvas.drawArc(rect.left - offset, rect.top - offset, rect.left + offset, rect.top + offset, 180f, 90f, false, paint)
    canvas.drawArc(rect.right - offset, rect.top - offset, rect.right + offset, rect.top + offset, 270f, 90f, false, paint)
}

fun drawMoodParticles(
    canvas: android.graphics.Canvas,
    face: com.google.mlkit.vision.face.Face,
    scale: Float,
    filter: SparkleFilter
) {
    val bounds = face.boundingBox
    val centerX = bounds.centerX().toFloat() * scale
    val centerY = bounds.centerY().toFloat() * scale
    val faceWidth = bounds.width().toFloat() * scale
    
    val time = System.currentTimeMillis()
    val numParticles = 15
    
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
    }

    for (i in 0 until numParticles) {
        // Deterministic but time-varying positions
        val angle = (i.toFloat() / numParticles) * 2 * Math.PI + (time % 5000 / 5000.0) * 2 * Math.PI
        val spiral = (time % 3000 / 3000.0) * faceWidth * 0.5f
        val radius = faceWidth * 0.7f + spiral
        
        val px = (centerX + Math.cos(angle) * radius).toFloat()
        val py = (centerY + Math.sin(angle) * radius).toFloat()
        
        when (filter) {
            SparkleFilter.MOOD_HAPPY -> {
                paint.color = android.graphics.Color.YELLOW
                drawStar(canvas, px, py, 15f * scale, paint)
            }
            SparkleFilter.MOOD_SHY -> {
                paint.color = android.graphics.Color.parseColor("#FFC0CB") // Pink
                canvas.drawCircle(px, py, 12f * scale, paint)
                // Draw a small petal/blossom
                canvas.drawCircle(px + 10f * scale, py, 8f * scale, paint)
                canvas.drawCircle(px - 10f * scale, py, 8f * scale, paint)
                canvas.drawCircle(px, py + 10f * scale, 8f * scale, paint)
                canvas.drawCircle(px, py - 10f * scale, 8f * scale, paint)
            }
            SparkleFilter.MOOD_SILLY -> {
                paint.color = android.graphics.Color.CYAN
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 3f * scale
                canvas.drawCircle(px, py, (10f + (time % 1000 / 100f)) * scale, paint)
            }
            else -> {}
        }
    }
}

fun drawStar(canvas: android.graphics.Canvas, x: Float, y: Float, radius: Float, paint: android.graphics.Paint) {
    val path = android.graphics.Path()
    val outerRadius = radius
    val innerRadius = radius * 0.5f
    for (i in 0 until 10) {
        val angle = i * Math.PI / 5 - Math.PI / 2
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val px = x + (Math.cos(angle) * r).toFloat()
        val py = y + (Math.sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    canvas.drawPath(path, paint)
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        continuation.resume(cameraProviderFuture.get())
    }, ContextCompat.getMainExecutor(this))
}
