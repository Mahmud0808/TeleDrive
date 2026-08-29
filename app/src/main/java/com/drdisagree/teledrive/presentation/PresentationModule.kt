package com.drdisagree.teledrive.presentation

import com.drdisagree.teledrive.presentation.di.sharedUiModule
import com.drdisagree.teledrive.presentation.platform.AndroidStandardFolderPaths
import com.drdisagree.teledrive.presentation.platform.StandardFolderPaths
import com.drdisagree.teledrive.presentation.preview.PreviewContentResolver
import com.drdisagree.teledrive.presentation.preview.PreviewViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val presentationModule = module {
    singleOf(::AndroidStandardFolderPaths) bind StandardFolderPaths::class
    includes(sharedUiModule)
    singleOf(::PreviewContentResolver)
    viewModelOf(::AppViewModel)
    viewModelOf(::PreviewViewModel)
}
