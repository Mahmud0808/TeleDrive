package com.drdisagree.teledrive.presentation.di

import com.drdisagree.teledrive.presentation.channels.ChannelsViewModel
import com.drdisagree.teledrive.presentation.collection.CollectionViewModel
import com.drdisagree.teledrive.presentation.files.FilesViewModel
import com.drdisagree.teledrive.presentation.gallery.GalleryViewModel
import com.drdisagree.teledrive.presentation.home.HomeViewModel
import com.drdisagree.teledrive.presentation.onboarding.OnboardingViewModel
import com.drdisagree.teledrive.presentation.note.NoteEditorViewModel
import com.drdisagree.teledrive.presentation.proxy.ProxyViewModel
import com.drdisagree.teledrive.presentation.search.SearchViewModel
import com.drdisagree.teledrive.presentation.settings.ExclusionsViewModel
import com.drdisagree.teledrive.presentation.settings.SettingsViewModel
import com.drdisagree.teledrive.presentation.transfers.TransfersViewModel
import com.drdisagree.teledrive.presentation.trash.TrashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sharedUiModule = module {
    viewModelOf(::ChannelsViewModel)
    viewModelOf(::CollectionViewModel)
    viewModelOf(::FilesViewModel)
    viewModelOf(::GalleryViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::NoteEditorViewModel)
    viewModelOf(::ProxyViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ExclusionsViewModel)
    viewModelOf(::TransfersViewModel)
    viewModelOf(::TrashViewModel)
}
