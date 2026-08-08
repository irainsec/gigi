package com.aman.gigi.ui.breaks

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aman.gigi.model.BreakInvite
import com.aman.gigi.ui.components.BreakInviteOverlay
import com.aman.gigi.ui.theme.RemindMeTheme
import com.aman.gigi.viewmodel.ScreensaverViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen "someone called a break" card — fires over the lock screen and over other
 * apps, exactly like an incoming scribble, so a tea/sutta shout-out actually reaches
 * people instead of waiting for them to open Gigi. Accept / Reject broadcast straight
 * back to the group, and the live tally stays on screen.
 */
@AndroidEntryPoint
class BreakInviteActivity : ComponentActivity() {
    private val vm: ScreensaverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val breakId = intent.getStringExtra("break_id").orEmpty()
        val cardId = intent.getStringExtra("card_id").orEmpty().ifBlank { "tea" }
        val fromName = intent.getStringExtra("from_name").orEmpty().ifBlank { "Someone" }
        val connectionId = intent.getStringExtra("connection_id").orEmpty()

        setContent {
            RemindMeTheme {
                // Prefer the live invite from the sync layer (it carries responses);
                // fall back to the intent extras if this launched from a cold start.
                val live by vm.activeBreak.collectAsState()
                val responses by vm.breakResponses.collectAsState()
                val myAnswer by vm.myBreakAnswer.collectAsState()

                val invite = live ?: BreakInvite(
                    breakId = breakId, connectionId = connectionId, cardId = cardId,
                    fromName = fromName, fromDeviceId = "", isMine = false
                )
                BreakInviteOverlay(
                    invite = invite,
                    responses = responses,
                    isMine = invite.isMine,
                    myResponse = myAnswer,
                    onAccept = { vm.answerBreak(true) },
                    onReject = { vm.answerBreak(false) },
                    onDismiss = { vm.dismissBreak(); finish() }
                )
            }
        }
    }
}
