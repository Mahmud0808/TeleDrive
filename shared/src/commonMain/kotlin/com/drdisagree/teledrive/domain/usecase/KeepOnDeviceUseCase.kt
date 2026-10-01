package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository

class KeepOnDeviceUseCase(
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository
) {

    /**
     * Does not reconcile first: File.exists() is false for paths this process cannot see,
     * so it would re-download copies already on the device.
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
