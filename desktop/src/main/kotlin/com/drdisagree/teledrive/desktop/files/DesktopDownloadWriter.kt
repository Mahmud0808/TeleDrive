package com.drdisagree.teledrive.desktop.files

import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.FileNameUtils
import java.io.File
import java.io.OutputStream

class DesktopDownloadWriter : DownloadWriter {

    override fun write(
        fileName: String,
        mimeType: String,
        folderPath: String?,
        body: (OutputStream) -> Unit
    ): String? = runCatching {
        val downloads = File(System.getProperty("user.home"), "Downloads")
        val root = File(downloads, ROOT_DIR)
        val target = folderPath
            ?.split('/')
            ?.filter { it.isNotBlank() }
            ?.fold(root) { parent, segment -> File(parent, FileNameUtils.sanitize(segment)) }
            ?: root
        target.mkdirs()
        val unique = FileNameUtils.uniqueName(FileNameUtils.sanitize(fileName)) { candidate ->
            File(target, candidate).exists()
        }
        val destination = File(target, unique)
        destination.outputStream().use(body)
        destination.absolutePath
    }.getOrNull()

    private companion object {
        const val ROOT_DIR = "TeleDrive"
    }
}
