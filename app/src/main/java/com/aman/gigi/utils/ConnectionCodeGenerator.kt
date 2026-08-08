package com.aman.gigi.utils

import java.security.SecureRandom

/**
 * Utility for generating connection codes
 */
object ConnectionCodeGenerator {
    
    // Exclude ambiguous characters: 0, O, 1, I, L
    private const val ALLOWED_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val CODE_LENGTH = 8
    private val random = SecureRandom()
    
    /**
     * Generate a random 8-character connection code
     * Raw format for server consistency: XXXXXXXX
     */
    fun generateCode(): String {
        val code = StringBuilder()
        repeat(CODE_LENGTH) {
            val index = random.nextInt(ALLOWED_CHARS.length)
            code.append(ALLOWED_CHARS[index])
        }
        
        return code.toString().uppercase()
    }
    
    /**
     * Validate connection code format
     */
    fun isValidCode(code: String): Boolean {
        val cleanCode = code.replace("-", "").uppercase()
        
        if (cleanCode.length != CODE_LENGTH) {
            return false
        }
        
        return cleanCode.all { it in ALLOWED_CHARS }
    }
    
    /**
     * Clean and normalize connection code
     */
    fun normalizeCode(code: String): String {
        return code.replace("-", "").uppercase()
    }
}
