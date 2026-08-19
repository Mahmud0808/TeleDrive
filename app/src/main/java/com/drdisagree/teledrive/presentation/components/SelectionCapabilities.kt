package com.drdisagree.teledrive.presentation.components

import com.drdisagree.teledrive.domain.model.DriveFile

data class SelectionCapabilities(
    val canUpload: Boolean = false,
    val canDownload: Boolean = false,
    val canFreeUpSpace: Boolean = false
) {
    companion object {
        fun of(files: List<DriveFile>): SelectionCapabilities = SelectionCapabilities(
            canUpload = files.any { !it.hasRemoteCopy },
            canDownload = files.any { it.hasRemoteCopy && !it.hasLocalCopy },
            canFreeUpSpace = files.any { it.hasRemoteCopy && it.hasLocalCopy }
        )
    }
}
