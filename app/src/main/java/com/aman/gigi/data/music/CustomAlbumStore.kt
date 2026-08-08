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
data class StoredCustomAlbum(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long
)

@Singleton
class CustomAlbumStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val listType = object : TypeToken<List<StoredCustomAlbum>>() {}.type

    suspend fun loadAlbums(): List<StoredCustomAlbum> = withContext(Dispatchers.IO) {
        readAlbums()
    }

    suspend fun createAlbum(
        name: String,
        songIds: List<Long>
    ): List<StoredCustomAlbum> = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || songIds.isEmpty()) {
            return@withContext readAlbums()
        }

        val albums = readAlbums().toMutableList()
        albums += StoredCustomAlbum(
            id = -System.currentTimeMillis(),
            name = trimmedName,
            songIds = songIds.distinct(),
            createdAt = System.currentTimeMillis()
        )
        writeAlbums(albums)
        albums.sortedByDescending { it.createdAt }
    }

    suspend fun deleteAlbum(albumId: Long): List<StoredCustomAlbum> = withContext(Dispatchers.IO) {
        val albums = readAlbums().filter { it.id != albumId }
        writeAlbums(albums)
        albums.sortedByDescending { it.createdAt }
    }

    private fun readAlbums(): List<StoredCustomAlbum> {
        val rawJson = prefs.getString(KEY_CUSTOM_ALBUMS, null).orEmpty()
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<StoredCustomAlbum>>(rawJson, listType).orEmpty()
                .sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    private fun writeAlbums(albums: List<StoredCustomAlbum>) {
        prefs.edit()
            .putString(KEY_CUSTOM_ALBUMS, gson.toJson(albums))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "gigi_music_custom_albums"
        const val KEY_CUSTOM_ALBUMS = "custom_albums"
    }
}
