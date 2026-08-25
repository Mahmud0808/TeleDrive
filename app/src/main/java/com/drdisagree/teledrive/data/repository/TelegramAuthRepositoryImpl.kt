package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.core.telegram.TelegramCredentials
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramUser
import com.drdisagree.teledrive.domain.model.CountryList
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramAuthRepositoryImpl @Inject constructor(
    private val telegramClient: TelegramClient,
    private val settingsRepository: SettingsRepository
) : TelegramAuthRepository {

    override val authState: StateFlow<TelegramAuthState> = telegramClient.authState

    override val connectionState: StateFlow<TelegramConnectionState> =
        telegramClient.connectionState

    override fun hasCredentials(): Flow<Boolean> = flow {
        emit(settingsRepository.getTelegramCredentials() != null)
    }

    override suspend fun configure(credentials: TelegramCredentials): AppResult<Unit> {
        if (credentials.apiId <= 0 || credentials.apiHash.length < 16) {
            return AppResult.Failure(
                AppError.InvalidApiCredentials
            )
        }
        settingsRepository.setTelegramCredentials(credentials)
        return runTelegram { telegramClient.start(credentials) }
    }

    override suspend fun startFromStoredCredentials(): AppResult<Boolean> {
        val credentials = settingsRepository.getTelegramCredentials()
            ?: return if (settingsRepository.hasStoredTelegramCredentials()) {
                AppResult.Failure(AppError.InvalidApiCredentials)
            } else {
                AppResult.Success(false)
            }
        return when (val result = runTelegram { telegramClient.start(credentials) }) {
            is AppResult.Success -> AppResult.Success(true)
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    override suspend fun countries(): AppResult<CountryList> = try {
        val all = telegramClient.countries()
        val detectedCode = runCatching { telegramClient.detectedCountryCode() }.getOrNull()
        AppResult.Success(
            CountryList(
                countries = all,
                detected = detectedCode?.let { code ->
                    all.firstOrNull { it.isoCode.equals(code, ignoreCase = true) }
                }
            )
        )
    } catch (e: TelegramException) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun submitPhoneNumber(phoneNumber: String): AppResult<Unit> =
        runTelegram { telegramClient.submitPhoneNumber(phoneNumber) }

    override suspend fun requestQrCodeAuthentication(): AppResult<Unit> =
        runTelegram { telegramClient.requestQrCodeAuthentication() }

    override suspend fun restartAuthentication(): AppResult<Unit> =
        runTelegram { telegramClient.restartAuthentication() }

    override suspend fun submitEmailAddress(email: String): AppResult<Unit> =
        runTelegram { telegramClient.submitEmailAddress(email) }

    override suspend fun submitEmailCode(code: String): AppResult<Unit> =
        runTelegram { telegramClient.submitEmailCode(code) }

    override suspend fun submitCode(code: String): AppResult<Unit> =
        runTelegram { telegramClient.submitCode(code) }

    override suspend fun submitPassword(password: String): AppResult<Unit> =
        runTelegram { telegramClient.submitPassword(password) }

    override suspend fun resendCode(): AppResult<Unit> =
        runTelegram { telegramClient.resendCode() }

    override suspend fun logout(): AppResult<Unit> =
        runTelegram { telegramClient.logout() }

    override suspend fun getCurrentUser(): AppResult<TelegramUser> = try {
        AppResult.Success(telegramClient.getCurrentUser())
    } catch (e: TelegramException) {
        AppResult.Failure(e.toAppError())
    }

    private suspend inline fun runTelegram(block: suspend () -> Unit): AppResult<Unit> = try {
        block()
        AppResult.Success(Unit)
    } catch (e: TelegramException) {
        AppResult.Failure(e.toAppError())
    }

    private fun TelegramException.toAppError(): AppError = when {
        isRateLimit -> AppError.RateLimited(retryAfterSeconds ?: 0)
        code == 401 -> AppError.AuthenticationRequired
        message.contains("PHONE_NUMBER_INVALID") ->
            AppError.InvalidPhoneNumber

        message.contains("PHONE_CODE_INVALID") ->
            AppError.IncorrectCode

        message.contains("PASSWORD_HASH_INVALID") ->
            AppError.IncorrectPassword

        message.contains("API_ID_INVALID") ->
            AppError.InvalidApiCredentials

        else -> AppError.TelegramError(code, message)
    }
}
