package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.domain.repository.BackupRepository
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val transferModule = module {
    singleOf(::BackupSessionTracker)
    singleOf(::MaintenanceScheduler)
    singleOf(::MediaTriggerScheduler)
    singleOf(::PartDownloader)
    singleOf(::PartUploader)
    singleOf(::TransferExecutor)
    singleOf(::TransferScheduler)
    single { MediaStoreWatcher(androidContext(), get(), lazy { get<BackupRepository>() }) }
    workerOf(::CacheCleanupWorker)
    workerOf(::MediaSweepWorker)
    workerOf(::MediaWatchWorker)
    workerOf(::ScheduledBackupWorker)
    workerOf(::TransferQueueWorker)
    workerOf(::TrashCleanupWorker)
}
