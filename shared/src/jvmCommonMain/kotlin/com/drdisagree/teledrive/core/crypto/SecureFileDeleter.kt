package com.drdisagree.teledrive.core.crypto

import java.io.File
import java.security.SecureRandom

/**
 * Best-effort secure deletion: overwrite once with random data, then delete.
 * On flash storage with wear leveling this cannot guarantee physical erasure;
 * that limitation is documented in SECURITY.md. Encrypted-at-rest files are
 * already unreadable once their key is discarded.
 */
class SecureFileDeleter {

    private val secureRandom = SecureRandom()

    fun delete(file: File): Boolean {
        if (!file.exists()) return true
        runCatching {
            if (file.isFile && file.canWrite()) {
                val buffer = ByteArray(OVERWRITE_BUFFER)
                file.outputStream().use { output ->
                    var remaining = file.length()
                    while (remaining > 0) {
                        secureRandom.nextBytes(buffer)
                        val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                        output.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                    output.fd.sync()
                }
            }
        }
        return file.delete()
    }

    companion object {
        private const val OVERWRITE_BUFFER = 64 * 1024
    }
}
