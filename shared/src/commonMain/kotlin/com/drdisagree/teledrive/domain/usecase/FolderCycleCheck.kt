package com.drdisagree.teledrive.domain.usecase

/**
 * A folder may not become its own parent or land inside one of its own
 * descendants, which would detach the whole subtree from the root.
 */
object FolderCycleCheck {

    fun createsCycle(
        folderId: String,
        targetParentId: String?,
        targetAncestors: List<String>
    ): Boolean = folderId == targetParentId || folderId in targetAncestors
}
