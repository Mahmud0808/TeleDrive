package com.drdisagree.teledrive.core.files

/** Asks for platform consent where deleting requires it. */
interface LocalCopyDeleter {

    fun delete(paths: List<String>): LocalCleanup

    fun isGone(path: String): Boolean
}
