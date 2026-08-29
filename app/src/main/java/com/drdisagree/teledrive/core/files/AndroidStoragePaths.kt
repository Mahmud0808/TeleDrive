package com.drdisagree.teledrive.core.files

import android.content.Context
import android.os.Environment
import java.io.File

class AndroidStoragePaths(
    private val context: Context
) : AppStoragePaths {

    override val filesDir: File
        get() = context.filesDir

    override val cacheDir: File
        get() = context.cacheDir

    override val externalCacheDir: File?
        get() = context.externalCacheDir

    override val externalStorageRoot: File?
        get() = Environment.getExternalStorageDirectory()
}
