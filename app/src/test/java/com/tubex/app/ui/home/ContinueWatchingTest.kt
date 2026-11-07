package com.tubex.app.ui.home

import com.tubex.app.data.local.entity.HistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingTest {
    private fun h(url: String, dur: Long, pos: Long, at: Long) =
        HistoryEntity(url, "t", "c", null, null, dur, pos, at)

    @Test fun keepsOnlyPartiallyWatchedNewestFirst() {
        val list = listOf(
            h("done", 100, 95_000, 5),      // ৯০%-এর বেশি: বাদ
            h("just-started", 100, 2_000, 6), // ৫ সেকেন্ডের কম: বাদ
            h("old", 100, 40_000, 1),
            h("new", 100, 50_000, 9),
            h("live", 0, 40_000, 8)           // দৈর্ঘ্য নেই: বাদ
        )
        assertEquals(listOf("new", "old"), continueWatching(list).map { it.url })
    }
}
