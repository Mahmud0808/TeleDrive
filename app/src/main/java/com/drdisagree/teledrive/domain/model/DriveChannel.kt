package com.drdisagree.teledrive.domain.model

/** A Telegram channel this account uses as a drive. */
data class DriveChannel(
    val chatId: Long,
    val title: String,
    val fileCount: Int,
    /** What Telegram holds, which is known even before this device indexes. */
    val remoteFileCount: Int,
    val storedBytes: Long,
    val backupFolders: Set<String>,
    val photoPath: String?,
    val isActive: Boolean,
    val isIndexed: Boolean,
    val lastOpenedAt: Long
) {

    /** Name without the shared drive prefix, which is what tells drives apart. */
    val label: String
        get() = title.removePrefix(DRIVE_PREFIX).trim().ifEmpty { title }

    companion object {
        const val DRIVE_PREFIX = "TeleDrive"
    }
}
