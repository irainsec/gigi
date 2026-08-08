package com.aman.gigi.data.music

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.Keep

@Keep
data class YTSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    var hasLyrics: Boolean = false
)

@Singleton
class YoutubeMusicClient @Inject constructor() {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    @Volatile
    private var baseApiUrls = listOf(
        "https://api.piped.private.coffee",
        "https://pipedapi.adminforge.de",
        "https://piped-api.privacy.com.de",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.owo.si",
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.reallyaweso.me"
    )

    @Volatile
    private var invidiousUrls = listOf(
        "https://inv.thepixora.com",
        "https://invidious.nerdvpn.de",
        "https://inv.nadeko.net",
        "https://invidious.f5.si"
    )

    @Volatile
    private var hasRefreshedInstances = false

    @Keep
    private class PipedInstanceInfo(
        @SerializedName("api_url") val apiUrl: String?
    )

    @Keep
    private class InvidiousInstanceDetails(
        @SerializedName("type") val type: String?,
        @SerializedName("uri") val uri: String?,
        @SerializedName("api") val api: Boolean?,
        @SerializedName("monitor") val monitor: InvidiousMonitor?
    )

    @Keep
    private class InvidiousMonitor(
        @SerializedName("down") val down: Boolean?
    )

    @Keep
    private class InvidiousSearchItem(
        @SerializedName("videoId") val videoId: String?,
        @SerializedName("title") val title: String?,
        @SerializedName("author") val author: String?,
        @SerializedName("videoThumbnails") val videoThumbnails: List<InvidiousThumbnail>?
    )

    @Keep
    private class InvidiousThumbnail(
        @SerializedName("url") val url: String?
    )

    @Keep
    private class InvidiousVideoResponse(
        @SerializedName("adaptiveFormats") val adaptiveFormats: List<InvidiousAdaptiveFormat>?
    )

    @Keep
    private class InvidiousAdaptiveFormat(
        @SerializedName("url") val url: String?,
        @SerializedName("type") val type: String?
    )

