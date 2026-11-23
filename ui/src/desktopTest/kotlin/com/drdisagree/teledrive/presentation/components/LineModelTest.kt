package com.drdisagree.teledrive.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineModelTest {

    @Test
    fun `each day is a header line followed by its rows of photos`() {
        val model = LineModel.days(listOf(5, 2), columns = 3)

        assertEquals(9, model.totalItems)
        assertEquals(5, model.lineCount)
        assertEquals(listOf(0, 1, 4, 6, 7), (0 until model.lineCount).map(model::startOf))
        assertTrue(model.isHeader(0))
        assertFalse(model.isHeader(1))
        assertTrue(model.isHeader(3))
    }

    @Test
    fun `items map to the line that holds them`() {
        val model = LineModel.days(listOf(5, 2), columns = 3)

        assertEquals(listOf(0, 1, 1, 1, 2, 2, 3, 4, 4), (0 until 9).map(model::lineOf))
    }

    @Test
    fun `a plain list fills rows evenly`() {
        val model = LineModel.uniform(totalItems = 10, columns = 4)

        assertEquals(3, model.lineCount)
        assertEquals(2, model.lineOf(9))
    }
}
