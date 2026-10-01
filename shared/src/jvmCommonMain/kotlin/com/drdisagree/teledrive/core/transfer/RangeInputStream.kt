package com.drdisagree.teledrive.core.transfer

import java.io.InputStream

internal class RangeInputStream(
    private val delegate: InputStream,
    private val limit: Long
) : InputStream() {

    private var read = 0L

    override fun read(): Int {
        if (read >= limit) return -1
        val value = delegate.read()
        if (value != -1) read++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (read >= limit) return -1
        val allowed = minOf(length.toLong(), limit - read).toInt()
        val count = delegate.read(buffer, offset, allowed)
        if (count > 0) read += count
        return count
    }

    override fun available(): Int = minOf(delegate.available().toLong(), limit - read).toInt()
}
