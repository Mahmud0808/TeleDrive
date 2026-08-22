package com.drdisagree.teledrive.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.CacheDao
import com.drdisagree.teledrive.data.local.dao.ExclusionDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.dao.StorageChannelDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.BackupRecordEntity
import com.drdisagree.teledrive.data.local.entity.BackupSessionEntity
import com.drdisagree.teledrive.data.local.entity.CacheEntryEntity
import com.drdisagree.teledrive.data.local.entity.ExclusionEntity
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.data.local.entity.StorageChannelEntity
import com.drdisagree.teledrive.data.local.entity.ThumbnailEntity
import com.drdisagree.teledrive.data.local.entity.TransferEntity

@Database(
    entities = [
        FileEntity::class,
        FolderEntity::class,
        TransferEntity::class,
        BackupSessionEntity::class,
        BackupRecordEntity::class,
        ExclusionEntity::class,
        ThumbnailEntity::class,
        CacheEntryEntity::class,
        StorageChannelEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class TeleDriveDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun folderDao(): FolderDao
    abstract fun transferDao(): TransferDao
    abstract fun backupDao(): BackupDao
    abstract fun exclusionDao(): ExclusionDao
    abstract fun thumbnailDao(): ThumbnailDao
    abstract fun cacheDao(): CacheDao
    abstract fun storageChannelDao(): StorageChannelDao

    companion object {
        const val NAME = "teledrive.db"
    }
}
