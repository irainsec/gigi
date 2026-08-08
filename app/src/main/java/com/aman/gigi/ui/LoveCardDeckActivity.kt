package com.aman.gigi.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.gigi.model.LoveCardStackStatus
import com.aman.gigi.ui.theme.RemindMeTheme
import com.aman.gigi.viewmodel.ScreensaverViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoveCardDeckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        enableEdgeToEdge()
        cancelDeckNotifications(intent)

        setContent {
            RemindMeTheme {
                LoveCardDeckActivityScreen(
                    stackId = intent.getStringExtra(EXTRA_STACK_ID),
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        cancelDeckNotifications(intent)
    }

    private fun cancelDeckNotifications(intent: Intent) {
        val stackId = intent.getStringExtra(EXTRA_STACK_ID) ?: return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(("love_cards_received_" + stackId).hashCode())
        notificationManager?.cancel(("love_cards_answered_" + stackId).hashCode())
    }

    companion object {
        const val EXTRA_STACK_ID = "stack_id"
        const val EXTRA_CONNECTION_ID = "connection_id"
    }
}

@Composable
private fun LoveCardDeckActivityScreen(
    stackId: String?,
    onFinish: () -> Unit,
    viewModel: ScreensaverViewModel = hiltViewModel()
) {
    val decks by viewModel.loveCardDecks.collectAsStateWithLifecycle()
    
    // Diagnostic logging
    androidx.compose.runtime.LaunchedEffect(decks, stackId) {
        android.util.Log.d("LoveCardDeckActivity", "🔍 Activity State: stackId=$stackId, decksCount=${decks.size}")
        decks.forEach { d ->
            android.util.Log.d("LoveCardDeckActivity", "   └─ Deck in list: id=${d.stack.stackId}, title=${d.stack.title}")
        }
    }

    val deck = decks.firstOrNull { it.stack.stackId == stackId }
    
    androidx.compose.runtime.LaunchedEffect(deck) {
        if (deck != null) {
            android.util.Log.i("LoveCardDeckActivity", "✅ Found matching deck! Starting dialog.")
        } else if (decks.isNotEmpty()) {
            android.util.Log.w("LoveCardDeckActivity", "⚠️ Deck $stackId NOT found in list of ${decks.size} decks.")
        }
    }
    var openedSignalSent by rememberSaveable(stackId) { mutableStateOf(false) }

    LaunchedEffect(stackId) {
        if (stackId.isNullOrBlank()) {
            onFinish()
        } else {
            viewModel.showLoveCardDeck(stackId)
        }
    }

    LaunchedEffect(deck?.stack?.stackId, deck?.stack?.status) {
        val currentDeck = deck ?: return@LaunchedEffect
        if (
            currentDeck.stack.isIncoming &&
            currentDeck.stack.status == LoveCardStackStatus.SENT.name &&
            !openedSignalSent
        ) {
            openedSignalSent = true
            viewModel.openLoveCardDeck(
                connectionId = currentDeck.stack.connectionId,
                stackId = currentDeck.stack.stackId
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEFE8FF),
                            Color(0xFFE3DBFF),
                            Color(0xFFF6EEFF)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (deck == null) {
                CircularProgressIndicator(color = Color(0xFF6C3CF0))
            } else {
                LoveCardDeckDialog(
                    deck = deck,
                    onDismiss = {
                        viewModel.dismissLoveCardDeck(deck.stack.stackId)
                        onFinish()
                    },
                    onAnswer = { responses ->
                        viewModel.answerLoveCardDeck(
                            connectionId = deck.stack.connectionId,
                            stackId = deck.stack.stackId,
                            responses = responses
                        )
                        viewModel.dismissLoveCardDeck(deck.stack.stackId)
                        onFinish()
                    }
                )
            }
        }
    }
}
