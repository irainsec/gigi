package com.aman.gigi.data.nowplaying

import android.net.Uri
import com.aman.gigi.model.LocalSong

/**
 * Lets the existing vinyl player render a track that Gigi isn't playing.
 *
 * The whole music UI is typed to [LocalSong], and rewriting it to be source-agnostic
 * would mean touching thousands of lines of a screen that already works. Presenting an
 * external session AS a LocalSong gets the vinyl, the artwork, the spin and every skin
 * for free — the only thing that has to change is where the transport buttons send
 * their taps.
 *
 * [EXTERNAL_SONG_ID] is the marker. Anything holding a LocalSong can ask
 * [LocalSong.isExternal] before assuming it has a real file on disk, because this one
 * has no playable contentUri — the audio lives in Spotify's process, not ours.
 */
const val EXTERNAL_SONG_ID = -777_001L
const val EXTERNAL_ALBUM_ID = -777_002L

val LocalSong.isExternal: Boolean get() = id == EXTERNAL_SONG_ID

fun NowPlaying.toLocalSong(): LocalSong = LocalSong(
    id = EXTERNAL_SONG_ID,
    title = title,
    // The vinyl shows the album line under the title; naming the source app there is
    // more useful than a blank, and it keeps "you are driving Spotify" always visible.
    artist = artist.ifBlank { app },
    album = if (app.isBlank()) artist else app,
    albumId = EXTERNAL_ALBUM_ID,
    trackNumber = 0,
    durationMs = durationMs,
    // Uri.EMPTY rather than a fake path: nothing should ever try to open this, and an
    // empty Uri fails loudly at the point of misuse instead of silently half-working.
    contentUri = Uri.EMPTY,
    albumArtUri = artworkUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
)
