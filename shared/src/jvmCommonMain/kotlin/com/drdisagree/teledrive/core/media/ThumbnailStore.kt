package com.drdisagree.teledrive.core.media

import java.io.File

/** Creates and caches preview thumbnails for stored files. */
interface ThumbnailStore {

    suspend fun thumbnailBytes(fileId: String): ByteArray?

    suspend fun uploadThumbnailFile(fileId: String): File?
}
