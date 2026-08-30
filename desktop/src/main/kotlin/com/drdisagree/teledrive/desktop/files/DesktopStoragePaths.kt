package com.drdisagree.teledrive.desktop.files

import com.drdisagree.teledrive.core.files.AppStoragePaths
import java.io.File

class DesktopStoragePaths : AppStoragePaths {

    private val root: File = run {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home").orEmpty()
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let(::File)
                ?: File(home, "AppData/Roaming")

            os.contains("mac") -> File(home, "Library/Application Support")
            else -> System.getenv("XDG_DATA_HOME")?.let(::File)
                ?: File(home, ".local/share")
        }
        File(base, "TeleDrive").apply { mkdirs() }
    }

    override val filesDir: File
        get() = File(root, "files").apply { mkdirs() }

    override val cacheDir: File
        get() = File(root, "cache").apply { mkdirs() }

    override val externalCacheDir: File? = null

    override val externalStorageRoot: File? = null
}
