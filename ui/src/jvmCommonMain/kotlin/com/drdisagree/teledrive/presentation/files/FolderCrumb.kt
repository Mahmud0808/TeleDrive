package com.drdisagree.teledrive.presentation.files

import com.drdisagree.teledrive.presentation.common.UiText

/** One step of the folder path shown in the toolbar. A null id is the root. */
data class FolderCrumb(
    val id: String?,
    val name: UiText
)
