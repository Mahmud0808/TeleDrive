package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import kotlinx.coroutines.flow.Flow

interface ExclusionRepository {

    fun observeAll(): Flow<List<Exclusion>>

    /**
     * Adds the rules every new drive starts with, currently skipping dot files
     * and dot folders. Only runs when a drive has none of a given rule, so a
     * user who removes one does not get it back.
     */
    suspend fun ensureDefaults(chatId: Long)

    suspend fun getEnabled(): List<Exclusion>

    suspend fun add(type: ExclusionType, value: String): Exclusion

    suspend fun setEnabled(id: String, enabled: Boolean)

    suspend fun remove(id: String)
}
