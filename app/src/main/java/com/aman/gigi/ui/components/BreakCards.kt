package com.aman.gigi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aman.gigi.model.BreakInvite
import com.aman.gigi.model.BreakResponse

/** A cute break someone can call out to their people. */
data class BreakKind(
    val id: String,
    val emoji: String,
    val title: String,
    val line: String,
    val c1: Color,
    val c2: Color
)

val BREAK_KINDS = listOf(
    BreakKind("tea", "🫖", "Tea break", "chai time, come na ☕", Color(0xFFF0A75A), Color(0xFFD97742)),
    BreakKind("coffee", "☕", "Coffee break", "need caffeine, urgently", Color(0xFFA9724F), Color(0xFF6F4630)),
    BreakKind("sutta", "🚬", "Sutta break", "5 min, terrace?", Color(0xFF8E9AAF), Color(0xFF5C6779)),
    BreakKind("walk", "🚶", "Walk break", "legs need a stretch", Color(0xFF6FCF97), Color(0xFF3C9E6B)),
    BreakKind("snack", "🍜", "Snack break", "i'm hungry, feed me", Color(0xFFF2994A), Color(0xFFCB6B24)),
    BreakKind("nap", "😴", "Nap break", "brain.exe stopped working", Color(0xFF9B8CE8), Color(0xFF6C5CC7)),
    BreakKind("music", "🎧", "Music break", "one song. promise.", Color(0xFFEB6FA8), Color(0xFFC33F7C)),
    BreakKind("vent", "🫂", "Talk break", "need you for a minute", Color(0xFF7FC8F8), Color(0xFF3E93CC)),
)

fun breakKindOf(id: String?): BreakKind =
    BREAK_KINDS.firstOrNull { it.id == id } ?: BREAK_KINDS.first()

/** Bottom sheet-ish picker: choose which break to call. */
@Composable
fun BreakPickerDialog(onPick: (BreakKind) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Call a break ✨", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF3B2A6B))
                Text("everyone gets it on their screen", fontSize = 12.sp, color = Color(0xFF9A8FC0))
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BREAK_KINDS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { k ->
                                Surface(
                                    onClick = { onPick(k) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .background(Brush.verticalGradient(listOf(k.c1, k.c2)),
                                                RoundedCornerShape(20.dp))
                                            .padding(vertical = 14.dp, horizontal = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(k.emoji, fontSize = 30.sp)
                                        Spacer(Modifier.height(6.dp))
                                        Text(k.title, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = Color.White, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Maybe later", color = Color(0xFF9A8FC0), fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onDismiss() })
            }
        }
    }
}

/**
 * Full-screen invite that lands on everyone's phone (like a scribble). The caller sees
 * the same card with the live tally; invitees get Accept / Reject.
 */
@Composable
fun BreakInviteOverlay(
    invite: BreakInvite,
    responses: List<BreakResponse>,
    isMine: Boolean,
    myResponse: Boolean?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    val k = breakKindOf(invite.cardId)
    val pulse = rememberInfiniteTransition(label = "break-pulse")
    val s by pulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "s"
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(30.dp), color = Color.White) {
            Column(
                modifier = Modifier.width(320.dp).padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // hero
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(k.c1, k.c2)))
                        .padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(k.emoji, fontSize = 62.sp, modifier = Modifier.scale(s))
                    Spacer(Modifier.height(8.dp))
                    Text(k.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        if (isMine) "you called it — “${k.line}”" else "${invite.fromName} says “${k.line}”",
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (responses.isEmpty()) {
                    Text("waiting for everyone… ⏳", fontSize = 12.sp, color = Color(0xFF9A8FC0))
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        items(responses, key = { it.deviceId + it.name }) { r ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier.size(42.dp).clip(CircleShape)
                                        .background(if (r.accepted) Color(0xFFDFF6E7) else Color(0xFFFBE3E3)),
                                    contentAlignment = Alignment.Center
                                ) { Text(if (r.accepted) "✅" else "❌", fontSize = 18.sp) }
                                Spacer(Modifier.height(3.dp))
                                Text(r.name.take(8), fontSize = 10.sp, color = Color(0xFF9A8FC0),
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                when {
                    isMine || myResponse != null -> {
                        val label = when {
                            isMine -> "Close"
                            myResponse == true -> "You're in ✅ · Close"
                            else -> "You passed ❌ · Close"
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0EBFF),
                                contentColor = Color(0xFF6D4FD0)),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(46.dp)
                        ) { Text(label, fontWeight = FontWeight.Bold) }
                    }
                    else -> Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = onReject, shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFF6F1FF), border = BorderStroke(1.dp, Color(0xFFE3DAF7)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Can't 😔", fontWeight = FontWeight.Bold, color = Color(0xFF9A8FC0))
                            }
                        }
                        Surface(
                            onClick = onAccept, shape = RoundedCornerShape(999.dp),
                            color = k.c2,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("I'm in! 🙌", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
