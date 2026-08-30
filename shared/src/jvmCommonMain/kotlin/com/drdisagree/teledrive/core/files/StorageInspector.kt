package com.drdisagree.teledrive.core.files

import java.io.File

class StorageInspector(
    private val storagePaths: AppStoragePaths
) {

    fun availableBytes(directory: File = storagePaths.filesDir): Long = runCatching {
        directory.usableSpace
    }.getOrDefault(0L)

    fun cacheSizeBytes(): Long = directorySize(storagePaths.cacheDir) +
            (storagePaths.externalCacheDir?.let(::directorySize) ?: 0L)

    fun directorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
