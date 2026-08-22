package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.telegram.StorageChannel
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.data.local.dao.StorageChannelDao
import com.drdisagree.teledrive.data.local.entity.StorageChannelEntity
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.ExclusionRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps the list of drives this account owns. Rows in files and folders carry
 * the channel that owns them, so switching is only a matter of pointing the
 * app at another chat id; nothing is deleted and nothing is re-downloaded.
 */
@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: StorageChannelDao,
    private val telegramClient: TelegramClient,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val channelOwnership: ChannelOwnership,
    private val exclusionRepository: ExclusionRepository
) : ChannelRepository {

    override fun observeChannels(): Flow<List<DriveChannel>> = combine(
        channelDao.observeAll(),
        settingsRepository.preferences.map { it.storageChatId }
    ) { channels, activeId ->
        channels.map { channel -> channel.toDomain(activeId) }
    }

    override suspend fun refresh(): AppResult<List<DriveChannel>> = runTelegram {
        val remote = discoverChannels()
        val now = System.currentTimeMillis()
        for (channel in remote) {
            val known = channelDao.byId(channel.chatId)
            if (known == null) {
                channelDao.upsert(
                    StorageChannelEntity(
                        chatId = channel.chatId,
                        title = channel.title,
                        remoteFileCount = channel.documentCount,
                        addedAt = now,
                        lastOpenedAt = 0
                    )
                )
            } else {
                if (known.title != channel.title) {
                    channelDao.setTitle(channel.chatId, channel.title)
                }
                if (known.remoteFileCount != channel.documentCount) {
                    channelDao.setRemoteFileCount(channel.chatId, channel.documentCount)
                }
            }
            seedDefaultsOnce(channel.chatId)
            refreshPhoto(channel.chatId)
        }
        val activeId = settingsRepository.preferences.first().storageChatId
        if (activeId != null && channelDao.byId(activeId)?.lastOpenedAt == 0L) {
            channelDao.touch(activeId, now)
        }
        AppResult.Success(channelDao.all().map { it.toDomain(activeId) })
    }

    /**
     * Right after sign-in TDLib has not loaded the chat list yet, so a single
     * pass can report no drives when the account has several. Creating a drive
     * on that answer would strand the user's files in the channel they already
     * had, so discovery is retried before it is believed.
     */
    private suspend fun discoverChannels(): List<StorageChannel> {
        repeat(DISCOVERY_ATTEMPTS) { attempt ->
            val found = telegramClient.listStorageChannels(channelDao.all().size)
            if (found.isNotEmpty()) return found
            if (attempt < DISCOVERY_ATTEMPTS - 1) delay(DISCOVERY_RETRY_MS.milliseconds)
        }
        SafeLog.d(TAG, "No drive channels found on this account")
        return emptyList()
    }

    override suspend fun refreshKnown(): AppResult<Unit> = runTelegram {
        for (channel in channelDao.all()) {
            seedDefaultsOnce(channel.chatId)
            refreshPhoto(channel.chatId)
        }
        AppResult.Success(Unit)
    }

    override suspend fun activeDriveMissing(): Boolean {
        val activeId = settingsRepository.preferences.first().storageChatId ?: return false
        return runCatching { telegramClient.chatExists(activeId) }.getOrNull() == false
    }

    override suspend fun pruneDeleted(): AppResult<Int> = runTelegram {
        var removed = 0
        for (channel in channelDao.all()) {
            if (telegramClient.chatExists(channel.chatId) == false) {
                forget(channel.chatId)
                removed++
                SafeLog.d(TAG, "Dropped a drive whose channel is gone")
            }
        }
        val activeId = settingsRepository.preferences.first().storageChatId
        if (removed > 0 && activeId != null && channelDao.fileCount(activeId) == 0) {
            channelDao.touch(activeId, System.currentTimeMillis())
            seedDefaultsOnce(activeId)
            syncRepository.fullResync()
        }
        AppResult.Success(removed)
    }

    override suspend fun create(label: String): AppResult<DriveChannel> = runTelegram {
        val created = telegramClient.createStorageChannel(label)
        val now = System.currentTimeMillis()
        channelDao.upsert(
            StorageChannelEntity(
                chatId = created.chatId,
                title = created.title,
                addedAt = now,
                lastOpenedAt = 0
            )
        )
        seedDefaultsOnce(created.chatId)
        SafeLog.d(TAG, "Registered a new drive channel")
        AppResult.Success(channelDao.byId(created.chatId)!!.toDomain(activeId = null))
    }

    override suspend fun switchTo(chatId: Long, index: Boolean): AppResult<Unit> {
        val current = settingsRepository.preferences.first().storageChatId
        if (current == chatId) return AppResult.Success(Unit)

        val known = channelDao.byId(chatId) ?: return AppResult.Failure(AppError.NotFound)
        settingsRepository.update { it.copy(storageChatId = chatId) }
        channelDao.touch(chatId, System.currentTimeMillis())
        channelOwnership.claimUnowned(chatId)
        seedDefaultsOnce(chatId)

        if (!index) return AppResult.Success(Unit)

        return if (channelDao.fileCount(chatId) == 0) {
            when (val result = syncRepository.fullResync()) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> AppResult.Failure(result.error)
            }
        } else {
            SafeLog.d(TAG, "Switched to an already indexed drive")
            AppResult.Success(Unit)
        }.also { if (known.lastOpenedAt == 0L) syncRepository.incrementalSync() }
    }

    override suspend fun rename(chatId: Long, label: String): AppResult<Unit> = runTelegram {
        val title = telegramClient.renameStorageChannel(chatId, label)
        channelDao.setTitle(chatId, title)
        AppResult.Success(Unit)
    }

    override suspend fun deleteRemotely(chatId: Long): AppResult<Unit> {
        if (channelDao.all().size <= 1) {
            return AppResult.Failure(AppError.LastDriveRemaining)
        }
        return deleteConfirmed(chatId)
    }

    private suspend fun deleteConfirmed(chatId: Long): AppResult<Unit> = runTelegram {
        telegramClient.deleteStorageChannel(chatId)
        forget(chatId)
        AppResult.Success(Unit)
    }

    /**
     * Folder selection used to live in the device preferences. The first drive
     * to ask adopts whatever was configured there, so an upgrade keeps backing
     * up the folders the user already picked.
     */
    override suspend fun backupFolders(chatId: Long): Set<String> {
        val row = channelDao.byId(chatId) ?: return emptySet()
        val stored = row.backupFolders.toFolderSet()
        if (stored.isNotEmpty()) return stored

        val legacy = settingsRepository.preferences.first().backupFolders
        if (legacy.isEmpty()) return emptySet()
        setBackupFolders(chatId, legacy)
        SafeLog.d(TAG, "Adopted ${legacy.size} backup folders from device settings")
        return legacy
    }

    override suspend fun setBackupFolders(chatId: Long, folders: Set<String>) {
        channelDao.setBackupFolders(chatId, folders.joinToString(FOLDER_SEPARATOR))
    }

    /** Channel pictures make drives recognisable at a glance in the picker. */
    private suspend fun refreshPhoto(chatId: Long) {
        val cached = channelDao.byId(chatId)?.photoPath
        if (cached != null && File(cached).exists()) return
        val path = runCatching { telegramClient.fetchChannelPhoto(chatId) }.getOrNull()
        if (path != channelDao.byId(chatId)?.photoPath) {
            channelDao.setPhotoPath(chatId, path)
        }
    }

    private suspend fun seedDefaultsOnce(chatId: Long) {
        if (channelDao.byId(chatId)?.defaultsSeeded != false) return
        exclusionRepository.ensureDefaults(chatId)
        channelDao.markDefaultsSeeded(chatId)
    }

    private suspend fun forget(chatId: Long) {
        channelDao.deleteFiles(chatId)
        channelDao.deleteFolders(chatId)
        channelDao.delete(chatId)
        if (settingsRepository.preferences.first().storageChatId == chatId) {
            val fallback = channelDao.all().firstOrNull()
            settingsRepository.update { it.copy(storageChatId = fallback?.chatId) }
            fallback?.let { channelOwnership.claimUnowned(it.chatId) }
        }
        SafeLog.d(TAG, "Removed a drive from this device")
    }

    private suspend fun StorageChannelEntity.toDomain(activeId: Long?) = DriveChannel(
        chatId = chatId,
        title = title,
        fileCount = channelDao.fileCount(chatId),
        remoteFileCount = remoteFileCount,
        storedBytes = channelDao.storedBytes(chatId),
        backupFolders = backupFolders.toFolderSet(),
        photoPath = photoPath?.takeIf { File(it).exists() },
        isActive = chatId == activeId,
        isIndexed = lastOpenedAt > 0 || channelDao.fileCount(chatId) > 0,
        lastOpenedAt = lastOpenedAt
    )

    private fun String.toFolderSet(): Set<String> =
        split(FOLDER_SEPARATOR).filter { it.isNotBlank() }.toSet()

    private inline fun <T> runTelegram(block: () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (e: TelegramException) {
        AppResult.Failure(
            if (e.isChannelLimit) {
                AppError.ChannelLimitReached
            } else if (e.isRateLimit) {
                AppError.RateLimited(e.retryAfterSeconds ?: 0)
            } else {
                AppError.TelegramError(e.code, e.message)
            }
        )
    }

    private companion object {
        const val TAG = "ChannelRepository"
        const val DISCOVERY_ATTEMPTS = 4
        const val DISCOVERY_RETRY_MS = 1_500L
        const val FOLDER_SEPARATOR = "\n"
    }
}
