package com.drdisagree.teledrive.core.files

import android.os.Environment
import androidx.annotation.StringRes
import com.drdisagree.teledrive.R

/**
 * Well-known media folders offered as one-tap backup sources. Paths are
 * resolved at runtime because the external storage root differs per device.
 */
enum class StandardBackupFolder(
    @param:StringRes val labelRes: Int,
    private val directory: String
) {
    CAMERA(R.string.backup_folder_camera, Environment.DIRECTORY_DCIM),
    PICTURES(R.string.backup_folder_pictures, Environment.DIRECTORY_PICTURES),
    MOVIES(R.string.backup_folder_movies, Environment.DIRECTORY_MOVIES),
    DOWNLOADS(R.string.backup_folder_downloads, Environment.DIRECTORY_DOWNLOADS),
    DOCUMENTS(R.string.backup_folder_documents, Environment.DIRECTORY_DOCUMENTS);

    val path: String
        get() = Environment.getExternalStoragePublicDirectory(directory).absolutePath

    companion object {
        fun pathsOf(vararg folders: StandardBackupFolder): Set<String> =
            folders.map { it.path }.toSet()

        fun isStandard(path: String): Boolean = entries.any { it.path == path }
    }
}
