package com.drdisagree.teledrive.core.files

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageInspector @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun availableBytes(directory: File = context.filesDir): Long = runCatching {
        StatFs(directory.absolutePath).availableBytes
    }.getOrDefault(0L)

    fun cacheSizeBytes(): Long = directorySize(context.cacheDir) +
            (context.externalCacheDir?.let(::directorySize) ?: 0L)

    fun directorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
