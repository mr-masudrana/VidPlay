package com.example.vidplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

data class Segment(val startMs: Long, val endMs: Long)

/** SponsorBlock ও Return YouTube Dislike */
object Extras {
    private val client = OkHttpClient()

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", MyDownloader.USER_AGENT).build()
        client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }

    /** প্রাইভেসির জন্য শুধু ভিডিও আইডির SHA-256 হ্যাশের প্রথম ৪ অক্ষর পাঠানো হয়। */
    suspend fun sponsorSegments(videoId: String): List<Segment> = withContext(Dispatchers.IO) {
        val hash = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)
        val cats = URLEncoder.encode("[\"sponsor\",\"selfpromo\",\"intro\",\"outro\",\"interaction\"]", "UTF-8")
        val actions = URLEncoder.encode("[\"skip\"]", "UTF-8")
        val body = get("https://sponsor.ajay.app/api/skipSegments/$hash?categories=$cats&actionTypes=$actions")
            ?: return@withContext emptyList<Segment>()
        try {
            val arr = JSONArray(body)
            var result = emptyList<Segment>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("videoID") != videoId) continue
                val segs = o.getJSONArray("segments")
                result = (0 until segs.length()).map { j ->
                    val s = segs.getJSONObject(j).getJSONArray("segment")
                    Segment((s.getDouble(0) * 1000).toLong(), (s.getDouble(1) * 1000).toLong())
                }
            }
            result
        } catch (e: Exception) { emptyList<Segment>() }
    }

    /** (likes, dislikes) */
    suspend fun ryd(videoId: String): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val body = get("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            ?: return@withContext null
        try {
            val o = JSONObject(body)
            Pair(o.getLong("likes"), o.getLong("dislikes"))
        } catch (e: Exception) { null }
    }
}
