package com.drdisagree.teledrive.presentation.common

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.error_authentication_failed
import com.drdisagree.teledrive.resources.error_backup_running
import com.drdisagree.teledrive.resources.error_channel_limit
import com.drdisagree.teledrive.resources.error_encryption_failed
import com.drdisagree.teledrive.resources.error_file_too_large
import com.drdisagree.teledrive.resources.error_folder_exists
import com.drdisagree.teledrive.resources.error_folder_into_itself
import com.drdisagree.teledrive.resources.error_incorrect_code
import com.drdisagree.teledrive.resources.error_incorrect_password
import com.drdisagree.teledrive.resources.error_insufficient_storage
import com.drdisagree.teledrive.resources.error_invalid_api
import com.drdisagree.teledrive.resources.error_invalid_phone
import com.drdisagree.teledrive.resources.error_key_unreadable
import com.drdisagree.teledrive.resources.error_last_drive
import com.drdisagree.teledrive.resources.error_no_internet
import com.drdisagree.teledrive.resources.error_no_local_copy
import com.drdisagree.teledrive.resources.error_no_remote_copy
import com.drdisagree.teledrive.resources.error_not_found
import com.drdisagree.teledrive.resources.error_not_supported
import com.drdisagree.teledrive.resources.error_permission_denied
import com.drdisagree.teledrive.resources.error_rate_limited
import com.drdisagree.teledrive.resources.error_sign_in_required
import com.drdisagree.teledrive.resources.error_telegram
import com.drdisagree.teledrive.resources.error_timed_out
import com.drdisagree.teledrive.resources.error_transfer_incomplete
import com.drdisagree.teledrive.resources.error_unknown

/** Maps typed errors to short, user-facing text. */
fun AppError.toUiText(): UiText = when (this) {
    is AppError.NetworkUnavailable -> UiText.Resource(Res.string.error_no_internet)
    is AppError.Timeout -> UiText.Resource(Res.string.error_timed_out)
    is AppError.AuthenticationRequired -> UiText.Resource(Res.string.error_sign_in_required)
    is AppError.AuthenticationFailed ->
        reason?.let(UiText::Plain) ?: UiText.Resource(Res.string.error_authentication_failed)

    is AppError.RateLimited -> UiText.Resource(Res.string.error_rate_limited, retryAfterSeconds)
    is AppError.TelegramError -> UiText.Resource(Res.string.error_telegram, message)
    is AppError.KeyUnreadable -> UiText.Resource(Res.string.error_key_unreadable)
    is AppError.FileTooLarge -> UiText.Resource(
        Res.string.error_file_too_large,
        Formatters.bytes(sizeBytes),
        Formatters.bytes(limitBytes)
    )

    is AppError.InsufficientStorage -> UiText.Resource(
        Res.string.error_insufficient_storage,
        Formatters.bytes(requiredBytes),
        Formatters.bytes(availableBytes)
    )

    is AppError.PermissionDenied -> UiText.Resource(Res.string.error_permission_denied)
    is AppError.NotFound -> UiText.Resource(Res.string.error_not_found)
    is AppError.FolderNameTaken -> UiText.Resource(Res.string.error_folder_exists)
    is AppError.FolderInsideItself -> UiText.Resource(Res.string.error_folder_into_itself)
    is AppError.BackupAlreadyRunning -> UiText.Resource(Res.string.error_backup_running)
    is AppError.NoRemoteCopy -> UiText.Resource(Res.string.error_no_remote_copy)
    is AppError.NoLocalCopy -> UiText.Resource(Res.string.error_no_local_copy)
    is AppError.LastDriveRemaining -> UiText.Resource(Res.string.error_last_drive)
    is AppError.ChannelLimitReached -> UiText.Resource(Res.string.error_channel_limit)
    is AppError.InvalidApiCredentials -> UiText.Resource(Res.string.error_invalid_api)
    is AppError.InvalidPhoneNumber -> UiText.Resource(Res.string.error_invalid_phone)
    is AppError.IncorrectCode -> UiText.Resource(Res.string.error_incorrect_code)
    is AppError.IncorrectPassword -> UiText.Resource(Res.string.error_incorrect_password)
    is AppError.UnsupportedOperation ->
        reason?.let(UiText::Plain) ?: UiText.Resource(Res.string.error_not_supported)

    is AppError.EncryptionFailure -> UiText.Resource(Res.string.error_encryption_failed)
    is AppError.TransferFailed ->
        reason?.let(UiText::Plain) ?: UiText.Resource(Res.string.error_transfer_incomplete)

    is AppError.Unknown -> message?.let(UiText::Plain) ?: UiText.Resource(Res.string.error_unknown)
}
