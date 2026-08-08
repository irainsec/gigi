package com.aman.gigi.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class LyricLine(val timestampMs: Long, val text: String)

@Singleton
class LyricsRepository @Inject constructor() {

    private val tag = "LyricsRepository"
    private val cache = HashMap<Long, List<LyricLine>?>()

    suspend fun hasLyrics(title: String, artist: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val encodedTitle = java.net.URLEncoder.encode(title.take(80), "UTF-8")
                val encodedArtist = java.net.URLEncoder.encode(artist.take(60), "UTF-8")
                val urlString = "https://lrclib.net/api/get" +
                        "?track_name=$encodedTitle" +
                        "&artist_name=$encodedArtist"

                val conn = URL(urlString).openConnection().apply {
                    connectTimeout = 3_000
                    readTimeout = 4_000
                    setRequestProperty("User-Agent", "GigiApp/1.0 (Android Music Player)")
                }
                val responseText = conn.getInputStream().bufferedReader().readText()
                val json = JSONObject(responseText)
                json.has("syncedLyrics") && !json.isNull("syncedLyrics")
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun fetchLyrics(
        songId: Long,
        title: String,
        artist: String,
        album: String
    ): List<LyricLine>? {
        cache[songId]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val encodedTitle = java.net.URLEncoder.encode(title.take(80), "UTF-8")
                val encodedArtist = java.net.URLEncoder.encode(artist.take(60), "UTF-8")
                val encodedAlbum = java.net.URLEncoder.encode(album.take(60), "UTF-8")
                val urlString = "https://lrclib.net/api/get" +
                        "?track_name=$encodedTitle" +
                        "&artist_name=$encodedArtist" +
                        "&album_name=$encodedAlbum"

                val conn = URL(urlString).openConnection().apply {
                    connectTimeout = 6_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", "GigiApp/1.0 (Android Music Player)")
                }
                val responseText = conn.getInputStream().bufferedReader().readText()
                val json = JSONObject(responseText)
                val syncedLyrics = if (json.has("syncedLyrics") && !json.isNull("syncedLyrics")) {
                    json.getString("syncedLyrics")
                } else null

                val parsed = syncedLyrics?.let { parseLrc(it) }
                cache[songId] = parsed
                parsed
            } catch (e: Exception) {
                Log.w(tag, "Lyrics fetch failed for '$title': ${e.message}")
                cache[songId] = null
                null
            }
        }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val pattern = Regex("""^\[(\d{2}):(\d{2})\.(\d{1,3})\](.*)$""")
        for (raw in lrc.lines()) {
            val match = pattern.find(raw.trim()) ?: continue
            val (min, sec, csec, text) = match.destructured
            val ms = min.toLong() * 60_000 +
                    sec.toLong() * 1_000 +
                    csec.padEnd(3, '0').take(3).toLong()
            lines.add(LyricLine(ms, text.trim()))
        }
        return lines.sortedBy { it.timestampMs }
    }

    fun clearCache() {
        cache.clear()
    }
}
