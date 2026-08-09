package com.aman.gigi.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong

enum class DownloadStatus { IDLE, DOWNLOADING, COMPLETED, ERROR }

data class InAppDownloadProgress(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val fileUri: Uri? = null,
    val error: String? = null,
    val versionName: String = ""
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PARALLEL_CHUNKS = 4 // 4 parallel HTTP streams for maximum throughput

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

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

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val resolvedUrl = UPDATE_JSON_URL
            Log.i(TAG, "🔍 [UPDATE-CHECK] Checking: $resolvedUrl (current build: ${BuildConfig.VERSION_CODE})")
            val url = URL(resolvedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 GigiApp/1.6")

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                
                val serverVersionCode = json.optInt("versionCode", 0)
                val serverVersionName = json.optString("versionName", "v1.0.0")
                val rawDownloadUrl = json.optString("downloadUrl", "https://gigi.iamanraj.com/downloads/gigi-latest.apk")
                val downloadUrl = rawDownloadUrl.replace("www.gigi.iamanraj.com", "gigi.iamanraj.com")
                val releaseNotes = json.optString("releaseNotes", "New performance and feature updates!")

                val currentVersionCode = BuildConfig.VERSION_CODE
                Log.i(TAG, "🔍 Version check: Current=$currentVersionCode, Server=$serverVersionCode")

                if (serverVersionCode > currentVersionCode) {
                    return@withContext UpdateInfo(
                        hasUpdate = true,
                        versionName = serverVersionName,
                        versionCode = serverVersionCode,
                        downloadUrl = downloadUrl,
                        releaseNotes = releaseNotes
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}")
        }
        return@withContext null
    }

    fun startDownload(context: Context, rawDownloadUrl: String, versionName: String = "") {
        val downloadUrl = rawDownloadUrl.replace("www.gigi.iamanraj.com", "gigi.iamanraj.com")
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                _downloadProgress.value = InAppDownloadProgress(
                    status = DownloadStatus.DOWNLOADING,
                    progressPercent = 0,
                    versionName = versionName
                )

                val targetFile = File(context.cacheDir, "gigi-update.apk")
                if (targetFile.exists()) targetFile.delete()

                Log.i(TAG, "⚡ [TURBO-DOWNLOAD] Range probe connection to: $downloadUrl")
                val probeConn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Range", "bytes=0-0")
                    setRequestProperty("User-Agent", "Mozilla/5.0 GigiApp/1.6")
                    instanceFollowRedirects = true
                    connect()
                }

                var totalBytes = probeConn.contentLengthLong
                val contentRange = probeConn.getHeaderField("Content-Range")
                if (contentRange != null && contentRange.contains("/")) {
                    runCatching { totalBytes = contentRange.substringAfter("/").trim().toLong() }
                }
                val probeCode = probeConn.responseCode
                val acceptRanges = probeConn.getHeaderField("Accept-Ranges")
                probeConn.inputStream?.close()
                probeConn.disconnect()

                val supportsRanges = probeCode == 206 || (acceptRanges != null && acceptRanges.contains("bytes", ignoreCase = true)) || totalBytes > 4 * 1024 * 1024
                val useParallel = supportsRanges && totalBytes > 4 * 1024 * 1024

                if (useParallel) {
                    Log.i(TAG, "🚀 [TURBO-DOWNLOAD] Resumable parallel multi-stream enabled! Total: $totalBytes bytes across $PARALLEL_CHUNKS chunks")
                    downloadInParallelChunks(context, downloadUrl, totalBytes, targetFile, versionName)
                } else {
                    Log.i(TAG, "🚀 [TURBO-DOWNLOAD] Single stream high-speed fallback. Total: $totalBytes bytes")
                    downloadSingleStream(context, downloadUrl, totalBytes, targetFile, versionName)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Turbo download error: ${e.message}", e)
                _downloadProgress.value = InAppDownloadProgress(
                    status = DownloadStatus.ERROR,
                    error = e.message ?: "Download failed"
                )
            }
        }
    }

    private suspend fun CoroutineScope.downloadInParallelChunks(
        context: Context,
        downloadUrl: String,
        totalBytes: Long,
        targetFile: File,
        versionName: String
    ) {
        val chunkSize = totalBytes / PARALLEL_CHUNKS
        val partFiles = Array(PARALLEL_CHUNKS) { i -> File(context.cacheDir, "gigi-update.part$i") }

        // Calculate initial bytes already downloaded from previous interrupted sessions
        var initialDownloaded = 0L
        val resumeOffsets = LongArray(PARALLEL_CHUNKS)
        for (i in 0 until PARALLEL_CHUNKS) {
            val startByte = i * chunkSize
            val endByte = if (i == PARALLEL_CHUNKS - 1) totalBytes - 1 else (startByte + chunkSize - 1)
            val expectedChunkSize = endByte - startByte + 1
            val existing = if (partFiles[i].exists()) partFiles[i].length() else 0L
            if (existing >= expectedChunkSize) {
                resumeOffsets[i] = expectedChunkSize
                initialDownloaded += expectedChunkSize
            } else {
                resumeOffsets[i] = existing
                initialDownloaded += existing
            }
        }

        val downloadedCounter = AtomicLong(initialDownloaded)

        // Progress updater loop
        val progressUpdater = launch {
            var lastPct = -1
            while (isActive) {
                val currentBytes = downloadedCounter.get()
                val pct = if (totalBytes > 0) ((currentBytes * 100) / totalBytes).toInt().coerceIn(0, 99) else 0
                if (pct != lastPct) {
                    lastPct = pct
                    _downloadProgress.value = InAppDownloadProgress(
                        status = DownloadStatus.DOWNLOADING,
                        progressPercent = pct,
                        downloadedBytes = currentBytes,
                        totalBytes = totalBytes,
                        versionName = versionName
                    )
                }
                delay(50)
            }
        }

        try {
            // Launch 4 parallel HTTP Range downloads
            val tasks = (0 until PARALLEL_CHUNKS).map { i ->
                async(Dispatchers.IO) {
                    val startByte = i * chunkSize
                    val endByte = if (i == PARALLEL_CHUNKS - 1) totalBytes - 1 else (startByte + chunkSize - 1)
                    val expectedChunkSize = endByte - startByte + 1
                    val existingBytes = resumeOffsets[i]
                    val partFile = partFiles[i]

                    // If chunk is already 100% finished from previous session, skip downloading!
                    if (existingBytes >= expectedChunkSize) {
                        Log.i(TAG, "⏩ Chunk $i already fully downloaded ($existingBytes bytes), skipping!")
                        return@async
                    }

                    val rangeStart = startByte + existingBytes
                    val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 30000
                        setRequestProperty("Range", "bytes=$rangeStart-$endByte")
                        setRequestProperty("User-Agent", "Mozilla/5.0 GigiApp/1.6")
                        setRequestProperty("Connection", "close")
                        instanceFollowRedirects = true
                        connect()
                    }

                    val code = conn.responseCode
                    if (code != 206 && code != 200) {
                        throw Exception("Chunk $i server error code: $code")
                    }

                    val input = BufferedInputStream(conn.inputStream, 128 * 1024)
                    val output = FileOutputStream(partFile, existingBytes > 0)
                    val buffer = ByteArray(128 * 1024)
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1 && isActive) {
                        output.write(buffer, 0, read)
                        downloadedCounter.addAndGet(read.toLong())
                    }

                    output.flush()
                    output.close()
                    input.close()
                    conn.disconnect()
                }
            }

            tasks.awaitAll()
            progressUpdater.cancel()

            // Zero-copy merge using FileChannel
            FileOutputStream(targetFile).use { destStream ->
                val destChannel: FileChannel = destStream.channel
                for (partFile in partFiles) {
                    FileInputStream(partFile).use { srcStream ->
                        val srcChannel: FileChannel = srcStream.channel
                        destChannel.transferFrom(srcChannel, destChannel.size(), srcChannel.size())
                    }
                    partFile.delete()
                }
            }

            if (isActive) {
                onDownloadCompleted(context, targetFile, totalBytes, versionName)
            }

        } finally {
            progressUpdater.cancel()
        }
    }


    private suspend fun downloadSingleStream(
        context: Context,
        downloadUrl: String,
        totalBytes: Long,
        targetFile: File,
        versionName: String
    ) = withContext(Dispatchers.IO) {
        val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Mozilla/5.0 GigiApp/1.6")
            instanceFollowRedirects = true
            connect()
        }

        val realTotal = if (totalBytes > 0) totalBytes else conn.contentLengthLong
        var downloadedBytes = 0L

        val inputStream = BufferedInputStream(conn.inputStream, 128 * 1024)
        val outputStream = FileOutputStream(targetFile)
        val buffer = ByteArray(128 * 1024)
        var bytesRead: Int
        var lastProgressUpdate = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1 && isActive) {
            outputStream.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead

            val now = System.currentTimeMillis()
            if (now - lastProgressUpdate > 60 || downloadedBytes == realTotal) {
                lastProgressUpdate = now
                val pct = if (realTotal > 0) ((downloadedBytes * 100) / realTotal).toInt().coerceIn(0, 99) else 0
                _downloadProgress.value = InAppDownloadProgress(
                    status = DownloadStatus.DOWNLOADING,
                    progressPercent = pct,
                    downloadedBytes = downloadedBytes,
                    totalBytes = realTotal,
                    versionName = versionName
                )
            }
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()

        if (isActive) {
            onDownloadCompleted(context, targetFile, realTotal, versionName)
        }
    }

    private fun onDownloadCompleted(context: Context, targetFile: File, totalBytes: Long, versionName: String) {
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context.applicationContext,
            "${context.packageName}.provider",
            targetFile
        )

        Log.i(TAG, "⚡ [TURBO-DOWNLOAD] Completed! Final File Size: ${targetFile.length()} bytes, URI: $fileUri")

        _downloadProgress.value = InAppDownloadProgress(
            status = DownloadStatus.COMPLETED,
            progressPercent = 100,
            downloadedBytes = targetFile.length(),
            totalBytes = totalBytes,
            fileUri = fileUri,
            versionName = versionName
        )

        installApk(context, fileUri)
    }

    fun installApk(context: Context, apkUri: Uri) {
        try {
            Log.i(TAG, "📦 [INSTALL-APK] Launching package installer for: $apkUri")
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
