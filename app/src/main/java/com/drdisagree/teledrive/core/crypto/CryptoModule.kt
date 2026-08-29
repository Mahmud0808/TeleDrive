package com.drdisagree.teledrive.core.crypto

import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CryptoModule {

    @Binds
    @Singleton
    fun bindTdlibDatabaseKeyProvider(impl: TdlibDatabaseKeyProviderImpl): TdlibDatabaseKeyProvider

    companion object {

        @Provides
        @Singleton
        fun provideStreamCrypto(): StreamCrypto = StreamCrypto()

        @Provides
        @Singleton
        fun providePassphraseKdf(): PassphraseKdf = PassphraseKdf()

        @Provides
        @Singleton
        fun provideKeyBackupCodec(
            kdf: PassphraseKdf,
            crypto: StreamCrypto
        ): KeyBackupCodec = KeyBackupCodec(kdf, crypto)

        @Provides
        @Singleton
        fun provideSecureFileDeleter(): SecureFileDeleter = SecureFileDeleter()
    }
}
