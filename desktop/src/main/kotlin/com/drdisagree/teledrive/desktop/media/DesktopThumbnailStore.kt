package com.drdisagree.teledrive.desktop.media

import com.drdisagree.teledrive.core.media.ThumbnailStore
import java.io.File

class DesktopThumbnailStore : ThumbnailStore {

    override suspend fun thumbnailBytes(fileId: String): ByteArray? = null

    override suspend fun uploadThumbnailFile(fileId: String): File? = null
}
