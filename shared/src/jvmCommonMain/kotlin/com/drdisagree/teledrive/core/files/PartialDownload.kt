package com.drdisagree.teledrive.core.files

import java.io.InputStream
import java.io.OutputStream

interface PartialDownload {

    val token: String

    fun length(): Long

    fun truncate(size: Long)

    fun append(body: (OutputStream) -> Unit)

    fun read(): InputStream

    fun finish(): String?

    fun discard()
}
