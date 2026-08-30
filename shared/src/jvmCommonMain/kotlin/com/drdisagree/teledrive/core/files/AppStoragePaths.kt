package com.drdisagree.teledrive.core.files

import java.io.File

/** Directories the app owns, resolved by each platform. */
interface AppStoragePaths {

    val filesDir: File

    val cacheDir: File

    val externalCacheDir: File?

    val externalStorageRoot: File?
}
