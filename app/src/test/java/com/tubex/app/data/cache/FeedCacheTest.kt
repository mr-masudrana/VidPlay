package com.tubex.app.data.cache

import com.tubex.app.data.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedCacheTest {
    private fun v(u: String) = VideoItem("t", u, "c", null, 0, 10, null, null)

    @Test fun get_returnsFreshEntry_nullWhenExpiredOrEmpty() {
        val c = FeedCache(ttlMs = 1000)
        c.put("k", listOf(v("a")), now = 0)
        assertEquals(1, c.get("k", now = 500)!!.size)
        assertNull(c.get("k", now = 1500))
        c.put("e", emptyList(), now = 0)
        assertNull(c.peek("e"))
    }

    @Test fun peek_ignoresExpiry() {
        val c = FeedCache(ttlMs = 1000)
        c.put("k", listOf(v("a")), now = 0)
        assertEquals("a", c.peek("k")!!.first().url)
    }
}
