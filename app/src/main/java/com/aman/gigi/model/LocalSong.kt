package com.aman.gigi.model

import android.net.Uri

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val trackNumber: Int,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri? = null
)
