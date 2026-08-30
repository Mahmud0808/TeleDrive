package com.drdisagree.teledrive.core.publish

import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val publishModule = module {
    singleOf(::PublishOutboxDrainer)
    singleOf(::WorkPublishScheduler) bind PublishScheduler::class
    workerOf(::PublishOutboxWorker)
}
