package com.drdisagree.teledrive.core.files

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val filesModule = module {
    singleOf(::AndroidStoragePaths) bind AppStoragePaths::class
    singleOf(::DownloadWriter)
    singleOf(::FileImporter)
    singleOf(::MediaStoreLocalCopyDeleter) bind LocalCopyDeleter::class
    singleOf(::NoteStore)
    singleOf(::PendingShare)
    singleOf(::StorageInspector)
}
