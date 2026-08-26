package com.drdisagree.teledrive.data.local.database

import android.content.Context
import androidx.room.Room
import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.CacheDao
import com.drdisagree.teledrive.data.local.dao.ExclusionDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FilePartDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.dao.PendingDeleteDao
import com.drdisagree.teledrive.data.local.dao.ProxyDao
import com.drdisagree.teledrive.data.local.dao.StorageChannelDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TeleDriveDatabase =
        Room.databaseBuilder(context, TeleDriveDatabase::class.java, TeleDriveDatabase.NAME)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            .build()

    @Provides
    fun provideStorageChannelDao(db: TeleDriveDatabase): StorageChannelDao = db.storageChannelDao()

    @Provides
    fun provideFileDao(db: TeleDriveDatabase): FileDao = db.fileDao()

    @Provides
    fun provideFolderDao(db: TeleDriveDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideTransferDao(db: TeleDriveDatabase): TransferDao = db.transferDao()

    @Provides
    fun provideBackupDao(db: TeleDriveDatabase): BackupDao = db.backupDao()

    @Provides
    fun provideExclusionDao(db: TeleDriveDatabase): ExclusionDao = db.exclusionDao()

    @Provides
    fun provideThumbnailDao(db: TeleDriveDatabase): ThumbnailDao = db.thumbnailDao()

    @Provides
    fun provideCacheDao(db: TeleDriveDatabase): CacheDao = db.cacheDao()

    @Provides
    fun providePendingDeleteDao(db: TeleDriveDatabase): PendingDeleteDao = db.pendingDeleteDao()

    @Provides
    fun provideFilePartDao(db: TeleDriveDatabase): FilePartDao = db.filePartDao()

    @Provides
    fun provideProxyDao(db: TeleDriveDatabase): ProxyDao = db.proxyDao()
}
