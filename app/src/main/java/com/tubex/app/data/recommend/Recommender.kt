package com.tubex.app.data.recommend

import com.tubex.app.data.local.AppDao
import com.tubex.app.data.local.entity.HistoryEntity
import com.tubex.app.data.local.entity.InterestEntity
import com.tubex.app.data.model.VideoItem
import com.tubex.app.util.L

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * অন-ডিভাইস রিকমেন্ডেশন (কোনো সার্ভার/অ্যাকাউন্ট নেই)।
 * সিগন্যাল: দেখার পরিমাণ (৫০%/৯০%), দ্রুত স্কিপ, ফেভারিট, সাবস্ক্রাইব, সার্চ, "আগ্রহ নেই"।
 * প্যাটার্ন: সময়ের বাকেট (কর্মদিবস/ছুটির দিন × সকাল/দুপুর/সন্ধ্যা/রাত), বিরক্তি শনাক্তকরণ, ডাইভার্সিটি।
 * স্কোর সময়ের সাথে কমে (half-life ১৪ দিন)।
 */
object Recommender {
    private const val HALF_LIFE_MS = 14 * 24 * 3600 * 1000.0

    private val stop = setOf(
        "the", "and", "for", "with", "this", "that", "from", "you", "your", "are", "was", "how", "what",
        "official", "video", "full", "new", "best", "live", "free", "part", "episode", "www", "com", "http", "https",
        "এবং", "এই", "এর", "একটি", "জন্য"
    )

    fun keywords(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+"))
            .filter { it.length >= 3 && it !in stop && !it.all { c -> c.isDigit() } }
            .distinct().take(8)

    private fun decayed(e: InterestEntity, now: Long): Double =
        e.score * 0.5.pow((now - e.updatedAt) / HALF_LIFE_MS)

    // ---------- সময়ের প্যাটার্ন ----------
    fun bucket(cal: Calendar = Calendar.getInstance()): String {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekend = if (TimeZone.getDefault().id == "Asia/Dhaka")
            dow == Calendar.FRIDAY || dow == Calendar.SATURDAY
        else dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val part = when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "m"
            in 12..16 -> "a"
            in 17..21 -> "e"
            else -> "n"
        }
        return (if (weekend) "we" else "wd") + "-" + part
    }

    fun bucketLabel(b: String = bucket()): String {
        val day = if (b.startsWith("we")) L.t("ছুটির দিন") else L.t("কর্মদিবস")
        val part = when (b.substringAfter('-')) { "m" -> L.t("সকাল"); "a" -> L.t("দুপুর/বিকেল"); "e" -> L.t("সন্ধ্যা"); else -> L.t("রাত") }
        return "$day, $part"
    }

    // ---------- শেখা ----------
    private fun keysFor(title: String, channel: String): List<Pair<String, Double>> = buildList {
        if (channel.isNotBlank()) add("ch:${channel.lowercase()}" to 2.0)
        keywords(title).forEach { add("kw:$it" to 1.0) }
    }

    suspend fun boost(dao: AppDao, title: String, channel: String, weight: Double) =
        learn(dao, keysFor(title, channel), weight)

    /** সার্চ কোয়েরি থেকে শেখা */
    suspend fun boostQuery(dao: AppDao, query: String, weight: Double) =
        learn(dao, keywords(query).map { "kw:$it" to 1.0 }, weight)

    /** প্রতিটি কী-তে গ্লোবাল স্কোর + বর্তমান সময়-বাকেটের স্কোর (০.৭ গুণ) জমা হয় */
    private suspend fun learn(dao: AppDao, keys: List<Pair<String, Double>>, weight: Double) {
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        val b = bucket()
        val existing = dao.interests().associateBy { it.topic }
        val acc = HashMap<String, Double>()
        for ((k, w) in keys) {
            acc[k] = (acc[k] ?: 0.0) + w * weight
            val tk = "t:$b:$k"
            acc[tk] = (acc[tk] ?: 0.0) + w * weight * 0.7
        }
        dao.upsertInterests(acc.map { (k, add) ->
            InterestEntity(k, (existing[k]?.let { decayed(it, now) } ?: 0.0) + add, now)
        })
        dao.pruneInterests()
    }

