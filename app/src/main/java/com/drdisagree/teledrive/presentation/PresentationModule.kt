package com.drdisagree.teledrive.presentation

import com.drdisagree.teledrive.presentation.di.sharedUiModule
import com.drdisagree.teledrive.presentation.platform.AndroidPlatformCapabilities
import com.drdisagree.teledrive.presentation.platform.AndroidStandardFolderPaths
import com.drdisagree.teledrive.presentation.platform.PlatformCapabilities
import com.drdisagree.teledrive.presentation.platform.StandardFolderPaths
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val presentationModule = module {
    singleOf(::AndroidStandardFolderPaths) bind StandardFolderPaths::class
    singleOf(::AndroidPlatformCapabilities) bind PlatformCapabilities::class
    includes(sharedUiModule)
}
