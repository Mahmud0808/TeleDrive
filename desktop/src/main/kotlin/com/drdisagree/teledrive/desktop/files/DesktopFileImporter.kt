package com.drdisagree.teledrive.desktop.files

import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.FileImporter
import com.drdisagree.teledrive.core.files.FileNameUtils
import com.drdisagree.teledrive.core.files.ImportedFile
import java.io.File

/**
 * Desktop references are plain paths the app can already read, so imports use
 * the original file directly and never stage a copy.
 */
class DesktopFileImporter(
    private val storagePaths: AppStoragePaths
) : FileImporter {

    override fun import(reference: String): ImportedFile? {
        val file = File(reference)
        if (!file.isFile || !file.canRead()) return null
        return ImportedFile(
            path = file.absolutePath,
            name = FileNameUtils.sanitize(file.name),
            sizeBytes = file.length()
        )
    }

    override fun discard(imported: ImportedFile) {
        val staged = File(imported.path)
        if (staged.parentFile == File(storagePaths.filesDir, IMPORT_DIR)) staged.delete()
    }

    private companion object {
        const val IMPORT_DIR = "imports"
    }
}
