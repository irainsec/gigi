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

/**
 * How many memories a connection holds — used by the Memories hub so each partner
 * shelf can show its own count without loading every payload.
 */
data class ConnectionMemoryCount(
    val connectionId: String,
    val total: Int
)
