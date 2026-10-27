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
}
