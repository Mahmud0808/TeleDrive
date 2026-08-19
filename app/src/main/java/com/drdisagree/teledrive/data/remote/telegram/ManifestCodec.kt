package com.drdisagree.teledrive.data.remote.telegram

import android.util.Base64
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caption wire format:
 *  - plain:     "td1:" + JSON manifest
 *  - encrypted: "tde1:" + base64(AES-GCM(JSON)) using the content key, so a
 *    Telegram-side leak reveals no file names or structure for encrypted files.
 */
@Singleton
class ManifestCodec @Inject constructor(
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(manifest: RemoteFileManifest, encrypt: Boolean): String {
        val payload = json.encodeToString(RemoteFileManifest.serializer(), manifest)
        return if (encrypt) {
            val key = wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT)
            PREFIX_ENCRYPTED + Base64.encodeToString(
                streamCrypto.encryptBytes(key, payload.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP
            )
        } else {
            PREFIX_PLAIN + payload
        }
    }

    /** Returns null for captions that are not TeleDrive manifests. */
    fun decode(caption: String): RemoteFileManifest? = runCatching {
        when {
            caption.startsWith(PREFIX_PLAIN) ->
                json.decodeFromString(
                    RemoteFileManifest.serializer(),
                    caption.removePrefix(PREFIX_PLAIN)
                )
            caption.startsWith(PREFIX_ENCRYPTED) -> {
                val key = wrappedKeyRepository.get(CryptoKeys.CONTENT) ?: return null
                val plaintext = streamCrypto.decryptBytes(
                    key,
                    Base64.decode(caption.removePrefix(PREFIX_ENCRYPTED), Base64.NO_WRAP)
                )
                json.decodeFromString(
                    RemoteFileManifest.serializer(),
                    String(plaintext, Charsets.UTF_8)
                )
            }
            else -> null
        }
    }.getOrNull()

    fun isEncryptedManifest(caption: String): Boolean = caption.startsWith(PREFIX_ENCRYPTED)

    /**
     * True when the caption is an encrypted manifest this device cannot read
     * yet, which means the key backup has not been restored.
     */
    fun isLocked(caption: String): Boolean =
        isEncryptedManifest(caption) && !wrappedKeyRepository.exists(CryptoKeys.CONTENT)

    companion object {
        private const val PREFIX_PLAIN = "td1:"
        private const val PREFIX_ENCRYPTED = "tde1:"
    }
}
