package com.drdisagree.teledrive.core.crypto

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chunked AES-256-GCM for large files, following the segment layout used by
 * well-known streaming AEAD designs: each chunk is sealed independently and its
 * index is bound as associated data, so chunks cannot be dropped or reordered.
 *
 * File layout: MAGIC(4) VERSION(1) SALT(8), then per chunk:
 * length(4, ciphertext length with the top bit set on the last chunk)
 * || ciphertext(+16 tag).
 * Chunk nonce = SALT(8) || chunkIndex(4, big-endian). The associated data
 * carries the chunk index and whether it is the last chunk, so dropped
 * trailing chunks fail authentication instead of decrypting to truncated data.
 */
@Singleton
class StreamCrypto @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun encryptStream(key: ByteArray, input: InputStream, output: OutputStream) {
        val salt = ByteArray(SALT_LENGTH).also(secureRandom::nextBytes)
        output.write(MAGIC)
        output.write(VERSION)
        output.write(salt)

        val secretKey = SecretKeySpec(key, "AES")
        var current = ByteArray(CHUNK_SIZE)
        var next = ByteArray(CHUNK_SIZE)
        var currentSize = readFully(input, current)
        var chunkIndex = 0
        while (true) {
            val nextSize = if (currentSize < CHUNK_SIZE) 0 else readFully(input, next)
            val isFinal = nextSize <= 0
            writeChunk(secretKey, salt, chunkIndex, current, currentSize, isFinal, output)
            if (isFinal) break
            val swap = current
            current = next
            next = swap
            currentSize = nextSize
            chunkIndex++
        }
        output.flush()
    }

    private fun writeChunk(
        secretKey: SecretKeySpec,
        salt: ByteArray,
        chunkIndex: Int,
        chunk: ByteArray,
        size: Int,
        isFinal: Boolean,
        output: OutputStream
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey,
            GCMParameterSpec(TAG_BITS, nonceFor(salt, chunkIndex))
        )
        cipher.updateAAD(aadFor(chunkIndex, isFinal))
        val sealed = cipher.doFinal(chunk, 0, size.coerceAtLeast(0))
        val header = if (isFinal) sealed.size or FINAL_FLAG else sealed.size
        output.write(ByteBuffer.allocate(4).putInt(header).array())
        output.write(sealed)
    }

    fun decryptStream(key: ByteArray, input: InputStream, output: OutputStream) {
        val header = ByteArray(MAGIC.size + 1 + SALT_LENGTH)
        if (readFully(input, header) != header.size) throw EOFException("Truncated header")
        for (i in MAGIC.indices) {
            if (header[i] != MAGIC[i]) throw IllegalArgumentException("Not an encrypted file")
        }
        if (header[MAGIC.size] != VERSION.toByte()) {
            throw IllegalArgumentException("Unsupported encryption version")
        }
        val salt = header.copyOfRange(MAGIC.size + 1, header.size)

        val secretKey = SecretKeySpec(key, "AES")
        val lengthBytes = ByteArray(4)
        var chunkIndex = 0
        var sawFinalChunk = false
        while (!sawFinalChunk) {
            val lengthRead = readFully(input, lengthBytes)
            if (lengthRead == 0) throw EOFException("Encrypted stream is truncated")
            if (lengthRead != 4) throw EOFException("Truncated chunk length")
            val marker = ByteBuffer.wrap(lengthBytes).int
            val isFinal = marker and FINAL_FLAG != 0
            val sealedLength = marker and FINAL_FLAG.inv()
            if (sealedLength < TAG_BYTES || sealedLength > CHUNK_SIZE + TAG_BYTES) {
                throw IllegalArgumentException("Invalid chunk length")
            }
            val sealed = ByteArray(sealedLength)
            if (readFully(input, sealed) != sealedLength) throw EOFException("Truncated chunk")

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(TAG_BITS, nonceFor(salt, chunkIndex))
            )
            cipher.updateAAD(aadFor(chunkIndex, isFinal))
            output.write(cipher.doFinal(sealed))
            sawFinalChunk = isFinal
            chunkIndex++
        }
        if (input.read() != -1) throw IllegalArgumentException("Data after the final chunk")
        output.flush()
    }

    /** One-shot helper for small payloads such as thumbnails. */
    fun encryptBytes(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    fun decryptBytes(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size > NONCE_LENGTH + TAG_BYTES) { "Blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, blob, 0, NONCE_LENGTH)
        )
        return cipher.doFinal(blob, NONCE_LENGTH, blob.size - NONCE_LENGTH)
    }

    private fun nonceFor(salt: ByteArray, chunkIndex: Int): ByteArray =
        ByteBuffer.allocate(NONCE_LENGTH).put(salt).putInt(chunkIndex).array()

    private fun aadFor(chunkIndex: Int, isFinal: Boolean): ByteArray = ByteBuffer.allocate(9)
        .putInt(VERSION)
        .putInt(chunkIndex)
        .put(if (isFinal) 1 else 0)
        .array()

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
        return offset
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf(0x54, 0x44, 0x45, 0x31) // "TDE1"
        private const val VERSION = 1
        private const val FINAL_FLAG = 1 shl 31
        const val CHUNK_SIZE = 1024 * 1024
        private const val SALT_LENGTH = 8
        private const val NONCE_LENGTH = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = 16
    }
}
