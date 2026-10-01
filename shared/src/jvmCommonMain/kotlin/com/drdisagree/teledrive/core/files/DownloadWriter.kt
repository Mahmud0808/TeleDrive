package com.drdisagree.teledrive.core.files

import java.io.OutputStream

interface DownloadWriter {

    fun write(
        fileName: String,
        mimeType: String,
        folderPath: String? = null,
        body: (OutputStream) -> Unit
    ): String?
}
