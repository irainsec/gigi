package com.aman.gigi.ui.live

import android.content.Context
import com.aman.gigi.repository.LivePost
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last feed we successfully loaded, kept on disk so re-opening the Live tab paints
 * instantly instead of showing a spinner while a location fix and a round trip complete.
 *
 * Deliberately a plain JSON file rather than Room: it's one small blob, read once at
 * ViewModel construction and written after each successful load.
 */
object LiveFeedCache {

    private const val FILE = "live_feed_cache.json"
    /** Older than this and it's not worth showing even as a placeholder. */
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    data class Cached(val posts: List<LivePost>, val lat: Double?, val lng: Double?)

    private fun file(context: Context) = File(context.cacheDir, FILE)

    fun read(context: Context): Cached? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        if (System.currentTimeMillis() - root.optLong("at") > MAX_AGE_MS) return null

        val arr = root.optJSONArray("posts") ?: return null
        val posts = (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i)) }
        if (posts.isEmpty()) return null
        Cached(
            posts = posts,
            lat = root.optDouble("lat").takeIf { !it.isNaN() },
            lng = root.optDouble("lng").takeIf { !it.isNaN() }
        )
    }.getOrNull()

    fun write(context: Context, posts: List<LivePost>, lat: Double?, lng: Double?) {
        runCatching {
            val arr = JSONArray()
            posts.forEach { arr.put(serialize(it)) }
            val root = JSONObject().apply {
                put("at", System.currentTimeMillis())
                lat?.let { put("lat", it) }
                lng?.let { put("lng", it) }
                put("posts", arr)
            }
            file(context).writeText(root.toString())
        }
    }

    fun clear(context: Context) { runCatching { file(context).delete() } }

    private fun serialize(p: LivePost) = JSONObject().apply {
        put("postId", p.postId)
        put("authorMemberId", p.authorMemberId)
        put("authorName", p.authorName)
        p.authorAvatarUrl?.let { put("authorAvatarUrl", it) }
        put("text", p.text)
        put("category", p.category)
        p.mood?.let { put("mood", it) }
        p.lat?.let { put("lat", it) }
        p.lng?.let { put("lng", it) }
        put("preciseLocation", p.preciseLocation)
        p.placeLabel?.let { put("placeLabel", it) }
        put("radiusM", p.radiusM)
        put("status", p.status)
        put("acceptedCount", p.acceptedCount)
        p.maxJoiners?.let { put("maxJoiners", it) }
        put("isMine", p.isMine)
        p.distanceM?.let { put("distanceM", it) }
        p.expiresAt?.let { put("expiresAt", it) }
    }

    private fun parse(o: JSONObject?): LivePost? {
        if (o == null) return null
        // Drop anything that has already expired — showing a stale "live" post is worse
        // than showing nothing.
        val expires = if (o.has("expiresAt")) o.optLong("expiresAt") else null
        if (expires != null && expires > 0 && expires < System.currentTimeMillis()) return null
        return LivePost(
            postId = o.optString("postId"),
            authorMemberId = o.optString("authorMemberId"),
            authorName = o.optString("authorName", "Someone"),
            authorAvatarUrl = o.optString("authorAvatarUrl").takeIf { it.isNotBlank() },
            text = o.optString("text"),
            category = o.optString("category", "other"),
            mood = o.optString("mood").takeIf { it.isNotBlank() },
            lat = if (o.has("lat")) o.optDouble("lat") else null,
            lng = if (o.has("lng")) o.optDouble("lng") else null,
            preciseLocation = o.optBoolean("preciseLocation"),
            placeLabel = o.optString("placeLabel").takeIf { it.isNotBlank() },
            radiusM = o.optInt("radiusM", 500),
            status = o.optString("status", "OPEN"),
            acceptedCount = o.optInt("acceptedCount"),
            maxJoiners = o.optInt("maxJoiners").takeIf { it > 0 },
            isMine = o.optBoolean("isMine"),
            distanceM = if (o.has("distanceM")) o.optInt("distanceM") else null,
            expiresAt = expires
        )
    }
}
