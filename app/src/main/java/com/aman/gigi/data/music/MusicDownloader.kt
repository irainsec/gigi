package com.aman.gigi.data.music

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "MusicDownloader"
    private val client = OkHttpClient()

    suspend fun downloadSong(
        result: YTSearchResult,
        audioStreamUrl: String,
        lyricsContent: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("gigi_dl_", ".tmp", context.cacheDir)
        val tempArt = File.createTempFile("gigi_art_", ".jpg", context.cacheDir)
        var taggedFile: File? = null

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "gigi_music_download_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Music Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications for Gigi Music"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = result.videoId.hashCode()
        val notificationBuilder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading ${result.title}")
            .setContentText("Connecting...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, true)

        notificationManager.notify(notificationId, notificationBuilder.build())

        try {
            if (result.thumbnailUrl.isNotBlank()) {
                downloadFile(result.thumbnailUrl, tempArt)
            }

            val artBitmap = if (tempArt.exists() && tempArt.length() > 0) {
                try {
                    android.graphics.BitmapFactory.decodeFile(tempArt.absolutePath)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            if (artBitmap != null) {
                notificationBuilder.setLargeIcon(artBitmap)
            }

            notificationBuilder.setContentText("0% completed").setProgress(100, 0, false)
            notificationManager.notify(notificationId, notificationBuilder.build())

            val mimeType = downloadFileWithProgress(audioStreamUrl, tempFile) { progress ->
                val pct = (progress * 100).toInt()
                notificationBuilder.setProgress(100, pct, false)
                    .setContentText("$pct% completed")
                notificationManager.notify(notificationId, notificationBuilder.build())
                onProgress(progress)
            }

            if (mimeType == null) {
                val failBuilder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setContentTitle("Download Failed")
                    .setContentText("Failed to download ${result.title}")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                    .setOngoing(false)
                    .setAutoCancel(true)
                notificationManager.notify(notificationId, failBuilder.build())
                return@withContext false
            }

            val extension = when {
                mimeType.contains("audio/mpeg") || mimeType.contains("mp3") -> ".mp3"
                mimeType.contains("audio/mp4") || mimeType.contains("m4a") -> ".m4a"
                mimeType.contains("audio/ogg") || mimeType.contains("ogg") || mimeType.contains("opus") -> ".ogg"
                else -> ".m4a"
            }

            val resolvedMimeType = when (extension) {
                ".mp3" -> "audio/mpeg"
                ".m4a" -> "audio/mp4"
                ".ogg" -> "audio/ogg"
                else -> "audio/mp4"
            }

            taggedFile = File(context.cacheDir, "gigi_tagged_${System.currentTimeMillis()}$extension")
            if (tempFile.renameTo(taggedFile)) {
                try {
                    val audioFile = AudioFileIO.read(taggedFile)
                    val audioTag = audioFile.tagOrCreateAndSetDefault
                    audioTag.setField(FieldKey.TITLE, result.title)
                    audioTag.setField(FieldKey.ARTIST, result.artist)
                    audioTag.setField(FieldKey.ALBUM, "YouTube Music")

                    if (tempArt.exists() && tempArt.length() > 0) {
                        try {
                            val artwork = ArtworkFactory.createArtworkFromFile(tempArt)
                            audioTag.deleteArtworkField()
                            audioTag.setField(artwork)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to create artwork with ArtworkFactory, using binary fallback", e)
                            val artwork = ArtworkFactory.getNew()
                            artwork.binaryData = tempArt.readBytes()
                            artwork.mimeType = "image/jpeg"
                            audioTag.deleteArtworkField()
                            audioTag.setField(artwork)
                        }
                    }
                    audioFile.commit()
                } catch (e: Exception) {
                    Log.e(tag, "Failed to write tags using jaudiotagger, continuing without tags", e)
                }

                saveToMediaStore(taggedFile, result.title, result.artist, resolvedMimeType, extension, lyricsContent)
            } else {
                saveToMediaStore(tempFile, result.title, result.artist, resolvedMimeType, extension, lyricsContent)
            }

            val doneBuilder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle("Downloaded ${result.title}")
                .setContentText("Download complete")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)
                .setAutoCancel(true)
            if (artBitmap != null) {
                doneBuilder.setLargeIcon(artBitmap)
            }
            notificationManager.notify(notificationId, doneBuilder.build())

            true
        } catch (e: Exception) {
            Log.e(tag, "Error downloading/processing song ${result.title}", e)
            val failBuilder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle("Download Failed")
                .setContentText("Failed to download ${result.title}")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)
                .setAutoCancel(true)
            notificationManager.notify(notificationId, failBuilder.build())
            false
        } finally {
            runCatching { tempFile.delete() }
            runCatching { tempArt.delete() }
            runCatching { taggedFile?.delete() }
        }
    }

    private fun downloadFile(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(target).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    private fun downloadFileWithProgress(
        url: String,
        target: File,
        onProgress: (Float) -> Unit
    ): String? {
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val totalBytes = body.contentLength()
                val mimeType = response.header("Content-Type") ?: "audio/mp4"
                var bytesWritten = 0L

                body.byteStream().use { inputStream ->
                    FileOutputStream(target).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytes = inputStream.read(buffer)
                        while (bytes >= 0) {
                            outputStream.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            if (totalBytes > 0) {
                                onProgress(bytesWritten.toFloat() / totalBytes)
                            }
                            bytes = inputStream.read(buffer)
                        }
                    }
                }
                mimeType
            }
        } catch (e: Exception) {
            Log.e(tag, "Download failed", e)
            null
        }
    }

    private fun saveToMediaStore(
        file: File,
        title: String,
        artist: String,
        mimeType: String,
        extension: String,
        lyricsContent: String?
    ) {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9.\\-_ ]"), "_").trim()
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$cleanTitle$extension")
            put(MediaStore.Audio.Media.TITLE, cleanTitle)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, "YouTube Music")
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Gigi")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, values) ?: return

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }

            val path = getPhysicalPathFromUri(uri)
            if (path != null) {
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                if (lyricsContent != null) {
                    val audioFile = File(path)
                    val lrcFile = File(audioFile.parent, "$cleanTitle.lrc")
                    try {
                        lrcFile.writeText(lyricsContent)
                        MediaScannerConnection.scanFile(context, arrayOf(lrcFile.absolutePath), null, null)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to write .lrc file next to audio file", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy file to MediaStore", e)
        }
    }

    private fun getPhysicalPathFromUri(uri: android.net.Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                if (cursor.moveToFirst()) {
                    cursor.getString(columnIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
