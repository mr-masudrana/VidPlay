package com.tubex.app.data.search

import com.tubex.app.data.model.PlaylistEntry

/**
 * হোমের "প্লেলিস্ট" সারির যুক্তি (নেটওয়ার্ক ছাড়া, ইউনিট টেস্টযোগ্য):
 * আগ্রহের বিষয় থেকে সার্চ-কোয়েরি বানানো এবং একাধিক সার্চের ফলাফল মিশিয়ে একটি তালিকা করা।
 */
object HomePlaylists {
    /** আগ্রহ-প্রোফাইল খালি থাকলে (নতুন ইউজার) এগুলো দিয়ে খোঁজা হয় */
    val FALLBACK_QUERIES = listOf("popular music playlist", "best of playlist")

    const val MAX_QUERIES = 3
    const val MAX_ITEMS = 12

    /** topics: ইন্টারেস্ট কী ("kw:ক্রিকেট", "ch:চ্যানেল", কিংবা সাদা টেক্সট); "t:" সময়-বাকেট বাদ */
    fun queries(topics: List<String>, max: Int = MAX_QUERIES): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (raw in topics) {
            if (raw.startsWith("t:")) continue
            val t = raw.removePrefix("kw:").removePrefix("ch:").trim()
            if (t.length < 2 || !seen.add(t.lowercase())) continue
            out += if (t.contains("playlist", ignoreCase = true)) t else "$t playlist"
            if (out.size >= max) break
        }
        return if (out.isEmpty()) FALLBACK_QUERIES.take(2) else out
    }

    /** প্রতিটি সার্চ থেকে পালাক্রমে নিয়ে মেশায় (এক বিষয়ের আধিপত্য এড়াতে); একই URL একবার; ভিডিও-সংখ্যা ০ হলে বাদ */
    fun merge(groups: List<List<PlaylistEntry>>, limit: Int = MAX_ITEMS): List<PlaylistEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<PlaylistEntry>()
        val rounds = groups.maxOfOrNull { it.size } ?: 0
        loop@ for (i in 0 until rounds) {
            for (g in groups) {
                val e = g.getOrNull(i) ?: continue
                if (e.count == 0L || !seen.add(e.url)) continue
                out += e
                if (out.size >= limit) break@loop
            }
        }
        return out
    }
}
