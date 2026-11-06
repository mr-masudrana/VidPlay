package com.tubex.app.data.model

import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.Page

data class VideoItem(
    val title: String,
    val url: String,
    val channel: String,
    val channelUrl: String?,
    val views: Long,
    val durationSec: Long,
    val thumb: String?,
    val uploaded: String?,
    val reason: String? = null,
    val channelAvatar: String? = null
)

data class ChannelData(
    val url: String, val name: String, val avatar: String?, val banner: String?,
    val subscribers: Long, val description: String?, val videos: List<VideoItem>,
    val handle: String? = null,            // @ইউজারনেম
    val videoCount: String? = null,        // যেমন "52K" বা "1,234"
    val tab: ListLinkHandler? = null,      // ভিডিও ট্যাব (আরও লোডের জন্য)
    val next: Page? = null,                // পরের পেজ; null = আর নেই
    val playlistsTab: ListLinkHandler? = null   // চ্যানেলের প্লেলিস্ট ট্যাব (না থাকলে null)
)

/** ইউটিউব প্লেলিস্ট (অনলাইন) */
data class PlaylistData(
    val url: String, val name: String, val uploader: String?, val thumb: String?,
    val count: Long, val videos: List<VideoItem>, val next: Page?
)

/** চ্যানেলের প্লেলিস্ট ট্যাবের একটি আইটেম */
data class PlaylistEntry(val url: String, val name: String, val thumb: String?, val count: Long, val uploader: String?)

data class CommentItem(
    val id: String, val author: String, val authorUrl: String?, val text: String, val likes: String,
    val date: String, val avatar: String?, val replyCount: Int, val replies: Page?, val time: Long?
)

data class CommentPage(val items: List<CommentItem>, val next: Page?, val total: Int = -1)
