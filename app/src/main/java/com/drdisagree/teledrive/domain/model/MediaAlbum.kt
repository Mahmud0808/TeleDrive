package com.drdisagree.teledrive.domain.model

data class MediaAlbum(
    val folderId: String?,
    val name: String,
    val itemCount: Int,
    val coverFileId: String?,
    val latestAt: Long
)
