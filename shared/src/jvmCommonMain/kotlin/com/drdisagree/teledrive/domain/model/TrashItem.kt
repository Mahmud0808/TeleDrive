package com.drdisagree.teledrive.domain.model

sealed interface TrashItem {
    val id: String
    val name: String
    val trashedAt: Long

    data class File(val file: DriveFile) : TrashItem {
        override val id: String get() = file.id
        override val name: String get() = file.name
        override val trashedAt: Long get() = file.trashedAt ?: 0L
    }

    data class Folder(val folder: DriveFolder) : TrashItem {
        override val id: String get() = folder.id
        override val name: String get() = folder.name
        override val trashedAt: Long get() = folder.trashedAt ?: 0L
    }
}
