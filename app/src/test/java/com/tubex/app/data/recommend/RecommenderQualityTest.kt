package com.tubex.app.data.recommend

import com.tubex.app.data.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecommenderQualityTest {
    private fun v(url: String) = VideoItem("t", url, "c", null, 0, 100, null, null)

    @Test fun likeRatio_nullWhenTooFewVotes() {
        assertNull(Recommender.likeRatio(10, 5))
    }

    @Test fun likeRatio_computesRatio() {
        assertEquals(0.75, Recommender.likeRatio(75, 25)!!, 1e-9)
    }

    @Test fun demote_movesLowRatioToEndKeepingOrder() {
        val list = listOf(v("a"), v("b"), v("c"), v("d"))
        val stats = mapOf("a" to (10L to 90L), "c" to (90L to 10L), "d" to (5L to 95L))
        assertEquals(listOf("b", "c", "a", "d"), Recommender.demoteLowQuality(list, stats).map { it.url })
    }

    @Test fun demote_ignoresMissingOrTinyStats() {
        val list = listOf(v("a"), v("b"))
        val stats = mapOf("a" to (1L to 9L))   // ১০ ভোট < ৫০: সিদ্ধান্ত নেই
        assertEquals(listOf("a", "b"), Recommender.demoteLowQuality(list, stats).map { it.url })
    }
}
