package com.drdisagree.teledrive.domain.usecase

object FolderCycleCheck {

    fun createsCycle(
        folderId: String,
        targetParentId: String?,
        targetAncestors: List<String>
    ): Boolean = folderId == targetParentId || folderId in targetAncestors
}
