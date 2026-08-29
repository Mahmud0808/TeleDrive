package com.drdisagree.teledrive.presentation

import com.drdisagree.teledrive.presentation.files.FilesViewModel
import com.drdisagree.teledrive.presentation.home.HomeViewModel
import com.drdisagree.teledrive.presentation.onboarding.OnboardingViewModel
import com.drdisagree.teledrive.presentation.di.sharedUiModule
import com.drdisagree.teledrive.presentation.preview.PreviewContentResolver
import com.drdisagree.teledrive.presentation.preview.PreviewViewModel
import com.drdisagree.teledrive.presentation.settings.ExclusionsViewModel
import com.drdisagree.teledrive.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    includes(sharedUiModule)
    singleOf(::PreviewContentResolver)
    viewModelOf(::AppViewModel)
    viewModelOf(::FilesViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::PreviewViewModel)
    viewModelOf(::ExclusionsViewModel)
    viewModelOf(::SettingsViewModel)
}
