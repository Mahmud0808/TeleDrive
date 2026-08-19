package com.drdisagree.teledrive.core.telegram

enum class TelegramConnectionState {
    WAITING_FOR_NETWORK,
    CONNECTING,
    UPDATING,
    READY
}
