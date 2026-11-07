package com.tubex.app.data.potoken

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Provider-এর "নিরাপদ ফলব্যাক" আচরণ: কিছু ঠিক না থাকলে null, যাতে Extractor আগের মতো চলে */
class PoTokenProviderTest {
    private var wasEnabled = true

    @Before fun save() { wasEnabled = PoTokenProviderImpl.enabled }
    @After fun restore() { PoTokenProviderImpl.enabled = wasEnabled }

    @Test fun disabled_returnsNull() {
        PoTokenProviderImpl.enabled = false
        assertNull(PoTokenProviderImpl.getWebClientPoToken("dQw4w9WgXcQ"))
    }

    @Test fun withoutContext_returnsNullInsteadOfCrashing() {
        // JVM টেস্টে init(context) কখনো ডাকা হয় না
        PoTokenProviderImpl.enabled = true
        assertNull(PoTokenProviderImpl.getWebClientPoToken("dQw4w9WgXcQ"))
    }

    @Test fun otherClients_neverGetTokens() {
        assertNull(PoTokenProviderImpl.getWebEmbedClientPoToken("x"))
        assertNull(PoTokenProviderImpl.getAndroidClientPoToken("x"))
        assertNull(PoTokenProviderImpl.getIosClientPoToken("x"))
    }

    @Test fun reset_withoutGeneratorIsNoOp() {
        PoTokenProviderImpl.reset()
        PoTokenProviderImpl.reset()
    }

    @Test fun prewarm_whenDisabledDoesNothing() {
        PoTokenProviderImpl.enabled = false
        PoTokenProviderImpl.prewarm()
    }

    // ---------- visitorData পার্স ----------
    @Test fun visitorData_parsedFromHomepageHtml() {
        val html = """<script>ytcfg.set({"VISITOR_DATA":"x","visitorData":"CgtBYmMtX3lA%3D%3D","a":1})</script>"""
        assertEquals("CgtBYmMtX3lA%3D%3D", VisitorData.parse(html))
    }

    @Test fun visitorData_takesFirstMatch() {
        assertEquals("AAA", VisitorData.parse("""{"visitorData":"AAA"} {"visitorData":"BBB"}"""))
    }

    @Test fun visitorData_missingReturnsNull() {
        assertNull(VisitorData.parse("<html>no token here</html>"))
        assertNull(VisitorData.parse(""))
    }

    @Test fun visitorData_fetchWithoutDownloaderReturnsNullNotCrash() {
        // NewPipe.init ডাকা হয়নি → Downloader নেই; ব্যতিক্রম গিলে null ফেরত দিতে হবে
        assertNull(VisitorData.fetch())
    }
}
