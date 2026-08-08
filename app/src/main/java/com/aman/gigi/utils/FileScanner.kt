package com.aman.gigi.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.aman.gigi.model.RemoteFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileScanner @Inject constructor(
    private val context: Context
) {
    private val TAG = "FileScanner"

    fun listFiles(dirPath: String? = null): List<RemoteFile> {
        val root = if (dirPath.isNullOrEmpty()) {
            Environment.getExternalStorageDirectory()
        } else {
            File(dirPath)
        }

        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid directory: ${root.absolutePath}")
            return emptyList()
        }

        return try {
            val files = root.listFiles() ?: emptyArray()
            files.map { file ->
                RemoteFile(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    extension = if (file.isFile) file.extension else null
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files in ${root.absolutePath}", e)
            emptyList()
        }
    }

    fun getFileBytes(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: $filePath", e)
            null
        }
    }
}
