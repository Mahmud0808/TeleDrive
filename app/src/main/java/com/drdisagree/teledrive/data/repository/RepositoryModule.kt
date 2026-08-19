package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.domain.repository.BackupRepository
import com.drdisagree.teledrive.domain.repository.CacheRepository
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.ExclusionRepository
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.KeyBackupRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    @Singleton
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    @Singleton
    fun bindTrashRepository(impl: TrashRepositoryImpl): TrashRepository

    @Binds
    @Singleton
    fun bindTransferRepository(impl: TransferRepositoryImpl): TransferRepository

    @Binds
    @Singleton
    fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    fun bindExclusionRepository(impl: ExclusionRepositoryImpl): ExclusionRepository

    @Binds
    @Singleton
    fun bindTelegramAuthRepository(impl: TelegramAuthRepositoryImpl): TelegramAuthRepository

    @Binds
    @Singleton
    fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    @Singleton
    fun bindCacheRepository(impl: CacheRepositoryImpl): CacheRepository

    @Binds
    @Singleton
    fun bindKeyBackupRepository(impl: KeyBackupRepositoryImpl): KeyBackupRepository
}
