package com.aman.gigi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aman.gigi.data.nowplaying.NowPlaying

/** 120 BPM — a mid-tempo default that reads as "in time" for most pop. */
private const val DEFAULT_BPM = 120

/**
 * Two Twigis, bouncing, because you're both listening to the same thing.
 *
 * The beat is honest about its limits. Android's Visualizer can only tap our OWN audio
 * session, so there is no way to read amplitude out of Spotify's process, and Spotify's
 * tempo endpoint is both deprecated and behind their Premium wall. So the bounce runs
 * at [bpm] — the track's real tempo when a local file carries a BPM tag, and a pleasant
 * default otherwise. It is deliberately not sold as exact beat detection.
 *
 * The two phones are also not phase-locked; each animates locally. Sharing a beat
 * origin over the socket would tighten it, but network jitter means "close", never
 * frame-exact, and the moment reads fine without it.
 */
@Composable
fun SharedListeningTwigis(
    mine: NowPlaying?,
    theirs: NowPlaying?,
    partnerName: String,
    myTwigiUrl: String?,
    partnerTwigiUrl: String?,
    modifier: Modifier = Modifier,
    bpm: Int = DEFAULT_BPM,
    onDismiss: (() -> Unit)? = null
) {
    val isMinePlaying = mine != null && mine.isPlaying
    val isTheirsPlaying = theirs != null && theirs.isPlaying
    val bothPlaying = isMinePlaying && isTheirsPlaying
    val sameSong = bothPlaying && isSameTrack(mine, theirs)

    AnimatedVisibility(
        visible = isMinePlaying || isTheirsPlaying,
        enter = scaleIn(initialScale = 0.85f) + fadeIn(),
        exit = scaleOut(targetScale = 0.85f) + fadeOut(),
        modifier = modifier
    ) {
        val track = mine ?: theirs ?: return@AnimatedVisibility
        val partnerTrack = theirs
        val beatMs = (60_000 / bpm.coerceIn(40, 220))

        val transition = rememberInfiniteTransition(label = "headbang")
        // Half a beat each way, reversing, so a full cycle is one beat.
        val bob by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(beatMs / 2), RepeatMode.Reverse),
            label = "bob"
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xF2241C3A), Color(0xF2321F4A)))
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bothPlaying) {
                // Both partners listening: side-by-side headbanging
                Bouncer(myTwigiUrl, bob, "🙂")
                Spacer(Modifier.width(8.dp))
                Bouncer(partnerTwigiUrl, 1f - bob, "🙃")
            } else if (isMinePlaying) {
                // Solo user listening: solo Twigi headbanging
                Bouncer(myTwigiUrl, bob, "🙂")
            } else {
                // Partner listening solo
                Bouncer(partnerTwigiUrl, bob, "🙃")
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        sameSong -> "You and $partnerName are synced to the same track! ⚡"
                        bothPlaying -> "You and $partnerName are jamming together! 🎧"
                        isMinePlaying -> "Jamming to your vibe ✨"
                        else -> "$partnerName is listening to music 🎶"
                    },
                    color = Color(0xFFF3E8FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        sameSong -> track.label
                        bothPlaying && partnerTrack != null -> "${track.title} · ${partnerTrack.title}"
                        else -> track.label
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onDismiss != null) {
                Box(
                    Modifier
                        .padding(4.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onDismiss() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("🎧", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Bouncer(twigiUrl: String?, phase: Float, fallback: String) {
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer {
                // Synchronized BPM headbanging physics: vertical compression, lift & rhythm tilt
                translationY = -10f * phase
                rotationZ = -7f + 14f * phase
                scaleX = 1f + 0.04f * phase
                scaleY = 1f - 0.04f * phase
            },
        contentAlignment = Alignment.Center
    ) {
        if (twigiUrl.isNullOrBlank()) {
            Text(fallback, fontSize = 28.sp)
        } else {
            AsyncImage(
                model = twigiUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/**
 * Whether two people are on the same song.
 *
 * Compared on normalised title and artist rather than any track ID: the two sides may
 * be playing from different apps entirely — one on Spotify, one from their own library
 * — and there is no shared identifier across those.
 */
private fun isSameTrack(a: NowPlaying?, b: NowPlaying?): Boolean {
    if (a == null || b == null) return false
    if (!a.isPlaying || !b.isPlaying) return false
    return norm(a.title) == norm(b.title) && norm(a.artist) == norm(b.artist)
}

private fun norm(s: String) = s.lowercase()
    .replace(Regex("\\(.*?\\)|\\[.*?]"), "")   // "(Remastered 2011)", "[Official Video]"
    .replace(Regex("[^a-z0-9]"), "")
    .trim()
