package com.drdisagree.teledrive.presentation.components

class LineHeights {

    private var headerHeight = 0f
    private var lineHeight = 0f

    fun record(model: LineModel, lines: List<ScrollLine>) {
        for (line in lines) {
            val index = model.lineOf(line.firstIndex)
            if (index >= model.lineCount) continue
            if (model.isHeader(index)) {
                headerHeight = line.height.toFloat()
            } else {
                lineHeight = line.height.toFloat()
            }
        }
    }

    private fun heightOf(model: LineModel, line: Int): Float {
        val fallback = maxOf(headerHeight, lineHeight)
        return if (model.isHeader(line)) {
            headerHeight.takeIf { it > 0f } ?: fallback
        } else {
            lineHeight.takeIf { it > 0f } ?: fallback
        }
    }

    fun before(model: LineModel, line: Int): Float {
        var sum = 0f
        for (i in 0 until line.coerceAtMost(model.lineCount)) sum += heightOf(model, i)
        return sum
    }

    fun total(model: LineModel): Float = before(model, model.lineCount)

    fun lineAt(model: LineModel, pixels: Float): Pair<Int, Float> {
        var sum = 0f
        for (i in 0 until model.lineCount) {
            val height = heightOf(model, i)
            if (sum + height > pixels) return i to (pixels - sum)
            sum += height
        }
        return (model.lineCount - 1).coerceAtLeast(0) to 0f
    }
}
