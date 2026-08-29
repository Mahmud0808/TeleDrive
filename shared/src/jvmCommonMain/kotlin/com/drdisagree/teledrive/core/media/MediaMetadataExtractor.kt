package com.drdisagree.teledrive.core.media

import java.io.File

/** Reads dimensions and duration from local media files. */
interface MediaMetadataExtractor {

    fun extract(file: File, mimeType: String): MediaInfo
}
