package com.drdisagree.teledrive.core.files

/**
 * Turns a picked document reference into a real path the transfer engine can
 * upload and resume from. References are platform URIs on Android and plain
 * file paths on desktop.
 */
interface FileImporter {

    fun import(reference: String): ImportedFile?

    /**
     * Splits a picked reference into the files to import. A folder yields one
     * entry per file inside it; anything else yields the reference itself.
     */
    fun expand(reference: String): List<ImportSource>

    /** Drops a staged copy the drive turned out not to need. */
    fun discard(imported: ImportedFile)

    fun isStaged(path: String): Boolean

    /** Deletes staged copies that no drive row references. */
    fun sweepOrphans(referencedPaths: Set<String>)
}
