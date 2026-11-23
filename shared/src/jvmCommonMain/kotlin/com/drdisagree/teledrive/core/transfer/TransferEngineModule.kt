package com.drdisagree.teledrive.core.transfer

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val transferEngineModule = module {
    singleOf(::ApkIconUploader)
    singleOf(::PartUploader)
    singleOf(::PartDownloader)
    single { UploadVerifier(get(), get(), get()) }
    singleOf(::DamagedUploads)
    singleOf(::TransferExecutor)
    singleOf(::TransferQueueDrainer)
}
