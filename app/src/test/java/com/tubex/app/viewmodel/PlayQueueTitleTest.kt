package com.tubex.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** কিউ কোন প্লেলিস্ট থেকে এসেছে তার নাম (অফিসিয়াল YouTube-এর মতো) */
class PlayQueueTitleTest {
    private fun q(n: Int) = (0 until n).map { QItem("https://youtu.be/$it", "t$it", "c", null) }

    @Test fun start_setsTitle() {
        val pq = PlayQueue()
        pq.start(q(3), 1, title = "Road trip")
        assertEquals("Road trip", pq.title)
        assertEquals(1, pq.index)
    }

    @Test fun start_withoutTitleClearsOldOne() {
        val pq = PlayQueue()
        pq.start(q(3), 0, title = "A")
        pq.start(q(2), 0)
        assertNull(pq.title)
    }

    @Test fun clear_resetsTitle() {
        val pq = PlayQueue()
        pq.start(q(3), 0, title = "A")
        pq.clear()
        assertNull(pq.title)
    }

    @Test fun advanceAndReplaceItems_keepTitle() {
        val pq = PlayQueue()
        pq.start(q(3), 0, title = "A")
        pq.advance()
        pq.replaceItems(q(6))
        assertEquals("A", pq.title)
    }

    @Test fun shuffleStartInsidePlaylist_keepsTitleAndStaysInList() {
        val pq = PlayQueue()
        val first = pq.start(q(5), 0, shuffleStart = true, title = "Mix")
        assertEquals("Mix", pq.title)
        assertEquals(true, pq.shuffle)
        assertEquals(pq.items[pq.index], first)
    }

    @Test fun addToPlaylistQueue_keepsTitle() {
        val pq = PlayQueue()
        pq.start(q(2), 0, title = "A")
        pq.add(QItem("https://youtu.be/new", "n", "c", null), playNext = true, seed = null)
        assertEquals("A", pq.title)
        assertEquals(3, pq.items.size)
    }
}
