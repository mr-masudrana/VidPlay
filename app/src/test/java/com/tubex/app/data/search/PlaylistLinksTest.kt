package com.tubex.app.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistLinksTest {
    private val vid = "dQw4w9WgXcQ"

    @Test fun parse_watchUrlWithList() {
        assertEquals(
            PlaylistLinks.Link(vid, "PLabc123_-x"),
            PlaylistLinks.parse("https://www.youtube.com/watch?v=$vid&list=PLabc123_-x&index=3")
        )
    }

    @Test fun parse_listBeforeVideoAndExtraParams() {
        assertEquals(
            PlaylistLinks.Link(vid, "PLz"),
            PlaylistLinks.parse("https://m.youtube.com/watch?app=desktop&list=PLz&v=$vid")
        )
    }

    @Test fun parse_shortLinkWithList() {
        assertEquals(PlaylistLinks.Link(vid, "PLz"), PlaylistLinks.parse("https://youtu.be/$vid?list=PLz"))
    }

    @Test fun parse_plainVideoHasNoLink() {
        assertNull(PlaylistLinks.parse("https://www.youtube.com/watch?v=$vid"))
        assertNull(PlaylistLinks.parse("https://youtu.be/$vid"))
    }

    @Test fun parse_playlistPageWithoutVideoHasNoLink() {
        assertNull(PlaylistLinks.parse("https://www.youtube.com/playlist?list=PLz"))
    }

    @Test fun parse_privateAccountListsAreIgnored() {
        assertNull(PlaylistLinks.parse("https://www.youtube.com/watch?v=$vid&list=WL"))
        assertNull(PlaylistLinks.parse("https://www.youtube.com/watch?v=$vid&list=LL"))
    }

    @Test fun videoId_variantsAndNull() {
        assertEquals(vid, PlaylistLinks.videoId("https://www.youtube.com/watch?v=$vid"))
        assertEquals(vid, PlaylistLinks.videoId("https://youtu.be/$vid"))
        assertNull(PlaylistLinks.videoId("https://example.com/"))
    }

    @Test fun fetchUrl_normalListUsesPlaylistPage_mixUsesOriginalLink() {
        val raw = "https://www.youtube.com/watch?v=$vid&list=PLz"
        assertEquals("https://www.youtube.com/playlist?list=PLz", PlaylistLinks.fetchUrl(raw, PlaylistLinks.Link(vid, "PLz")))
        val mix = "https://www.youtube.com/watch?v=$vid&list=RD$vid"
        assertEquals(mix, PlaylistLinks.fetchUrl(mix, PlaylistLinks.Link(vid, "RD$vid")))
    }
}
