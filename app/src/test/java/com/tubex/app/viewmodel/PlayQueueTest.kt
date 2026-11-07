package com.tubex.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayQueueTest {
    private fun q(vararg ids: String) = ids.map { QItem(it, it, "c", null) }

    @Test fun start_setsIndexAndReturnsItem() {
        val pq = PlayQueue()
        assertEquals("b", pq.start(q("a", "b", "c"), 1).url)
        assertEquals(1, pq.index)
    }

    @Test fun advance_walksThenStopsWithoutRepeat() {
        val pq = PlayQueue(); pq.start(q("a", "b"), 0)
        assertEquals("b", pq.advance()?.url)
        assertNull(pq.advance())
    }

    @Test fun advance_repeatQueueWraps() {
        val pq = PlayQueue(); pq.start(q("a", "b"), 1); pq.setRepeat(1)
        assertEquals("a", pq.advance()?.url)
    }

    @Test fun advance_shuffleVisitsEachOnceBeforeEnding() {
        val pq = PlayQueue(); pq.start(q("a", "b", "c"), 0); pq.setShuffle(true)
        val seen = mutableSetOf("a")
        repeat(2) { seen.add(pq.advance()!!.url) }
        assertEquals(setOf("a", "b", "c"), seen)
        assertNull(pq.advance())
    }

    @Test fun back_stopsAtStart() {
        val pq = PlayQueue(); pq.start(q("a", "b"), 1)
        assertEquals("a", pq.back()?.url)
        assertNull(pq.back())
    }

    @Test fun add_playNextInsertsAfterCurrent_andSeedsEmptyQueue() {
        val pq = PlayQueue()
        pq.add(QItem("n", "n", "c", null), playNext = true, seed = QItem("cur", "cur", "c", null))
        assertEquals(listOf("cur", "n"), pq.items.map { it.url })
        pq.add(QItem("m", "m", "c", null), playNext = false, seed = null)
        assertEquals(listOf("cur", "n", "m"), pq.items.map { it.url })
    }

    @Test fun remove_andMove_keepCurrentPointer() {
        val pq = PlayQueue(); pq.start(q("a", "b", "c"), 1)
        pq.move(0, 1)                       // b, a, c — বর্তমান b এখন ০
        assertEquals(0, pq.index)
        pq.remove(2)
        assertEquals(listOf("b", "a"), pq.items.map { it.url })
        pq.clear()
        assertTrue(pq.isEmpty)
    }
}
