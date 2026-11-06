package com.tubex.app.data.recommend


object Blocks {
    /** entries: "ch:<চ্যানেল>", "kw:<কিওয়ার্ড>", "vid:<url>" */
    fun allowed(entries: Set<String>, title: String, channel: String, url: String): Boolean {
        if (entries.isEmpty()) return true
        if ("vid:$url" in entries || "ch:${channel.lowercase()}" in entries) return false
        val t = title.lowercase()
        return entries.none { it.startsWith("kw:") && t.contains(it.removePrefix("kw:")) }
    }
}
