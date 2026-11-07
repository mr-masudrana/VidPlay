package com.tubex.app.data.cache

import com.tubex.app.data.model.VideoItem

/** ফিডের (আপনার জন্য / ক্যাটাগরি) স্বল্পমেয়াদি ক্যাশ: একই ফিডে ফিরলে আবার লোড হয় না। */
class FeedCache(private val ttlMs: Long = 10 * 60_000L) {
    private val map = HashMap<String, Pair<Long, List<VideoItem>>>()

    /** মেয়াদ থাকলে এবং তালিকা খালি না হলে */
    fun get(key: String, now: Long = System.currentTimeMillis()): List<VideoItem>? =
        map[key]?.takeIf { now - it.first < ttlMs && it.second.isNotEmpty() }?.second

    /** মেয়াদ না দেখে (ব্যাক জেসচারের প্রিভিউর জন্য) */
    fun peek(key: String): List<VideoItem>? = map[key]?.second

    fun put(key: String, list: List<VideoItem>, now: Long = System.currentTimeMillis()) {
        if (list.isNotEmpty()) map[key] = now to list
    }
}
