package com.drdisagree.teledrive.core.update

import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val updateModule = module {
    singleOf(::UpdateChecker)
    workerOf(::UpdateCheckWorker)
}
