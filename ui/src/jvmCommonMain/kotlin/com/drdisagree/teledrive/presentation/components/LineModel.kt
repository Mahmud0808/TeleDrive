package com.drdisagree.teledrive.presentation.components

class LineModel private constructor(
    private val starts: IntArray,
    private val headers: BooleanArray,
    val totalItems: Int
) {
    val lineCount: Int get() = starts.size

    fun startOf(line: Int): Int = starts[line]

    fun isHeader(line: Int): Boolean = headers[line]

    fun lineOf(index: Int): Int {
        var low = 0
        var high = starts.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (starts[mid] <= index) low = mid else high = mid - 1
        }
        return low.coerceAtLeast(0)
    }

    companion object {
        fun uniform(totalItems: Int, columns: Int): LineModel {
            val perLine = columns.coerceAtLeast(1)
            val count = (totalItems + perLine - 1) / perLine
            return LineModel(IntArray(count) { it * perLine }, BooleanArray(count), totalItems)
        }

        fun days(counts: List<Int>, columns: Int): LineModel {
            val perLine = columns.coerceAtLeast(1)
            val starts = ArrayList<Int>()
            val headers = ArrayList<Boolean>()
            var index = 0
            for (count in counts) {
                starts += index
                headers += true
                index++
                for (line in 0 until (count + perLine - 1) / perLine) {
                    starts += index + line * perLine
                    headers += false
                }
                index += count
            }
            return LineModel(starts.toIntArray(), headers.toBooleanArray(), index)
        }
    }
}
