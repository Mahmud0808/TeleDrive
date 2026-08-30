package com.drdisagree.teledrive.core.files

/**
 * Turns a picked document reference into a real path the transfer engine can
 * upload and resume from. References are platform URIs on Android and plain
 * file paths on desktop.
 */
interface FileImporter {

    fun import(reference: String): ImportedFile?

    /** Drops a staged copy the drive turned out not to need. */
    fun discard(imported: ImportedFile)
}