    private suspend fun refreshActiveInstances() = withContext(Dispatchers.IO) {
        // 1. Refresh Piped instances
        try {
            val request = Request.Builder()
                .url("https://piped-instances.kavin.rocks/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val instances = gson.fromJson(bodyString, Array<PipedInstanceInfo>::class.java).toList()
                        val urls = instances.mapNotNull { it.apiUrl?.trim() }.filter { it.startsWith("http") }
                        if (urls.isNotEmpty()) {
                            baseApiUrls = urls
                            android.util.Log.d("YoutubeMusicClient", "Dynamically loaded active Piped API instances: $baseApiUrls")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YoutubeMusicClient", "Failed to refresh active Piped instances", e)
        }

        // 2. Refresh Invidious instances
        try {
            val request = Request.Builder()
                .url("https://api.invidious.io/instances.json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        // Raw response format: List of Lists [ [domain, details_dict], ... ]
                        val rawList = gson.fromJson(bodyString, Array<Array<Any>>::class.java).map { it.toList() }
                        val urls = mutableListOf<String>()
                        for (item in rawList) {
                            if (item.size == 2) {
                                val detailsRaw = item[1]
                                val detailsJson = gson.toJson(detailsRaw)
                                val details = gson.fromJson(detailsJson, InvidiousInstanceDetails::class.java)
                                val monitor = details.monitor
                                val isDown = monitor?.down ?: true
                                val isHttps = details.type == "https"
                                val uri = details.uri
                                if (isHttps && !isDown && !uri.isNullOrBlank()) {
                                    urls.add(uri.trim().removeSuffix("/"))
                                }
                            }
                        }
                        if (urls.isNotEmpty()) {
                            invidiousUrls = urls
                            android.util.Log.d("YoutubeMusicClient", "Dynamically loaded active Invidious API instances: $invidiousUrls")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YoutubeMusicClient", "Failed to refresh active Invidious instances", e)
        }
    }

    private suspend fun ensureInstancesRefreshed() {
        if (!hasRefreshedInstances) {
            refreshActiveInstances()
            hasRefreshedInstances = true
        }
    }

    @Keep
    private class PipedSearchResponse(
        @SerializedName("items") val items: List<PipedSearchItem>?
    )

    @Keep
    private class PipedSearchItem(
        @SerializedName("type") val type: String?,
        @SerializedName("url") val url: String?,
        @SerializedName("title") val title: String?,
        @SerializedName("thumbnail") val thumbnail: String?,
        @SerializedName("uploaderName") val uploaderName: String?
    )

    @Keep
    private class PipedStreamResponse(
        @SerializedName("audioStreams") val audioStreams: List<PipedAudioStream>?
    )

    @Keep
    private class PipedAudioStream(
        @SerializedName("url") val url: String?,
        @SerializedName("format") val format: String?,
        @SerializedName("mimeType") val mimeType: String?,
        @SerializedName("bitrate") val bitrate: Int?
    )

    suspend fun searchSongs(query: String): List<YTSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList<YTSearchResult>()
        ensureInstancesRefreshed()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        
        // 1. Try Invidious search first
        for (baseUrl in invidiousUrls) {
            try {
                val url = "$baseUrl/api/v1/search?q=$encodedQuery&type=video"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: return@use
                        val items = gson.fromJson(bodyString, Array<InvidiousSearchItem>::class.java).toList()
                        val results = items.mapNotNull { item ->
                            val videoId = item.videoId ?: return@mapNotNull null
                            val thumbnail = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
                            YTSearchResult(
                                videoId = videoId,
                                title = item.title ?: "Unknown Song",
                                artist = item.author ?: "Unknown Artist",
                                thumbnailUrl = thumbnail
                            )
                        }
                        if (results.isNotEmpty()) {
                            android.util.Log.d("YoutubeMusicClient", "Invidious search success on $baseUrl: ${results.size} items")
                            return@withContext results
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YoutubeMusicClient", "Invidious search failed on $baseUrl", e)
            }
        }

        // 2. Fall back to Piped search
        for (baseUrl in baseApiUrls) {
            try {
                val url = "$baseUrl/search?q=$encodedQuery&filter=music_songs"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: return@use
                        val parsed = gson.fromJson(bodyString, PipedSearchResponse::class.java)
                        val items = parsed.items ?: return@use
                        val results = items.mapNotNull { item ->
                            val rawUrl = item.url ?: return@mapNotNull null
                            val videoId = rawUrl.substringAfter("?v=", "").takeIf { it.isNotEmpty() }
                                ?: rawUrl.substringAfter("/watch?v=", "").takeIf { it.isNotEmpty() }
                                ?: return@mapNotNull null
                            val thumbnail = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
                            YTSearchResult(
                                videoId = videoId,
                                title = item.title ?: "Unknown Song",
                                artist = item.uploaderName ?: "Unknown Artist",
                                thumbnailUrl = thumbnail
                            )
                        }
                        if (results.isNotEmpty()) {
                            android.util.Log.d("YoutubeMusicClient", "Piped search success on $baseUrl: ${results.size} items")
                            return@withContext results
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YoutubeMusicClient", "Piped search failed on $baseUrl", e)
            }
        }
        emptyList<YTSearchResult>()
    }

    @Keep
    private class CobaltResponse(
        @SerializedName("status") val status: String?,
        @SerializedName("url") val url: String?,
        @SerializedName("filename") val filename: String?
    )

    private val cobaltUrls = listOf(
        "https://apicobalt.mgytr.top",
        "https://cobaltapi.kittycat.boo",
        "https://fox.kittycat.boo",
        "https://dog.kittycat.boo"
    )

    suspend fun getAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        // 0. Try Cobalt stream extraction first
        for (baseUrl in cobaltUrls) {
            try {
                val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                val mediaJson = "{\"url\":\"$videoUrl\",\"downloadMode\":\"audio\",\"aFormat\":\"wav\"}"
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = mediaJson.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(baseUrl)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .post(requestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val parsed = gson.fromJson(bodyString, CobaltResponse::class.java)
                            val streamUrl = parsed.url
                            if (!streamUrl.isNullOrBlank()) {
                                android.util.Log.d("YoutubeMusicClient", "Cobalt stream success on $baseUrl")
                                return@withContext streamUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YoutubeMusicClient", "Cobalt stream failed on $baseUrl", e)
            }
        }

        ensureInstancesRefreshed()

        // 1. Try Invidious stream extraction first
        for (baseUrl in invidiousUrls) {
            try {
                val url = "$baseUrl/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: return@use
                        val parsed = gson.fromJson(bodyString, InvidiousVideoResponse::class.java)
                        val formats = parsed.adaptiveFormats ?: return@use
                        val audioStreams = formats.filter { it.type?.contains("audio") == true && !it.url.isNullOrBlank() }
                        val bestStream = audioStreams.firstOrNull { it.type?.contains("audio/mp4") == true || it.type?.contains("m4a") == true }
                            ?: audioStreams.firstOrNull()
                        val streamUrl = bestStream?.url
                        if (streamUrl != null) {
                            android.util.Log.d("YoutubeMusicClient", "Invidious stream success on $baseUrl")
                            return@withContext streamUrl
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YoutubeMusicClient", "Invidious stream failed on $baseUrl", e)
            }
        }

        // 2. Fall back to Piped stream extraction
        for (baseUrl in baseApiUrls) {
            try {
                val url = "$baseUrl/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: return@use
                        val parsed = gson.fromJson(bodyString, PipedStreamResponse::class.java)
                        val streams = parsed.audioStreams ?: return@use
                        val bestStream = streams.firstOrNull { it.format?.uppercase() == "M4A" || it.mimeType?.contains("audio/mp4") == true }
                            ?: streams.firstOrNull()
                        val streamUrl = bestStream?.url
                        if (streamUrl != null) {
                            android.util.Log.d("YoutubeMusicClient", "Piped stream success on $baseUrl")
                            return@withContext streamUrl
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YoutubeMusicClient", "Piped stream failed on $baseUrl", e)
            }
        }
        null
    }
}

