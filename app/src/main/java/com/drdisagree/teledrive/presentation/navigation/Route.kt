package com.drdisagree.teledrive.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object Onboarding : Route

    @Serializable
    data object Home : Route

    @Serializable
    data class Files(val folderId: String? = null) : Route

    @Serializable
    data object Gallery : Route

    @Serializable
    data class GalleryAlbum(val folderId: String?, val title: String) : Route

    @Serializable
    /**
     * [fileId] opens the viewer; the rest reproduce the exact list the grid
     * showed, so paging sideways follows the same order the user saw.
     */
    data class Preview(
        val fileId: String,
        val mediaOnly: Boolean = false,
        val folderId: String? = null,
        val filterByFolder: Boolean = false,
        val nameQuery: String? = null,
        val categories: String? = null,
        val favoritesOnly: Boolean = false,
        val hiddenOnly: Boolean = false,
        val archivedOnly: Boolean = false,
        val sortField: String? = null,
        val sortDescending: Boolean = false
    ) : Route

    @Serializable
    data object Transfers : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object Trash : Route

    @Serializable
    data object Channels : Route

    @Serializable
    data class NoteEditor(
        val fileId: String? = null,
        val folderId: String? = null,
        val title: String? = null,
        val sharedText: String? = null
    ) : Route

    @Serializable
    data class Collection(val type: String) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data class SettingsSection(val section: String) : Route

    @Serializable
    data object Exclusions : Route
}
