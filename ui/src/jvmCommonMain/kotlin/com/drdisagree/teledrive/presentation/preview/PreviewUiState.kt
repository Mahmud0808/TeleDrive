package com.drdisagree.teledrive.presentation.preview

import com.drdisagree.teledrive.domain.model.DriveFile

data class PreviewUiState(
    val files: List<DriveFile> = emptyList(),
    val initialIndex: Int = 0,
    val ready: Boolean = false,
    val closed: Boolean = false
)
