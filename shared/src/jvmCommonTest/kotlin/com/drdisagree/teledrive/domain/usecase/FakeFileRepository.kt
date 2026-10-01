package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.testing.unused

internal class FakeFileRepository(
    private val all: List<DriveFile>,
    private val tree: Map<String, List<String>>
) : FileRepository by unused() {
    val pinnedFiles = mutableListOf<String>()
    val unpinnedFiles = mutableListOf<String>()
    val pinnedFolders = mutableListOf<String>()

    override suspend fun setFilesPinned(ids: List<String>, pinned: Boolean) {
        (if (pinned) pinnedFiles else unpinnedFiles) += ids
    }

    override suspend fun setFolderPinned(id: String, pinned: Boolean) {
        if (pinned) pinnedFolders += id
    }

    override suspend fun fileIdsInTree(folderId: String) = tree[folderId].orEmpty()

    override suspend fun filesByIds(ids: List<String>) = all.filter { it.id in ids }
}
