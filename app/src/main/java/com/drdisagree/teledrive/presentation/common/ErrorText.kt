package com.drdisagree.teledrive.presentation.common

import android.content.Context
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppError

/** Maps typed errors to short, user-facing text. */
fun AppError.toUserMessage(context: Context): String = when (this) {
    is AppError.NetworkUnavailable -> context.getString(R.string.error_no_internet)
    is AppError.Timeout -> context.getString(R.string.error_timed_out)
    is AppError.AuthenticationRequired -> context.getString(R.string.error_sign_in_required)
    is AppError.AuthenticationFailed ->
        reason ?: context.getString(R.string.error_authentication_failed)

    is AppError.RateLimited -> context.getString(R.string.error_rate_limited, retryAfterSeconds)
    is AppError.TelegramError -> context.getString(R.string.error_telegram, message)
    is AppError.KeyUnreadable -> context.getString(R.string.error_key_unreadable)
    is AppError.FileTooLarge -> context.getString(
        R.string.error_file_too_large,
        Formatters.bytes(sizeBytes),
        Formatters.bytes(limitBytes)
    )

    is AppError.InsufficientStorage -> context.getString(
        R.string.error_insufficient_storage,
        Formatters.bytes(requiredBytes),
        Formatters.bytes(availableBytes)
    )

    is AppError.PermissionDenied -> context.getString(R.string.error_permission_denied)
    is AppError.NotFound -> context.getString(R.string.error_not_found)
    is AppError.FolderNameTaken -> context.getString(R.string.error_folder_exists)
    is AppError.FolderInsideItself -> context.getString(R.string.error_folder_into_itself)
    is AppError.BackupAlreadyRunning -> context.getString(R.string.error_backup_running)
    is AppError.NoRemoteCopy -> context.getString(R.string.error_no_remote_copy)
    is AppError.NoLocalCopy -> context.getString(R.string.error_no_local_copy)
    is AppError.LastDriveRemaining -> context.getString(R.string.error_last_drive)
    is AppError.ChannelLimitReached -> context.getString(R.string.error_channel_limit)
    is AppError.InvalidApiCredentials -> context.getString(R.string.error_invalid_api)
    is AppError.InvalidPhoneNumber -> context.getString(R.string.error_invalid_phone)
    is AppError.IncorrectCode -> context.getString(R.string.error_incorrect_code)
    is AppError.IncorrectPassword -> context.getString(R.string.error_incorrect_password)
    is AppError.UnsupportedOperation -> reason ?: context.getString(R.string.error_not_supported)
    is AppError.EncryptionFailure -> context.getString(R.string.error_encryption_failed)
    is AppError.TransferFailed -> reason ?: context.getString(R.string.error_transfer_incomplete)
    is AppError.Unknown -> message ?: context.getString(R.string.error_unknown)
}
