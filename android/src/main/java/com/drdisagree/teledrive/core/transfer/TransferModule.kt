package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.domain.repository.BackupRepository
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val transferModule = module {
    single<TransferErrorMessages> { ResourceTransferErrorMessages(androidContext()) }
    singleOf(::NotifyingBackupSessionTracker) bind BackupSessionTracker::class
    singleOf(::WorkMaintenanceScheduler) bind MaintenanceScheduler::class
    singleOf(::MediaTriggerScheduler)
    singleOf(::WorkTransferScheduler) bind TransferScheduler::class
    single { MediaStoreWatcher(androidContext(), get(), lazy { get<BackupRepository>() }) }
    workerOf(::CacheCleanupWorker)
    workerOf(::MediaSweepWorker)
    workerOf(::MediaWatchWorker)
    workerOf(::ScheduledBackupWorker)
    workerOf(::TransferQueueWorker)
    workerOf(::TrashCleanupWorker)
}
