package com.example.vidplay

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("vidplay", Context.MODE_PRIVATE)

    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }

    var sponsorBlock: Boolean
        get() = sp.getBoolean("sponsorblock", true)
        set(v) { sp.edit().putBoolean("sponsorblock", v).apply() }

    var ryd: Boolean
        get() = sp.getBoolean("ryd", true)
        set(v) { sp.edit().putBoolean("ryd", v).apply() }

    var captionLang: String
        get() = sp.getString("captionLang", "") ?: ""
        set(v) { sp.edit().putString("captionLang", v).apply() }

    var captionSize: Int
        get() = sp.getInt("captionSize", 1)
        set(v) { sp.edit().putInt("captionSize", v).apply() }

    var qualityWifi: Int
        get() = sp.getInt("qualityWifi", 0)
        set(v) { sp.edit().putInt("qualityWifi", v).apply() }

    var qualityMobile: Int
        get() = sp.getInt("qualityMobile", 360)
        set(v) { sp.edit().putInt("qualityMobile", v).apply() }

    var skipSilence: Boolean
        get() = sp.getBoolean("skipSilence", false)
        set(v) { sp.edit().putBoolean("skipSilence", v).apply() }

    var audioOnly: Boolean
        get() = sp.getBoolean("audioOnly", false)
        set(v) { sp.edit().putBoolean("audioOnly", v).apply() }

    var searchHistory: List<String>
        get() = (sp.getString("searchHistory", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) { sp.edit().putString("searchHistory", v.joinToString("\n")).apply() }

    // নতুন ভিডিওর নোটিফিকেশন
    var notifyEnabled: Boolean
        get() = sp.getBoolean("notifyEnabled", true)
        set(v) { sp.edit().putBoolean("notifyEnabled", v).apply() }

    var notifyHours: Int
        get() = sp.getInt("notifyHours", 6)
        set(v) { sp.edit().putInt("notifyHours", v).apply() }

    var seenVideos: Set<String>
        get() = sp.getStringSet("seenVideos", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("seenVideos", v).apply() }

    var baselined: Set<String>
        get() = sp.getStringSet("baselined", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("baselined", v).apply() }

    // DeArrow
    var dearrowTitles: Boolean
        get() = sp.getBoolean("dearrowTitles", true)
        set(v) { sp.edit().putBoolean("dearrowTitles", v).apply() }

    var dearrowThumbs: Boolean
        get() = sp.getBoolean("dearrowThumbs", true)
        set(v) { sp.edit().putBoolean("dearrowThumbs", v).apply() }

    /** সাবটাইটেলের রঙ: 0 সাদা, 1 হলুদ, 2 সবুজ, 3 নীল */
    var captionColor: Int
        get() = sp.getInt("captionColor", 0)
        set(v) { sp.edit().putInt("captionColor", v).apply() }

    /** ব্যাকগ্রাউন্ড: 0 নেই (আউটলাইন), 1 আধা-স্বচ্ছ, 2 কালো */
    var captionBg: Int
        get() = sp.getInt("captionBg", 1)
        set(v) { sp.edit().putInt("captionBg", v).apply() }

    /** ভাষা: 0 বাংলা, 1 English, 2 সিস্টেম */
    var lang: Int
        get() = sp.getInt("lang", 0)
        set(v) { sp.edit().putInt("lang", v).apply() }

    /** ব্যাক করলে ভাসমান (PiP-এর মতো) মিনি প্লেয়ার; বন্ধ থাকলে নিচের বারে */
    var floatMini: Boolean
        get() = sp.getBoolean("floatMini", true)
        set(v) { sp.edit().putBoolean("floatMini", v).apply() }
}
