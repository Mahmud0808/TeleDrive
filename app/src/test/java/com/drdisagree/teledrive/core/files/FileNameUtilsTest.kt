package com.drdisagree.teledrive.core.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameUtilsTest {

    @Test
    fun `sanitize strips invalid characters`() {
        assertEquals("a_b_c_.jpg", FileNameUtils.sanitize("a/b\\c?.jpg"))
    }

    @Test
    fun `sanitize of blank yields placeholder`() {
        assertEquals("unnamed", FileNameUtils.sanitize("   "))
    }

    @Test
    fun `unique name appends counter before extension`() {
        val existing = setOf("photo.jpg", "photo (1).jpg")
        val result = FileNameUtils.uniqueName("photo.jpg") { it in existing }
        assertEquals("photo (2).jpg", result)
    }

    @Test
    fun `unique name without extension`() {
        val existing = setOf("notes")
        assertEquals("notes (1)", FileNameUtils.uniqueName("notes") { it in existing })
    }

    @Test
    fun `unique name returns original when free`() {
        assertEquals("free.txt", FileNameUtils.uniqueName("free.txt") { false })
    }

    @Test
    fun `extension and base name helpers`() {
        assertEquals("jpg", FileNameUtils.extensionOf("a.b.jpg"))
        assertEquals("a.b", FileNameUtils.baseNameOf("a.b.jpg"))
        assertEquals("", FileNameUtils.extensionOf("noext"))
    }
}
