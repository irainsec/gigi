package com.aman.gigi.utils

import android.util.Base64
import com.aman.gigi.model.Scribble
import java.io.File
import java.net.URI

/**
 * The single place that turns a Scribble's media into something Coil / BitmapFactory
 * can actually render.
 *
 * A sparkle reaches the device in three different shapes and every surface used to
 * re-invent the resolution locally (and got it wrong):
 *
 *  - sent by me   → `mediaBase64` only (Base64.DEFAULT, so it contains newlines) and
 *                   `mediaUrl == null`, because the upload swaps in the remote path on
 *                   the *outgoing copy* of the payload only.
 *  - over the socket → `mediaUrl` is a **relative** server asset path
 *                      ("captures/ab12.jpg") plus a re-hydrated base64 blob.
 *  - over FCM     → `mediaUrl` is already absolute.
 *
 * Handing a raw base64 string or a relative asset path straight to Coil renders nothing
 * at all, which is why the live reveal screen (manual BitmapFactory decode) worked while
 * the memories gallery showed empty cards.
 */
object SparkleMedia {

    /** Presence/location pings ride the same table — they are not memories. */
    const val MEDIA_TYPE_HEARTBEAT = "application/vnd.gigi.heartbeat"

    /** http(s) origin of the sync server, derived from the ws(s) BuildConfig URL. */
    private val httpBase: String by lazy {
        runCatching {
            val uri = URI(com.aman.gigi.BuildConfig.SERVER_URL)
            val scheme = if (uri.scheme.equals("wss", true) || uri.scheme.equals("https", true)) "https" else "http"
            val port = if (uri.port == -1) "" else ":${uri.port}"
            "$scheme://${uri.host}$port"
        }.getOrDefault("")
    }

    fun hasMedia(scribble: Scribble): Boolean =
        !scribble.mediaBase64.isNullOrBlank() || !scribble.mediaUrl.isNullOrBlank()

    fun isDoodle(scribble: Scribble): Boolean = scribble.strokes.isNotEmpty()

    fun hasNote(scribble: Scribble): Boolean = !scribble.secretMessage.isNullOrBlank()

    /** True when this row is worth showing in a memories timeline. */
    fun isMemory(scribble: Scribble): Boolean =
        scribble.mediaType != MEDIA_TYPE_HEARTBEAT &&
            (hasMedia(scribble) || isDoodle(scribble) || hasNote(scribble))

    /**
     * Returns a ByteArray, absolute URL String or File — all types Coil understands —
     * or null when the row carries no picture at all.
     */
    fun resolve(scribble: Scribble): Any? = resolve(scribble.mediaUrl, scribble.mediaBase64)

    fun resolve(mediaUrl: String?, mediaBase64: String?): Any? {
        decodeBase64(mediaBase64)?.let { return it }
        val raw = mediaUrl?.takeIf { it.isNotBlank() } ?: return null
        return locate(raw)
    }

    /**
     * Decodes an inline blob. Tolerates data-URL prefixes and the newlines that
     * Base64.DEFAULT sprinkles in. Returns null when the value is really a URL.
     */
    fun decodeBase64(raw: String?): ByteArray? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        if (value.startsWith("http", ignoreCase = true) || value.startsWith("file://", ignoreCase = true)) return null
        val payload = (if (value.contains("base64,")) value.substringAfter("base64,") else value)
            .filterNot { it == '\n' || it == '\r' || it == ' ' }
        if (payload.length < 32) return null
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Turns an absolute URL, a file path or a relative server asset path into a loadable model. */
    fun locate(path: String): Any {
        if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
            return path
                .replace("/app/captures/", "/captures/")
                .replace("/captures/captures/", "/captures/")
        }
        if (path.startsWith("file://", ignoreCase = true)) {
            val file = File(path.removePrefix("file://"))
            return if (file.exists()) file else path
        }
        if (path.startsWith("/")) {
            val file = File(path)
            if (file.exists()) return file
        }
        val clean = path.replace("\\", "/")
            .trimStart('/')
            .removePrefix("app/")
            .removePrefix("captures/")
            .trimStart('/')
        return if (httpBase.isBlank()) path else "$httpBase/captures/$clean"
    }
}
