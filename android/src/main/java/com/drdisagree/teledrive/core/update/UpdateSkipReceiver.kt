package com.drdisagree.teledrive.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Silences one version. Later releases are announced as usual, and a manual
 * check still reports the skipped one, so this hides a reminder rather than
 * hiding the update itself.
 */
class UpdateSkipReceiver : BroadcastReceiver(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val version = intent.getStringExtra(AppNotifications.EXTRA_VERSION).orEmpty()
        if (version.isBlank()) return

        NotificationManagerCompat.from(context)
            .cancel(AppNotifications.NOTIFICATION_ID_UPDATE)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                settingsRepository.update { it.copy(skippedUpdateVersion = version) }
            } finally {
                pending.finish()
            }
        }
    }
}
