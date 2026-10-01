package com.drdisagree.teledrive.core.files

/**
 * [relativeFolder] is empty for a plain pick, or the path under the picked folder so the drive can
 * mirror it.
 */
data class ImportSource(
    val reference: String,
    val relativeFolder: String
)
