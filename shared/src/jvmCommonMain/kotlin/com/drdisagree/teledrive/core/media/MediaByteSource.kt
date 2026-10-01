package com.drdisagree.teledrive.core.media

/** Reads block until the range is buffered; an empty array marks the end of the stream. */
interface MediaByteSource : AutoCloseable {

    suspend fun size(): Long

    suspend fun read(position: Long, count: Int): ByteArray
}
