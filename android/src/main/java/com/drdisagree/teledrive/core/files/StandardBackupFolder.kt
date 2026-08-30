package com.drdisagree.teledrive.core.files

import android.os.Environment
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.backup_folder_camera
import com.drdisagree.teledrive.resources.backup_folder_documents
import com.drdisagree.teledrive.resources.backup_folder_downloads
import com.drdisagree.teledrive.resources.backup_folder_movies
import com.drdisagree.teledrive.resources.backup_folder_pictures
import org.jetbrains.compose.resources.StringResource

/**
 * Well-known media folders offered as one-tap backup sources. Paths are
 * resolved at runtime because the external storage root differs per device.
 */
enum class StandardBackupFolder(
    val labelRes: StringResource,
    private val directory: String
) {
    CAMERA(Res.string.backup_folder_camera, Environment.DIRECTORY_DCIM),
    PICTURES(Res.string.backup_folder_pictures, Environment.DIRECTORY_PICTURES),
    MOVIES(Res.string.backup_folder_movies, Environment.DIRECTORY_MOVIES),
    DOWNLOADS(Res.string.backup_folder_downloads, Environment.DIRECTORY_DOWNLOADS),
    DOCUMENTS(Res.string.backup_folder_documents, Environment.DIRECTORY_DOCUMENTS);

    val path: String
        get() = Environment.getExternalStoragePublicDirectory(directory).absolutePath

    companion object {
        fun pathsOf(vararg folders: StandardBackupFolder): Set<String> =
            folders.map { it.path }.toSet()

        fun isStandard(path: String): Boolean = entries.any { it.path == path }
    }
}
