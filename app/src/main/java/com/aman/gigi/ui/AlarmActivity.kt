package com.aman.gigi.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.app.KeyguardManager
import android.content.Intent
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.aman.gigi.data.sync.ScribbleSyncManager
import com.aman.gigi.repository.ConnectionRepository
import com.aman.gigi.ui.CardSoundEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aman.gigi.alarm.AlarmReceiver
import com.aman.gigi.model.Reminder
import com.aman.gigi.ui.theme.RemindMeTheme
import com.aman.gigi.utils.Constants
import com.aman.gigi.utils.Utils
import com.skydoves.cloudy.Cloudy
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: ScribbleSyncManager

    @Inject
    lateinit var connectionRepository: ConnectionRepository

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        }

//        val reminder = Reminder(0, "Title", "Description", System.currentTimeMillis()) // debug
        val reminder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra<Reminder>(
                Constants.REMINDER_ITEM_KEY, Reminder::class.java
            )
        } else {
            intent.getParcelableExtra<Reminder>(Constants.REMINDER_ITEM_KEY)
        }

        if (reminder == null) {
            finish()
            return
        }

        var ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        if (ringtoneUri == null) {
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
        }

        ringtone = runCatching {
            RingtoneManager.getRingtone(applicationContext, ringtoneUri)?.also { tone ->
                tone.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    tone.isLooping = true
                }
                tone.play()
            }
        }.onFailure {
            Log.w("AlarmActivity", "Unable to start ringtone for alarm UI", it)
        }.getOrNull()

        setContent {
            RemindMeTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { innerPadding ->
                    AlarmScreen(
                        modifier = Modifier.padding(innerPadding),
                        reminder = reminder,
                        onDismiss = {
                            stopRingtoneSafely()
                            startService(Intent(this, com.aman.gigi.service.AlarmForegroundService::class.java).apply {
                                action = com.aman.gigi.service.AlarmForegroundService.ACTION_STOP_ALARM
                            })
                            finish()
                        },
                        onSnooze = {
                            stopRingtoneSafely()
                            startService(Intent(this, com.aman.gigi.service.AlarmForegroundService::class.java).apply {
                                action = com.aman.gigi.service.AlarmForegroundService.ACTION_STOP_ALARM
                            })
                            // Re-schedule alarm for 10 minutes from now
                            scheduleSnooze(reminder)
                            finish()
                        },
                        onDoneTogether = {
                            stopRingtoneSafely()
                            startService(Intent(this, com.aman.gigi.service.AlarmForegroundService::class.java).apply {
                                action = com.aman.gigi.service.AlarmForegroundService.ACTION_STOP_ALARM
                            })
                            
                            // Initialize sound engine and play success jingle
                            CardSoundEngine.init(this)
                            CardSoundEngine.playReveal()
                            
                            // Send "Did it together" remote command
                            lifecycleScope.launch {
                                val activeConnections = connectionRepository.getAllActiveConnectionsOnce()
                                activeConnections.forEach { conn ->
                                    val data = org.json.JSONObject().apply {
                                        put("text", "Done Together: ${reminder.title}")
                                        put("alarmTitle", reminder.title)
                                    }
                                    syncManager.sendRemoteCommandWithData(
                                        connectionId = conn.connectionId,
                                        command = "COMMAND_ALARM_DONE_TOGETHER",
                                        data = data
                                    )
                                }
                                
                                // Give it a little time to play the sound and enqueue the network request
                                delay(1000)
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun scheduleSnooze(reminder: Reminder) {
        val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(Constants.REMINDER_ITEM_KEY, reminder)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder._id.toInt() + 90_000,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
        } else {
            alarmManager?.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneSafely()
    }

    private fun stopRingtoneSafely() {
        runCatching {
            ringtone?.takeIf { it.isPlaying }?.stop()
        }.onFailure {
            Log.w("AlarmActivity", "Unable to stop ringtone cleanly", it)
        }
    }
}

@Composable
fun AlarmScreen(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onDoneTogether: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = false
    val dateTime = LocalDateTime
        .ofInstant(
            Instant.ofEpochMilli(reminder.dueDate),
            ZoneId.systemDefault()
        )
    val formattedTime = Utils.formatTime(LocalContext.current, dateTime.toLocalTime())
    val formattedDate = Utils.formatDate(LocalContext.current, dateTime.toLocalDate())

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(key1 = true) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A0828),
                            Color(0xFF2D1060),
                            Color(0xFF0D1A40),
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF99CC),
                            Color(0xFF9966FF),
                            Color(0xFF66CCFF),
                        )
                    )
                }
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            AlarmIcon()

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Elite Glass Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
            ) {
                Cloudy(radius = 25) {
                    Box(modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0.1f)))
                }
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = if (isDark) Color(0xFF1A1A2E).copy(alpha = 0.92f) else Color(0xFFE6E0FF).copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface, // Dark text on Lilac
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        reminder.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Text(
                text = "Alarm active for ${formatSeconds(seconds)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Done Together button — bright accent
                Button(
                    onClick = onDoneTogether,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B9E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Done Together ♥",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Snooze button
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.3f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Snooze 10 min", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    // Dismiss button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF2A1A4E) else Color.White,
                            contentColor = Color(0xFF8B5CF6)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AlarmIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "alarmPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.25f, animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                color = Color.White.copy(alpha = 0.25f),
            )
            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "Alarm",
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
