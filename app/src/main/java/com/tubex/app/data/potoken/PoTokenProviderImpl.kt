package com.tubex.app.data.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

/**
 * Extractor-কে WEB ক্লায়েন্টের poToken দেয়, যাতে YouTube "Sign in to confirm you're not a bot" / 403 না দেয়।
 *
 * নিরাপদ ডিজাইন: কোনো ধাপে ব্যর্থ হলে `null` ফেরত যায় — তখন Extractor আগের মতোই (poToken ছাড়া)
 * অন্য ক্লায়েন্ট দিয়ে চেষ্টা করে। অর্থাৎ এই ফিচার কখনো আগের চেয়ে খারাপ করবে না।
 *
 * NewPipe-এর PoTokenProviderImpl.kt (GPL-3.0) থেকে রূপান্তরিত।
 */
object PoTokenProviderImpl : PoTokenProvider {
    private const val TAG = "PoTokenProvider"

    @Volatile private var appContext: Context? = null
    /** সেটিংস থেকে বন্ধ করা যায় */
    @Volatile var enabled: Boolean = true
    @Volatile private var webViewBad = false   // এই ডিভাইসের WebView অচল হলে আর চেষ্টা নয়

    private object Lock
    private var visitorData: String? = null
    private var streamingPot: String? = null
    private var generator: PoTokenWebView? = null

    fun init(context: Context) { appContext = context.applicationContext }

    /** অ্যাপ চালুর পর ব্যাকগ্রাউন্ডে আগে থেকে তৈরি রাখে, যাতে প্রথম ভিডিও দ্রুত খোলে */
    fun prewarm() {
        if (!enabled) return
        Thread({ runCatching { getWebClientPoToken("dQw4w9WgXcQ") } }, "potoken-prewarm").start()
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        val ctx = appContext
        if (!enabled || ctx == null || webViewBad || !supportsWebView()) return null
        return try {
            obtain(ctx, videoId, forceRecreate = false)
        } catch (e: BadWebViewException) {
            Log.e(TAG, "WebView অচল, poToken বন্ধ", e); webViewBad = true; null
        } catch (t: Throwable) {
            Log.e(TAG, "poToken পাওয়া যায়নি; poToken ছাড়াই চলবে", t); null
        }
    }

    private fun obtain(ctx: Context, videoId: String, forceRecreate: Boolean): PoTokenResult {
        val (gen, vd, pot, recreated) = synchronized(Lock) {
            val recreate = generator == null || forceRecreate || generator!!.isExpired()
            if (recreate) {
                val old = generator
                generator = null
                if (old != null) Handler(Looper.getMainLooper()).post { old.close() }

                val newVisitor = VisitorData.fetch() ?: throw PoTokenException("visitorData পাওয়া যায়নি")
                val g = PoTokenWebView.create(ctx)
                // স্ট্রিমিং poToken অবশ্যই অন্য যেকোনো টোকেনের আগে, ঠিক একবার তৈরি করতে হয়
                val sp = try { g.generatePoToken(newVisitor) } catch (t: Throwable) {
                    Handler(Looper.getMainLooper()).post { g.close() }; throw t
                }
                generator = g; visitorData = newVisitor; streamingPot = sp
            }
            State(generator!!, visitorData!!, streamingPot!!, recreate)
        }

        val playerPot = try {
            // লক ছাড়া: একই জেনারেটর সমান্তরালে একাধিক টোকেন বানাতে পারে
            gen.generatePoToken(videoId)
        } catch (t: Throwable) {
            if (recreated) throw t
            // WebView-এর কনটেন্ট হারিয়ে যেতে পারে (যেমন অ্যাপ ব্যাকগ্রাউন্ডে গেলে) — নতুন করে চেষ্টা
            Log.e(TAG, "poToken ব্যর্থ, নতুন করে চেষ্টা", t)
            return obtain(ctx, videoId, forceRecreate = true)
        }
        return PoTokenResult(vd, playerPot, pot)
    }

    private data class State(val gen: PoTokenWebView, val visitor: String, val pot: String, val recreated: Boolean)

    // এই ক্লায়েন্টগুলোর জন্য আমরা poToken দিই না (null = দেব না)
    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null
    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null
    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

    private fun supportsWebView(): Boolean = try {
        android.webkit.CookieManager.getInstance(); true
    } catch (t: Throwable) { false }

    /** ডিবাগ/পরীক্ষার জন্য: বর্তমান জেনারেটর ফেলে দেয় */
    fun reset() {
        val old = synchronized(Lock) {
            val g = generator
            generator = null; visitorData = null; streamingPot = null
            g
        }
        if (old != null) Handler(Looper.getMainLooper()).post { old.close() }
    }
}

/** YouTube হোমপেজ থেকে anonymous visitorData নেয় (poToken এই আইডিতে বাঁধা থাকে) */
internal object VisitorData {
    private val regex = Regex("\"visitorData\":\"([\\w\\-%]+?)\"")

    fun fetch(): String? = try {
        val res = NewPipe.getDownloader().get(
            "https://www.youtube.com/",
            mapOf(
                "User-Agent" to listOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"),
                "Accept-Language" to listOf("en-US,en;q=0.9"),
                "Cookie" to listOf("SOCS=CAI; CONSENT=YES+1")   // ইউরোপের কনসেন্ট পেজ এড়াতে
            )
        )
        regex.find(res.responseBody())?.groupValues?.get(1)
    } catch (t: Throwable) { null }
}
