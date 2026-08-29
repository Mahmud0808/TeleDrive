package com.drdisagree.teledrive.presentation.gallery

import com.drdisagree.teledrive.domain.model.DriveFile

sealed interface GalleryListItem {

    val key: String

    data class Media(val file: DriveFile) : GalleryListItem {
        override val key: String get() = file.id
    }

    data class DayHeader(val dayStartMillis: Long) : GalleryListItem {
        override val key: String get() = "day-$dayStartMillis"
    }
}
