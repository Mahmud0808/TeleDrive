package com.drdisagree.teledrive.core.transfer

/** Sizing and naming rules shared by everything that handles a split file. */
object FileParts {

    /**
     * Telegram accepts 2 GB per file, 4 GB with Premium. Parts are far smaller
     * than the cap on purpose: an interrupted upload only loses the part in
     * flight, and a player seeking into a file only fetches the part it lands in.
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
}
