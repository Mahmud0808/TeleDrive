package com.drdisagree.teledrive.data.remote.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Metadata embedded in every uploaded document's caption. Keys are shortened
 * to fit Telegram's caption limit. This makes the storage chat self-describing
 * so the local database can be rebuilt from Telegram alone after a data wipe.
 */
@Serializable
data class RemoteFileManifest(
    @SerialName("v") val version: Int = VERSION,
    @SerialName("id") val fileId: String,
    @SerialName("n") val name: String,
    @SerialName("p") val folderPath: String,
    @SerialName("fid") val folderId: String? = null,
    @SerialName("m") val mimeType: String,
    @SerialName("s") val sizeBytes: Long,
    @SerialName("h") val contentHash: String? = null,
    @SerialName("hd") val hidden: Boolean = false,
    @SerialName("ar") val archived: Boolean = false,
    @SerialName("fv") val favorite: Boolean = false,
    @SerialName("tr") val trashedAt: Long? = null,
    @SerialName("e") val encrypted: Boolean = false,
    @SerialName("ct") val createdAt: Long,
    @SerialName("mt") val modifiedAt: Long,
    @SerialName("w") val width: Int? = null,
    @SerialName("ht") val height: Int? = null,
    @SerialName("d") val durationMs: Long? = null
) {
    companion object {
        const val VERSION = 1
    }
}
