package com.drdisagree.teledrive.data.remote.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteFolderEntry(
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
