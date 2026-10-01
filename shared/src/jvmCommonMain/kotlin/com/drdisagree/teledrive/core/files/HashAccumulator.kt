package com.drdisagree.teledrive.core.files

import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Hashes bytes already being streamed elsewhere, so the file is not read twice; feed chunks in file
 * order.
 */
class HashAccumulator {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var abandoned = false

    fun wrap(input: InputStream): InputStream = DigestInputStream(input, digest)

    fun abandon() {
        abandoned = true
    }

    fun result(): String? =
        if (abandoned) null else digest.digest().joinToString("") { "%02x".format(it) }
}
