package com.drdisagree.teledrive.core.media

interface ThumbnailMemoryCache {

    fun remove(fileId: String)

    fun clear()
}
