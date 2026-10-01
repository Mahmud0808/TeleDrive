package com.drdisagree.teledrive.presentation.components

sealed interface FolderListResult {
    data class Success(val items: List<LocalFolderItem>) : FolderListResult
    data object Unreadable : FolderListResult
}
