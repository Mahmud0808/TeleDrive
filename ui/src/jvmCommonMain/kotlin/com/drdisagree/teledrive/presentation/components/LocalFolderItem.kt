package com.drdisagree.teledrive.presentation.components

data class LocalFolderItem(
    val name: String,
    val path: String
)

sealed interface FolderListResult {
    data class Success(val items: List<LocalFolderItem>) : FolderListResult
    data object Unreadable : FolderListResult
}
