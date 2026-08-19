package com.drdisagree.teledrive.presentation.files

/** One step of the folder path shown in the toolbar. A null id is the root. */
data class FolderCrumb(
    val id: String?,
    val name: String
)
