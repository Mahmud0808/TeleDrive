package com.drdisagree.teledrive.core.telegram

/** Never logged and never bundled with the app. */
data class TelegramCredentials(
    val apiId: Int,
    val apiHash: String
) {
    override fun toString(): String = "TelegramCredentials(apiId=***, apiHash=***)"
}
