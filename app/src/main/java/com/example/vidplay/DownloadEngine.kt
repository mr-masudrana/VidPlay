package com.example.vidplay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * নিজস্ব ডাউনলোডার: কিউ (একটার পর একটা), থামানো/চালু (HTTP Range দিয়ে রিজিউম),
 * ফ্রেশ স্ট্রিম URL (পুরনো URL মেয়াদোত্তীর্ণ হলেও রিজিউম চলে), ভিডিও+অডিও মার্জ।
 */
object DownloadEngine {
    private const val CHUNK = 8L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private var loop: Job? = null
    private var current: Job? = null
    @Volatile private var currentId = -1L

    /** সার্ভিস চালু রাখার সংকেত */
    val active = MutableStateFlow(false)
    /** নোটিফিকেশনের জন্য: (শিরোনাম, শতাংশ) */
    val status = MutableStateFlow<Pair<String, Int>?>(null)
    val isActive: Boolean get() = active.value

    fun kick(ctx: Context) {
        val app = ctx.applicationContext
        active.value = true
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        if (loop?.isActive == true) return
        loop = scope.launch {
            val dao = AppDb.get(app).dao()
            while (true) {
                val next = dao.nextQueued() ?: break
                currentId = next.id
                val job = launch { runOne(dao, next) }
                current = job
                job.join()
                currentId = -1
            }
            status.value = null
            active.value = false
            if (dao.nextQueued() != null) kick(app)   // শেষ মুহূর্তে যোগ হওয়া আইটেম
        }
    }

    suspend fun pause(dao: AppDao, id: Long) {
        dao.pauseOne(id)
        if (currentId == id) current?.cancel()
    }

    suspend fun pauseAll(dao: AppDao) {
        dao.pauseAll()
        current?.cancel()
    }

    suspend fun resume(ctx: Context, dao: AppDao, id: Long) { dao.resumeOne(id); kick(ctx) }
    suspend fun resumeAll(ctx: Context, dao: AppDao) { dao.resumeAll(); kick(ctx) }

    suspend fun remove(dao: AppDao, d: DownloadEntity) {
        if (currentId == d.id) current?.cancelAndJoin()
        dao.removeDownload(d)
        listOf("", ".part", ".v.part", ".a.part", ".tmp").forEach { File(d.path + it).delete() }
    }

    // ---------- একটি আইটেম ----------
    private suspend fun runOne(dao: AppDao, d: DownloadEntity) {
        dao.setState(d.id, 1)
        status.value = d.title to 0
        var lastWrite = 0L

        suspend fun report(frac: Float) {
            val p = (frac * 1000).toInt().coerceIn(0, 1000)
            status.value = d.title to p / 10
            val now = System.currentTimeMillis()
            if (now - lastWrite > 1000) { lastWrite = now; dao.setProgress(d.id, p) }
        }
        fun frac(h: Long, t: Long) = if (t > 0) h.toFloat() / t else 0f

        try {
            // ফ্রেশ URL: আগের স্ট্রিম URL মেয়াদোত্তীর্ণ হতে পারে
            val info = StreamInfo.getInfo(ServiceList.YouTube, d.videoUrl)
            val final = File(d.path)
            final.parentFile?.mkdirs()

            if (d.audioOnly) {
                val a = info.audioStreams.firstOrNull { it.itag == d.audioItag }
                    ?: info.audioStreams.maxByOrNull { it.averageBitrate } ?: error(L.t("অডিও স্ট্রিম পাওয়া যায়নি"))
                val part = File(d.path + ".part")
                fetch(a.content, part) { h, t -> report(frac(h, t)) }
                finish(part, final)
            } else if (d.audioItag != 0) {
                val v = (info.videoOnlyStreams + info.videoStreams).firstOrNull { it.itag == d.videoItag }
                    ?: info.videoOnlyStreams.firstOrNull { it.height == d.height } ?: error(L.t("ভিডিও স্ট্রিম পাওয়া যায়নি"))
                val a = info.audioStreams.firstOrNull { it.itag == d.audioItag }
                    ?: info.audioStreams.maxByOrNull { it.averageBitrate } ?: error(L.t("অডিও স্ট্রিম পাওয়া যায়নি"))
                val vp = File(d.path + ".v.part")
                val ap = File(d.path + ".a.part")
                fetch(v.content, vp) { h, t -> report(0.92f * frac(h, t)) }
                fetch(a.content, ap) { h, t -> report(0.92f + 0.08f * frac(h, t)) }

                dao.setState(d.id, 5)            // মার্জ হচ্ছে
                status.value = d.title to 100
                val tmp = File(d.path + ".tmp")
                tmp.delete()
                Muxer.mux(vp, ap, tmp, d.path.endsWith(".webm"))
                finish(tmp, final)
                vp.delete(); ap.delete()
            } else {
                val v = info.videoStreams.firstOrNull { it.itag == d.videoItag }
                    ?: info.videoStreams.firstOrNull { it.height == d.height } ?: error(L.t("ভিডিও স্ট্রিম পাওয়া যায়নি"))
                val part = File(d.path + ".part")
                fetch(v.content, part) { h, t -> report(frac(h, t)) }
                finish(part, final)
            }
            dao.markDone(d.id)
        } catch (e: CancellationException) {
            throw e            // থামানো/মোছা: অবস্থা বাইরে থেকেই ঠিক করা হয়েছে
        } catch (e: Exception) {
            dao.setState(d.id, 4)
        }
    }

    private fun finish(from: File, to: File) {
        to.delete()
        if (!from.renameTo(to)) { from.copyTo(to, overwrite = true); from.delete() }
    }

    /** ৮ MB-এর খণ্ডে ডাউনলোড; ফাইলে যা আছে তার পর থেকে চালু হয় (রিজিউম)। */
    private suspend fun fetch(url: String, file: File, onProgress: suspend (Long, Long) -> Unit) {
        var have = if (file.exists()) file.length() else 0L
        var total = -1L
        while (total < 0 || have < total) {
            currentCoroutineContext().ensureActive()
            val before = have
            val req = Request.Builder().url(url).header("User-Agent", MyDownloader.USER_AGENT)
                .header("Range", "bytes=$have-${have + CHUNK - 1}").build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    416 -> total = have
                    200, 206 -> {
                        val body = resp.body ?: throw IOException(L.t("খালি উত্তর"))
                        if (resp.code == 200) {          // সার্ভার রেঞ্জ মানেনি: গোড়া থেকে
                            have = 0
                            total = body.contentLength()
                        } else {
                            total = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                                ?: throw IOException(L.t("ফাইলের সাইজ জানা যায়নি"))
                        }
                        FileOutputStream(file, resp.code == 206).use { out ->
                            val src = body.byteStream()
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = src.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                have += n
                                onProgress(have, total)
                            }
                        }
                        if (resp.code == 200 && total < 0) total = have
                    }
                    else -> throw IOException("HTTP ${resp.code}")
                }
                Unit
            }
            if (have == before && (total < 0 || have < total)) throw IOException(L.t("ডাউনলোড আটকে গেছে"))
        }
    }
}
