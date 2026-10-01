package com.drdisagree.teledrive.data.remote.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Folder tree snapshot stored as a single document in the storage chat. File
 * manifests only carry a folder path, so this is what preserves empty folders,
 * folder ids, and per-folder flags across a local wipe.
 */
@Serializable
data class RemoteFolderState(
    @SerialName("v") val version: Int = VERSION,
    @SerialName("f") val folders: List<Entry> = emptyList(),
    @SerialName("d") val deleted: List<Tombstone> = emptyList()
) {

    @Serializable
    data class Entry(
        @SerialName("id") val id: String,
        @SerialName("p") val parentId: String? = null,
        @SerialName("n") val name: String,
        @SerialName("hd") val hidden: Boolean = false,
        @SerialName("ar") val archived: Boolean = false,
        @SerialName("fv") val favorite: Boolean = false,
        @SerialName("tr") val trashedAt: Long? = null,
        @SerialName("pt") val preTrashParentId: String? = null,
        @SerialName("ct") val createdAt: Long,
        @SerialName("mt") val modifiedAt: Long,
        @SerialName("ca") val changedAt: Long? = null
    ) {
        val clock: Long get() = changedAt ?: modifiedAt
    }

    @Serializable
    data class Tombstone(
        @SerialName("id") val id: String,
        @SerialName("at") val deletedAt: Long
    )

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "teledrive.folders.json"
        const val MARKER = "#teledrive-folders"
    }
}
