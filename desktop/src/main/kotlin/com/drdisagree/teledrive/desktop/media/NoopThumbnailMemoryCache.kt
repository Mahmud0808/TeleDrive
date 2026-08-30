package com.drdisagree.teledrive.desktop.media

import com.drdisagree.teledrive.core.media.ThumbnailMemoryCache

class NoopThumbnailMemoryCache : ThumbnailMemoryCache {

    override fun remove(fileId: String) {
    }

    override fun clear() {
    }
}
