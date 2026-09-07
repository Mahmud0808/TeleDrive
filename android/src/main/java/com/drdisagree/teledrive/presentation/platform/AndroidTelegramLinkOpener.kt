package com.drdisagree.teledrive.presentation.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Visibility of tg: handlers on Android 11+ depends on the queries block in
 * the manifest; without it the probe reports no Telegram app even when one
 * is installed.
 */
class AndroidTelegramLinkOpener(
    private val context: Context
) : TelegramLinkOpener {

    override val canOpenTelegram: Boolean
        get() = probe().resolveActivity(context.packageManager) != null

    override fun open(link: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, link.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    private fun probe() = Intent(Intent.ACTION_VIEW, "tg://login".toUri())
}
