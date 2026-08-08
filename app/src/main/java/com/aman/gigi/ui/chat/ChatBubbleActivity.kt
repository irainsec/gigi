package com.aman.gigi.ui.chat

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.aman.gigi.ui.theme.RemindMeTheme
import com.aman.gigi.viewmodel.ScreensaverViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Chat launched from OUTSIDE the Compose tree (push notifications, the lock screen, the
 * floating chat head). Over the keyguard it shows a small BUBBLE; everywhere else it
 * opens the real ChatScreen, wired to the shared repositories via a Hilt
 * ScreensaverViewModel (the socket lives in singleton managers, so this second VM
 * instance is just an observer — no double-connect).
 */
@AndroidEntryPoint
class ChatBubbleActivity : ComponentActivity() {
    private val chatVm: ScreensaverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        // Accept both extra-key conventions used across callers.
        val connectionId = intent.getStringExtra("connection_id")
            ?: intent.getStringExtra("CONNECTION_ID") ?: ""
        val senderName = intent.getStringExtra("sender_name")
            ?: intent.getStringExtra("SENDER_NAME") ?: "Chat"
        val startCompact = intent.getBooleanExtra("compact", false)

        setContent {
            RemindMeTheme {
                var compact by remember { mutableStateOf(startCompact) }
                if (connectionId.isBlank()) {
                    finish()
                } else if (compact) {
                    LockBubble(
                        senderName = senderName,
                        emojiUrl = intent.getStringExtra("emoji_url").orEmpty(),
                        onOpen = { compact = false },
                        onDismiss = { finish() }
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ChatScreen(
                            connectionId = connectionId,
                            title = senderName,
                            relationshipType = "ROMANTIC",
                            onClose = { finish() },
                            viewModel = chatVm
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lock-screen chat head — visually the same floating bubble as the in-app overlay (an
 * overlay window can't draw over the keyguard, so we mimic it with a translucent
 * activity). Shows the sender's animated emoji / Twigi with an unread dot; tap it to
 * open the conversation, tap anywhere else to dismiss. The message text stays private
 * until you choose to read it.
 */
@Composable
private fun LockBubble(
    senderName: String,
    emojiUrl: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(context).components {
            if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }
    // gentle pulse so it reads as "new message" on a dark lock screen
    val pulse = rememberInfiniteTransition(label = "bubble")
    val scale by pulse.animateFloat(
        initialValue = 0.96f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse), label = "s"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 18.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(scale)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, Color(0xFF8B5CF6), CircleShape)
                        .clickable(onClick = onOpen),
                    contentAlignment = Alignment.Center
                ) {
                    if (emojiUrl.isNotBlank()) {
                        AsyncImage(
                            model = emojiUrl, imageLoader = loader, contentDescription = null,
                            modifier = Modifier.size(52.dp)
                        )
                    } else {
                        Text(
                            senderName.trim().take(1).uppercase().ifBlank { "M" },
                            color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold, fontSize = 26.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFEC4899)),
                    contentAlignment = Alignment.Center
                ) { Text("1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.padding(top = 6.dp))
            Surface(shape = RoundedCornerShape(999.dp), color = Color.Black.copy(alpha = 0.55f)) {
                Text(
                    senderName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}
