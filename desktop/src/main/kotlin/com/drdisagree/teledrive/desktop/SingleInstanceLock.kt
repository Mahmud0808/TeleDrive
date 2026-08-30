package com.drdisagree.teledrive.desktop

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Two instances sharing one database and TDLib session deadlock the second
 * before its window appears, so the app refuses to start twice. The lock
 * releases with the process, surviving crashes that a marker file would not.
 */
class SingleInstanceLock(private val directory: File) {

    private var channel: FileChannel? = null

    fun acquire(): Boolean = runCatching {
        directory.mkdirs()
        val opened = FileChannel.open(
            File(directory, LOCK_FILE).toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        )
        val lock = opened.tryLock()
        if (lock == null) {
            opened.close()
            false
        } else {
            channel = opened
            true
        }
    }.getOrDefault(true)

    private companion object {
        const val LOCK_FILE = "app.lock"
    }
}
