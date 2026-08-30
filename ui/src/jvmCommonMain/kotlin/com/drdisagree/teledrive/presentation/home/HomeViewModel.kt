package com.drdisagree.teledrive.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.home_backing_up_new_files
import com.drdisagree.teledrive.resources.home_nothing_new_to_back_up
import com.drdisagree.teledrive.resources.home_queued_files
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.core.permissions.AppPermission
import com.drdisagree.teledrive.core.permissions.PermissionChecker
import com.drdisagree.teledrive.core.security.AppLockManager
import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.repository.ActiveChannel
import com.drdisagree.teledrive.domain.model.BackupSession
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.BackupTrigger
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.StorageSlice
import com.drdisagree.teledrive.domain.repository.BackupRepository
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.presentation.common.UiText
import com.drdisagree.teledrive.presentation.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val connection: TelegramConnectionState = TelegramConnectionState.CONNECTING,
    val offline: Boolean = false,
    val totalFiles: Int = 0,
    val remoteBytes: Long = 0,
    val backedUpCount: Int = 0,
    val pendingCount: Int = 0,
    val localOnlyCount: Int = 0,
    val failedCount: Int = 0,
    val recentFiles: List<DriveFile> = emptyList(),
    val favoriteFolders: List<DriveFolder> = emptyList(),
    val activeBackup: BackupSession? = null,
    val missingPermissions: List<AppPermission> = emptyList(),
    val backupFoldersSelected: Boolean = true,
    val showArchivedSection: Boolean = false,
    val showHiddenSection: Boolean = false,
    val showRecentSection: Boolean = true,
    val rebuilding: Boolean = false,
    val appLockEnabled: Boolean = false,
    val activeTransferCount: Int = 0,
    val activeChannel: DriveChannel? = null,
    val storage: List<StorageSlice> = emptyList(),
    val autoBackupEnabled: Boolean = false,
    val lastBackupAt: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository,
    private val backupRepository: BackupRepository,
    private val syncRepository: SyncRepository,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val networkMonitor: NetworkMonitor,
    private val permissionChecker: PermissionChecker,
    private val appLockManager: AppLockManager,
    private val settingsRepository: SettingsRepository,
    private val fileDao: FileDao,
    private val activeChannel: ActiveChannel,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val missingPermissions = MutableStateFlow(permissionChecker.missingCritical())
    private val _scanning = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)

    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    /** The drive that is open right now, which owns its own folder selection. */
    private val activeDrive: Flow<DriveChannel?> = activeChannel.observe()
        .flatMapLatest { chatId ->
            channelRepository.observeChannels().map { channels ->
                channels.firstOrNull { it.chatId == chatId }
            }
        }

    private val counts: Flow<HomeCounts> = activeChannel.observe().flatMapLatest { chatId ->
        combine(
            combine(
                fileDao.observeCountByBackupState(BackupState.BACKED_UP, chatId),
                fileDao.observeCountByBackupState(BackupState.QUEUED, chatId),
                fileDao.observeCountByBackupState(BackupState.FAILED, chatId),
                fileDao.observeLocalOnlyCount(chatId)
            ) { backed, pending, failed, localOnly ->
                listOf(backed, pending, failed, localOnly)
            },
            fileDao.observeFileCount(chatId),
            fileDao.observeRemoteBytes(chatId)
        ) { states, total, bytes ->
            HomeCounts(
                total = total,
                remoteBytes = bytes,
                backedUp = states[0],
                pending = states[1],
                failed = states[2],
                localOnly = states[3]
            )
        }
    }

    private val countsAndStorage: Flow<Triple<HomeCounts, List<StorageSlice>, Long?>> = combine(
        counts,
        fileRepository.observeStorageByCategory(),
        backupRepository.observeLastBackupAt()
    ) { totals, slices, lastBackupAt -> Triple(totals, slices, lastBackupAt) }

    val uiState: StateFlow<HomeUiState> = combine(
        countsAndStorage,
        fileRepository.observeRecent(12),
        fileRepository.observeFavoriteFolders(),
        transferRepository.observeActiveCount(),
        combine(
            backupRepository.observeActiveSession(),
            telegramAuthRepository.connectionState,
            networkMonitor.status,
            missingPermissions,
            combine(
                settingsRepository.preferences,
                syncRepository.syncing,
                activeDrive
            ) { prefs, syncing, drive -> Triple(prefs, syncing, drive) }
        ) { session, connection, network, missing, prefsAndSync ->
            val (prefs, syncing, drive) = prefsAndSync
            HomeMisc(
                syncing,
                session,
                connection,
                network,
                missing,
                drive,
                prefs.autoBackupEnabled,
                prefs.appLockEnabled,
                prefs.showArchivedFiles,
                prefs.showHiddenFiles,
                prefs.showRecentFiles
            )
        }
    ) { countsWithStorage, recents, favorites, activeTransfers, misc ->
        val (counts, storage, lastBackupAt) = countsWithStorage
        val (syncing, session, connection, network, missing, drive) = misc
        val appLockEnabled = misc.appLockEnabled
        val showArchived = misc.showArchivedSection
        val showHidden = misc.showHiddenSection
        HomeUiState(
            loading = false,
            connection = connection,
            offline = network == NetworkStatus.UNAVAILABLE,
            totalFiles = counts.total,
            remoteBytes = counts.remoteBytes,
            backedUpCount = counts.backedUp,
            pendingCount = counts.pending,
            localOnlyCount = counts.localOnly,
            failedCount = counts.failed,
            recentFiles = recents,
            favoriteFolders = favorites,
            activeBackup = session,
            missingPermissions = missing,
            backupFoldersSelected = drive?.backupFolders?.isNotEmpty() == true,
            activeChannel = drive,
            storage = storage,
            autoBackupEnabled = misc.autoBackupEnabled,
            lastBackupAt = lastBackupAt,
            appLockEnabled = appLockEnabled,
            rebuilding = syncing,
            showArchivedSection = showArchived,
            showHiddenSection = showHidden,
            showRecentSection = misc.showRecentSection,
            activeTransferCount = activeTransfers
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Queues the files sitting on this device alone, which a folder scan never
     * reaches: anything added by hand, or canceled or failed on its way up.
     */
    fun backUpPending() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val message = when (val result = transferRepository.enqueuePendingUploads()) {
                is AppResult.Success -> if (result.value == 0) {
                    UiText.Resource(Res.string.home_nothing_new_to_back_up)
                } else {
                    UiText.PluralResource(Res.plurals.home_queued_files, result.value,
                        result.value
                    )
                }

                is AppResult.Failure -> result.error.toUiText()
            }
            _scanning.value = false
            _messages.tryEmit(message)
        }
    }

    fun scanNow() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val message = when (val result = backupRepository.startBackup(BackupTrigger.MANUAL)) {
                is AppResult.Success ->
                    if (result.value == null) {
                        UiText.Resource(Res.string.home_nothing_new_to_back_up)
                    } else {
                        UiText.Resource(Res.string.home_backing_up_new_files)
                    }

                is AppResult.Failure -> result.error.toUiText()
            }
            _scanning.value = false
            _messages.tryEmit(message)
        }
    }

    fun pauseBackup(sessionId: String) {
        viewModelScope.launch { backupRepository.pauseBackup(sessionId) }
    }

    fun resumeBackup(sessionId: String) {
        viewModelScope.launch { backupRepository.resumeBackup(sessionId) }
    }

    fun cancelBackup(sessionId: String) {
        viewModelScope.launch { backupRepository.cancelBackup(sessionId) }
    }

    fun lockNow() = appLockManager.lockNow()

    fun refreshPermissions() {
        missingPermissions.value = permissionChecker.missingCritical()
    }

    private data class HomeMisc(
        val syncing: Boolean,
        val session: BackupSession?,
        val connection: TelegramConnectionState,
        val network: NetworkStatus,
        val missing: List<AppPermission>,
        val activeDrive: DriveChannel?,
        val autoBackupEnabled: Boolean,
        val appLockEnabled: Boolean,
        val showArchivedSection: Boolean,
        val showHiddenSection: Boolean,
        val showRecentSection: Boolean
    )

    private data class HomeCounts(
        val total: Int,
        val remoteBytes: Long,
        val backedUp: Int,
        val pending: Int,
        val failed: Int,
        val localOnly: Int = 0
    )
}
