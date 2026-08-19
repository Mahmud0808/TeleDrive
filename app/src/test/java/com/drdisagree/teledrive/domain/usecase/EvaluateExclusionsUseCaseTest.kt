package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateExclusionsUseCaseTest {

    private val useCase = EvaluateExclusionsUseCase()

    private fun candidate(
        path: String,
        size: Long = 1000,
        mime: String = "image/jpeg",
        hidden: Boolean = false
    ) = EvaluateExclusionsUseCase.Candidate(path, size, mime, hidden)

    private fun exclusion(type: ExclusionType, value: String, enabled: Boolean = true) =
        Exclusion("id", type, value, enabled, 0)

    @Test
    fun `folder exclusion matches files below it`() {
        val exclusions = listOf(exclusion(ExclusionType.FOLDER_PATH, "/storage/emulated/0/WhatsApp"))
        assertTrue(
            useCase(candidate("/storage/emulated/0/WhatsApp/Media/img.jpg"), exclusions)
        )
        assertFalse(useCase(candidate("/storage/emulated/0/DCIM/img.jpg"), exclusions))
    }

    @Test
    fun `folder exclusion does not match sibling folder with same prefix`() {
        val exclusions = listOf(exclusion(ExclusionType.FOLDER_PATH, "/a/b"))
        assertFalse(useCase(candidate("/a/bc/file.jpg"), exclusions))
    }

    @Test
    fun `extension exclusion matches case-insensitively`() {
        val exclusions = listOf(exclusion(ExclusionType.EXTENSION, "TMP"))
        assertTrue(useCase(candidate("/x/file.tmp"), exclusions))
        assertFalse(useCase(candidate("/x/file.tmpx"), exclusions))
    }

    @Test
    fun `extension exclusion accepts leading dot`() {
        val exclusions = listOf(exclusion(ExclusionType.EXTENSION, ".log"))
        assertTrue(useCase(candidate("/x/app.log"), exclusions))
    }

    @Test
    fun `mime prefix exclusion`() {
        val exclusions = listOf(exclusion(ExclusionType.MIME_TYPE, "video/"))
        assertTrue(useCase(candidate("/x/a.mp4", mime = "video/mp4"), exclusions))
        assertFalse(useCase(candidate("/x/a.jpg", mime = "image/jpeg"), exclusions))
    }

    @Test
    fun `glob pattern with double star crosses directories`() {
        val exclusions = listOf(exclusion(ExclusionType.PATH_PATTERN, "**/screenshots/**"))
        assertTrue(
            useCase(candidate("/storage/emulated/0/Pictures/Screenshots/s.png"), exclusions)
        )
        assertFalse(useCase(candidate("/storage/emulated/0/Pictures/s.png"), exclusions))
    }

    @Test
    fun `single star does not cross directories`() {
        val exclusions = listOf(exclusion(ExclusionType.PATH_PATTERN, "/a/*.jpg"))
        assertTrue(useCase(candidate("/a/photo.jpg"), exclusions))
        assertFalse(useCase(candidate("/a/b/photo.jpg"), exclusions))
    }

    @Test
    fun `max size exclusion`() {
        val exclusions = listOf(exclusion(ExclusionType.MAX_SIZE, "500"))
        assertTrue(useCase(candidate("/x/big.bin", size = 501), exclusions))
        assertFalse(useCase(candidate("/x/small.bin", size = 500), exclusions))
    }

    @Test
    fun `hidden exclusion`() {
        val exclusions = listOf(exclusion(ExclusionType.HIDDEN, ""))
        assertTrue(useCase(candidate("/x/.secret", hidden = true), exclusions))
        assertFalse(useCase(candidate("/x/visible.jpg", hidden = false), exclusions))
    }

    @Test
    fun `disabled exclusions are ignored`() {
        val exclusions = listOf(
            exclusion(ExclusionType.EXTENSION, "jpg", enabled = false)
        )
        assertFalse(useCase(candidate("/x/photo.jpg"), exclusions))
    }
}
