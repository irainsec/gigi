package com.aman.gigi.ui.screensaver.components

import android.os.Build as AndroidOSBuild
import android.util.Base64
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import androidx.hilt.navigation.compose.hiltViewModel
import com.aman.gigi.data.sync.ScribbleSerializer
import java.net.URI
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private fun sanitizeMediaValue(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

private fun buildServerMediaUrl(assetPath: String): String {
    val wsUri = URI(com.aman.gigi.BuildConfig.SERVER_URL)
    val scheme = if (wsUri.scheme.equals("wss", ignoreCase = true)) "https" else "http"
    val cleanPath = assetPath.replace("\\", "/")
        .trimStart('/')
        .removePrefix("app/")
        .removePrefix("captures/")
        .trimStart('/')
    return URI(
        scheme,
        wsUri.userInfo,
        wsUri.host,
        if (wsUri.port == -1) -1 else wsUri.port,
        "/captures/$cleanPath",
        null,
        null
    ).toString()
}

private fun resolveMediaModel(mediaUrl: String?, mediaBase64: String?): Any? {
    val sanitizedBase64 = sanitizeMediaValue(mediaBase64)
    if (!sanitizedBase64.isNullOrBlank()) {
        if (sanitizedBase64.startsWith("http://", ignoreCase = true) ||
            sanitizedBase64.startsWith("https://", ignoreCase = true) ||
            sanitizedBase64.startsWith("content://", ignoreCase = true) ||
            sanitizedBase64.startsWith("file://", ignoreCase = true)) {
            return sanitizedBase64
        }
        if ((sanitizedBase64.startsWith("/data/") || sanitizedBase64.startsWith("/storage/") || sanitizedBase64.startsWith("/sdcard/")) &&
            java.io.File(sanitizedBase64).exists()) {
            return java.io.File(sanitizedBase64)
        }
        val cleanBase64 = if (sanitizedBase64.contains(",")) sanitizedBase64.substringAfter(",") else sanitizedBase64
        val bytes = runCatching {
            Base64.decode(cleanBase64.replace("\n", "").replace("\r", "").trim(), Base64.DEFAULT)
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) {
            return bytes
        }
    }

    val sanitizedUrl = sanitizeMediaValue(mediaUrl) ?: return null
    // If it's a raw binary strokes capture (.bin), don't pass to image loader
    if (sanitizedUrl.endsWith(".bin", ignoreCase = true)) return null

    // Check if sanitizedUrl is actually Base64 data (e.g. data:image/ or starts with /9j/ or long base64 string)
    if (sanitizedUrl.startsWith("data:image/") || sanitizedUrl.startsWith("/9j/") ||
        (sanitizedUrl.length > 500 && !sanitizedUrl.contains("://") && !sanitizedUrl.contains(" "))) {
        val cleanBase64 = if (sanitizedUrl.contains(",")) sanitizedUrl.substringAfter(",") else sanitizedUrl
        val bytes = runCatching {
            Base64.decode(cleanBase64.replace("\n", "").replace("\r", "").trim(), Base64.DEFAULT)
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) {
            return bytes
        }
    }

    return when {
        sanitizedUrl.startsWith("http://", ignoreCase = true) || sanitizedUrl.startsWith("https://", ignoreCase = true) -> {
            sanitizedUrl.replace("/app/captures/", "/captures/").replace("/captures/captures/", "/captures/")
        }
        sanitizedUrl.startsWith("content://", ignoreCase = true) || sanitizedUrl.startsWith("file://", ignoreCase = true) -> sanitizedUrl
        (sanitizedUrl.startsWith("/data/") || sanitizedUrl.startsWith("/storage/") || sanitizedUrl.startsWith("/sdcard/")) && java.io.File(sanitizedUrl).exists() -> java.io.File(sanitizedUrl)
        sanitizedUrl.startsWith("binary://", ignoreCase = true) -> null
        else -> buildServerMediaUrl(sanitizedUrl)
    }
}

@Composable
fun ScribblePlaybackComponent(
    scribbleJson: String,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: com.aman.gigi.viewmodel.ScreensaverViewModel = hiltViewModel()
) {
    val scribble = remember(scribbleJson) { ScribbleSerializer.deserialize(scribbleJson) }
    
    if (scribble == null) {
        onAnimationFinished()
        return
    }

    // Handle GIF/Media Rendering
    if (!sanitizeMediaValue(scribble.mediaUrl).isNullOrBlank() || !sanitizeMediaValue(scribble.mediaBase64).isNullOrBlank()) {
        Log.d("ScribblePlayer", "Rendering media: url=${scribble.mediaUrl != null}, base64=${scribble.mediaBase64?.length ?: 0}")
        val context = LocalContext.current
        
        // Re-introduce localized loader with DNS fallback and high timeouts
        val imageLoader = remember {
            ImageLoader.Builder(context)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .dns(object : okhttp3.Dns {
                            override fun lookup(hostname: String): List<java.net.InetAddress> {
                                return try {
                                    okhttp3.Dns.SYSTEM.lookup(hostname)
                                } catch (e: Exception) {
                                    // Fallback for Giphy DNS issues in emulator/restricted networks
                                    if (hostname.contains("giphy.com")) {
                                        Log.w("ScribblePlayer", "DNS: System failed for $hostname, trying hardcoded fallback")
                                        listOf(java.net.InetAddress.getByName("151.101.1.181")) 
                                    } else {
                                        throw e
                                    }
                                }
                            }
                        })
                        .hostnameVerifier { hostname, session ->
                            val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                            if (defaultVerifier.verify(hostname, session)) {
                                true
                            } else {
                                // Fallback: Allow Giphy to pass if it's using the Fastly certificate 
                                // that we reach via our hardcoded IP fallback
                                val isGiphy = hostname.contains("giphy.com")
                                val certMatch = session.peerPrincipal?.name?.contains("fastly.net") == true
                                if (isGiphy && certMatch) {
                                    Log.w("ScribblePlayer", "SSL Bypass: Allowing Giphy via Fastly cert for $hostname")
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        .build()
                }
                .components {
                    if (AndroidOSBuild.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .crossfade(true)
                .build()
        }

        var errorMessage by remember { mutableStateOf<String?>(null) }
        // Use partner presence from VM instead of a local ping to google.com
        val presenceStatus by viewModel.partnerDisconnected.collectAsState()
        val isOnline = presenceStatus == null
        var isLoadingMedia by remember { mutableStateOf(true) }
        var retryCount by remember { mutableIntStateOf(0) }

        val model = remember(scribble.mediaUrl, scribble.mediaBase64, retryCount) {
            runCatching {
                resolveMediaModel(scribble.mediaUrl, scribble.mediaBase64)
            }.onFailure { error ->
                Log.e("ScribblePlayer", "Failed to resolve media model", error)
            }.getOrNull()
        }

        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onLoading = { 
                        isLoadingMedia = true
                        errorMessage = null
                        Log.d("ScribblePlayer", "Coil: Loading started for $model") 
                    },
                    onSuccess = { 
                        isLoadingMedia = false
                        errorMessage = null
                        Log.d("ScribblePlayer", "Coil: Loading success") 
                    },
                    onError = { error -> 
                        isLoadingMedia = false
                        val throwable = error.result.throwable
                        val msg = throwable.message ?: "Unknown Error"
                        Log.e("ScribblePlayer", "Coil: Loading failed", throwable)
                        errorMessage = when {
                            msg.contains("resolve host", ignoreCase = true) || msg.contains("UnknownHostException", ignoreCase = true) ->
                                "Network issue: Unable to reach media server."
                            msg.contains("Unable to create a fetcher", ignoreCase = true) ->
                                "Unable to display media."
                            msg.length > 80 ->
                                "Unable to load drawing media."
                            else -> msg
                        }
                    }
                )
            }
            
            if (isLoadingMedia && errorMessage == null) {
                CircularProgressIndicator(color = Color(0xFF8B5CF6))
            }

            if (errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Text("⚠️ Playback Issue", color = Color.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!, 
                        color = Color.DarkGray, 
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    if (!isOnline) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Status: Device is OFFLINE", 
                            color = Color(0xFFD32F2F), 
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val modelStr = model as? String
                    if (modelStr != null && modelStr.length < 50 && !modelStr.startsWith("data:") && !modelStr.startsWith("/9j/")) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "From: $modelStr", 
                            color = Color.LightGray, 
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            retryCount++
                            errorMessage = null
                            isLoadingMedia = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try Again", color = Color.White)
                    }
                }
            }
            
            if (model == null && errorMessage == null) {
                Text("Invalid Media Data", color = Color.Red)
            }
        }
        
        // GIFs don't have a drawing phase, so signal finished after a short delay to allow rendering start
        LaunchedEffect(Unit) {
            delay(1000) // Give it a second to show up
            onAnimationFinished()
        }
        return
    }

    // Drawing Animation logic
    var currentStrokeIndex by remember(scribble) { mutableIntStateOf(0) }
    var currentPointIndex by remember(scribble) { mutableIntStateOf(0) }
    
    LaunchedEffect(scribble) {
        for (strokeIndex in scribble.strokes.indices) {
            currentStrokeIndex = strokeIndex
            val stroke = scribble.strokes[strokeIndex]
            for (pointIndex in stroke.points.indices) {
                currentPointIndex = pointIndex
                delay(10) 
            }
            delay(100)
        }
        onAnimationFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        for (i in 0..currentStrokeIndex) {
            val stroke = scribble.strokes.getOrNull(i) ?: continue
            val points = stroke.points
            if (points.isEmpty()) continue

            // Determine if this is the currently animating stroke
            val isAnimating = (i == currentStrokeIndex)
            
            // If animating, we might want to slice the points for "drawPath", 
            // BUT ScribbleEngine expects a full Stroke object.
            // To support partial animation of complex brushes, we create a temporary "partial stroke"
            val limit = if (isAnimating) currentPointIndex else points.size - 1
            
            // Only draw if we have enough points
            if (limit >= 0) {
                 val visiblePoints = points.take(limit + 1)
                 if (visiblePoints.isNotEmpty()) {
                     val partialStroke = stroke.copy(points = visiblePoints)
                     with(com.aman.gigi.engine.ScribbleEngine) {
                         drawStroke(partialStroke, canvasWidth, canvasHeight)
                     }
                 }
            }
        }
    }
    
}
