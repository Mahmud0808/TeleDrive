package com.drdisagree.teledrive.core.crypto

import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CryptoModule {

    @Binds
    @Singleton
    fun bindTdlibDatabaseKeyProvider(impl: TdlibDatabaseKeyProviderImpl): TdlibDatabaseKeyProvider
}
