package com.drdisagree.teledrive.core.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Looks for a release while the app is closed. Only the notification comes from
 * here: tapping it opens the app, which checks again and shows the dialog, so
 * the release notes are never served from a stale copy.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val settingsRepository: SettingsRepository,
    private val appNotifications: AppNotifications
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = settingsRepository.preferences.first()
        if (!prefs.onboardingComplete || !prefs.updateCheckEnabled) return Result.success()

        val release = updateChecker.newerRelease()
        settingsRepository.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
        if (release == null) return Result.success()
        if (release.version == prefs.notifiedUpdateVersion) return Result.success()
        if (release.version == prefs.skippedUpdateVersion) return Result.success()

        appNotifications.createChannels()
        appNotifications.notifyUpdate(
            title = applicationContext.getString(R.string.notification_update_title),
            message = applicationContext.getString(
                R.string.notification_update_message,
                applicationContext.getString(R.string.app_name),
                release.version
            ),
            version = release.version
        )
        settingsRepository.update { it.copy(notifiedUpdateVersion = release.version) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "update-check"
    }
}
