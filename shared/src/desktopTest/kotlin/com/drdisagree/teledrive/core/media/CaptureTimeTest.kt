package com.drdisagree.teledrive.core.media

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTimeTest {

    private val dhaka = TimeZone.getTimeZone("Asia/Dhaka")

    @Test
    fun `an exif time without an offset is read in the device's zone`() {
        assertEquals(
            1_683_864_930_000L,
            CaptureTime.fromExif("2023:05:12 10:15:30", null, dhaka)
        )
    }

    @Test
    fun `an exif offset wins over the device's zone`() {
        assertEquals(
            1_683_886_530_000L,
            CaptureTime.fromExif("2023:05:12 10:15:30", "+00:00", dhaka)
        )
    }

    @Test
    fun `blank, zeroed or malformed exif times are ignored`() {
        assertNull(CaptureTime.fromExif(null, null, dhaka))
        assertNull(CaptureTime.fromExif("    ", null, dhaka))
        assertNull(CaptureTime.fromExif("0000:00:00 00:00:00", null, dhaka))
        assertNull(CaptureTime.fromExif("2023:05:12 10:15:30 junk", null, dhaka))
    }

    @Test
    fun `video dates are read as UTC with or without milliseconds`() {
        assertEquals(1_683_886_530_000L, CaptureTime.fromMediaDate("20230512T101530.000Z"))
        assertEquals(1_683_886_530_000L, CaptureTime.fromMediaDate("20230512T101530Z"))
    }

    @Test
    fun `placeholder and future dates are not trusted`() {
        assertNull(CaptureTime.fromMediaDate("19040101T000000.000Z"))
        assertNull(CaptureTime.fromMediaDate("19700101T000000.000Z"))
        assertNull(CaptureTime.plausible(2_000_000_000_000L, now = 1_700_000_000_000L))
    }
}