    /** ইম্পোর্ট করা ইতিহাস থেকে প্রোফাইল তৈরি (সময়-বাকেট ছাড়া) */
    suspend fun seed(dao: AppDao, items: List<Pair<String, String>>, weight: Double) {
        val now = System.currentTimeMillis()
        val existing = dao.interests().associateBy { it.topic }
        val acc = HashMap<String, Double>()
        items.forEach { (title, channel) ->
            keysFor(title, channel).forEach { (k, w) -> acc[k] = (acc[k] ?: 0.0) + w * weight }
        }
        dao.upsertInterests(acc.map { (k, add) ->
            InterestEntity(k, (existing[k]?.let { decayed(it, now) } ?: 0.0) + add, now)
        })
        dao.pruneInterests()
    }

    // ---------- ড্যাশবোর্ডের জন্য ----------
    fun top(interests: List<InterestEntity>, n: Int): List<Pair<String, Double>> {
        val now = System.currentTimeMillis()
        return interests.filter { !it.topic.startsWith("t:") }
            .map { it.topic to decayed(it, now) }.sortedByDescending { it.second }.take(n)
    }

    /** এখনকার সময়-বাকেটে সাধারণত যা দেখেন */
    fun topForBucket(interests: List<InterestEntity>, n: Int): List<Pair<String, Double>> {
        val now = System.currentTimeMillis()
        val prefix = "t:${bucket()}:"
        return interests.filter { it.topic.startsWith(prefix) }
            .map { it.topic.removePrefix(prefix) to decayed(it, now) }
            .filter { it.second > 0.3 }.sortedByDescending { it.second }.take(n)
    }

    // ---------- বৈচিত্র্য ----------
    /** ফিডে [count] বার দেখানো ভিডিওর জরিমানা (সর্বোচ্চ ২.৫) */
    fun impressionPenalty(count: Int): Double = minOf(2.5, 0.9 * count.coerceAtLeast(0))

    /** "3 hours ago" / "২ দিন আগে" ধরনের লেখা থেকে নতুনত্বের বোনাস; বোঝা না গেলে ০ */
    fun recencyBonus(uploaded: String?): Double {
        val s = uploaded?.lowercase() ?: return 0.0
        val n = Regex("\\d+").find(s)?.value?.toIntOrNull() ?: 1
        return when {
            "year" in s || "বছর" in s -> -0.3
            "month" in s || "মাস" in s -> 0.0
            "week" in s || "সপ্তাহ" in s -> 0.1
            "day" in s || "দিন" in s -> if (n <= 3) 0.4 else 0.25
            "hour" in s || "minute" in s || "ঘণ্টা" in s || "মিনিট" in s -> 0.5
            else -> 0.0
        }
    }

    /**
     * ওজন ধরে র‍্যান্ডম বাছাই (পুনরাবৃত্তি ছাড়া): বেশি স্কোরের বিষয় বেশি আসে, কিন্তু প্রতিবার একই ৩টি নয়।
     */
    fun pickTopics(weights: List<Pair<String, Double>>, n: Int, rnd: Random = Random.Default): List<String> {
        val pool = weights.filter { it.second > 0.0 }.toMutableList()
        val out = ArrayList<String>()
        while (out.size < n && pool.isNotEmpty()) {
            var r = rnd.nextDouble() * pool.sumOf { it.second }
            var idx = pool.indexOfFirst { r -= it.second; r <= 0.0 }
            if (idx < 0) idx = pool.lastIndex
            out += pool.removeAt(idx).first
        }
        return out
    }

    // ---------- র‍্যাংকিং ----------
    private data class Scored(val v: VideoItem, val score: Double, val reason: String)

