package com.tubex.app.data.search

import com.tubex.app.data.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFilterTest {
    private fun v(url: String, dur: Long, views: Long = 0, up: String? = null) =
        VideoItem("t", url, "c", null, views, dur, null, up)

    @Test fun ageHours_englishAndBangla() {
        assertEquals(72L, SearchFilter.ageHours("3 days ago"))
        assertEquals(72L, SearchFilter.ageHours("৩ দিন আগে"))
        assertEquals(5L, SearchFilter.ageHours("5 hours ago"))
        assertNull(SearchFilter.ageHours("Streamed live"))
    }

    @Test fun apply_durationShortFilter() {
        val list = listOf(v("a", 100), v("b", 600), v("c", 3000))
        assertEquals(listOf("a"), SearchFilter.apply(list, 0, 1, 0).map { it.url })
        assertEquals(listOf("b"), SearchFilter.apply(list, 0, 2, 0).map { it.url })
        assertEquals(listOf("c"), SearchFilter.apply(list, 0, 3, 0).map { it.url })
    }

    @Test fun apply_sortByViews() {
        val list = listOf(v("a", 100, 5), v("b", 100, 50), v("c", 100, 20))
        assertEquals(listOf("b", "c", "a"), SearchFilter.apply(list, 0, 0, 2).map { it.url })
    }
}
