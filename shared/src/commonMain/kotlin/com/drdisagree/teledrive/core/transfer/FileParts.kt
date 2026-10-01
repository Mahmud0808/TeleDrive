package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest

object FileParts {

    /**
     * Far below the cap, so an interrupted upload loses only one part and a seek fetches only the
     * part it lands in.
     */
    const val PART_SIZE: Long = 512L * 1024 * 1024

    fun countFor(sizeBytes: Long): Int {
        if (sizeBytes <= 0) return 1
        val whole = sizeBytes / PART_SIZE
        val remainder = sizeBytes % PART_SIZE
        return (whole + if (remainder > 0) 1 else 0).toInt().coerceAtLeast(1)
    }

    fun offsetOf(partIndex: Int): Long = partIndex.toLong() * PART_SIZE

    fun sizeOf(partIndex: Int, totalSize: Long): Long =
        (totalSize - offsetOf(partIndex)).coerceAtMost(PART_SIZE).coerceAtLeast(0)

    fun indexOf(plainOffset: Long): Int = (plainOffset / PART_SIZE).toInt()

    fun nameFor(name: String, partIndex: Int): String =
        name + "." + (partIndex + 1).toString().padStart(3, '0')

    fun splits(sizeBytes: Long, limitBytes: Long): Boolean = sizeBytes > limitBytes

    fun asFirstPart(
        manifest: RemoteFileManifest,
        partCount: Int = countFor(manifest.sizeBytes)
    ): RemoteFileManifest = manifest.copy(
        version = RemoteFileManifest.PART_VERSION,
        partCount = partCount,
        partIndex = 0,
        partOffset = offsetOf(0),
        partSize = sizeOf(0, manifest.sizeBytes)
    )
}
