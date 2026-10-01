package com.drdisagree.teledrive.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.core.permissions.PermissionChecker
import com.drdisagree.teledrive.core.power.PowerMonitor
import com.drdisagree.teledrive.core.security.AppLockManager
import com.drdisagree.teledrive.core.transfer.BackupGate
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.repository.ActiveChannel
import com.drdisagree.teledrive.domain.model.BackupTrigger
import com.drdisagree.teledrive.domain.model.DriveChannel
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
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.home_backing_up_new_files
import com.drdisagree.teledrive.resources.home_nothing_new_to_back_up
import com.drdisagree.teledrive.resources.home_queued_files
import com.drdisagree.teledrive.resources.home_switched_drive
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val channelRepository: ChannelRepository,
    private val powerMonitor: PowerMonitor
) : ViewModel() {

    private val missingPermissions = MutableStateFlow(permissionChecker.missingCritical())
    private val _scanning = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)

    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    private val activeDrive: Flow<DriveChannel?> = activeChannel.observe()
        .flatMapLatest { chatId ->
            channelRepository.observeChannels().map { channels ->
                channels.firstOrNull { it.chatId == chatId }
            }
        }

    val driveCount: StateFlow<Int> = channelRepository.observeChannels()
        .map { it.size }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    fun cycleDrive(direction: Int) {
        viewModelScope.launch {
            val channels = channelRepository.observeChannels().first()
            if (channels.size < 2) return@launch
            val index = channels.indexOfFirst { it.isActive }
            if (index < 0) return@launch
            val target = channels[(index + direction).mod(channels.size)]
            if (channelRepository.switchTo(target.chatId) is AppResult.Success) {
                _messages.tryEmit(
                    UiText.Resource(Res.string.home_switched_drive, target.displayName)
                )
            }
        }
    }

    @OptIn(FlowPreview::class)
    private val counts: Flow<HomeCounts> = activeChannel.observe().flatMapLatest { chatId ->
        fileDao.observeHomeAggregates(chatId)
            .debounce(COUNTS_DEBOUNCE_MS.milliseconds)
            .distinctUntilChanged()
            .map { aggregates ->
                HomeCounts(
                    total = aggregates.total,
                    remoteBytes = aggregates.remoteBytes,
                    backedUp = aggregates.backedUp,
                    pending = aggregates.queued,
                    failed = aggregates.failed,
                    localOnly = aggregates.localOnly,
                    offlineBytes = aggregates.offlineBytes
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
                activeDrive,
                powerMonitor.charging,
                ::HomeSettings
            )
        ) { session, connection, network, missing, settings ->
            val (prefs, syncing, drive, charging) = settings
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
                prefs.showRecentFiles,
                BackupGate.hold(prefs, network, charging)
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
            activeTransferCount = activeTransfers,
            offlineBytes = counts.offlineBytes,
            backupHold = misc.backupHold
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** A folder scan never reaches files added by hand, or canceled or failed on their way up. */
    fun backUpPending() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val message = when (val result = transferRepository.enqueuePendingUploads()) {
                is AppResult.Success -> if (result.value == 0) {
                    UiText.Resource(Res.string.home_nothing_new_to_back_up)
                } else {
                    UiText.PluralResource(
                        Res.plurals.home_queued_files, result.value,
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

    private companion object {
        const val COUNTS_DEBOUNCE_MS = 300L
    }
}
