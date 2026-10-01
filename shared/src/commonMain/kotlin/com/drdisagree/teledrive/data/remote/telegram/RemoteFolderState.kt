package com.drdisagree.teledrive.data.remote.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * File manifests only carry a folder path, so this keeps empty folders, folder ids and flags across
 * a wipe.
 */
@Serializable
data class RemoteFolderState(
    @SerialName("v") val version: Int = VERSION,
    @SerialName("f") val folders: List<RemoteFolderEntry> = emptyList(),
    @SerialName("d") val deleted: List<RemoteFolderTombstone> = emptyList()
) {

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "teledrive.folders.json"
        const val MARKER = "#teledrive-folders"
    }
}
