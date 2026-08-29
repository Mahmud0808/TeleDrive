package com.drdisagree.teledrive.core.files

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val filesModule = module {
    singleOf(::DownloadWriter)
    singleOf(::FileImporter)
    singleOf(::LocalCopyDeleter)
    singleOf(::NoteStore)
    singleOf(::PendingShare)
    singleOf(::StorageInspector)
}
