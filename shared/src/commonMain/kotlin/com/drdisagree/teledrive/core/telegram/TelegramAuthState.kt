package com.drdisagree.teledrive.core.telegram

sealed interface TelegramAuthState {

    /** TDLib has not been started yet (no credentials configured). */
    data object Uninitialized : TelegramAuthState

    /** TDLib is starting up or transitioning between states. */
    data object Initializing : TelegramAuthState

    data object WaitingForPhoneNumber : TelegramAuthState

    data class WaitingForCode(
        val phoneNumber: String,
        val channel: CodeDeliveryChannel,
        val codeLength: Int?,
        val resendTimeoutSeconds: Int
    ) : TelegramAuthState

    /** A QR code is on screen for an authorized Telegram app to scan. */
    data class WaitingForQrScan(val link: String) : TelegramAuthState

    /** The account signs in with a login email rather than SMS. */
    data object WaitingForEmailAddress : TelegramAuthState

    data class WaitingForEmailCode(
        val emailPattern: String,
        val codeLength: Int?
    ) : TelegramAuthState

    data class WaitingForPassword(
        val passwordHint: String?
    ) : TelegramAuthState

    /**
     * The phone number has no Telegram account. Account creation is out of
     * scope for this app; the user must register with an official client.
     */
    data object RegistrationRequired : TelegramAuthState

    data class Failed(val message: String) : TelegramAuthState

    data object Ready : TelegramAuthState

    data object LoggingOut : TelegramAuthState

    data object Closed : TelegramAuthState
}
