package com.drdisagree.teledrive.presentation

import com.drdisagree.teledrive.domain.model.ViewMode
import com.drdisagree.teledrive.presentation.components.GridZoomLevel
import com.drdisagree.teledrive.presentation.components.MAX_GRID_COLUMNS
import com.drdisagree.teledrive.presentation.components.MIN_GRID_COLUMNS
import com.drdisagree.teledrive.presentation.components.zoomedIn
import com.drdisagree.teledrive.presentation.components.zoomedOut
import org.junit.Assert.assertEquals
import org.junit.Test

class GridZoomTest {

    @Test
    fun `zooming out past the densest grid switches to list`() {
        val densest = GridZoomLevel(ViewMode.GRID, MAX_GRID_COLUMNS)
        assertEquals(GridZoomLevel(ViewMode.LIST, MAX_GRID_COLUMNS), densest.zoomedOut())
    }

    @Test
    fun `zooming in from list returns the densest grid`() {
        val list = GridZoomLevel(ViewMode.LIST, 3)
        assertEquals(GridZoomLevel(ViewMode.GRID, MAX_GRID_COLUMNS), list.zoomedIn())
    }

    @Test
    fun `zooming out past list stays on list`() {
        val list = GridZoomLevel(ViewMode.LIST, MAX_GRID_COLUMNS)
        assertEquals(list, list.zoomedOut())
    }

    @Test
    fun `zooming in stops at the largest tiles`() {
        val largest = GridZoomLevel(ViewMode.GRID, MIN_GRID_COLUMNS)
        assertEquals(largest, largest.zoomedIn())
    }

    @Test
    fun `zoom steps walk every level in both directions`() {
        var level = GridZoomLevel(ViewMode.GRID, MIN_GRID_COLUMNS)
        val out = mutableListOf(level)
        repeat(MAX_GRID_COLUMNS - MIN_GRID_COLUMNS + 1) {
            level = level.zoomedOut()
            out += level
        }
        assertEquals(
            listOf(
                GridZoomLevel(ViewMode.GRID, 2),
                GridZoomLevel(ViewMode.GRID, 3),
                GridZoomLevel(ViewMode.GRID, 4),
                GridZoomLevel(ViewMode.GRID, 5),
                GridZoomLevel(ViewMode.GRID, 6),
                GridZoomLevel(ViewMode.LIST, 6)
            ),
            out
        )

        var back = out.last()
        val inward = mutableListOf(back)
        repeat(MAX_GRID_COLUMNS - MIN_GRID_COLUMNS + 1) {
            back = back.zoomedIn()
            inward += back
        }
        assertEquals(GridZoomLevel(ViewMode.GRID, MIN_GRID_COLUMNS), inward.last())
    }
}
