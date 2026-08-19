package com.aman.gigi.ui.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.aman.gigi.data.spotify.SpotifyPlaylist
import com.aman.gigi.data.spotify.SpotifyTrack
import com.aman.gigi.viewmodel.SpotifyViewModel

private val SpotifyGreen = Color(0xFF1DB954)
private val Ink = Color(0xFF1E1638)
private val InkSoft = Color(0xFFC4B5FD)

/**
 * Your Spotify library, inside Gigi.
 *
 * Playback stays in Spotify — this browses and points. Tapping a track asks Spotify's
 * API to start it (so you never leave Gigi), and falls back to a deep link when there
 * is no awake device to target.
 */
@Composable
fun SpotifySheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpotifyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // The OAuth callback lands in MainActivity, so this screen only finds out the
    // handshake finished when it comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshConnection()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            state.openPlaylist?.let {
                Text(
                    "‹",
                    color = Color.White,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { viewModel.closePlaylist() }
                        .padding(horizontal = 10.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.openPlaylist?.name ?: "Spotify",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Spotify's terms require their content to be visibly attributed.
                Text(
                    if (state.connected) "Playing through Spotify" else "Not connected",
                    color = InkSoft,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onClose) { Text("Done", color = InkSoft) }
        }

        Spacer(Modifier.height(14.dp))

        when {
            !state.configured -> Notice(
                emoji = "🔧",
                title = "Spotify isn't set up yet",
                body = "No Spotify app is configured on the server. Add a client ID in the " +
                    "admin panel and this will light up — no app update needed."
            )

            !state.connected -> ConnectPrompt(
                onConnect = { viewModel.connect(context) }
            )

            state.premiumRequired -> Notice(
                emoji = "⭐",
                title = "Spotify wants Premium",
                body = "Spotify blocks its Web API for apps owned by a free account, so " +
                    "your playlists can't be read. Playing and controlling music from " +
                    "Gigi still works without it."
            )

            state.loading && state.playlists.isEmpty() && state.tracks.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }

            state.openPlaylist != null -> TrackList(
                tracks = state.tracks,
                onPlay = { viewModel.play(context, it) }
            )

            else -> PlaylistList(
                playlists = state.playlists,
                onOpen = viewModel::openPlaylist
            )
        }

        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFBBF24), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ConnectPrompt(onConnect: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎧", fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Connect Spotify",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Bring your playlists into Gigi. Music keeps playing in Spotify — " +
                "your vinyl just becomes the remote.",
            color = InkSoft,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text("Connect", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Notice(emoji: String, title: String, body: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            color = InkSoft,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun PlaylistList(playlists: List<SpotifyPlaylist>, onOpen: (SpotifyPlaylist) -> Unit) {
    if (playlists.isEmpty()) {
        Notice("📂", "No playlists yet", "Nothing saved on this Spotify account.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpen(playlist) }
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(playlist.imageUrl, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${playlist.trackCount} tracks", color = InkSoft, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackList(tracks: List<SpotifyTrack>, onPlay: (SpotifyTrack) -> Unit) {
    if (tracks.isEmpty()) {
        Notice("🎵", "Empty playlist", "There are no tracks in here.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tracks, key = { it.id }) { track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPlay(track) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(track.imageUrl, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title, color = Color.White, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist, color = InkSoft, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text("▶", color = SpotifyGreen, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Artwork(url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Text("🎵", fontSize = 16.sp)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
    }
}
