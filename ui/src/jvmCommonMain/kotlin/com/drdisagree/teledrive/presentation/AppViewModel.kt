package com.drdisagree.teledrive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.core.security.AppLockManager
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.domain.model.AppTheme
import com.drdisagree.teledrive.domain.model.BackupTrigger
import com.drdisagree.teledrive.domain.model.LayoutDensity
import com.drdisagree.teledrive.domain.repository.BackupRepository
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.drdisagree.teledrive.core.update.AppRelease
import com.drdisagree.teledrive.core.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.data.repository.LocalDataWiper
import kotlinx.coroutines.NonCancellable
import com.drdisagree.teledrive.domain.model.AppLanguage
import kotlinx.coroutines.withContext

data class AppUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val dynamicColor: Boolean = true,
    val compactLayout: Boolean = false,
    val locked: Boolean = false
)

class AppViewModel(
    private val settingsRepository: SettingsRepository,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val syncRepository: SyncRepository,
    private val backupRepository: BackupRepository,
    private val channelRepository: ChannelRepository,
    private val appLockManager: AppLockManager,
    private val localDataWiper: LocalDataWiper,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _sessionBroken = MutableStateFlow(false)
    val sessionBroken: StateFlow<Boolean> = _sessionBroken.asStateFlow()

    private val _driveMissing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val driveMissing = _driveMissing.asSharedFlow()

    private val _pendingUpdate = MutableStateFlow<AppRelease?>(null)
    val pendingUpdate: StateFlow<AppRelease?> = _pendingUpdate.asStateFlow()

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.preferences,
        appLockManager.locked
    ) { prefs, locked ->
        AppUiState(
            loading = false,
            onboardingComplete = prefs.onboardingComplete,
            theme = prefs.theme,
            language = prefs.language,
            dynamicColor = prefs.dynamicColor,
            compactLayout = prefs.layoutDensity == LayoutDensity.COMPACT,
            locked = locked
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        viewModelScope.launch {
            appLockManager.onAppStarted()
            backupRepository.refreshActiveSession()
            val started = telegramAuthRepository.startFromStoredCredentials()
            if (started is AppResult.Failure) _sessionBroken.value = true
        }
        checkForUpdate()
        combine(
            telegramAuthRepository.authState.map { it == TelegramAuthState.Ready },
            uiState.map { it.onboardingComplete }
        ) { ready, onboarded -> ready && onboarded }
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                channelRepository.refreshKnown()
                if (channelRepository.activeDriveMissing()) {
                    _driveMissing.tryEmit(Unit)
                    return@onEach
                }
                syncRepository.syncOnStart()
                catchUpBackup()
            }
            .launchIn(viewModelScope)
    }

    /** Folders live with the active drive, so the check has to ask the channel. */
    private suspend fun catchUpBackup() {
        val chatId = settingsRepository.preferences.first().storageChatId ?: return
        if (channelRepository.backupFolders(chatId).isEmpty()) return
        backupRepository.startBackup(BackupTrigger.SCHEDULED)
    }

    fun onAppStopped() = appLockManager.onAppStopped()

    fun onAppStarted() {
        viewModelScope.launch { appLockManager.onAppStarted() }
    }

    /**
     * Runs at most once a day whatever the notification setting says;
     * [force] is for the notification tap, which asks for the answer now.
     */
    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            val prefs = settingsRepository.preferences.first()
            if (!prefs.onboardingComplete) return@launch
            val elapsed = System.currentTimeMillis() - prefs.lastUpdateCheckAt
            if (!force && elapsed < UPDATE_CHECK_INTERVAL_MS) return@launch

            settingsRepository.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
            val release = updateChecker.newerRelease()
            if (!force && release?.version == prefs.skippedUpdateVersion) return@launch
            _pendingUpdate.value = release
        }
    }

    /** Drops the unusable session and sends the user back to signing in. */
    fun resetSession() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                telegramAuthRepository.logout()
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
            _sessionBroken.value = false
        }
    }

    fun dismissUpdate() {
        _pendingUpdate.value = null
    }

    fun unlock() = appLockManager.unlock()

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
