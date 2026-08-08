package com.aman.gigi.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import com.google.gson.Gson

@Singleton
class HttpUploader @Inject constructor() {
    private val gson = Gson()
    private val TAG = "HttpUploader"
    
    interface OnProgressListener {
        fun onProgress(bytesRead: Long, totalBytes: Long, done: Boolean)
    }
    private val BASE_URL = run {
        val wsUri = URI(com.aman.gigi.BuildConfig.SERVER_URL)
        val scheme = if (wsUri.scheme.equals("wss", ignoreCase = true)) "https" else "http"
        URI(
            scheme,
            wsUri.userInfo,
            wsUri.host,
            if (wsUri.port == -1) -1 else wsUri.port,
            null,
            null,
            null
        ).toString().trimEnd('/')
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Longer timeout for uploads
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads a file to the server and returns the asset path (e.g., "CONNECTION_ID/filename.bin")
     */
    fun uploadFile(file: File, connectionCode: String, scribbleId: String, connectionId: String? = null, sessionToken: String? = null): String? {
        val url = "$BASE_URL/api/upload"
        
        Log.i(TAG, "📤 Uploading file: ${file.name} (${file.length()} bytes) to $url")

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("connectionCode", connectionCode)
            .addFormDataPart("scribbleId", scribbleId)
            .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
        
        connectionId?.let {
            builder.addFormDataPart("connectionId", it)
        }

        val requestBody = builder.build()

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        sessionToken?.let { requestBuilder.addHeader("x-session-token", it) }

        val request = requestBuilder.build()

        try {
            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (responseBodyString != null) {
                try {
                    val json = org.json.JSONObject(responseBodyString)
                    if (json.optBoolean("success")) {
                        val assetPath = json.optString("assetPath")
                        Log.i(TAG, "✅ Upload success! Asset path: $assetPath")
                        return assetPath
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to parse upload response: $responseBodyString")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload exception", e)
        }
        return null
    }

    /**
     * Downloads a file from the server
     */
    fun downloadFile(assetPath: String, destFile: File, progressListener: OnProgressListener? = null): Boolean {
        // Ensure assetPath is URL friendly
        val cleanPath = assetPath.replace("\\", "/")
        val url = "$BASE_URL/captures/$cleanPath"
        
        Log.i(TAG, "📥 Downloading file from $url to ${destFile.absolutePath}")

        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Download failed: ${response.code}")
                return false
            }

            val body = response.body
            if (body == null) {
                Log.e(TAG, "❌ Download body is null")
                return false
            }

            val totalBytes = body.contentLength()
            var bytesRead = 0L

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        progressListener?.onProgress(bytesRead, totalBytes, false)
                    }
                }
            }
            progressListener?.onProgress(bytesRead, totalBytes, true)
            Log.i(TAG, "✅ Download success: ${destFile.length()} bytes")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download exception", e)
        }
        return false
    }

    /**
     * Sends a generic POST request with a JSON body
     */
    fun postJson(path: String, body: Map<String, Any?>): Int {
        val url = "$BASE_URL${if (path.startsWith("/")) path else "/$path"}"
        val jsonBody = gson.toJson(body)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            code
        } catch (e: Exception) {
            Log.e(TAG, "❌ POST exception for $url", e)
            -1
        }
    }

    /**
     * Fetches remote notifications from the server
     */
    fun getNotifications(connectionCode: String, deviceId: String? = null, skip: Int = 0, limit: Int = 20, sessionToken: String? = null): List<com.aman.gigi.model.RemoteNotification> {
        val urlBuilder = StringBuilder("$BASE_URL/api/notifications?connectionCode=${connectionCode}&skip=$skip&limit=$limit")
        deviceId?.let { urlBuilder.append("&deviceId=$it") }
        val url = urlBuilder.toString()
        Log.i(TAG, "🔍 Fetching notifications from: $url")
        
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        
        sessionToken?.let { requestBuilder.addHeader("x-session-token", it) }
        
        val request = requestBuilder.build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Failed to fetch notifications: ${response.code}")
                return emptyList()
            }

            val body = response.body?.string() ?: return emptyList()
            val jsonResponse = gson.fromJson(body, NotificationResponse::class.java)
            
            // Map the icons so they have full URLs
            return (jsonResponse.notifications ?: emptyList()).map { notif ->
                if (notif.iconUrl != null && !notif.iconUrl.startsWith("http")) {
                    notif.copy(iconUrl = "$BASE_URL${notif.iconUrl}")
                } else {
                    notif
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception fetching notifications", e)
        }
        return emptyList()
    }

    fun getPresignedUrl(isUpload: Boolean, fileName: String, sessionToken: String? = null): String? {
        val endpoint = if (isUpload) "/api/storage/presigned-upload-url" else "/api/storage/presigned-download-url"
        val url = "$BASE_URL$endpoint?fileName=$fileName"
        Log.i(TAG, "🔍 Requesting pre-signed URL from: $url")

        val requestBuilder = Request.Builder().url(url)
        sessionToken?.let { requestBuilder.addHeader("x-session-token", it) }
        val request = requestBuilder.build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = org.json.JSONObject(body)
                    return json.optString("url")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception getting presigned URL", e)
        }
        return null
    }

    fun uploadToPresignedUrl(url: String, file: File, progressListener: OnProgressListener? = null): Boolean {
        Log.i(TAG, "📤 Direct PUT upload to MinIO: ${file.name} (${file.length()} bytes)")
        val baseBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        
        val progressBody = object : RequestBody() {
            override fun contentType() = baseBody.contentType()
            override fun contentLength() = baseBody.contentLength()
            override fun writeTo(sink: okio.BufferedSink) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var bytesWritten = 0L
                    val total = contentLength()
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        bytesWritten += read
                        progressListener?.onProgress(bytesWritten, total, false)
                    }
                }
                progressListener?.onProgress(contentLength(), contentLength(), true)
            }
        }

        val request = Request.Builder()
            .url(url)
            .put(progressBody)
            .build()
        try {
            val response = client.newCall(request).execute()
            Log.i(TAG, "📤 Direct PUT upload response code: ${response.code}")
            return response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "❌ Direct PUT upload exception", e)
        }
        return false
    }

    fun downloadFromPresignedUrl(url: String, destFile: File, offset: Long = 0L, expectedSize: Long = -1L, progressListener: OnProgressListener? = null): Boolean {
        Log.i(TAG, "📥 Direct GET download from MinIO: $url")
        val requestBuilder = Request.Builder().url(url)
        if (offset > 0L) {
            requestBuilder.addHeader("Range", "bytes=$offset-")
        }
        val request = requestBuilder.build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 206) {
                Log.e(TAG, "❌ Download failed: ${response.code}")
                return false
            }

            val body = response.body ?: return false
            var totalBytes = body.contentLength() + offset
            if (totalBytes <= 0L && expectedSize > 0L) {
                totalBytes = expectedSize
            }
            var bytesRead = offset

            body.byteStream().use { input ->
                java.io.FileOutputStream(destFile, offset > 0L).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        progressListener?.onProgress(bytesRead, totalBytes, false)
                    }
                }
            }
            progressListener?.onProgress(bytesRead, totalBytes, true)
            Log.i(TAG, "✅ Download from MinIO complete: ${destFile.length()} bytes")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download from MinIO exception", e)
        }
        return false
    }

    fun compressFileGzip(file: File): File? {
        val compressed = File(file.parent, file.name + ".gz")
        try {
            java.io.FileInputStream(file).use { input ->
                java.util.zip.GZIPOutputStream(java.io.FileOutputStream(compressed)).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            return compressed
        } catch (e: Exception) {
            Log.e(TAG, "❌ GZIP compression error for ${file.name}", e)
        }
        return null
    }

    fun decompressFileGzip(compressedFile: File, outputFile: File): Boolean {
        try {
            java.util.zip.GZIPInputStream(java.io.FileInputStream(compressedFile)).use { input ->
                java.io.FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ GZIP decompression error for ${compressedFile.name}", e)
        }
        return false
    }

    fun computeSHA256(file: File): String {
        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hashBytes = digest.digest()
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Checksum compute exception", e)
        }
        return ""
    }

    data class ChunkRange(val start: Long, val end: Long)

    fun parallelDownloadFromPresignedUrl(url: String, destFile: File, offset: Long = 0L, expectedSize: Long = -1L, progressListener: OnProgressListener? = null): Boolean {
        return downloadFromPresignedUrl(url, destFile, offset, expectedSize, progressListener)
    }

    private data class NotificationResponse(
        val success: Boolean,
        val notifications: List<com.aman.gigi.model.RemoteNotification>?
    )
}
