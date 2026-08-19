package com.drdisagree.teledrive.core.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reschedules periodic work after a reboot. WorkManager re-registers its own
 * periodic jobs, so this receiver only exists to trigger process start early
 * enough for pending transfer recovery.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    }
}
