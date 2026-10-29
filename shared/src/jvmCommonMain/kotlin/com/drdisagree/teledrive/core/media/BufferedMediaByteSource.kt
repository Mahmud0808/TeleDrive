package com.drdisagree.teledrive.core.media

class BufferedMediaByteSource(
    private val source: MediaByteSource,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : MediaByteSource {

    private var buffer = ByteArray(0)
    private var bufferStart = -1L

    override suspend fun size(): Long = source.size()

    override suspend fun read(position: Long, count: Int): ByteArray {
        if (count <= 0) return ByteArray(0)
        if (!holds(position)) {
            val chunk = source.read(position, maxOf(count, chunkSize))
            if (chunk.isEmpty()) return chunk
            buffer = chunk
            bufferStart = position
        }
        val from = (position - bufferStart).toInt()
        return buffer.copyOfRange(from, minOf(from + count, buffer.size))
    }

    private fun holds(position: Long): Boolean =
        bufferStart >= 0 && position >= bufferStart && position < bufferStart + buffer.size

    override fun close() {
        buffer = ByteArray(0)
        bufferStart = -1L
        source.close()
    }

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 512 * 1024
    }
}
