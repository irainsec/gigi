package com.aman.gigi.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.repository.LivePost

private val Ink = Color(0xFF15121F)
private val Card1 = Color(0xFF221C33)
private val Lav = Color(0xFFB9A6FF)
private val Mint = Color(0xFF8FE3C6)
private val Peach = Color(0xFFFFB4A2)

/**
 * Everything you've ever posted to Live, with a way to delete it.
 *
 * The status line matters more than it looks: a post that quietly stopped appearing is
 * indistinguishable from a bug unless you can see it says "expired".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveHistorySheet(
    history: List<LivePost>,
    loading: Boolean,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var confirming by remember { mutableStateOf<LivePost?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card1) {
        Column(Modifier.padding(20.dp, 0.dp, 20.dp, 28.dp)) {
            Text(
                "Your Live history",
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "everything you've posted, newest first",
                color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))

            when {
                loading && history.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(160.dp), Alignment.Center
                ) { CircularProgressIndicator(color = Lav) }

                history.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🕊️", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Nothing yet",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Posts you create will collect here.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history, key = { it.postId }) { post ->
                        HistoryRow(post) { confirming = post }
                    }
                }
            }
        }
    }

    confirming?.let { post ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            containerColor = Card1,
            title = { Text("Delete this post?", color = Color.White) },
            text = {
                Text(
                    "\"${post.text.take(60)}\" will be removed for everyone, along with " +
                        "its join requests and location history. This can't be undone.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(post.postId); confirming = null }) {
                    Text("Delete", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text("Keep", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(post: LivePost, onDelete: () -> Unit) {
    val vibe = vibeOf(post.category)
    val live = post.endedReason == null
    val statusText = when (post.endedReason) {
        "done" -> "wrapped up"
        "expired" -> "expired"
        "cancelled" -> "cancelled"
        else -> timeLeftLabel(post.expiresAt) ?: "live now"
    }
    val statusColour = if (live) Mint else Color.White.copy(alpha = 0.4f)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(vibe.tint.copy(alpha = if (live) 0.30f else 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(vibe.emoji, fontSize = 17.sp) }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                post.text.ifBlank { "(no text)" },
                color = Color.White.copy(alpha = if (live) 0.95f else 0.6f),
                fontSize = 14.sp, maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape).background(Mint)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    buildString {
                        append(statusText)
                        append(" · ")
                        append(radiusLabel(post.radiusM))
                        if (post.acceptedCount > 0) append(" · ${post.acceptedCount} joined")
                    },
                    color = statusColour, fontSize = 11.sp
                )
            }
        }

        Text(
            "🗑️",
            fontSize = 16.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onDelete() }
                .padding(8.dp)
        )
    }
}
