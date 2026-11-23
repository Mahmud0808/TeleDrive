package com.drdisagree.teledrive.data.local.entity

import androidx.room.Embedded

data class GalleryRowEntity(
    val isHeader: Int,
    val day: String,
    @Embedded val file: FileEntity?
)
