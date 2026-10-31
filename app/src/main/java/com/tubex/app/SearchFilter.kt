package com.tubex.app

/** সার্চ রেজাল্টের ফিল্টার: সময়, দৈর্ঘ্য ও সর্ট (ডিভাইসেই প্রয়োগ হয়) */
object SearchFilter {
    val dates = listOf("যেকোনো সময়", "আজ", "এই সপ্তাহ", "এই মাস", "এই বছর")
    val durations = listOf("যেকোনো দৈর্ঘ্য", "ছোট (৪ মিনিটের কম)", "মাঝারি (৪–২০ মিনিট)", "লম্বা (২০ মিনিটের বেশি)")
    val sorts = listOf("প্রাসঙ্গিকতা", "নতুন আগে", "সবচেয়ে বেশি দেখা")

    private val bnDigits = "০১২৩৪৫৬৭৮৯"
    private val unitRe = Regex(
        "(\\d+)\\s*(second|minute|hour|day|week|month|year|সেকেন্ড|মিনিট|ঘণ্টা|ঘন্টা|দিন|সপ্তাহ|মাস|বছর)",
        RegexOption.IGNORE_CASE
    )

    /** "৩ দিন আগে" / "3 days ago" → ঘণ্টায় বয়স; বোঝা না গেলে null */
    fun ageHours(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val norm = buildString { text.forEach { c -> val i = bnDigits.indexOf(c); append(if (i >= 0) ('0' + i) else c) } }
        val m = unitRe.find(norm) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "second", "সেকেন্ড", "minute", "মিনিট" -> 0L
            "hour", "ঘণ্টা", "ঘন্টা" -> n
            "day", "দিন" -> n * 24
            "week", "সপ্তাহ" -> n * 24 * 7
            "month", "মাস" -> n * 24 * 30
            else -> n * 24 * 365
        }
    }

    fun apply(list: List<VideoItem>, date: Int, duration: Int, sort: Int): List<VideoItem> {
        var r = list
        if (date > 0) {
            val max = when (date) { 1 -> 24L; 2 -> 24L * 7; 3 -> 24L * 30; else -> 24L * 365 }
            r = r.filter { v -> ageHours(v.uploaded)?.let { it <= max } ?: false }
        }
        if (duration > 0) r = r.filter { v ->
            val s = v.durationSec
            s > 0 && when (duration) { 1 -> s < 240; 2 -> s in 240..1200; else -> s > 1200 }
        }
        return when (sort) {
            1 -> r.sortedBy { ageHours(it.uploaded) ?: Long.MAX_VALUE }
            2 -> r.sortedByDescending { it.views }
            else -> r
        }
    }
}
