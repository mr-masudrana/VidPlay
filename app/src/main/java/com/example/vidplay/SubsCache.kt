package com.example.vidplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** সাবস্ক্রিপশন ফিডের ক্যাশ: অ্যাপ খুললেই আগের ফিড সাথে সাথে দেখায়, নেটওয়ার্ক পরে আপডেট করে। */
object SubsCache {
    data class Snapshot(val time: Long, val key: Int, val items: List<VideoItem>)

    private fun file(ctx: Context) = File(ctx.filesDir, "subs_feed.json")

    fun load(ctx: Context): Snapshot? = try {
        val f = file(ctx)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val a = o.getJSONArray("items")
            Snapshot(o.getLong("time"), o.getInt("key"), (0 until a.length()).map { i ->
                val v = a.getJSONObject(i)
                VideoItem(
                    title = v.getString("title"), url = v.getString("url"), channel = v.optString("channel"),
                    channelUrl = v.optString("channelUrl").takeIf { it.isNotEmpty() },
                    views = v.optLong("views", -1), durationSec = v.optLong("durationSec"),
                    thumb = v.optString("thumb").takeIf { it.isNotEmpty() },
                    uploaded = v.optString("uploaded").takeIf { it.isNotEmpty() },
                    channelAvatar = v.optString("channelAvatar").takeIf { it.isNotEmpty() }
                )
            })
        }
    } catch (e: Exception) { null }

    fun save(ctx: Context, key: Int, items: List<VideoItem>) {
        runCatching {
            val a = JSONArray()
            items.forEach {
                a.put(
                    JSONObject().put("title", it.title).put("url", it.url).put("channel", it.channel)
                        .put("channelUrl", it.channelUrl).put("views", it.views).put("durationSec", it.durationSec)
                        .put("thumb", it.thumb).put("uploaded", it.uploaded).put("channelAvatar", it.channelAvatar)
                )
            }
            file(ctx).writeText(JSONObject().put("time", System.currentTimeMillis()).put("key", key).put("items", a).toString())
        }
    }
}
