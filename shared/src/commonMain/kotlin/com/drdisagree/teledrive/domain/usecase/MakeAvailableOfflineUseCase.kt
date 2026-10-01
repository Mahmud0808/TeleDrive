package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import kotlinx.coroutines.flow.first

class MakeAvailableOfflineUseCase(
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
        available: Boolean
    ): Int {
        if (fileIds.isNotEmpty()) fileRepository.setFilesAvailableOffline(fileIds, available)
        folderIds.forEach { fileRepository.setFolderAvailableOffline(it, available) }
        if (!available) return 0

        val ids = (fileIds + folderIds.flatMap { fileRepository.fileIdsInTree(it) }).distinct()
        if (ids.isEmpty()) return 0
        val missing = fileRepository.filesByIds(ids)
            .filter { it.hasRemoteCopy && !it.hasLocalCopy }
        missing.forEach { transferRepository.enqueueDownload(it.id) }
        return missing.size
    }

    suspend fun downloadMissing(): Int =
        fileRepository.observeAvailableOfflineMissingIds().first()
            .count { transferRepository.enqueueDownload(it) is AppResult.Success }
}
