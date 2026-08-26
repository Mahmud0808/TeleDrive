package com.drdisagree.teledrive.core.common

/**
 * Typed error model shared by all layers. Telegram-specific failures are mapped
 * into these types at the data layer so the UI never sees raw TDLib errors.
 */
sealed interface AppError {

    data object NetworkUnavailable : AppError

    data class Timeout(val operation: String? = null) : AppError

    data object AuthenticationRequired : AppError

    data class AuthenticationFailed(val reason: String? = null) : AppError

    data class RateLimited(val retryAfterSeconds: Int) : AppError

    data class TelegramError(val code: Int, val message: String) : AppError

    data object KeyUnreadable : AppError

    data class FileTooLarge(val sizeBytes: Long, val limitBytes: Long) : AppError

    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : AppError

    data class PermissionDenied(val permission: String? = null) : AppError

    data object NotFound : AppError

    data object FolderNameTaken : AppError

    data object FolderInsideItself : AppError

    data object BackupAlreadyRunning : AppError

    data object NoRemoteCopy : AppError

    data object NoLocalCopy : AppError

    data object LastDriveRemaining : AppError

    data object ChannelLimitReached : AppError

    data object InvalidApiCredentials : AppError

    data object InvalidPhoneNumber : AppError

    data object IncorrectCode : AppError

    data object IncorrectPassword : AppError

    data class UnsupportedOperation(val reason: String? = null) : AppError

    data class EncryptionFailure(val stage: String? = null) : AppError

    data class TransferFailed(val reason: String? = null) : AppError

    data class Unknown(val message: String? = null) : AppError
}
