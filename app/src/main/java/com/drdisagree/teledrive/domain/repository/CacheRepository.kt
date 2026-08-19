package com.drdisagree.teledrive.domain.repository

import kotlinx.coroutines.flow.Flow

interface CacheRepository {

    data class CacheStats(
        val thumbnailBytes: Long,
        val previewBytes: Long,
        val streamBytes: Long,
        val tempBytes: Long,
        val tdlibBytes: Long
    ) {
        val totalBytes: Long
            get() = thumbnailBytes + previewBytes + streamBytes + tempBytes + tdlibBytes
    }

    fun observeStats(): Flow<CacheStats>

    suspend fun refreshStats()

    suspend fun clearThumbnails()

    suspend fun clearTemp()

    suspend fun clearAll()

    /** Evicts least-recently-used entries until total size <= [maxBytes]. */
    suspend fun enforceLimit(maxBytes: Long)
}
