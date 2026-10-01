package com.drdisagree.teledrive.presentation.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** On Android 11+ tg: handlers are only visible through the manifest's queries block. */
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
