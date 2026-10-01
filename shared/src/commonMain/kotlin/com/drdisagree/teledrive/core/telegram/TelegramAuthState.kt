package com.drdisagree.teledrive.core.telegram

sealed interface TelegramAuthState {

    data object Uninitialized : TelegramAuthState

    data object Initializing : TelegramAuthState

    data object WaitingForPhoneNumber : TelegramAuthState

    data class WaitingForCode(
        val phoneNumber: String,
        val channel: CodeDeliveryChannel,
        val codeLength: Int?,
        val resendTimeoutSeconds: Int
    ) : TelegramAuthState

    data class WaitingForQrScan(val link: String) : TelegramAuthState

    data object WaitingForEmailAddress : TelegramAuthState

    data class WaitingForEmailCode(
        val emailPattern: String,
        val codeLength: Int?
    ) : TelegramAuthState

    data class WaitingForPassword(
        val passwordHint: String?
    ) : TelegramAuthState

    /** Account creation is out of scope; the user registers with an official client. */
    data object RegistrationRequired : TelegramAuthState

    data class Failed(val message: String) : TelegramAuthState

    data object Ready : TelegramAuthState

    data object LoggingOut : TelegramAuthState

    data object Closed : TelegramAuthState
}
