package com.drdisagree.teledrive.core.publish

import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val publishModule = module {
    singleOf(::PublishScheduler)
    workerOf(::PublishOutboxWorker)
}
