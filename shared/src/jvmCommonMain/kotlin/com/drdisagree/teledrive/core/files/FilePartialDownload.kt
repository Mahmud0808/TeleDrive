package com.drdisagree.teledrive.core.files

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

class FilePartialDownload private constructor(private val file: File) : PartialDownload {

    override val token: String get() = file.absolutePath

    override fun length(): Long = if (file.exists()) file.length() else 0L

    override fun truncate(size: Long) {
        RandomAccessFile(file, "rw").use { it.setLength(size) }
    }

    override fun append(body: (OutputStream) -> Unit) {
        FileOutputStream(file, true).use(body)
    }

    override fun read(): InputStream = file.inputStream()

    override fun finish(): String? {
        val directory = file.parentFile ?: return null
        val name = FileNameUtils.uniqueName(file.name.removeSuffix(SUFFIX)) { candidate ->
            File(directory, candidate).exists()
        }
        val destination = File(directory, name)
        return if (file.renameTo(destination)) destination.absolutePath else null
    }

    override fun discard() {
        file.delete()
    }

    companion object {
        private const val SUFFIX = ".part"

        fun open(directory: File, fileName: String, token: String?): FilePartialDownload? {
            token?.let(::File)?.takeIf { it.isFile && it.name.endsWith(SUFFIX) }?.let {
                return FilePartialDownload(it)
            }
            if (!directory.exists() && !directory.mkdirs()) return null
            val name = FileNameUtils.uniqueName(FileNameUtils.sanitize(fileName)) { candidate ->
                File(directory, candidate).exists() || File(directory, candidate + SUFFIX).exists()
            }
            val file = File(directory, name + SUFFIX)
            if (!file.exists() && !file.createNewFile()) return null
            return FilePartialDownload(file)
        }
    }
}
