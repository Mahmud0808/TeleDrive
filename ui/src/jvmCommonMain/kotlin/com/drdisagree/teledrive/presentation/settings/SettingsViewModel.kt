package com.drdisagree.teledrive.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.resources.files_removed_local_copies
import com.drdisagree.teledrive.resources.settings_nothing_to_remove
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.about_no_update
import com.drdisagree.teledrive.resources.message_backing_up_new_folder
import com.drdisagree.teledrive.resources.message_cache_cleared
import com.drdisagree.teledrive.resources.message_key_backup_saved
import com.drdisagree.teledrive.resources.message_key_restored
import com.drdisagree.teledrive.resources.message_rebuild_done
import com.drdisagree.teledrive.resources.message_thumbnails_cleared
import com.drdisagree.teledrive.resources.message_wrong_passphrase
import com.drdisagree.teledrive.resources.rebuild_locked_files
import com.drdisagree.teledrive.core.files.DeleteConsentRequest
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.permissions.PermissionChecker
import com.drdisagree.teledrive.core.security.AppLockManager
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.core.telegram.TelegramUser
import com.drdisagree.teledrive.core.transfer.MaintenanceScheduler
import com.drdisagree.teledrive.core.transfer.TransferScheduler
import com.drdisagree.teledrive.data.repository.LocalDataWiper
import com.drdisagree.teledrive.domain.model.BackupTrigger
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.domain.repository.BackupRepository
import com.drdisagree.teledrive.domain.repository.CacheRepository
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.KeyBackupRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import com.drdisagree.teledrive.presentation.common.UiText
import com.drdisagree.teledrive.presentation.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.drdisagree.teledrive.core.update.AppRelease
import com.drdisagree.teledrive.core.update.UpdateChecker

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: AppRelease) : UpdateState
}

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val user: TelegramUser? = null,
    val connection: TelegramConnectionState = TelegramConnectionState.CONNECTING,
    val cacheStats: CacheRepository.CacheStats = CacheRepository.CacheStats(0, 0, 0, 0, 0),
    val syncing: Boolean = false,
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val telegramClient: TelegramClient,
    private val cacheRepository: CacheRepository,
    private val fileRepository: FileRepository,
    private val keyBackupRepository: KeyBackupRepository,
    private val syncRepository: SyncRepository,
    private val localDataWiper: LocalDataWiper,
    private val channelRepository: ChannelRepository,
    private val backupRepository: BackupRepository,
    private val appLockManager: AppLockManager,
    private val maintenanceScheduler: MaintenanceScheduler,
    private val transferScheduler: TransferScheduler,
    val permissionChecker: PermissionChecker,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val user = MutableStateFlow<TelegramUser?>(null)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _keyBackupWorking = MutableStateFlow(false)
    val keyBackupWorking: StateFlow<Boolean> = _keyBackupWorking.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferences,
        user,
        telegramAuthRepository.connectionState,
        cacheRepository.observeStats(),
        syncRepository.syncing
    ) { prefs, currentUser, connection, cacheStats, syncing ->
        SettingsUiState(
            preferences = prefs,
            user = currentUser,
            connection = connection,
            cacheStats = cacheStats,
            syncing = syncing,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            cacheRepository.refreshStats()
            when (val result = telegramAuthRepository.getCurrentUser()) {
                is AppResult.Success -> user.value = result.value
                is AppResult.Failure -> Unit
            }
        }
    }

    fun update(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            val previous = settingsRepository.preferences.first()
            settingsRepository.update(transform)
            val current = settingsRepository.preferences.first()
            if (current.linkPreviews != previous.linkPreviews) {
                cacheRepository.clearLinkThumbnails()
            }
            if (current.allowMeteredTransfers != previous.allowMeteredTransfers) {
                transferScheduler.rekick(current.allowMeteredTransfers)
            }
            if (current.updateCheckEnabled != previous.updateCheckEnabled) {
                maintenanceScheduler.scheduleUpdateCheck(current.updateCheckEnabled)
            }
            backupRepository.syncActiveSessionWithSelection()
            rescheduleWork()
        }
    }

    private suspend fun scanForNewBackupWork() {
        when (val result = backupRepository.startBackup(BackupTrigger.MANUAL)) {
            is AppResult.Success -> result.value?.let {
                _messages.tryEmit(UiText.Resource(Res.string.message_backing_up_new_folder))
            }

            is AppResult.Failure -> Unit
        }
    }

    private suspend fun rescheduleWork() {
        val prefs = settingsRepository.preferences.first()
        maintenanceScheduler.scheduleAll(
            backupEnabled = prefs.autoBackupEnabled && prefs.backupIntervalHours > 0,
            backupIntervalHours = prefs.backupIntervalHours,
            wifiOnly = prefs.backupWifiOnly,
            chargingOnly = prefs.backupChargingOnly,
            instantBackup = prefs.instantBackupEnabled,
            updateChecks = prefs.updateCheckEnabled
        )
    }

    /** Backup folders belong to the active drive, not to the device. */
    val backupFolders: StateFlow<Set<String>> = settingsRepository.preferences
        .map { it.storageChatId }
        .distinctUntilChanged()
        .flatMapLatest { chatId ->
            channelRepository.observeChannels().map { channels ->
                channels.firstOrNull { it.chatId == chatId }?.backupFolders.orEmpty()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun addBackupFolder(path: String) {
        editBackupFolders { it + path }
    }

    fun removeBackupFolder(path: String) {
        editBackupFolders { it - path }
    }

    private fun editBackupFolders(transform: (Set<String>) -> Set<String>) {
        viewModelScope.launch {
            val chatId = settingsRepository.preferences.first().storageChatId ?: return@launch
            val before = channelRepository.backupFolders(chatId)
            val after = transform(before)
            if (after == before) return@launch
            channelRepository.setBackupFolders(chatId, after)
            backupRepository.syncActiveSessionWithSelection()
            rescheduleWork()
            if ((after - before).isNotEmpty()) scanForNewBackupWork()
        }
    }

    fun notify(message: UiText) {
        _messages.tryEmit(message)
    }

    /**
     * Saves the key backup and only then turns encryption on, so uploads can
     * never be sealed with a key the user has no way to recover.
     */
    fun backUpEncryptionKey(passphrase: String, hint: String, enableEncryption: Boolean) {
        _keyBackupWorking.value = true
        viewModelScope.launch {
            val result = keyBackupRepository.createBackup(
                passphrase.toCharArray(),
                hint.takeIf { it.isNotBlank() }
            )
            _keyBackupWorking.value = false
            when (result) {
                is AppResult.Success -> {
                    settingsRepository.update {
                        it.copy(
                            keyBackupCreated = true,
                            encryptFiles = if (enableEncryption) true else it.encryptFiles
                        )
                    }
                    _messages.tryEmit(UiText.Resource(Res.string.message_key_backup_saved))
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    /** Only callable once a key backup exists; see [backUpEncryptionKey]. */
    fun enableEncryption() {
        update { it.copy(encryptFiles = true) }
    }

    fun disableEncryption() {
        update { it.copy(encryptFiles = false) }
    }

    private val _keyHint = MutableStateFlow<KeyHint>(KeyHint.Unknown)
    val keyHint: StateFlow<KeyHint> = _keyHint.asStateFlow()

    /** Reads the stored hint so a forgotten passphrase has something to go on. */
    fun loadKeyHint() {
        _keyHint.value = KeyHint.Loading
        viewModelScope.launch {
            _keyHint.value = when (val result = keyBackupRepository.backupHint()) {
                is AppResult.Success -> KeyHint.Loaded(result.value)
                is AppResult.Failure -> KeyHint.Missing
            }
        }
    }

    fun restoreEncryptionKey(passphrase: String) {
        _keyBackupWorking.value = true
        viewModelScope.launch {
            val result = keyBackupRepository.restore(passphrase.toCharArray())
            _keyBackupWorking.value = false
            when (result) {
                is AppResult.Success -> {
                    if (result.value) {
                        settingsRepository.update { it.copy(keyBackupCreated = true) }
                        _messages.tryEmit(UiText.Resource(Res.string.message_key_restored))
                        syncRepository.fullResync()
                    } else {
                        _messages.tryEmit(UiText.Resource(Res.string.message_wrong_passphrase))
                    }
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    val reclaimableBytes: StateFlow<Long> = fileRepository.observeReclaimableBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _deleteConsentRequests = MutableSharedFlow<DeleteConsentRequest>(extraBufferCapacity = 1)
    val deleteConsentRequests = _deleteConsentRequests.asSharedFlow()

    /** Files indexed so far, so a long rebuild shows movement. */
    val indexedSoFar: StateFlow<Int> = syncRepository.indexedSoFar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun freeUpSpace() {
        viewModelScope.launch {
            when (val result = fileRepository.freeUpSpace()) {
                is AppResult.Success -> {
                    val consent = result.value.consentRequest
                    if (consent != null) {
                        _deleteConsentRequests.tryEmit(consent)
                    } else {
                        _messages.tryEmit(
                            if (result.value.deletedCount == 0) {
                                UiText.Resource(Res.string.settings_nothing_to_remove)
                            } else {
                                UiText.Resource(
                                    Res.string.files_removed_local_copies,
                                    result.value.deletedCount
                                )
                            }
                        )
                    }
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cacheRepository.clearAll()
            _messages.tryEmit(UiText.Resource(Res.string.message_cache_cleared))
        }
    }

    fun clearThumbnails() {
        viewModelScope.launch {
            cacheRepository.clearThumbnails()
            _messages.tryEmit(UiText.Resource(Res.string.message_thumbnails_cleared))
        }
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val release = updateChecker.newerRelease()
            settingsRepository.update { it.copy(notifiedUpdateVersion = release?.version ?: "") }
            settingsRepository.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
            _updateState.value = if (release == null) {
                _messages.tryEmit(UiText.Resource(Res.string.about_no_update))
                UpdateState.Idle
            } else {
                UpdateState.Available(release)
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun resync() {
        viewModelScope.launch {
            when (val result = syncRepository.fullResync()) {
                is AppResult.Success -> _messages.tryEmit(
                    if (result.value.lockedFiles > 0) {
                        UiText.PluralResource(Res.plurals.rebuild_locked_files, result.value.lockedFiles,
                            result.value.lockedFiles
                        )
                    } else {
                        UiText.Resource(Res.string.message_rebuild_done,
                            result.value.inserted,
                            result.value.updated,
                            result.value.detachedFromRemote
                        )
                    }
                )

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }


    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            when (val result = telegramAuthRepository.logout()) {
                is AppResult.Success -> {
                    withContext(NonCancellable) {
                        localDataWiper.wipe()
                        settingsRepository.clearTelegramCredentials()
                        settingsRepository.update {
                            it.copy(
                                onboardingComplete = false,
                                storageChatId = null,
                                backupFolders = emptySet()
                            )
                        }
                    }
                    onLoggedOut()
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }
}
