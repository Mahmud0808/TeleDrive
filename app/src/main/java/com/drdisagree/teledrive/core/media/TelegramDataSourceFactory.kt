package com.drdisagree.teledrive.core.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.telegram.TelegramClient
import javax.inject.Inject

@UnstableApi
class TelegramDataSourceFactory @Inject constructor(
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository
) {

    fun create(remoteFileId: String): DataSource.Factory =
        DataSource.Factory { TelegramDataSource(telegramClient, remoteFileId) }

    /**
     * For a file stored as parts, streamed as though it were whole. The content
     * key is read here rather than carried through the UI, which keeps key
     * material out of state that gets held, compared and recomposed.
     */
    fun createParted(
        parts: List<MediaPart>,
        encrypted: Boolean
    ): DataSource.Factory = DataSource.Factory {
        val key = if (encrypted) wrappedKeyRepository.get(CryptoKeys.CONTENT) else null
        PartedTelegramDataSource(telegramClient, streamCrypto, parts, encrypted, key)
    }
}
