package com.tubex.app

import androidx.core.text.HtmlCompat

object TextUtil {
    private val tagRegex = Regex("<[a-zA-Z/!][^>]*>")
    private val entityRegex = Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);")
    private val brRegex = Regex("(?i)<br\\s*/?>")
    private val blockEnd = Regex("(?i)</(p|div|li|ul|ol|h[1-6])\\s*>")

    /**
     * বিবরণ ও মন্তব্যের HTML (<a>, <b>, <br> ইত্যাদি) সরিয়ে সাধারণ লেখায় আনে।
     * <br> ও ব্লক ট্যাগ নতুন লাইন হয়, এনটিটি (&amp; &#39;) ডিকোড হয়। সাধারণ লেখার নতুন লাইন ঠিক থাকে।
     */
    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw
        if (tagRegex.containsMatchIn(s) || entityRegex.containsMatchIn(s)) {
            val hasBr = brRegex.containsMatchIn(s)
            if (!hasBr) s = s.replace("\n", "<br>")
            s = blockEnd.replace(s, "<br>")
            s = HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
        return s.replace("\uFFFC", "").replace("\r", "")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}

/** "৩ দিন আগে" / "3 days ago" */
fun relativeTime(epochMs: Long): String {
    val s = ((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> L.t("এইমাত্র")
        s < 3_600 -> L.f("{0} মিনিট আগে", s / 60)
        s < 86_400 -> L.f("{0} ঘণ্টা আগে", s / 3_600)
        s < 86_400L * 30 -> L.f("{0} দিন আগে", s / 86_400)
        s < 86_400L * 365 -> L.f("{0} মাস আগে", s / (86_400L * 30))
        else -> L.f("{0} বছর আগে", s / (86_400L * 365))
    }
}
