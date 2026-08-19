package com.aman.gigi.ui.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.aman.gigi.utils.AppConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Raster map tiles for the Live screens.
 *
 * Three things matter for this to feel instant:
 *  - **Disk cache.** Tiles are immutable, so once fetched they never need fetching
 *    again. Without this every visit re-downloaded the whole viewport.
 *  - **Request de-duplication.** Pan and zoom ask for the same tile from several
 *    recompositions at once; without this they'd all hit the network.
 *  - **Retries, not blacklists.** An earlier version cached failures forever, so one
 *    flaky moment left permanent holes in the map.
 *
 * The URL template is remote-configurable (`osmTileUrl` in the admin App Settings), so
 * the tile source can be switched — to a proxy on our own server, or to a keyed
 * provider — without shipping an app update.
 */
object OsmTiles {
    private const val UA = "GigiApp/1.0 (+https://gigi.iamanraj.com)"
    private const val MAX_DISK_TILES = 1200
    private const val ATTEMPTS = 3

    private val memCache = object : LruCache<String, Bitmap>(140) {}
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    @Volatile private var diskDir: File? = null
    @Volatile private var lastPrune = 0L

    fun init(context: Context) {
        if (diskDir != null) return
        diskDir = File(context.applicationContext.cacheDir, "osm_tiles").apply { mkdirs() }
    }

    private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"
    private fun diskFile(z: Int, x: Int, y: Int) = diskDir?.let { File(it, "${z}_${x}_$y.png") }

    private fun url(z: Int, x: Int, y: Int): String =
        AppConfig.settings.osmTileUrl
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())

    /** Memory-only peek — safe to call while drawing. */
    fun peek(z: Int, x: Int, y: Int): Bitmap? = memCache.get(key(z, x, y))

    /**
     * Nearest cached ancestor of this tile, for drawing a blurry stand-in while the
     * real one loads. Returns the ancestor plus which quadrant of it to sample.
     */
    fun peekAncestor(z: Int, x: Int, y: Int, maxLevels: Int = 4): AncestorTile? {
        var lz = z; var lx = x; var ly = y
        repeat(maxLevels) {
            if (lz <= 1) return null
            lz--; lx /= 2; ly /= 2
            val bmp = peek(lz, lx, ly)
            if (bmp != null) {
                val levels = z - lz
                val span = 1 shl levels                 // ancestor covers span x span tiles
                val sub = 256f / span                   // size of our tile inside it
                val ox = (x - (lx shl levels)) * sub
                val oy = (y - (ly shl levels)) * sub
                return AncestorTile(bmp, ox, oy, sub)
            }
        }
        return null
    }

    data class AncestorTile(val bitmap: Bitmap, val srcX: Float, val srcY: Float, val srcSize: Float)

    suspend fun tile(z: Int, x: Int, y: Int): Bitmap? {
        val n = 1 shl z
        if (z < 0 || x < 0 || y < 0 || x >= n || y >= n) return null
        val k = key(z, x, y)
        memCache.get(k)?.let { return it }

        // Coalesce concurrent requests for the same tile into one fetch.
        val mine = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(k, mine)
        if (existing != null) return existing.await()

        try {
            val bmp = loadFromDisk(z, x, y) ?: fetch(z, x, y)
            if (bmp != null) memCache.put(k, bmp)
            mine.complete(bmp)
            return bmp
        } catch (t: Throwable) {
            mine.complete(null)
            throw t
        } finally {
            inFlight.remove(k)
        }
    }

    private suspend fun loadFromDisk(z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = diskFile(z, x, y) ?: return@withContext null
        if (!f.exists() || f.length() < 64L) return@withContext null
        runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    private suspend fun fetch(z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        repeat(ATTEMPTS) { attempt ->
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url(z, x, y)).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", UA)
                    connectTimeout = 8000
                    readTimeout = 10000
                }
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        writeToDisk(z, x, y, bytes)
                        return@withContext bmp
                    }
                }
            } catch (_: Exception) {
                // fall through to the backoff below
            } finally {
                runCatching { conn?.disconnect() }
            }
            // Back off, then try again — a transient failure must not become permanent.
            if (attempt < ATTEMPTS - 1) delay(250L * (attempt + 1))
        }
        null
    }

    private fun writeToDisk(z: Int, x: Int, y: Int, bytes: ByteArray) {
        val f = diskFile(z, x, y) ?: return
        runCatching { f.writeBytes(bytes) }
        pruneIfNeeded()
    }

    /** Keep the tile cache from growing without bound; cheap and rate-limited. */
    private fun pruneIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastPrune < 60_000L) return
        lastPrune = now
        val dir = diskDir ?: return
        runCatching {
            val files = dir.listFiles() ?: return
            if (files.size <= MAX_DISK_TILES) return
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_DISK_TILES)
                .forEach { it.delete() }
        }
    }

    // ── projection ───────────────────────────────────────────────────────────

    /** Web-Mercator world pixel coordinates (256px tiles) at integer [zoom]. */
    fun project(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val worldPx = 256.0 * (1 shl zoom)
        val x = (lng + 180.0) / 360.0 * worldPx
        val latR = Math.toRadians(lat)
        val y = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * worldPx
        return x to y
    }

    fun unproject(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
        val worldPx = 256.0 * (1 shl zoom)
        val lng = x / worldPx * 360.0 - 180.0
        val lat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / worldPx))))
        return lat to lng
    }

    fun metersPerPixel(lat: Double, zoom: Double): Double =
        156543.03392 * cos(Math.toRadians(lat)) / Math.pow(2.0, zoom)
}
