package com.drdisagree.teledrive.core.media

/**
 * Random access reads over a file that may still be arriving from Telegram.
 * Reads block until the requested range is buffered; an empty array marks the
 * end of the stream.
 */
interface MediaByteSource : AutoCloseable {

    suspend fun size(): Long

    suspend fun read(position: Long, count: Int): ByteArray
}
