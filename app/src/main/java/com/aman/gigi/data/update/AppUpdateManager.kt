package com.aman.gigi.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.aman.gigi.BuildConfig
import com.aman.gigi.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

enum class DownloadStatus { IDLE, WAITING_FOR_NETWORK, DOWNLOADING, VERIFYING, COMPLETED, ERROR }

data class InAppDownloadProgress(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val fileUri: Uri? = null,
    val error: String? = null,
    val versionName: String = "",
    /** Why we're parked in [DownloadStatus.WAITING_FOR_NETWORK], in words the UI can show. */
    val waitingReason: String? = null
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    /** Lowercase hex SHA-256 of the APK at [downloadUrl], when the server publishes one. */
    val sha256: String? = null,
    /** Which ABI-specific build we picked, for logging. */
    val abi: String? = null
)

/**
 * Downloads and installs updates served from our own site.
 *
 * The download itself is owned by [UpdateDownloadService] — a foreground service — so
 * the OS treats a 50–100 MB transfer as user-visible work instead of a background
 * process it can freeze or kill the moment the screen locks. This object holds the
 * logic and the progress state; it deliberately owns no CoroutineScope and no Context.
 */
object AppUpdateManager {
    private const val TAG = "AppUpdateManager"

    // The tunnel throttles per-connection (~160 KB/s measured), not in aggregate, so
    // throughput scales almost linearly with stream count — but only while the link
    // itself has headroom. NetworkQuality picks the actual number per download; this is
    // the ceiling, and the number of part slots the cache is cleaned up against.
    private const val MAX_PARALLEL_STREAMS = 8
    // Chunk boundaries must not depend on how many streams are running — see
    // downloadInChunks. 4 MB keeps the request count sane (7 for a 25 MB build) while
    // still giving the worker pool something to balance across.
    private const val CHUNK_BYTES = 4L * 1024 * 1024
    private const val MIN_PARALLEL_BYTES = 4L * 1024 * 1024
    private const val CHUNK_RETRIES = 4
    private const val PROBE_RETRIES = 3
    // The tunnel can stall for a while under load; a short read timeout turns a slow
    // moment into a failed update, and re-downloading 25 MB costs far more than waiting.
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 45_000
    // How many times a lost connection may pause-and-resume the whole transfer before we
    // give up, and how long we'll sit waiting for the network each time.
    private const val NETWORK_RESUME_ATTEMPTS = 6
    private const val WAIT_FOR_NETWORK_MS = 10L * 60 * 1000
    // Waiting for Wi-Fi is a user preference, not a fault, so it waits longer — but not
    // indefinitely: this runs inside a foreground service, and Android caps how long one
    // of those may sit around. Half an hour covers "I'm nearly home", and past that it's
    // more honest to stop and let the user start it again.
    private const val WAIT_FOR_WIFI_MS = 30L * 60 * 1000

    /** Set per download from [NetworkQuality]; slow links get a much longer one. */
    @Volatile private var readTimeoutMs = READ_TIMEOUT_MS

    private const val APK_NAME = "gigi-update.apk"
    private const val META_NAME = "gigi-update.meta"
    private fun partName(i: Int) = "gigi-update.part$i"

    private val _downloadProgress = MutableStateFlow(InAppDownloadProgress())
    val downloadProgress: StateFlow<InAppDownloadProgress> = _downloadProgress.asStateFlow()

    private val UPDATE_JSON_URL: String get() {
        val base = Constants.SERVER_URL
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .replace("www.gigi.iamanraj.com", "gigi.iamanraj.com")
            .trimEnd('/')
        return "$base/downloads/latest.json"
    }

    private fun normalize(url: String) = url.replace("www.gigi.iamanraj.com", "gigi.iamanraj.com")

    // ── check ────────────────────────────────────────────────────────────────

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val resolvedUrl = UPDATE_JSON_URL
            Log.i(TAG, "🔍 [UPDATE-CHECK] $resolvedUrl (current build: ${BuildConfig.VERSION_CODE})")
            val connection = (URL(resolvedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "GigiApp/${BuildConfig.VERSION_NAME}")
            }

