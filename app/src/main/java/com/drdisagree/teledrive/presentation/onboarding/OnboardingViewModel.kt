package com.drdisagree.teledrive.presentation.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.files.StandardBackupFolder
import com.drdisagree.teledrive.core.telegram.CodeDeliveryChannel
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.core.telegram.TelegramCredentials
import com.drdisagree.teledrive.core.transfer.MaintenanceScheduler
import com.drdisagree.teledrive.domain.model.Country
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import com.drdisagree.teledrive.presentation.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val maintenanceScheduler: MaintenanceScheduler,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        telegramAuthRepository.authState
            .onEach(::onAuthStateChanged)
            .launchIn(viewModelScope)
    }

    private fun onAuthStateChanged(state: TelegramAuthState) {
        _uiState.update { current ->
            when (state) {
                /* Falling back here from a QR wait means the token died, so the
                   cached link is dropped and a fresh one can be asked for. */
                is TelegramAuthState.WaitingForPhoneNumber -> when {
                    current.step == OnboardingStep.API_CREDENTIALS && current.working ->
                        current.copy(step = OnboardingStep.PHONE, working = false, error = null)

                    current.qrLink != null -> current.copy(working = false, qrLink = null)
                    else -> current
                }

                is TelegramAuthState.WaitingForCode -> current.copy(
                    step = OnboardingStep.CODE,
                    working = false,
                    error = null,
                    codePhoneNumber = state.phoneNumber,
                    codeChannel = state.channel,
                    codeLength = state.codeLength
                )
                /* Signing in by QR is a different face of the phone step, not
                   a step of its own, so this only carries the link across. */
                is TelegramAuthState.WaitingForQrScan -> current.copy(
                    working = false,
                    qrLink = state.link
                )

                is TelegramAuthState.WaitingForEmailAddress -> current.copy(
                    step = OnboardingStep.EMAIL_ADDRESS,
                    working = false,
                    error = null
                )

                is TelegramAuthState.WaitingForEmailCode -> current.copy(
                    step = OnboardingStep.EMAIL_CODE,
                    working = false,
                    error = null,
                    codePhoneNumber = state.emailPattern,
                    codeChannel = CodeDeliveryChannel.EMAIL,
                    codeLength = state.codeLength
                )

                is TelegramAuthState.WaitingForPassword -> current.copy(
                    step = OnboardingStep.PASSWORD,
                    working = false,
                    error = null,
                    passwordHint = state.passwordHint
                )

                is TelegramAuthState.RegistrationRequired -> current.copy(
                    working = false,
                    registrationRequired = true,
                    error = context.getString(R.string.onboarding_no_account)
                )

                is TelegramAuthState.Failed -> current.copy(
                    working = false,
                    error = state.message
                )

                is TelegramAuthState.Ready ->
                    // A signed-in user reopening the app resumes past sign-in.
                    if (current.step in listOf(
                            OnboardingStep.WELCOME,
                            OnboardingStep.PHONE,
                            OnboardingStep.CODE,
                            OnboardingStep.PASSWORD,
                            OnboardingStep.API_CREDENTIALS
                        )
                    ) {
                        current.copy(
                            step = OnboardingStep.PERMISSIONS,
                            working = false,
                            error = null
                        )
                    } else current

                else -> current
            }
        }
    }

    fun start() {
        _uiState.update { it.copy(step = OnboardingStep.API_CREDENTIALS, error = null) }
    }

    fun submitCredentials(apiIdText: String, apiHash: String) {
        val apiId = apiIdText.trim().toIntOrNull()
        if (apiId == null || apiId <= 0) {
            _uiState.update { it.copy(error = context.getString(R.string.onboarding_api_id_number)) }
            return
        }
        if (apiHash.trim().length < 16) {
            _uiState.update { it.copy(error = context.getString(R.string.onboarding_api_hash_short)) }
            return
        }
        runAction {
            val result =
                telegramAuthRepository.configure(TelegramCredentials(apiId, apiHash.trim()))
            if (result is AppResult.Success) {
                onAuthStateChanged(telegramAuthRepository.authState.value)
            }
            result
        }
    }

    /**
     * Dialling codes come from Telegram so they stay current and localized.
     * A failure is not surfaced: the field still accepts a full number, and
     * anything that stops this call will stop the login too.
     */
    fun loadCountries() {
        if (_uiState.value.countries.isNotEmpty()) return
        _uiState.update { it.copy(countryLoadState = CountryLoadState.LOADING) }
        viewModelScope.launch {
            when (val result = telegramAuthRepository.countries()) {
                is AppResult.Success -> _uiState.update { current ->
                    current.copy(
                        countries = result.value.countries,
                        selectedCountry = current.selectedCountry ?: result.value.detected,
                        countryLoadState = if (result.value.countries.isEmpty()) {
                            CountryLoadState.FAILED
                        } else {
                            CountryLoadState.READY
                        }
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(countryLoadState = CountryLoadState.FAILED)
                }
            }
        }
    }

    fun selectCountry(country: Country) {
        _uiState.update { it.copy(selectedCountry = country) }
    }

    fun submitPhone(phone: String) {
        if (phone.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.onboarding_enter_phone)) }
            return
        }
        _uiState.update { it.copy(qrLink = null, qrMode = false) }
        runAction { telegramAuthRepository.submitPhoneNumber(phone.trim()) }
    }

    /** Telegram keeps the link fresh by re-emitting the state, so one call is enough. */
    /**
     * TDLib stays in the QR state once asked, and repeating the request from
     * there is an error, so a link already in hand just reopens the page.
     */
    fun startQrLogin() {
        _uiState.update { it.copy(qrMode = true, error = null) }
        if (_uiState.value.qrLink != null) return
        runAction { telegramAuthRepository.requestQrCodeAuthentication() }
    }

    /**
     * Returns to the phone form. TDLib rejects a phone number while it waits
     * for another device to confirm, so the session is restarted to land back
     * on the phone step. The link is dropped with it.
     */
    fun cancelQrLogin() {
        _uiState.update { it.copy(qrMode = false, qrLink = null, error = null) }
        viewModelScope.launch { telegramAuthRepository.restartAuthentication() }
    }

    fun submitEmailAddress(email: String) {
        if (email.isBlank()) return
        runAction { telegramAuthRepository.submitEmailAddress(email.trim()) }
    }

    fun submitEmailCode(code: String) {
        if (code.isBlank()) return
        runAction { telegramAuthRepository.submitEmailCode(code.trim()) }
    }

    fun submitCode(code: String) {
        if (code.isBlank()) return
        runAction { telegramAuthRepository.submitCode(code.trim()) }
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) return
        runAction { telegramAuthRepository.submitPassword(password) }
    }

    fun resendCode() {
        runAction { telegramAuthRepository.resendCode() }
    }

    /**
     * The drive is always shown before setup continues, so the user sees which
     * channel their files will live in. An account with none gets one created.
     */
    fun onPermissionsResolved() {
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch { loadOrCreateDrive() }
    }

    /** Retried from the drive step once the account has room again. */
    fun retryDriveSetup() {
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch { loadOrCreateDrive() }
    }

    private suspend fun loadOrCreateDrive() {
        var channels = refreshChannels()
        var created = false
        var failure: String? = null
        if (channels.isEmpty()) {
            when (val result = channelRepository.create(DEFAULT_DRIVE_LABEL)) {
                is AppResult.Success -> created = true
                is AppResult.Failure -> failure = result.error.toUserMessage(context)
            }
            channels = refreshChannels()
        }
        _uiState.update {
            it.copy(
                step = OnboardingStep.CHANNEL_SELECT,
                channels = channels,
                channelCreated = created,
                selectedChatId = channels.firstOrNull { channel -> channel.isActive }?.chatId
                    ?: channels.firstOrNull()?.chatId,
                error = failure.takeIf { channels.isEmpty() },
                working = false
            )
        }
    }

    private suspend fun refreshChannels(): List<DriveChannel> =
        when (val result = channelRepository.refresh()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> emptyList()
        }

    fun selectChannel(chatId: Long) = _uiState.update { it.copy(selectedChatId = chatId) }

    fun confirmChannel() {
        val chatId = _uiState.value.selectedChatId ?: run {
            _uiState.update { it.copy(step = OnboardingStep.BACKUP_SETUP) }
            return
        }
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            channelRepository.switchTo(chatId, index = false)
            _uiState.update { it.copy(step = OnboardingStep.BACKUP_SETUP, working = false) }
        }
    }

    fun setBackupDcim(enabled: Boolean) = _uiState.update { it.copy(backupDcim = enabled) }

    fun setBackupPictures(enabled: Boolean) = _uiState.update { it.copy(backupPictures = enabled) }

    fun setBackupMovies(enabled: Boolean) = _uiState.update { it.copy(backupMovies = enabled) }

    fun setAutoBackup(enabled: Boolean) = _uiState.update { it.copy(autoBackupEnabled = enabled) }

    fun setWifiOnly(enabled: Boolean) = _uiState.update { it.copy(wifiOnly = enabled) }

    fun finishSetup() {
        val state = _uiState.value
        _uiState.update { it.copy(finishing = true) }
        viewModelScope.launch {
            val folders = buildSet {
                if (state.backupDcim) add(StandardBackupFolder.CAMERA.path)
                if (state.backupPictures) add(StandardBackupFolder.PICTURES.path)
                if (state.backupMovies) add(StandardBackupFolder.MOVIES.path)
            }
            settingsRepository.update {
                it.copy(
                    onboardingComplete = true,
                    autoBackupEnabled = state.autoBackupEnabled,
                    backupWifiOnly = state.wifiOnly
                )
            }
            maintenanceScheduler.scheduleAll(
                backupEnabled = state.autoBackupEnabled,
                backupIntervalHours = 24,
                wifiOnly = state.wifiOnly,
                chargingOnly = false,
                instantBackup = state.autoBackupEnabled
            )
            syncRepository.fullResync()
            channelRepository.refresh()
            settingsRepository.preferences.first().storageChatId?.let { chatId ->
                channelRepository.setBackupFolders(chatId, folders)
            }
            _uiState.update { it.copy(step = OnboardingStep.DONE, finishing = false) }
        }
    }

    private fun runAction(block: suspend () -> AppResult<*>) {
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> _uiState.update {
                    it.copy(working = false, error = result.error.toUserMessage(context))
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_DRIVE_LABEL = ""
    }
}
