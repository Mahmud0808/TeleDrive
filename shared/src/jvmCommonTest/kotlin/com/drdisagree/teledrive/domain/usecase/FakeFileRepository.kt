package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.testing.unused

internal class FakeFileRepository(
    private val all: List<DriveFile>,
    private val tree: Map<String, List<String>>
) : FileRepository by unused() {
    val offlineFiles = mutableListOf<String>()
    val onlineOnlyFiles = mutableListOf<String>()
    val offlineFolders = mutableListOf<String>()

    override suspend fun setFilesAvailableOffline(ids: List<String>, available: Boolean) {
        (if (available) offlineFiles else onlineOnlyFiles) += ids
    }

    override suspend fun setFolderAvailableOffline(id: String, available: Boolean) {
        if (available) offlineFolders += id
    }

    override suspend fun fileIdsInTree(folderId: String) = tree[folderId].orEmpty()

    override suspend fun filesByIds(ids: List<String>) = all.filter { it.id in ids }
}
