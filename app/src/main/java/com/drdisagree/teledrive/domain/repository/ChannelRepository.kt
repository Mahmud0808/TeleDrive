package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.DriveChannel
import kotlinx.coroutines.flow.Flow

/** Drives this account owns, and the switching between them. */
interface ChannelRepository {

    /** Known channels, active one included, newest first by last use. */
    fun observeChannels(): Flow<List<DriveChannel>>

    /** Asks Telegram for drive channels and records any that are new. */
    suspend fun refresh(): AppResult<List<DriveChannel>>

    /** Updates what is already known without scanning the account for drives. */
    suspend fun refreshKnown(): AppResult<Unit>

    /** True only when the active drive's channel is confirmed gone. */
    suspend fun activeDriveMissing(): Boolean

    /** Drops drives whose channel no longer exists. Returns how many went. */
    suspend fun pruneDeleted(): AppResult<Int>

    /** Creates a channel named after [label] and records it. */
    suspend fun create(label: String): AppResult<DriveChannel>

    /**
     * Points the app at [chatId]. A drive with no local index is indexed right
     * away unless [index] is false, which lets onboarding defer the one pass it
     * already runs at the end of setup.
     */
    suspend fun switchTo(chatId: Long, index: Boolean = true): AppResult<Unit>

    suspend fun rename(chatId: Long, label: String): AppResult<Unit>

    /** Deletes the channel in Telegram along with every file it holds. */
    suspend fun deleteRemotely(chatId: Long): AppResult<Unit>

    /** Backup folders configured for [chatId]. */
    suspend fun backupFolders(chatId: Long): Set<String>

    suspend fun setBackupFolders(chatId: Long, folders: Set<String>)
}
