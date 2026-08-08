package com.aman.gigi.model

/**
 * Lightweight summary of a scribble for list queries to avoid TransactionTooLargeException
 */
data class ScribbleSummary(
    val scribbleId: String,
    val connectionId: String,
    val status: ScribbleStatus,
    val createdAt: Long,
    val mediaType: String? = null,
    val revealType: String? = null,
    val isSent: Boolean // True if sent by local user
)
