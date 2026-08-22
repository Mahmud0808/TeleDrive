package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.local.dao.ExclusionDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rows written before multichannel support, and rows created while no drive
 * was selected, carry no owner. The first channel to open adopts them, which
 * keeps a drive built by an older build visible instead of silently empty.
 */
@Singleton
class ChannelOwnership @Inject constructor(
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
