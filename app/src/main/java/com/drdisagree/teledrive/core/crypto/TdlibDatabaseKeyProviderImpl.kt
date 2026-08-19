package com.drdisagree.teledrive.core.crypto

import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TdlibDatabaseKeyProviderImpl @Inject constructor(
    private val wrappedKeyRepository: WrappedKeyRepository
) : TdlibDatabaseKeyProvider {

    override fun databaseKey(): ByteArray =
        wrappedKeyRepository.getOrCreate(CryptoKeys.TDLIB_DATABASE)
}
