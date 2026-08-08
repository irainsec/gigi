package com.aman.gigi.model

import android.net.Uri

data class MusicAlbum(
    val albumId: Long,
    val title: String,
    val artist: String,
    val albumArtUri: Uri?,
    val tracks: List<LocalSong>,
    val isCustom: Boolean = false,
    val sharedByPartnerName: String? = null
) {
    val trackCount: Int
        get() = tracks.size
}
