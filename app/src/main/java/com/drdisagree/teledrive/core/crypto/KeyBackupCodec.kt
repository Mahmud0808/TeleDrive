package com.drdisagree.teledrive.core.crypto

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes the content key wrapped with a passphrase-derived key so it can
 * be stored in the Telegram storage channel. A leak of the backup blob alone
 * reveals nothing without the owner's passphrase.
 *
 * The optional hint is stored in plaintext (like Telegram's own 2FA hint) and
 * is shown before the passphrase prompt during restore. UI must warn the user
 * not to put the passphrase itself into the hint.
 *
 * Layout: MAGIC(4) VERSION(1) iterations(4) salt(16) hintLength(2) hint(utf-8)
 * then AES-GCM blob (nonce || ciphertext+tag) from [StreamCrypto.encryptBytes].
 */
@Singleton
class KeyBackupCodec @Inject constructor(
    private val passphraseKdf: PassphraseKdf,
    private val streamCrypto: StreamCrypto
) {

    data class BackupInfo(val hint: String?)

    fun encode(contentKey: ByteArray, passphrase: CharArray, hint: String?): ByteArray {
        val salt = passphraseKdf.newSalt()
        val kek = passphraseKdf.deriveKey(passphrase, salt)
        val sealed = streamCrypto.encryptBytes(kek, contentKey)
        val hintBytes = hint.orEmpty().take(MAX_HINT_LENGTH).toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(
            MAGIC.size + 1 + 4 + salt.size + 2 + hintBytes.size + sealed.size
        )
            .put(MAGIC)
            .put(VERSION)
            .putInt(PassphraseKdf.ITERATIONS)
            .put(salt)
            .putShort(hintBytes.size.toShort())
            .put(hintBytes)
            .put(sealed)
            .array()
    }

    /** Reads the plaintext hint without needing the passphrase. */
    fun readInfo(blob: ByteArray): BackupInfo? = runCatching {
        val buffer = header(blob)
        val hintLength = buffer.short.toInt()
        val hintBytes = ByteArray(hintLength).also(buffer::get)
        BackupInfo(
            hint = String(hintBytes, StandardCharsets.UTF_8).takeIf { it.isNotEmpty() }
        )
    }.getOrNull()

    /** Returns null when the passphrase is wrong or the blob is corrupt. */
    fun decode(blob: ByteArray, passphrase: CharArray): ByteArray? = runCatching {
        val buffer = ByteBuffer.wrap(blob)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Not a key backup" }
        require(buffer.get() == VERSION) { "Unsupported version" }
        val iterations = buffer.int
        require(iterations in 10_000..10_000_000) { "Implausible iteration count" }
        val salt = ByteArray(SALT_LENGTH).also(buffer::get)
        val hintLength = buffer.short.toInt()
        require(hintLength in 0..MAX_HINT_LENGTH * 4) { "Invalid hint length" }
        buffer.position(buffer.position() + hintLength)
        val sealed = ByteArray(buffer.remaining()).also(buffer::get)
        val kek = passphraseKdf.deriveKey(passphrase, salt, iterations)
        streamCrypto.decryptBytes(kek, sealed)
    }.getOrNull()

    private fun header(blob: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.wrap(blob)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Not a key backup" }
        require(buffer.get() == VERSION) { "Unsupported version" }
        buffer.int
        buffer.position(buffer.position() + SALT_LENGTH)
        return buffer
    }

    companion object {
        private val MAGIC = byteArrayOf(0x54, 0x44, 0x4B, 0x42) // "TDKB"
        private const val VERSION: Byte = 1
        private const val SALT_LENGTH = 16
        private const val MAX_HINT_LENGTH = 128
        const val BACKUP_FILE_NAME = "teledrive.keybackup"
    }
}
