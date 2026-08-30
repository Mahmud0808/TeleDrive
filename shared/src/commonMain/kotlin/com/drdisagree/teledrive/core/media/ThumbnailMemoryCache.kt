package com.drdisagree.teledrive.core.media

/** In-memory thumbnail cache owned by the image loader. */
interface ThumbnailMemoryCache {

    fun remove(fileId: String)

    fun clear()
}
