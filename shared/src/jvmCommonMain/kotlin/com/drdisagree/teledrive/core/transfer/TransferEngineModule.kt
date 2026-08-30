package com.drdisagree.teledrive.core.transfer

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val transferEngineModule = module {
    singleOf(::PartUploader)
    singleOf(::PartDownloader)
    singleOf(::TransferExecutor)
    singleOf(::TransferQueueDrainer)
}
