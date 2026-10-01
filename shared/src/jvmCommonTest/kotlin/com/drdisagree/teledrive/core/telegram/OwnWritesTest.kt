package com.drdisagree.teledrive.core.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnWritesTest {

    private val writes = OwnWrites()

    @Test
    fun `the echo of this session's caption edit is recognised`() {
        writes.wroteCaption(10, "td1:{\"n\":\"a.jpg\"}")

        assertTrue(writes.isOwnContent(10, "td1:{\"n\":\"a.jpg\"}"))
    }

    @Test
    fun `another device editing the same message still gets through`() {
        writes.wroteCaption(10, "td1:{\"n\":\"a.jpg\"}")

        assertFalse(writes.isOwnContent(10, "td1:{\"n\":\"b.jpg\"}"))
    }

    @Test
    fun `the latest caption written replaces the earlier one`() {
        writes.wroteCaption(10, "first")
        writes.wroteCaption(10, "second")

        assertFalse(writes.isOwnContent(10, "first"))
        assertTrue(writes.isOwnContent(10, "second"))
    }

    @Test
    fun `messages this session never touched are other devices' changes`() {
        assertFalse(writes.isOwnContent(10, "anything"))
        assertFalse(writes.isOwnContent(10, null))
    }

    @Test
    fun `only deletions made elsewhere are reported`() {
        writes.deleted(listOf(1, 2))

        assertEquals(listOf(3L), writes.deletedElsewhere(listOf(1, 2, 3)))
    }

    @Test
    fun `memory stays bounded and the oldest writes are forgotten first`() {
        val small = OwnWrites(capacity = 2)
        small.wroteCaption(1, "a")
        small.wroteCaption(2, "b")
        small.wroteCaption(3, "c")

        assertFalse(small.isOwnContent(1, "a"))
        assertTrue(small.isOwnContent(2, "b"))
        assertTrue(small.isOwnContent(3, "c"))
    }
}
