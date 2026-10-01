package com.drdisagree.teledrive.core.crypto

import java.io.File
import java.security.SecureRandom

/** Best effort: wear leveling on flash storage can keep the old bytes physically. */
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
