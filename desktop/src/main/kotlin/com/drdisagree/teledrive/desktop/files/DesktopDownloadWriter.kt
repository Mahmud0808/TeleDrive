package com.drdisagree.teledrive.desktop.files

import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.FileNameUtils
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DesktopDownloadWriter(
    private val settingsRepository: SettingsRepository
) : DownloadWriter {

    override fun write(
        fileName: String,
        mimeType: String,
        folderPath: String?,
        body: (OutputStream) -> Unit
    ): String? = runCatching {
        val configured = runBlocking { settingsRepository.preferences.first() }.downloadDirectory
        val base = configured?.let(::File)
            ?: File(System.getProperty("user.home"), "Downloads")
        val root = File(base, ROOT_DIR)
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
