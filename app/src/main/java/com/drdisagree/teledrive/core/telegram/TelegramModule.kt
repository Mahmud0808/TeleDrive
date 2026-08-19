package com.drdisagree.teledrive.core.telegram

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface TelegramModule {

    @Binds
    @Singleton
    fun bindTelegramClient(impl: TdLibTelegramClient): TelegramClient
}
