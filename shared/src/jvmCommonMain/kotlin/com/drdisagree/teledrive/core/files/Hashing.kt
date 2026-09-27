package com.drdisagree.teledrive.core.files

import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

object Hashing {

    /**
     * Accumulates an SHA-256 across several passes, so a file already being
     * streamed elsewhere does not have to be read a second time just to hash
     * it. Chunks must be fed in file order.
     */
    class Accumulator {
        private val digest = MessageDigest.getInstance("SHA-256")
        private var abandoned = false

        fun wrap(input: InputStream): InputStream = DigestInputStream(input, digest)

        fun abandon() {
            abandoned = true
        }

        fun result(): String? =
            if (abandoned) null else digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Streaming SHA-256; returns null when the file cannot be read. */
    fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
