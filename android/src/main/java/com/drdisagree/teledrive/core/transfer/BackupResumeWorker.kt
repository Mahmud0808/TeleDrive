package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class BackupResumeWorker(
    appContext: Context,
    params: WorkerParameters,
    private val transferScheduler: TransferScheduler,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        transferScheduler.kick(settingsRepository.preferences.first().allowMeteredTransfers)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "held_backup_resume"
    }
}
