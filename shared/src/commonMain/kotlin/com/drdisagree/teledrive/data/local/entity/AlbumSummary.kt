package com.drdisagree.teledrive.data.local.entity

/** Projection backing the gallery album list; not a stored table. */
data class AlbumSummary(
    val folderId: String?,
    val name: String?,
    val itemCount: Int,
    val coverFileId: String?,
    val latestAt: Long
)
