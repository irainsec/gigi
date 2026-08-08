package com.aman.gigi.ui.onboarding

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF3B2A6B)
private val Lavender = Color(0xFF8B5CF6)
private val Muted = Color(0xFF9A8FC0)

/**
 * The moment that actually decides whether Gigi works: getting the other person here.
 * Shown right after sign-up (and any time the galaxy is empty) instead of dropping a
 * brand-new user into a lonely galaxy with no idea what to do next.
 *
 * Auto-advances the instant someone joins — the caller stops rendering this once
 * a connection exists.
 */
@Composable
fun InvitePersonScreen(
    code: String,
    myName: String,
    /** Registers the code as a real connection so the invitee can actually join it. */
    onReserveCode: () -> Unit,
    onSkip: () -> Unit,
    onEnterCode: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val pulse = rememberInfiniteTransition(label = "await")
    val glow by pulse.animateFloat(
        initialValue = 0.9f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "g"
    )

    val pretty = if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF241B45), Color(0xFF352866), Color(0xFF4A3585))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // a lone sun waiting for its first planet
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(glow)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFFD27D).copy(alpha = 0.55f), Color.Transparent)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) { Text("🌞", fontSize = 54.sp) }

            Spacer(Modifier.height(18.dp))
            Text(
                "Now bring your person 💜",
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Gigi is only magic with someone in it. Send them your code — they'll orbit your galaxy the moment they join.",
                color = Color(0xFFB9A9E8), fontSize = 14.sp, textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(26.dp))

            // the code, tap to copy
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(code)); copied = true
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        pretty, color = Color.White, fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp
                    )
                    Text(
                        if (copied) "copied ✓" else "tap to copy",
                        color = if (copied) Color(0xFF6FCF97) else Muted, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    // The code IS the room id — it must exist on the server before we
                    // hand it out, otherwise the invitee joins an empty room.
                    onReserveCode()
                    val msg = buildString {
                        append(myName.ifBlank { "I" }.let { if (it == "I") "I'm" else "$it is" })
                        append(" on Gigi 💜 — a tiny private galaxy just for us.\n\n")
                        append("Tap to join me:\n")
                        append("https://gigi.iamanraj.com/join?code=$code\n\n")
                        append("(or type the code by hand: $pretty)")
                    }
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                },
                                "Invite them to Gigi"
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Lavender),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Send the invite ✨", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                onClick = onEnterCode,
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("They already sent me a code", color = Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(7.dp).scale(glow).clip(CircleShape)
                        .background(Color(0xFF6FCF97))
                )
                Spacer(Modifier.width(8.dp))
                Text("waiting for them to join…", color = Muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "I'll do this later",
                color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onSkip() }
            )
        }
    }
}
