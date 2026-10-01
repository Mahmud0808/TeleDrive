package com.drdisagree.teledrive.domain.model

data class DriveChannel(
    val chatId: Long,
    val title: String,
    val fileCount: Int,
    val remoteFileCount: Int,
    val storedBytes: Long,
    val backupFolders: Set<String>,
    val photoPath: String?,
    val isActive: Boolean,
    val isIndexed: Boolean,
    val lastOpenedAt: Long
) {

    /** The part the user chose, empty for a drive that was never named. */
    val label: String
        get() = title.removePrefix(DRIVE_PREFIX).trim()

    val displayName: String
        get() = label.ifEmpty { DRIVE_PREFIX }

    companion object {
        const val DRIVE_PREFIX = "TeleDrive"
    }
}
