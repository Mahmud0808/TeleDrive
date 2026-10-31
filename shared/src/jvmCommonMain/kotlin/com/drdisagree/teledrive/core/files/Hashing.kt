package com.drdisagree.teledrive.core.files

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Hashing {

    fun sha256(file: File): String? = sha256(file.inputStream())

    fun sha256(stream: InputStream): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        stream.use { input ->
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
