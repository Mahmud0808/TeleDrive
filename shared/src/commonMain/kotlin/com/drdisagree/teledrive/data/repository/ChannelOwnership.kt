package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.local.dao.ExclusionDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FolderDao

/** Rows from before multichannel support carry no owner; the first channel to open adopts them. */
class ChannelOwnership(
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val exclusionDao: ExclusionDao
) {

    suspend fun claimUnowned(chatId: Long) {
        fileDao.claimUnownedRows(chatId)
        folderDao.claimUnownedRows(chatId)
        exclusionDao.claimUnownedRows(chatId)
    }
}
