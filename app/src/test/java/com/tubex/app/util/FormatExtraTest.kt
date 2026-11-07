package com.tubex.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatExtraTest {
    @Test fun fmtCount_millionsAndBillions() {
        assertEquals("2.5M", fmtCount(2_500_000))
        assertEquals("1.2B", fmtCount(1_200_000_000))
    }

    @Test fun fmtTime_zeroIsNotLive() {
        assertEquals("0:00", fmtTime(0))
        assertEquals("2:03:04", fmtTime(7_384))
    }

    @Test fun fixUrl_handlesProtocolRelativeAndBlank() {
        assertEquals("https://a.com/x.jpg", fixUrl("//a.com/x.jpg"))
        assertEquals("https://a.com/x.jpg", fixUrl("https://a.com/x.jpg"))
        assertNull(fixUrl(""))
        assertNull(fixUrl(null))
    }

    @Test fun textUtil_plainTextKeepsLinesAndCollapsesBlankRuns() {
        assertEquals("", TextUtil.clean(null))
        assertEquals("a\n\nb", TextUtil.clean("a\n\n\n\nb  "))
    }
}
