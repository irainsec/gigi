package com.aman.gigi.ui.screensaver

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aman.gigi.service.PlaybackManager
import com.aman.gigi.service.PlaybackSource
import com.aman.gigi.ui.MusicScreen
import com.aman.gigi.ui.theme.RemindMeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LockscreenPlayerActivity : ComponentActivity() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    // Capture the timestamp of the last user screen interaction
    private var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis() - 10_000)

    private val _isAmbientMode = MutableStateFlow(false)
    val isAmbientMode = _isAmbientMode.asStateFlow()



    private var isLandscapeMode by mutableStateOf(true) // Default to landscape mode!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force landscape orientation by default on startup!
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Initial setup for lockscreen display
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // True fullscreen immersive experience hiding status and navigation bars
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        enableEdgeToEdge()

        // 1. Smart Screen Wake Management based on active music state
        lifecycleScope.launch {
            playbackManager.isPlaying.collect { playing ->
                runOnUiThread {
                    if (playing) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        // 1.5. Dynamic External Pause Dismissal with 300ms transition safety
        lifecycleScope.launch {
            playbackManager.isPlaying.collect { playing ->
                if (!playing) {
                    delay(300) // Debounce transient preparing states
                    if (!playbackManager.isPlaying.value && !playbackManager.isPreparing.value) {
                        val source = playbackManager.lastPauseSource.value
                        if (source == PlaybackSource.LOCKSCREEN) {
                            android.util.Log.i("LockscreenPlayer", "⏸️ Paused locally from lockscreen. Keeping lockscreen active.")
                        } else {
                            android.util.Log.i("LockscreenPlayer", "⏹️ Paused externally via source: $source. Dismissing lockscreen.")
                            runOnUiThread {
                                finish()
                            }
                        }
                    }
                }
            }
        }

        // 2. Continuous idle timer checking for 10-second AMOLED dim transition
        lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val idleDuration = now - lastInteractionTime
                val isPlaying = playbackManager.isPlaying.value

                // Dim ONLY if a track is actively playing and 10 seconds of inactivity has elapsed
                if (idleDuration >= 10_000 && isPlaying) {
                    if (!_isAmbientMode.value) {
                        setAmbientMode(true)
                    }
                } else {
                    if (_isAmbientMode.value) {
                        setAmbientMode(false)
                    }
                }
                delay(500)
            }
        }



        setContent {
            RemindMeTheme {
                val isAmbient by isAmbientMode.collectAsStateWithLifecycle()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black // Solid black background guarantees OLED/AMOLED pixels turn off entirely
                ) {
                    LockscreenPlayerContent(
                        isAmbientMode = isAmbient,
                        isLandscape = isLandscapeMode,
                        onToggleOrientation = {
                            isLandscapeMode = !isLandscapeMode
                            requestedOrientation = if (isLandscapeMode) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    // Intercept all system screen touches to reset the dimming timer instantly
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        lastInteractionTime = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    // Dynamically adjust hardware backlight and OLED pixel brightness
    private fun setAmbientMode(ambient: Boolean) {
        _isAmbientMode.value = ambient
        runOnUiThread {
            val lp = window.attributes
            lp.screenBrightness = if (ambient) 0.05f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }
}

@Composable
fun LockscreenPlayerContent(
    isAmbientMode: Boolean,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit,
    onDismiss: () -> Unit
) {
    // Intercept hardware/gesture back to exit keyguard securely
    BackHandler {
        onDismiss()
    }

    // Smooth transition between bright active mode and dimmed battery-saver mode
    val dimAlpha by animateFloatAsState(
        targetValue = if (isAmbientMode) 0.18f else 1f,
        animationSpec = tween(durationMillis = 800),
        label = "dimAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Embed the exact same rich music player within an animated graphics layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = dimAlpha
                }
        ) {
            MusicScreen(
                onBottomNavVisibilityChanged = {}
            )
        }
    }
}
