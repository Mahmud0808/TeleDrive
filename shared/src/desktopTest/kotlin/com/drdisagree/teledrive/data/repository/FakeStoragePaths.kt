package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.testing.unused
import java.io.File

internal class FakeStoragePaths : AppStoragePaths by unused() {
    private val root = File.createTempFile("teledrive-paths", "").also {
        it.delete()
        it.mkdirs()
    }
    override val cacheDir: File = File(root, "cache").also { it.mkdirs() }
}
