package com.drdisagree.teledrive.core.files

/**
 * One file to import. [relativeFolder] is empty for a plain pick and carries
 * the path under the chosen folder when a whole folder was picked, so the
 * drive can mirror the structure below the folder the user is looking at.
 */
data class ImportSource(
    val reference: String,
    val relativeFolder: String
)
