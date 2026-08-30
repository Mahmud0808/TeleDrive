package com.drdisagree.teledrive.core.files

/** [name] is what the drive shows; [path] may carry a staging suffix. */
data class ImportedFile(val path: String, val name: String, val sizeBytes: Long)
