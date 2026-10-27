package com.example.vidplay

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * অন-ডিভাইস রিকমেন্ডেশন: কোনো সার্ভার/অ্যাকাউন্ট নেই।
 * দেখা ভিডিওর চ্যানেল ও শিরোনামের শব্দে "আগ্রহ স্কোর" জমে; সময়ের সাথে স্কোর কমে (half-life ১৪ দিন)।
 */
object Recommender {
    private const val HALF_LIFE_MS = 14 * 24 * 3600 * 1000.0

    private val stop = setOf(
        "the", "and", "for", "with", "this", "that", "from", "you", "your", "are", "was", "how", "what",
        "official", "video", "full", "new", "best", "live", "free", "part", "episode", "www", "com", "http", "https",
        "এবং", "এই", "এর", "একটি", "জন্য"
    )

    fun keywords(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in stop && !it.all { c -> c.isDigit() } }
            .distinct().take(8)

    private fun decayed(e: InterestEntity, now: Long): Double =
        e.score * 0.5.pow((now - e.updatedAt) / HALF_LIFE_MS)

    private fun keysFor(title: String, channel: String): List<Pair<String, Double>> = buildList {
        if (channel.isNotBlank()) add("ch:${channel.lowercase()}" to 2.0)
        keywords(title).forEach { add("kw:$it" to 1.0) }
    }

    suspend fun boost(dao: AppDao, title: String, channel: String, weight: Double) {
        val now = System.currentTimeMillis()
        val existing = dao.interests().associateBy { it.topic }
        val updated = keysFor(title, channel).map { (k, w) ->
            val base = existing[k]?.let { decayed(it, now) } ?: 0.0
            InterestEntity(k, base + w * weight, now)
        }
        dao.upsertInterests(updated)
        dao.pruneInterests()
    }

    fun top(interests: List<InterestEntity>, n: Int): List<Pair<String, Double>> {
        val now = System.currentTimeMillis()
        return interests.map { it.topic to decayed(it, now) }.sortedByDescending { it.second }.take(n)
    }

    /** স্কোর অনুযায়ী সাজায়, একই চ্যানেল ২টির বেশি পরপর আসতে দেয় না (ডাইভার্সিটি), দেখা ভিডিও বাদ দেয়। */
    fun rank(videos: List<VideoItem>, interests: List<InterestEntity>, seen: Set<String>): List<VideoItem> {
        val now = System.currentTimeMillis()
        val map = interests.associate { it.topic to decayed(it, now) }

        fun score(v: VideoItem): Double {
            var s = map["ch:${v.channel.lowercase()}"] ?: 0.0
            val kws = keywords(v.title)
            kws.forEach { s += map["kw:$it"] ?: 0.0 }
            return s / sqrt(1.0 + kws.size) + Random.nextDouble() * 0.5   // একটু এলোমেলো = নতুন কিছুর সুযোগ
        }

        val scored = videos.filter { it.url !in seen }.map { it to score(it) }
            .sortedByDescending { it.second }.map { it.first }
        val count = HashMap<String, Int>()
        val first = ArrayList<VideoItem>()
        val rest = ArrayList<VideoItem>()
        for (v in scored) {
            val c = count[v.channel] ?: 0
            if (c < 2) { first.add(v); count[v.channel] = c + 1 } else rest.add(v)
        }
        return first + rest
    }
}
