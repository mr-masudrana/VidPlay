package com.tubex.app.data.search

import com.tubex.app.data.model.ChannelResult
import com.tubex.app.data.recommend.Blocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSearchTest {
    private fun ch(name: String, url: String = "https://youtube.com/c/$name", subs: Long = 100) =
        ChannelResult(url, name, null, subs, null)

    // ---------- ব্লক ----------
    @Test fun blockKey_isLowercasedChPrefix() {
        assertEquals("ch:mrbeast", ChannelSearch.blockKey("MrBeast"))
    }

    @Test fun blockKey_matchesWhatVideoFilterBlocks() {
        // চ্যানেল সারি ও ভিডিও তালিকা একই ব্লক-কী ব্যবহার করে — কোনোটা আলাদা হয়ে গেলে এখানে ধরা পড়বে
        val set = setOf(ChannelSearch.blockKey("MrBeast"))
        assertFalse(Blocks.allowed(set, "any title", "MRBEAST", "https://youtu.be/x"))
        assertTrue(Blocks.allowed(set, "any title", "Other", "https://youtu.be/x"))
    }

    @Test fun filterBlocked_removesBlockedCaseInsensitivelyKeepingOrder() {
        val list = listOf(ch("Alpha"), ch("MrBeast"), ch("Gamma"))
        val out = ChannelSearch.filterBlocked(list, setOf("ch:mrbeast"))
        assertEquals(listOf("Alpha", "Gamma"), out.map { it.name })
    }

    @Test fun filterBlocked_ignoresOtherBlockKinds() {
        val list = listOf(ch("Alpha"), ch("Beta"))
        val out = ChannelSearch.filterBlocked(list, setOf("kw:alpha", "vid:https://youtu.be/x"))
        assertEquals(2, out.size)
    }

    @Test fun filterBlocked_emptyBlockSetReturnsSameList() {
        val list = listOf(ch("Alpha"))
        assertSame(list, ChannelSearch.filterBlocked(list, emptySet()))
    }

    @Test fun filterBlocked_allBlockedGivesEmpty() {
        assertTrue(ChannelSearch.filterBlocked(listOf(ch("A")), setOf("ch:a")).isEmpty())
    }

    // ---------- NewPipe আইটেম → ফলাফল ----------
    @Test fun toResult_usesLastThumbnailAsAvatar() {
        val r = ChannelSearch.toResult("u", "N", listOf("small.jpg", "mid.jpg", "big.jpg"), 1_500, "desc")
        assertEquals("big.jpg", r.avatar)
        assertEquals(ChannelResult("u", "N", "big.jpg", 1_500, "desc"), r)
    }

    @Test fun toResult_noThumbnailsMeansNullAvatar() {
        assertNull(ChannelSearch.toResult("u", "N", emptyList(), 0, null).avatar)
    }

    @Test fun toResult_keepsUnknownSubscriberCountMarker() {
        // NewPipe অজানা সংখ্যায় -1 দেয়; UI "c.subscribers >= 0" দিয়ে লুকায়, তাই মান বদলানো চলবে না
        assertEquals(-1L, ChannelSearch.toResult("u", "N", emptyList(), -1, null).subscribers)
    }

    // ---------- সাবস্ক্রাইব অবস্থা ----------
    @Test fun isSubscribed_matchesByUrlNotName() {
        val c = ch("Alpha", url = "https://youtube.com/channel/UC1")
        assertTrue(ChannelSearch.isSubscribed(listOf("https://youtube.com/channel/UC1"), c))
        assertFalse(ChannelSearch.isSubscribed(listOf("https://youtube.com/channel/UC2"), c))
        assertFalse(ChannelSearch.isSubscribed(emptyList(), c))
    }

    @Test fun defaultLimit_isSmallSoVideosStayOnTop() {
        assertEquals(3, ChannelSearch.DEFAULT_LIMIT)
    }
}
