package com.drdisagree.teledrive.core.media

import android.media.MediaDataSource
import kotlinx.coroutines.runBlocking

class ByteSourceMediaDataSource(
    private val source: MediaByteSource
) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        val bytes = runBlocking { source.read(position, size) }
        if (bytes.isEmpty()) return -1
        val count = minOf(bytes.size, size)
        bytes.copyInto(buffer, offset, 0, count)
        return count
    }

    override fun getSize(): Long = runBlocking { source.size() }

    override fun close() = Unit
}
