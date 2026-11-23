package com.drdisagree.teledrive.presentation.files

import com.drdisagree.teledrive.core.files.ImportedFile

data class PendingImport(
    val file: ImportedFile,
    val destination: String?,
    val clashingIds: List<String>
)
