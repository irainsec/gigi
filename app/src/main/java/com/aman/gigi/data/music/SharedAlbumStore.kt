package com.aman.gigi.data.music

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.Keep
import javax.inject.Inject
import javax.inject.Singleton

@Keep
data class SharedSong(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

@Keep
data class StoredSharedAlbum(
    val id: Long, // Unique ID for Hilt/UI list selection
    val name: String,
    val senderName: String,
    val senderConnectionId: String,
    val songs: List<SharedSong>,
    val sharedAt: Long
)

@Singleton
class SharedAlbumStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val listType = object : TypeToken<List<StoredSharedAlbum>>() {}.type

    suspend fun loadSharedAlbums(): List<StoredSharedAlbum> = withContext(Dispatchers.IO) {
        readAlbums()
    }

    suspend fun saveSharedAlbum(
        name: String,
        senderName: String,
        senderConnectionId: String,
        songs: List<SharedSong>
    ): List<StoredSharedAlbum> = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || songs.isEmpty()) {
            return@withContext readAlbums()
        }

        val albums = readAlbums().toMutableList()
        
        // Check if this album already exists from this sender, if so remove the old one first
        val existingIndex = albums.indexOfFirst { it.name.lowercase() == trimmedName.lowercase() && it.senderConnectionId == senderConnectionId }
        if (existingIndex != -1) {
            albums.removeAt(existingIndex)
        }

        albums += StoredSharedAlbum(
            id = -Math.abs(System.currentTimeMillis()), // Negative ID to match custom album style
            name = trimmedName,
            senderName = senderName,
            senderConnectionId = senderConnectionId,
            songs = songs,
            sharedAt = System.currentTimeMillis()
        )
        writeAlbums(albums)
        albums.sortedByDescending { it.sharedAt }
    }

    suspend fun deleteSharedAlbum(albumId: Long): List<StoredSharedAlbum> = withContext(Dispatchers.IO) {
        val albums = readAlbums().filter { it.id != albumId }
        writeAlbums(albums)
        albums.sortedByDescending { it.sharedAt }
    }

    private fun readAlbums(): List<StoredSharedAlbum> {
        val rawJson = prefs.getString(KEY_SHARED_ALBUMS, null).orEmpty()
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<StoredSharedAlbum>>(rawJson, listType).orEmpty()
                .sortedByDescending { it.sharedAt }
        }.getOrDefault(emptyList())
    }

    private fun writeAlbums(albums: List<StoredSharedAlbum>) {
        prefs.edit()
            .putString(KEY_SHARED_ALBUMS, gson.toJson(albums))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "gigi_music_shared_albums"
        const val KEY_SHARED_ALBUMS = "shared_albums"
    }
}
