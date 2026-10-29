package com.drdisagree.teledrive.core.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BufferedMediaByteSourceTest {

    private val bytes = ByteArray(10_000) { (it % 251).toByte() }

    private class CountingSource(private val bytes: ByteArray) : MediaByteSource {
        var reads = 0
        override suspend fun size(): Long = bytes.size.toLong()
        override suspend fun read(position: Long, count: Int): ByteArray {
            reads++
            if (position >= bytes.size) return ByteArray(0)
            val end = minOf(position + count, bytes.size.toLong()).toInt()
            return bytes.copyOfRange(position.toInt(), end)
        }
        override fun close() = Unit
    }

    @Test
    fun `tiny sequential reads reach the source once per chunk`() = runBlocking {
        val inner = CountingSource(bytes)
        val buffered = BufferedMediaByteSource(inner, chunkSize = 1_000)

        val out = ByteArray(bytes.size)
        var position = 0
        while (position < bytes.size) {
            val piece = buffered.read(position.toLong(), 3)
            piece.copyInto(out, position)
            position += piece.size
        }

        assertArrayEquals(bytes, out)
        assertEquals(10, inner.reads)
    }

    @Test
    fun `a read larger than the chunk is served whole`() = runBlocking {
        val buffered = BufferedMediaByteSource(CountingSource(bytes), chunkSize = 100)

        assertArrayEquals(bytes.copyOfRange(50, 550), buffered.read(50, 500))
    }

    @Test
    fun `reading past the end returns nothing`() = runBlocking {
        val buffered = BufferedMediaByteSource(CountingSource(bytes), chunkSize = 1_000)

        assertEquals(0, buffered.read(10_000, 10).size)
    }

    @Test
    fun `a jump outside the buffer fetches from the new position`() = runBlocking {
        val inner = CountingSource(bytes)
        val buffered = BufferedMediaByteSource(inner, chunkSize = 1_000)
        buffered.read(0, 4)

        assertArrayEquals(bytes.copyOfRange(9_000, 9_004), buffered.read(9_000, 4))
        assertArrayEquals(bytes.copyOfRange(2, 6), buffered.read(2, 4))
        assertEquals(3, inner.reads)
    }
}
