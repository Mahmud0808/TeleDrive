package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.DriveChannel
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {

    fun observeChannels(): Flow<List<DriveChannel>>

    suspend fun refresh(): AppResult<List<DriveChannel>>

    suspend fun refreshKnown(): AppResult<Unit>

    /** True only when the active drive's channel is confirmed gone. */
    suspend fun activeDriveMissing(): Boolean

    suspend fun pruneDeleted(): AppResult<Int>

    suspend fun create(label: String): AppResult<DriveChannel>

    /** [index] false lets onboarding defer indexing to the pass it runs at the end of setup. */
    suspend fun switchTo(chatId: Long, index: Boolean = true): AppResult<Unit>

    suspend fun rename(chatId: Long, label: String): AppResult<Unit>

    suspend fun deleteRemotely(chatId: Long): AppResult<Unit>

    suspend fun backupFolders(chatId: Long): Set<String>

    suspend fun setBackupFolders(chatId: Long, folders: Set<String>)
}
