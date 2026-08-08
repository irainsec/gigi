package com.aman.gigi.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Utility for compressing and decompressing data using Gzip
 */
object CompressionUtil {
    
    /**
     * Compress string data using Gzip
     * 
     * @param data String to compress
     * @return Compressed byte array
     */
    fun compress(data: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        
        GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
            gzipOutputStream.write(data.toByteArray(Charsets.UTF_8))
        }
        
        return byteArrayOutputStream.toByteArray()
    }
    
    /**
     * Compress byte array using Gzip
     * 
     * @param data Byte array to compress
     * @return Compressed byte array
     */
    fun compress(data: ByteArray): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        
        GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
            gzipOutputStream.write(data)
        }
        
        return byteArrayOutputStream.toByteArray()
    }
    
    /**
     * Decompress Gzip data to string
     * 
     * @param compressedData Compressed byte array
     * @return Decompressed string
     */
    fun decompressToString(compressedData: ByteArray): String {
        val byteArrayInputStream = java.io.ByteArrayInputStream(compressedData)
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        
        GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
            gzipInputStream.copyTo(byteArrayOutputStream)
        }
        
        return byteArrayOutputStream.toString(Charsets.UTF_8.name())
    }

    /**
     * Returns a GZIPInputStream for streaming decompression.
     * This avoids loading the entire decompressed JSON into a String (memory optimization).
     */
    fun decompressToStream(compressedData: ByteArray): GZIPInputStream {
        return GZIPInputStream(java.io.ByteArrayInputStream(compressedData))
    }
    
    /**
     * Decompress Gzip data to byte array
     * 
     * @param compressedData Compressed byte array
     * @return Decompressed byte array
     */
    fun decompress(compressedData: ByteArray): ByteArray {
        val byteArrayInputStream = ByteArrayInputStream(compressedData)
        val byteArrayOutputStream = ByteArrayOutputStream()
        
        GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
            gzipInputStream.copyTo(byteArrayOutputStream)
        }
        
        return byteArrayOutputStream.toByteArray()
    }
    
    /**
     * Calculate compression ratio
     * 
     * @param original Original data size
     * @param compressed Compressed data size
     * @return Compression ratio (0-1, lower is better)
     */
    fun compressionRatio(original: Int, compressed: Int): Float {
        return compressed.toFloat() / original.toFloat()
    }
}
