package com.tubex.app.data.search

import com.tubex.app.data.model.PlaylistEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePlaylistsTest {
    private fun pl(url: String, count: Long = 10) = PlaylistEntry(url, "n-$url", null, count, "up")

    // ---------- কোয়েরি ----------
    @Test fun queries_stripsPrefixesAndAppendsPlaylist() {
        assertEquals(
            listOf("cricket playlist", "Sports Hub playlist"),
            HomePlaylists.queries(listOf("kw:cricket", "ch:Sports Hub"))
        )
    }

    @Test fun queries_dedupesCaseInsensitivelyAndSkipsTinyAndTimeBuckets() {
        val out = HomePlaylists.queries(listOf("kw:Cricket", "kw:cricket", "x", "t:evening:music", "kw:গান"))
        assertEquals(listOf("Cricket playlist", "গান playlist"), out)
    }

    @Test fun queries_keepsExistingPlaylistWord() {
        assertEquals(listOf("lofi playlist"), HomePlaylists.queries(listOf("lofi playlist")))
    }

    @Test fun queries_respectsMax() {
        val out = HomePlaylists.queries(listOf("a1", "b2", "c3", "d4", "e5"), max = 3)
        assertEquals(listOf("a1 playlist", "b2 playlist", "c3 playlist"), out)
    }

    @Test fun queries_emptyProfileFallsBackToGenericQueries() {
        assertEquals(HomePlaylists.FALLBACK_QUERIES.take(2), HomePlaylists.queries(emptyList()))
        assertEquals(HomePlaylists.FALLBACK_QUERIES.take(2), HomePlaylists.queries(listOf("x", "t:morning:y")))
    }

    // ---------- মেশানো ----------
    @Test fun merge_interleavesGroupsAndDedupes() {
        val g1 = listOf(pl("a1"), pl("a2"), pl("a3"))
        val g2 = listOf(pl("b1"), pl("a1"), pl("b2"))
        assertEquals(listOf("a1", "b1", "a2", "a3", "b2"), HomePlaylists.merge(listOf(g1, g2)).map { it.url })
    }

    @Test fun merge_respectsLimit() {
        val g1 = listOf(pl("a1"), pl("a2")); val g2 = listOf(pl("b1"), pl("b2"))
        assertEquals(listOf("a1", "b1", "a2"), HomePlaylists.merge(listOf(g1, g2), limit = 3).map { it.url })
    }

    @Test fun merge_dropsEmptyPlaylistsButKeepsUnknownCount() {
        val out = HomePlaylists.merge(listOf(listOf(pl("zero", 0), pl("unknown", -1), pl("ok", 5))))
        assertEquals(listOf("unknown", "ok"), out.map { it.url })
    }

    @Test fun merge_noGroupsIsEmpty() {
        assertTrue(HomePlaylists.merge(emptyList()).isEmpty())
        assertTrue(HomePlaylists.merge(listOf(emptyList(), emptyList())).isEmpty())
    }
}
