package com.tubex.app.data.search

import com.tubex.app.data.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchPlanTest {
    private fun v(id: String, dur: Long = 300) = VideoItem("t", "https://youtu.be/$id", "c", null, 0, dur, null, null)

    @Test fun picksFirstUncachedInOrder() {
        val list = listOf(v("a"), v("b"), v("c"))
        assertEquals(listOf("a", "b"), PrefetchPlan.pick(list, { false }, 2).map { it.url.substringAfterLast('/') })
    }

    @Test fun skipsLiveStreams() {
        val list = listOf(v("live", 0), v("b"), v("c"))
        assertEquals(listOf("b", "c"), PrefetchPlan.pick(list, { false }, 2).map { it.url.substringAfterLast('/') })
    }

    @Test fun skipsAlreadyCachedOrInFlight() {
        val list = listOf(v("a"), v("b"), v("c"))
        val picked = PrefetchPlan.pick(list, { it.endsWith("/a") }, 2)
        assertEquals(listOf("b", "c"), picked.map { it.url.substringAfterLast('/') })
    }

    @Test fun onlyLooksAtTheTopWindow() {
        // প্রথম ৬টি সবই ক্যাশে → সপ্তম ভিডিও (ব্যবহারকারী সাধারণত যেখানে যায় না) আনা হবে না
        val list = (1..10).map { v("v$it") }
        val cached = (1..6).map { "https://youtu.be/v$it" }.toSet()
        assertTrue(PrefetchPlan.pick(list, { it in cached }, 2).isEmpty())
    }

    @Test fun respectsMaxAndEmptyList() {
        assertEquals(1, PrefetchPlan.pick(listOf(v("a"), v("b")), { false }, 1).size)
        assertTrue(PrefetchPlan.pick(emptyList(), { false }, 3).isEmpty())
        assertTrue(PrefetchPlan.pick(listOf(v("a")), { false }, 0).isEmpty())
    }
}
