package com.aman.gigi.utils

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.aman.gigi.model.LocalSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicLibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "MusicLibraryScanner"

    suspend fun scanSongs(): List<LocalSong> = withContext(Dispatchers.IO) {
        runCatching {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.TRACK
            )

            val selection = buildString {
                append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
                append(" AND ${MediaStore.Audio.Media.DURATION} > 0")
            }

            val songs = mutableListOf<LocalSong>()

            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val trackNumberColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

                if (idColumn == -1 || durationColumn == -1) {
                    Log.w(tag, "Device music provider did not return required audio columns.")
                    return@use
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getStringOrNull(titleColumn).orEmpty().ifBlank { "Unknown track" }
                    val artist = cursor.getStringOrNull(artistColumn).normalizeMediaLabel("Unknown artist")
                    val album = cursor.getStringOrNull(albumColumn).normalizeMediaLabel("Unknown album")
                    val duration = cursor.getLong(durationColumn)
                    val albumId = cursor.getLongOrNull(albumIdColumn) ?: 0L
                    val trackNumber = (cursor.getLongOrNull(trackNumberColumn)?.toInt() ?: 0).let { rawTrack ->
                        if (rawTrack > 1000) rawTrack % 1000 else rawTrack
                    }.coerceAtLeast(0)

                    songs += LocalSong(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        trackNumber = trackNumber,
                        durationMs = duration,
                        contentUri = ContentUris.withAppendedId(collection, id),
                        albumArtUri = albumArtUriFor(albumId)
                    )
                }
            }

            songs
        }.onFailure { error ->
            Log.e(tag, "Unable to scan device music", error)
        }.getOrDefault(emptyList())
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            getString(columnIndex)
        } else {
            null
        }
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) {
            runCatching { getLong(columnIndex) }
                .onFailure { error ->
                    Log.w(tag, "Unable to read media column $columnIndex", error)
                }
                .getOrNull()
        } else {
            null
        }
    }

    private fun String?.normalizeMediaLabel(fallback: String): String {
        val value = this?.trim().orEmpty()
        return when {
            value.isBlank() -> fallback
            value.equals("<unknown>", ignoreCase = true) -> fallback
            else -> value
        }
    }

    private fun albumArtUriFor(albumId: Long): android.net.Uri? {
        if (albumId <= 0L) return null
        return runCatching {
            ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        }.getOrNull()
    }
}
