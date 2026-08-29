package com.drdisagree.teledrive.core.files

import java.io.OutputStream

/** Writes a finished download where the platform keeps user downloads. */
interface DownloadWriter {

    fun write(
        fileName: String,
        mimeType: String,
        folderPath: String? = null,
        body: (OutputStream) -> Unit
    ): String?
}
