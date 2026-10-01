package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.testing.unused

internal class FakeTransferRepository : TransferRepository by unused() {
    val downloads = mutableListOf<String>()

    override suspend fun enqueueDownload(fileId: String, priority: Int): AppResult<String> {
        downloads += fileId
        return AppResult.Success(fileId)
    }
}
