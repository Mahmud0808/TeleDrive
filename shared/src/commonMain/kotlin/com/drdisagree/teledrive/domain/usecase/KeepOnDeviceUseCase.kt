package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository

class KeepOnDeviceUseCase(
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository
) {

    /**
     * Pinning promises an offline copy, so a file with none is queued. It does
     * not reconcile first: that decides "missing" from File.exists(), which is
     * false for any path this process cannot see, and would re-download copies
     * that are sitting on the device already.
     *
     * Returns how many downloads were queued.
     */
    suspend operator fun invoke(
        fileIds: List<String>,
        folderIds: List<String> = emptyList(),
        pinned: Boolean
    ): Int {
        if (fileIds.isNotEmpty()) fileRepository.setFilesPinned(fileIds, pinned)
        folderIds.forEach { fileRepository.setFolderPinned(it, pinned) }
        if (!pinned) return 0

        val ids = (fileIds + folderIds.flatMap { fileRepository.fileIdsInTree(it) }).distinct()
        if (ids.isEmpty()) return 0
        val missing = fileRepository.filesByIds(ids)
            .filter { it.hasRemoteCopy && !it.hasLocalCopy }
        missing.forEach { transferRepository.enqueueDownload(it.id) }
        return missing.size
    }
}
