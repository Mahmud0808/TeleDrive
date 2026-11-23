package com.drdisagree.teledrive.domain.model

sealed interface GalleryEntry {

    data class Day(val dayStartMillis: Long) : GalleryEntry

    data class Item(val file: DriveFile) : GalleryEntry
}