            if (connection.responseCode == 200) {
                val json = JSONObject(
                    connection.inputStream.bufferedReader().use { it.readText() }
                )

                val serverVersionCode = json.optInt("versionCode", 0)
                if (serverVersionCode <= BuildConfig.VERSION_CODE) {
                    Log.i(TAG, "🔍 Up to date (server=$serverVersionCode)")
                    return@withContext null
                }

                // Prefer a build matching this device's ABI — it skips the ~44 MB of
                // native libs for architectures this phone can't run.
                val abiUrls = json.optJSONObject("abiUrls")
                val abiSha = json.optJSONObject("abiSha256")
                val abi = Build.SUPPORTED_ABIS?.firstOrNull { abiUrls?.has(it) == true }

                val downloadUrl = normalize(
                    abi?.let { abiUrls?.optString(it) }?.takeIf { it.isNotBlank() }
                        ?: json.optString(
                            "downloadUrl",
                            "https://gigi.iamanraj.com/downloads/gigi-latest.apk"
                        )
                )
                val sha256 = (abi?.let { abiSha?.optString(it) } ?: json.optString("sha256"))
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?.lowercase()

                Log.i(TAG, "🔍 Update available: $serverVersionCode, abi=$abi, url=$downloadUrl")
                return@withContext UpdateInfo(
                    hasUpdate = true,
                    versionName = json.optString("versionName", "v1.0.0"),
                    versionCode = serverVersionCode,
                    downloadUrl = downloadUrl,
                    releaseNotes = json.optString("releaseNotes", "New performance and feature updates!"),
                    sha256 = sha256,
                    abi = abi
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}")
        }
        return@withContext null
    }

    // ── download ─────────────────────────────────────────────────────────────

    /**
     * Hands the work to the foreground service; safe to call from a composable.
     *
     * [respectWifiOnly] is false when the user tapped "download anyway" — an explicit
     * choice always beats the saved preference.
     */
    fun startDownload(
        context: Context,
        rawDownloadUrl: String,
        versionName: String = "",
        sha256: String? = null,
        respectWifiOnly: Boolean = true
    ) {
        val ctx = context.applicationContext
        UpdateDownloadService.start(
            ctx,
            normalize(rawDownloadUrl),
            versionName,
            sha256,
            requireUnmetered = respectWifiOnly && UpdatePrefs.wifiOnly(ctx)
        )
    }

    fun cancelDownload(context: Context) {
        UpdateDownloadService.stop(context.applicationContext)
        _downloadProgress.value = InAppDownloadProgress()
    }

    /**
     * Runs the whole download. Only [UpdateDownloadService] should call this — it must
     * run inside a foreground service or Android will kill it partway through.
     */
    suspend fun download(
        context: Context,
        downloadUrl: String,
        versionName: String,
        expectedSha256: String?,
        requireUnmetered: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val targetFile = File(cacheDir, APK_NAME)
        try {
            // The user asked for Wi-Fi only. Park here rather than failing — the service
            // notification says what we are waiting for, and the moment Wi-Fi appears the
            // download starts on its own.
            if (requireUnmetered && NetworkQuality.of(context).metered) {
                Log.i(TAG, "⏸️ Wi-Fi only is on and this is mobile data — waiting")
                waiting(versionName, "Waiting for Wi-Fi")
                if (!NetworkQuality.awaitOnline(context, WAIT_FOR_WIFI_MS, requireUnmetered = true)) {
                    throw IOException("Still no Wi-Fi — start the update again once you are on one")
                }
            }

            _downloadProgress.value = InAppDownloadProgress(
                status = DownloadStatus.DOWNLOADING, progressPercent = 0, versionName = versionName
            )
            if (targetFile.exists()) targetFile.delete()

            // Losing signal mid-transfer is the normal case on mobile, not an error. The
            // part files are already on disk, so each pass picks up exactly where the
            // last one stopped — including across an app restart.
            var networkAttempt = 0
            var totalBytes: Long
            while (true) {
                try {
                    totalBytes = runTransfer(context, cacheDir, downloadUrl, targetFile, versionName)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    networkAttempt++
                    val recoverable = networkAttempt <= NETWORK_RESUME_ATTEMPTS &&
                        !NetworkQuality.isOnline(context)
                    if (!recoverable) throw e
                    Log.w(TAG, "📴 Connection lost (${e.message}) — pausing, will resume")
                    waiting(versionName, "Waiting for a connection")
                    if (!NetworkQuality.awaitOnline(context, WAIT_FOR_NETWORK_MS)) throw e
                }
            }

            if (!isActive) return@withContext

            verify(targetFile, totalBytes, expectedSha256, versionName)
            onDownloadCompleted(context, targetFile, totalBytes, versionName)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            // Only the stitched file goes — the parts stay, so a retry resumes instead
            // of starting the whole 25 MB again.
            targetFile.delete()
            _downloadProgress.value = InAppDownloadProgress(
                status = DownloadStatus.ERROR,
                error = e.message ?: "Download failed",
                versionName = versionName
            )
        }
    }

    private fun waiting(versionName: String, reason: String) {
        _downloadProgress.value = _downloadProgress.value.copy(
            status = DownloadStatus.WAITING_FOR_NETWORK,
            versionName = versionName,
            waitingReason = reason,
            error = null
        )
    }

    /**
     * One attempt at moving the whole file onto disk. Safe to call again after a stall.
     *
     * @return the size the server reported, which [verify] checks the result against.
     */
    private suspend fun runTransfer(
        context: Context,
        cacheDir: File,
        downloadUrl: String,
        targetFile: File,
        versionName: String
    ): Long {
        val link = NetworkQuality.of(context)
        readTimeoutMs = link.readTimeoutMs

        val (totalBytes, supportsRanges) = probe(downloadUrl)
        if (totalBytes <= 0) throw IOException("Server did not report a file size")

        ensureFreeSpace(cacheDir, totalBytes)

        // Part files from a DIFFERENT build must never be resumed into this one — that
        // silently produces an APK stitched from two versions.
        val streams = if (supportsRanges && totalBytes > MIN_PARALLEL_BYTES) {
            link.streams.coerceIn(1, MAX_PARALLEL_STREAMS)
        } else 1
        invalidateStalePartsIfNeeded(cacheDir, downloadUrl, totalBytes)

        _downloadProgress.value = _downloadProgress.value.copy(
            status = DownloadStatus.DOWNLOADING, versionName = versionName, waitingReason = null
        )

        if (streams > 1) {
            Log.i(TAG, "🚀 Chunked download: $totalBytes bytes over $streams streams")
            downloadInChunks(cacheDir, downloadUrl, totalBytes, targetFile, versionName, streams)
        } else {
            Log.i(TAG, "🚀 Single stream: $totalBytes bytes")
            downloadSingleStream(downloadUrl, totalBytes, targetFile, versionName)
        }
        return totalBytes
    }

    /**
     * @return total size and whether the server honours Range requests.
     *
     * Retried, because this runs before a single byte is transferred: a momentary
     * stall here used to fail the entire update at 0% with nothing to resume from.
     */
    private suspend fun probe(downloadUrl: String): Pair<Long, Boolean> {
        var last: Exception? = null
        repeat(PROBE_RETRIES) { attempt ->
            try {
                return probeOnce(downloadUrl)
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "Probe attempt ${attempt + 1} failed (${e.message}); retrying")
                delay(1200L * (attempt + 1))
            }
        }
        throw last ?: IOException("Could not reach the update server")
    }

    private fun probeOnce(downloadUrl: String): Pair<Long, Boolean> {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = readTimeoutMs
                setRequestProperty("Range", "bytes=0-0")
                setRequestProperty("User-Agent", "GigiApp/${BuildConfig.VERSION_NAME}")
                instanceFollowRedirects = true
                connect()
            }
            val code = conn.responseCode
            val contentRange = conn.getHeaderField("Content-Range")
            val acceptRanges = conn.getHeaderField("Accept-Ranges")

            var total = conn.contentLengthLong
            if (contentRange != null && contentRange.contains("/")) {
                runCatching { total = contentRange.substringAfter("/").trim().toLong() }
            }
            // Only trust an actual 206 or an explicit Accept-Ranges. Guessing from file
            // size means that when the server ignores Range, every parallel stream gets
            // the WHOLE file — N× the bytes, and a corrupt merge.
            val ranges = code == 206 ||
                acceptRanges?.contains("bytes", ignoreCase = true) == true
            runCatching { conn.inputStream?.close() }
            return total to ranges
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun ensureFreeSpace(cacheDir: File, totalBytes: Long) {
        // parts + merged copy, plus headroom for the installer itself
        val needed = totalBytes * 2 + 32L * 1024 * 1024
        val free = cacheDir.usableSpace
        if (free in 1 until needed) {
            throw IOException(
                "Not enough free space — needs ${needed / 1048576} MB, ${free / 1048576} MB available"
            )
        }
    }

    private fun invalidateStalePartsIfNeeded(cacheDir: File, url: String, totalBytes: Long) {
        val meta = File(cacheDir, META_NAME)
        // Deliberately independent of the stream count. Chunk boundaries are fixed by
        // CHUNK_BYTES alone, so walking out of Wi-Fi onto mobile — which changes how many
        // streams we use — leaves every part file still valid.
        val fingerprint = "$url|$totalBytes|$CHUNK_BYTES"
        val matches = meta.exists() && runCatching { meta.readText() }.getOrNull() == fingerprint
        if (!matches) {
            Log.i(TAG, "🧹 Discarding stale part files (different build, or the link changed)")
            cacheDir.listFiles { f -> f.name.startsWith("gigi-update.part") }
                ?.forEach { it.delete() }
            runCatching { meta.writeText(fingerprint) }
        }
    }

    /**
     * Downloads the file as fixed-size chunks pulled from a shared queue.
     *
     * The earlier version split the file into exactly N slices, one per stream. That made
     * the slice boundaries a function of the stream count, so the moment the connection
     * changed — Wi-Fi to mobile, or back — [NetworkQuality] picked a different N and every
     * part file on disk became meaningless. Fixed 4 MB chunks are addressed the same way
     * regardless of how many workers are pulling them, so a network change costs nothing
     * but the bytes that were in flight.
     */
    private suspend fun downloadInChunks(
        cacheDir: File,
        downloadUrl: String,
        totalBytes: Long,
        targetFile: File,
        versionName: String,
        streams: Int
    ) = coroutineScope {
        val chunkCount = ((totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()
        val partFiles = Array(chunkCount) { i -> File(cacheDir, partName(i)) }
        fun expectedOf(i: Int) = minOf(CHUNK_BYTES, totalBytes - i.toLong() * CHUNK_BYTES)

        var initialDownloaded = 0L
        for (i in 0 until chunkCount) {
            val existing = if (partFiles[i].exists()) partFiles[i].length() else 0L
            // A part longer than its slot is corrupt — start it over rather than merge it.
            if (existing > expectedOf(i)) partFiles[i].delete() else initialDownloaded += existing
        }
        val downloadedCounter = AtomicLong(initialDownloaded)
        if (initialDownloaded > 0) {
            Log.i(TAG, "⏩ Resuming with ${initialDownloaded / 1048576} MB already on disk")
        }

        val progressUpdater = launch {
            var lastPct = -1
            while (isActive) {
                val current = downloadedCounter.get()
                val pct = if (totalBytes > 0) ((current * 100) / totalBytes).toInt().coerceIn(0, 99) else 0
                if (pct != lastPct) {
                    lastPct = pct
                    _downloadProgress.value = InAppDownloadProgress(
                        status = DownloadStatus.DOWNLOADING,
                        progressPercent = pct,
                        downloadedBytes = current,
                        totalBytes = totalBytes,
                        versionName = versionName
                    )
                }
                delay(100)
            }
        }

        // A worker pool rather than one coroutine per chunk: chunks outnumber streams, and
        // whichever worker frees up first takes the next one, so a slow stream can't hold
        // the whole download hostage the way a fixed slice-per-stream split did.
        val nextChunk = java.util.concurrent.atomic.AtomicInteger(0)
        try {
            (0 until minOf(streams, chunkCount)).map { worker ->
                async(Dispatchers.IO) {
                    while (true) {
                        val i = nextChunk.getAndIncrement()
                        if (i >= chunkCount) break

                        val expected = expectedOf(i)
                        val startByte = i.toLong() * CHUNK_BYTES

                        var attempt = 0
                        while (true) {
                            val have = if (partFiles[i].exists()) partFiles[i].length() else 0L
                            if (have >= expected) break
                            try {
                                downloadChunk(
                                    downloadUrl = downloadUrl,
                                    rangeStart = startByte + have,
                                    rangeEnd = startByte + expected - 1,
                                    partFile = partFiles[i],
                                    append = have > 0,
                                    counter = downloadedCounter,
                                    index = i
                                )
                                break
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                attempt++
                                if (attempt >= CHUNK_RETRIES) throw e
                                Log.w(TAG, "Chunk $i attempt $attempt failed (${e.message}); retrying")
                                delay(1000L * attempt)
                            }
                        }
                    }
                }
            }.awaitAll()
        } finally {
            progressUpdater.cancel()
        }

        // sanity-check every part before stitching
        for (i in 0 until chunkCount) {
            val actual = partFiles[i].length()
            if (actual != expectedOf(i)) {
                throw IOException("Chunk $i is $actual bytes, expected ${expectedOf(i)}")
            }
        }

        mergeParts(partFiles, targetFile)
        partFiles.forEach { it.delete() }
        File(cacheDir, META_NAME).delete()
    }

    private fun downloadChunk(
        downloadUrl: String,
        rangeStart: Long,
        rangeEnd: Long,
        partFile: File,
        append: Boolean,
        counter: AtomicLong,
        index: Int
    ) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = readTimeoutMs
                setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
                setRequestProperty("User-Agent", "GigiApp/${BuildConfig.VERSION_NAME}")
                instanceFollowRedirects = true
                connect()
            }
            val code = conn.responseCode
            // A 200 here means Range was ignored and we're being handed the whole file.
            // Writing that into a part would corrupt the merge, so refuse it.
            if (code != 206) throw IOException("Chunk $index: expected 206, got $code")

            BufferedInputStream(conn.inputStream, 128 * 1024).use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        counter.addAndGet(read.toLong())
                    }
                    output.flush()
                }
            }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * transferFrom is explicitly allowed to move fewer bytes than asked for, so it has
     * to be driven in a loop — otherwise a short transfer silently truncates the APK
     * and misaligns every part after it.
     */
    private fun mergeParts(partFiles: Array<File>, targetFile: File) {
        FileOutputStream(targetFile).use { destStream ->
            val dest = destStream.channel
            var position = 0L
            for (partFile in partFiles) {
                FileInputStream(partFile).use { srcStream ->
                    val src = srcStream.channel
                    var remaining = src.size()
                    while (remaining > 0) {
                        val moved = dest.transferFrom(src, position, remaining)
                        if (moved <= 0) throw IOException("Merge stalled at byte $position")
                        position += moved
                        remaining -= moved
                    }
                }
            }
            destStream.fd.sync()
        }
    }

    private fun downloadSingleStream(
        downloadUrl: String,
        totalBytes: Long,
        targetFile: File,
        versionName: String
    ) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", "GigiApp/${BuildConfig.VERSION_NAME}")
                instanceFollowRedirects = true
                connect()
            }
            val realTotal = if (totalBytes > 0) totalBytes else conn.contentLengthLong
            var downloaded = 0L
            var lastUpdate = 0L

            BufferedInputStream(conn.inputStream, 128 * 1024).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100) {
                            lastUpdate = now
                            val pct = if (realTotal > 0) {
                                ((downloaded * 100) / realTotal).toInt().coerceIn(0, 99)
                            } else 0
                            _downloadProgress.value = InAppDownloadProgress(
                                status = DownloadStatus.DOWNLOADING,
                                progressPercent = pct,
                                downloadedBytes = downloaded,
                                totalBytes = realTotal,
                                versionName = versionName
                            )
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    // ── verify & install ─────────────────────────────────────────────────────

    private fun verify(
        targetFile: File,
        totalBytes: Long,
        expectedSha256: String?,
        versionName: String
    ) {
        _downloadProgress.value = _downloadProgress.value.copy(
            status = DownloadStatus.VERIFYING, progressPercent = 100, versionName = versionName
        )

        val actualSize = targetFile.length()
        if (actualSize != totalBytes) {
            throw IOException("Download is $actualSize bytes but should be $totalBytes")
        }
        if (expectedSha256 != null) {
            val actual = sha256Of(targetFile)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("Checksum mismatch — the download is corrupt")
            }
            Log.i(TAG, "✅ Checksum verified")
        } else {
            Log.w(TAG, "⚠️ No checksum published for this build; size check only")
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun onDownloadCompleted(
        context: Context, targetFile: File, totalBytes: Long, versionName: String
    ) {
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context.applicationContext, "${context.packageName}.provider", targetFile
        )
        Log.i(TAG, "✅ Ready to install: ${targetFile.length()} bytes → $fileUri")

        // Deliberately NOT auto-launching the installer. Doing that yanks the user out
        // of whatever they were doing and, once they confirm, Android kills the app to
        // replace it — which reads as "the app closed by itself". The dialog and the
        // notification both offer an explicit Install action instead.
        _downloadProgress.value = InAppDownloadProgress(
            status = DownloadStatus.COMPLETED,
            progressPercent = 100,
            downloadedBytes = targetFile.length(),
            totalBytes = totalBytes,
            fileUri = fileUri,
            versionName = versionName
        )
    }

    fun installApk(context: Context, apkUri: Uri) {
        try {
            Log.i(TAG, "📦 Launching package installer for $apkUri")
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
        }
    }

    fun resetProgress() {
        _downloadProgress.value = InAppDownloadProgress()
    }
}
