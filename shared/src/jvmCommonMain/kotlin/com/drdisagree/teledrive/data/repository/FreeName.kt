package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.files.FileNameUtils
import com.drdisagree.teledrive.data.local.dao.FileDao

internal suspend fun FileDao.freeNameIn(
    folderId: String?,
    desired: String,
    excludeId: String? = null
): String {
    val taken = if (excludeId == null) {
        namesInFolder(folderId)
    } else {
        namesInFolderExcluding(folderId, excludeId)
    }
    return FileNameUtils.uniqueName(desired) { candidate ->
        taken.any { it.equals(candidate, ignoreCase = true) }
    }
}
