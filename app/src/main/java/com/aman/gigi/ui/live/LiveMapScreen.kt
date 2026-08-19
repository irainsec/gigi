package com.aman.gigi.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.gigi.service.LiveTrackingService

private val Ink = Color(0xFF15121F)
private val Card1 = Color(0xFF221C33)
private val Lav = Color(0xFFB9A6FF)
private val Peach = Color(0xFFFFB4A2)

/**
 * The live meet-up map. Only reachable for posts we host or were accepted into —
 * everyone else never gets precise coordinates in the first place.
 */
@Composable
fun LiveMapScreen(
    postId: String,
    viewModel: LiveViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val post = remember(state.posts, postId) { state.posts.firstOrNull { it.postId == postId } }

    // Sharing starts when this screen opens and stops when it closes or the meet-up ends.
    DisposableEffect(postId) {
        viewModel.watch(postId)
        LiveTrackingService.start(context, postId, post?.authorName ?: "your meet-up")
        onDispose {
            viewModel.watch(null)
            // Deliberately NOT stopping the service here. People walk to a meet-up with
            // the phone in their pocket; if sharing ended when the screen closed, every
            // pin would freeze the moment someone stopped staring at the map. The
            // foreground notification keeps it visible and one tap stops it, and the
            // server ends it on Done / leave / expiry via `stop: true`.
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        // Everyone who's in this meet-up. A live position always wins over the static
        // post pin, so once someone starts walking their avatar moves with them.
        val pins = remember(post, state.presence) {
            buildList {
                val live = state.presence
                post?.let { pt ->
                    val hostLive = live[pt.authorMemberId]
                    val hostAvatar = pt.participants.firstOrNull { it.isHost }?.avatarUrl
                        ?: pt.authorAvatarUrl
                    val lat = hostLive?.lat ?: pt.lat
                    val lng = hostLive?.lng ?: pt.lng
                    if (lat != null && lng != null) {
                        add(
                            MapPin(
                                id = pt.authorMemberId,
                                lat = lat, lng = lng,
                                label = if (pt.isMine) "You" else pt.authorName,
                                color = Peach, isSelf = pt.isMine,
                                avatarUrl = hostLive?.avatarUrl ?: hostAvatar,
                                isHost = true
                            )
                        )
                    }
                    // Accepted joiners who haven't pinged yet still deserve a face —
                    // they just don't have a position, so they're only shown once they do.
                    pt.participants.filterNot { it.isHost }.forEach { part ->
                        val at = live[part.memberId] ?: return@forEach
                        add(
                            MapPin(
                                id = part.memberId,
                                lat = at.lat, lng = at.lng,
                                label = part.name, color = Lav,
                                isSelf = false,
                                avatarUrl = at.avatarUrl ?: part.avatarUrl
                            )
                        )
                    }
                }
                // Anyone streaming who isn't in the roster yet (roster arrives on the
                // next feed refresh) — better to show them than to drop them.
                val known = buildSet {
                    post?.let { add(it.authorMemberId) }
                    post?.participants?.forEach { add(it.memberId) }
                }
                state.presence.values
                    .filterNot { known.contains(it.memberId) }
                    .forEach {
                        add(
                            MapPin(
                                id = it.memberId, lat = it.lat, lng = it.lng,
                                label = it.name, color = Lav, avatarUrl = it.avatarUrl
                            )
                        )
                    }
            }
        }
        val focus = post?.takeIf { it.lat != null && it.lng != null }
        OsmMapView(
            pins = pins,
            fallbackLat = focus?.lat ?: state.myLat,
            fallbackLng = focus?.lng ?: state.myLng,
            modifier = Modifier.fillMaxSize()
        )

        // top bar
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Card1),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }
        }

        // bottom sheet-ish info card
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp, 0.dp, 16.dp, 110.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Card1)
                .padding(18.dp)
        ) {
            Text(
                post?.text ?: "Meet-up",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    val n = pins.size
                    append(if (n <= 1) "Just you so far" else "$n here right now")
                    if (post?.isFull == true) append(" · full ✨")
                },
                color = Lav, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Sharing your live location until this ends — you can close the app " +
                    "and keep walking. Stop any time from the notification.",
                color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (post?.isMine == true) {
                    Button(
                        onClick = {
                            viewModel.markDone(postId)
                            LiveTrackingService.stop(context)
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Peach, contentColor = Ink),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                    ) { Text("We're done") }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.leave(postId)
                            LiveTrackingService.stop(context)
                            onBack()
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                    ) { Text("Leave", color = Color.White.copy(alpha = 0.7f)) }
                }
            }
        }
    }
}
