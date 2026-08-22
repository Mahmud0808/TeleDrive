package com.drdisagree.teledrive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.core.security.AppLockManager
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.domain.model.AppTheme
import com.drdisagree.teledrive.domain.model.LayoutDensity
import com.drdisagree.teledrive.domain.repository.BackupRepository
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import com.drdisagree.teledrive.domain.model.BackupTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AppUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = true,
    val compactLayout: Boolean = false,
    val locked: Boolean = false
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val syncRepository: SyncRepository,
    private val backupRepository: BackupRepository,
    private val channelRepository: ChannelRepository,
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val _driveMissing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val driveMissing = _driveMissing.asSharedFlow()

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.preferences,
        appLockManager.locked
    ) { prefs, locked ->
        AppUiState(
            loading = false,
            onboardingComplete = prefs.onboardingComplete,
            theme = prefs.theme,
            dynamicColor = prefs.dynamicColor,
            compactLayout = prefs.layoutDensity == LayoutDensity.COMPACT,
            locked = locked
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        viewModelScope.launch {
            appLockManager.onAppStarted()
            backupRepository.refreshActiveSession()
            telegramAuthRepository.startFromStoredCredentials()
        }
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

    fun unlock() = appLockManager.unlock()
}