    fun rank(
        videos: List<VideoItem>, interests: List<InterestEntity>, seen: Set<String>,
        recent: List<HistoryEntity>, subscribed: Set<String>,
        related: Set<String> = emptySet(),
        shown: Map<String, Int> = emptyMap()
    ): List<VideoItem> {
        val now = System.currentTimeMillis()
        val b = bucket()
        val map = interests.associate { it.topic to decayed(it, now) }

        // বিরক্তি: সাম্প্রতিক ১০টির মধ্যে ৪+ বার আসা বিষয়/চ্যানেলের প্রভাব কমে
        val recentCount = HashMap<String, Int>()
        recent.take(10).forEach { h ->
            keysFor(h.title, h.channel).forEach { (k, _) -> recentCount[k] = (recentCount[k] ?: 0) + 1 }
        }
        val bored = recentCount.filter { it.value >= 4 }.keys

        fun contribution(k: String): Double {
            var s = (map[k] ?: 0.0) + 0.6 * (map["t:$b:$k"] ?: 0.0)
            if (k in bored) s *= 0.4
            return s
        }

        fun score(v: VideoItem): Scored {
            var bestVal = 0.0
            var bestLabel = ""
            val chLower = v.channel.lowercase()

            val chScore = contribution("ch:$chLower")
            var total = chScore
            if (chScore > bestVal) { bestVal = chScore; bestLabel = L.f("আপনি \"{0}\" চ্যানেল প্রায়ই দেখেন", v.channel) }

            val kws = keywords(v.title)
            var kwSum = 0.0
            kws.forEach { w ->
                val c = contribution("kw:$w")
                kwSum += c
                if (c > bestVal) {
                    bestVal = c
                    val timeBoost = 0.6 * (map["t:$b:kw:$w"] ?: 0.0)
                    bestLabel = if (timeBoost > c * 0.45) L.f("এই সময়ে ({0}) আপনার পছন্দ: {1}", bucketLabel(b), w)
                    else L.f("আপনার আগ্রহের বিষয়: {0}", w)
                }
            }
            total += kwSum / sqrt(1.0 + kws.size)

            if (chLower in subscribed) {
                total += 1.0
                if (bestVal < 1.0) { bestVal = 1.0; bestLabel = L.t("আপনার সাবস্ক্রাইব করা চ্যানেল") }
            }

            // সাম্প্রতিক দেখা ভিডিওর "related" থেকে: স্বাভাবিকভাবে বিষয় বদলের সুযোগ
            if (v.url in related) {
                total += 0.8
                if (bestVal < 0.8) { bestVal = 0.8; bestLabel = L.t("আপনার সাম্প্রতিক দেখার সাথে সম্পর্কিত") }
            }

            // মান ফিল্টার (ফিডের তথ্যে লাইক নেই, তাই ভিউ ও শিরোনামের ধরন দেখা হয়)
            if (v.views in 0..199 && chLower !in subscribed) total -= 1.0
            val letters = v.title.count { it.isLetter() }
            if (letters > 10 && v.title.count { it.isUpperCase() } > letters * 0.6) total -= 0.5
            if (v.title.count { it == '!' } >= 3) total -= 0.4

            // আগে ফিডে দেখানো হয়েছে কিন্তু খোলা হয়নি: প্রতিবার একটু করে নিচে নামে, যাতে রিফ্রেশে নতুন ভিডিও ওঠে
            total -= impressionPenalty(shown[v.url] ?: 0)
            total += recencyBonus(v.uploaded)
            total += Random.nextDouble() * 0.9     // এলোমেলো = নতুন কিছুর সুযোগ
            val reason = when {
                bestLabel.isNotEmpty() && bestVal > 0.3 -> bestLabel
                total < 0.3 -> L.t("এক্সপ্লোর: আপনার জন্য নতুন কিছু")
                else -> L.t("এখন জনপ্রিয়")
            }
            return Scored(v, total, reason)
        }

        val scored = videos.filter { it.url !in seen }.map { score(it) }.sortedByDescending { it.score }

        // ডাইভার্সিটি: একই চ্যানেল প্রথম দফায় ২টির বেশি নয়
        val count = HashMap<String, Int>()
        val first = ArrayList<VideoItem>()
        val rest = ArrayList<VideoItem>()
        for (s in scored) {
            val item = s.v.copy(reason = s.reason)
            val c = count[item.channel] ?: 0
            if (c < 2) { first.add(item); count[item.channel] = c + 1 } else rest.add(item)
        }
        return first + rest
    }

    // ---------- এনগেজমেন্ট (লাইক/ডিসলাইক) ফিল্টার ----------
    /** লাইক অনুপাত; ভোট কম হলে null (সিদ্ধান্ত নেওয়ার মতো তথ্য নেই) */
    fun likeRatio(likes: Long, dislikes: Long, minVotes: Long = 50): Double? {
        val total = likes + dislikes
        return if (total < minVotes) null else likes.toDouble() / total
    }

    /**
     * stats: ভিডিও url → (likes, dislikes)। অনুপাত [minRatio]-এর নিচে হলে ভিডিও তালিকার শেষে যায়
     * (বাদ যায় না); বাকিদের ক্রম অপরিবর্তিত থাকে।
     */
    fun demoteLowQuality(
        videos: List<VideoItem>, stats: Map<String, Pair<Long, Long>>, minRatio: Double = 0.6
    ): List<VideoItem> {
        val (bad, good) = videos.partition { v ->
            val st = stats[v.url] ?: return@partition false
            val r = likeRatio(st.first, st.second) ?: return@partition false
            r < minRatio
        }
        return good + bad
    }
}
