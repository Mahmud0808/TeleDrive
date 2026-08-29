package com.drdisagree.teledrive.presentation.di

import com.drdisagree.teledrive.presentation.channels.ChannelsViewModel
import com.drdisagree.teledrive.presentation.collection.CollectionViewModel
import com.drdisagree.teledrive.presentation.gallery.GalleryViewModel
import com.drdisagree.teledrive.presentation.note.NoteEditorViewModel
import com.drdisagree.teledrive.presentation.proxy.ProxyViewModel
import com.drdisagree.teledrive.presentation.search.SearchViewModel
import com.drdisagree.teledrive.presentation.transfers.TransfersViewModel
import com.drdisagree.teledrive.presentation.trash.TrashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sharedUiModule = module {
    viewModelOf(::ChannelsViewModel)
    viewModelOf(::CollectionViewModel)
    viewModelOf(::GalleryViewModel)
    viewModelOf(::NoteEditorViewModel)
    viewModelOf(::ProxyViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::TransfersViewModel)
    viewModelOf(::TrashViewModel)
}
