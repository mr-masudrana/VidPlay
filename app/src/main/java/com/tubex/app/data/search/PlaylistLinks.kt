package com.tubex.app.data.search

/**
 * `watch?v=ভিডিও&list=প্লেলিস্ট` ধরনের লিংক চেনা: অফিসিয়াল YouTube-এর মতো এমন লিংকে ভিডিওটা চলে
 * এবং পুরো প্লেলিস্ট কিউতে ওঠে। Android-মুক্ত, তাই ইউনিট টেস্টযোগ্য।
 */
object PlaylistLinks {
    data class Link(val videoId: String, val listId: String)

    private val listRe = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
    private val vRe = Regex("""[?&]v=([A-Za-z0-9_-]{11})""")
    private val shortRe = Regex("""youtu\.be/([A-Za-z0-9_-]{11})""")

    /** প্রাইভেট/অ্যাকাউন্ট-ভিত্তিক তালিকা (পরে দেখুন, লাইক করা, লাইব্রেরি) — লগইন ছাড়া আনা যায় না */
    private val PRIVATE = setOf("WL", "LL", "LM")

    fun videoId(url: String): String? =
        vRe.find(url)?.groupValues?.get(1) ?: shortRe.find(url)?.groupValues?.get(1)

    /** ভিডিও ও প্লেলিস্ট দুটোই থাকলে (এবং প্লেলিস্ট খোলা যাওয়ার মতো হলে) Link, নইলে null */
    fun parse(raw: String): Link? {
        val v = videoId(raw) ?: return null
        val l = listRe.find(raw)?.groupValues?.get(1) ?: return null
        if (l in PRIVATE) return null
        return Link(v, l)
    }

    /** প্লেলিস্ট আনার URL; মিক্স (RD…) আসল লিংকেই চলে, কারণ মিক্স ভিডিও-নির্ভর */
    fun fetchUrl(raw: String, link: Link): String =
        if (link.listId.startsWith("RD")) raw else "https://www.youtube.com/playlist?list=${link.listId}"
}
