package com.tubex.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun fmtCount_scales() {
        assertEquals("999", fmtCount(999))
        assertEquals("1.5K", fmtCount(1_500))
        assertEquals("2.0M", fmtCount(2_000_000))
        assertEquals("", fmtCount(-1))
    }

    @Test fun fmtDur_formats() {
        assertEquals("LIVE", fmtDur(0))
        assertEquals("1:05", fmtDur(65))
        assertEquals("1:01:01", fmtDur(3661))
    }
}
