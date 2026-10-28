package com.example.vidplay

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    fun get(url: String): String? = try {
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

data class Brand(val title: String?, val thumb: String?)

/** DeArrow: ক্লিকবেইট টাইটেল/থাম্বনেইলের কমিউনিটি সংস্করণ (ভিডিও আইডির হ্যাশের প্রথম ৪ অক্ষর পাঠানো হয়) */
object DeArrow {
    var titles by mutableStateOf(true)
    var thumbs by mutableStateOf(true)
    val cache = mutableStateMapOf<String, Brand>()
    private val gate = Semaphore(4)

    fun videoId(url: String): String? {
        val u = Uri.parse(url)
        u.getQueryParameter("v")?.let { return it }
        if (u.host == "youtu.be") return u.lastPathSegment
        val seg = u.pathSegments
        return if (seg.size >= 2 && seg[0] == "shorts") seg[1] else null
    }

    suspend fun ensure(id: String) {
        if (id in cache) return
        cache[id] = Brand(null, null)          // একই ভিডিও দু'বার না আনার জন্য
        val b = gate.withPermit { fetch(id) }
        if (b != null) cache[id] = b
    }

    private fun clean(t: String) = Regex("(^|\\s)>").replace(t) { it.groupValues[1] }.trim()

    private suspend fun fetch(id: String): Brand? = withContext(Dispatchers.IO) {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)
        val body = Extras.get("https://sponsor.ajay.app/api/branding/$hash") ?: return@withContext null
        try {
            val root = JSONObject(body).optJSONObject(id) ?: return@withContext Brand(null, null)
            fun JSONArray.list(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
            val title = root.optJSONArray("titles")?.list()
                ?.firstOrNull { !it.optBoolean("original") && (it.optInt("votes") >= 0 || it.optBoolean("locked")) }
                ?.optString("title")?.takeIf { it.isNotBlank() }?.let { clean(it) }
            val ts = root.optJSONArray("thumbnails")?.list()
                ?.firstOrNull {
                    !it.optBoolean("original") && !it.isNull("timestamp") &&
                            (it.optInt("votes") >= 0 || it.optBoolean("locked"))
                }?.optDouble("timestamp")
            val thumb = ts?.let { "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$id&time=$it" }
            Brand(title, thumb)
        } catch (e: Exception) { null }
    }
}
