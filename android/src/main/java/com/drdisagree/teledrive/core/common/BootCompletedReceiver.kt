package com.drdisagree.teledrive.core.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Only exists to start the process early enough for pending transfer recovery; WorkManager
 * re-registers its own jobs.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    }
}
