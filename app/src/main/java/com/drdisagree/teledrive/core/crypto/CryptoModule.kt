package com.drdisagree.teledrive.core.crypto

import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val cryptoModule = module {
    singleOf(::StreamCrypto)
    singleOf(::PassphraseKdf)
    singleOf(::KeyBackupCodec)
    singleOf(::SecureFileDeleter)
    singleOf(::KeystoreManager)
    singleOf(::WrappedKeyRepository)
    singleOf(::TdlibDatabaseKeyProviderImpl) bind TdlibDatabaseKeyProvider::class
}
