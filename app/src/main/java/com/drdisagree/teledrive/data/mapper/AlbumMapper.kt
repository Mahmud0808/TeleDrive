package com.drdisagree.teledrive.data.mapper

import com.drdisagree.teledrive.data.local.entity.AlbumSummary
import com.drdisagree.teledrive.domain.model.MediaAlbum

fun AlbumSummary.toDomain(): MediaAlbum = MediaAlbum(
    folderId = folderId,
    name = name,
    itemCount = itemCount,
    coverFileId = coverFileId,
    latestAt = latestAt
)
