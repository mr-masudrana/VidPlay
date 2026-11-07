package com.tubex.app.data.potoken

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * লুকানো WebView-এ YouTube-এর BotGuard চালিয়ে poToken বানায়।
 * ধাপ: Create (চ্যালেঞ্জ) → WebView-এ BotGuard চালানো → GenerateIT (integrity token) → মিন্ট।
 * একবার তৈরি হলে একই integrity token দিয়ে বহু poToken বানানো যায় (মেয়াদ শেষ না হওয়া পর্যন্ত)।
 *
 * NewPipe-এর PoTokenWebView.kt (GPL-3.0) থেকে রূপান্তরিত; RxJava-র বদলে CompletableFuture।
 */
class PoTokenWebView private constructor(context: Context) : Closeable {

    private val main = Handler(Looper.getMainLooper())
    private val webView = WebView(context)
    private val initFuture = CompletableFuture<PoTokenWebView>()
    private val emitters = mutableListOf<Pair<String, CompletableFuture<String>>>()

    @Volatile private var expiresAtMs = 0L
    @Volatile private var closed = false

    fun isExpired(): Boolean = closed || System.currentTimeMillis() > expiresAtMs

    // ---------- ইনিশিয়ালাইজেশন ----------
    private fun setup(context: Context) {
        val s = webView.settings
        @Suppress("SetJavaScriptEnabled")
        s.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= 26) s.safeBrowsingEnabled = false
        s.userAgentString = USER_AGENT
        s.blockNetworkLoads = true   // WebView-এর ইন্টারনেট লাগে না; নেটওয়ার্ক Kotlin দিক থেকে হয়

        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Uncaught")) {
                    // সাধারণত খুব পুরনো WebView (JS সিনট্যাক্স সাপোর্ট করে না)
                    val e = BadWebViewException("\"${m.message()}\", source: ${m.sourceId()} (${m.lineNumber()})")
                    Log.e(TAG, "WebView ভাঙা: ${e.message}")
                    failInit(e)
                    popAll().forEach { (_, f) -> f.completeExceptionally(e) }
                }
                return super.onConsoleMessage(m)
            }
        }

        val html = try {
            context.assets.open("po_token.html").bufferedReader().use { it.readText() }
        } catch (t: Throwable) { failInit(t); return }

        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            // পেজ লোড শেষে downloadAndRunBotguard() ডাকা হয়
            html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"),
            "text/html", "utf-8", null
        )
    }

    /** JS থেকে ডাকা হয় (JavaBridge থ্রেডে) */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        post("https://www.youtube.com/api/jnn/v1/Create", "[ \"$REQUEST_KEY\" ]") { body ->
            val data = parseChallengeData(body)
            main.post {
                if (closed) return@post
                webView.evaluateJavascript(
                    """try {
                        data = $data
                        runBotGuard(data).then(function (result) {
                            this.webPoSignalOutput = result.webPoSignalOutput
                            $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                        }, function (error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                        })
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    }""", null
                )
            }
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        Log.e(TAG, "JS ইনিশিয়ালাইজেশন ত্রুটি: $error")
        failInit(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        post("https://www.youtube.com/api/jnn/v1/GenerateIT", "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]") { body ->
            val (integrityToken, ttlSec) = parseIntegrityTokenData(body)
            // ১০ মিনিট মার্জিন রেখে মেয়াদ ধরা হয়
            expiresAtMs = System.currentTimeMillis() + (ttlSec - 600) * 1000
            main.post {
                if (closed) return@post
                webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                    initFuture.complete(this)
                }
            }
        }
    }

    // ---------- poToken তৈরি ----------
    /** ব্লকিং: ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকুন (মেইন থ্রেড থেকে নয়) */
    fun generatePoToken(identifier: String): String {
        val future = CompletableFuture<String>()
        main.post {
            if (closed) { future.completeExceptionally(PoTokenException("closed")); return@post }
            synchronized(emitters) { emitters.add(identifier to future) }
            webView.evaluateJavascript(
                """try {
                    identifier = ${JSONObject.quote(identifier)}
                    u8Identifier = ${stringToU8(identifier)}
                    poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                    poTokenU8String = ""
                    for (i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ","
                        poTokenU8String += poTokenU8[i]
                    }
                    $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                } catch (error) {
                    $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                }""", null
            )
        }
        return await(future, TOKEN_TIMEOUT_SEC)
    }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        Log.e(TAG, "poToken ত্রুটি: $error")
        pop(identifier)?.completeExceptionally(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val token = try { u8ToBase64(poTokenU8) } catch (t: Throwable) {
            pop(identifier)?.completeExceptionally(t); return
        }
        pop(identifier)?.complete(token)
    }

    // ---------- সহায়ক ----------
    private fun pop(identifier: String): CompletableFuture<String>? = synchronized(emitters) {
        emitters.indexOfFirst { it.first == identifier }.takeIf { it >= 0 }?.let { emitters.removeAt(it).second }
    }

    private fun popAll(): List<Pair<String, CompletableFuture<String>>> = synchronized(emitters) {
        emitters.toList().also { emitters.clear() }
    }

    /** নেটওয়ার্ক অনুরোধ ব্যাকগ্রাউন্ডে; ব্যর্থ হলে ইনিশিয়ালাইজেশন বাতিল */
    private fun post(url: String, data: String, onBody: (String) -> Unit) {
        io.execute {
            try {
                val res = NewPipe.getDownloader().post(
                    url,
                    mapOf(
                        "User-Agent" to listOf(USER_AGENT),
                        "Accept" to listOf("application/json"),
                        "Content-Type" to listOf("application/json+protobuf"),
                        "x-goog-api-key" to listOf(GOOGLE_API_KEY),
                        "x-user-agent" to listOf("grpc-web-javascript/0.1"),
                    ),
                    data.toByteArray()
                )
                if (res.responseCode() != 200) throw PoTokenException("Invalid response code: ${res.responseCode()}")
                onBody(res.responseBody())
            } catch (t: Throwable) {
                failInit(t)
            }
        }
    }

    private fun failInit(error: Throwable) {
        if (initFuture.completeExceptionally(error)) main.post { close() }
    }

    @Suppress("DEPRECATION")
    override fun close() {
        if (closed) return
        closed = true
        popAll().forEach { (_, f) -> f.completeExceptionally(PoTokenException("closed")) }
        runCatching {
            webView.clearHistory()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    companion object {
        private const val TAG = "PoTokenWebView"
        // BotGuard-এর সার্বজনীন API key ও request key (YouTube ওয়েবের অনুরোধ থেকে নেওয়া)
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"
        private const val INIT_TIMEOUT_SEC = 40L
        private const val TOKEN_TIMEOUT_SEC = 20L

        private val io = Executors.newCachedThreadPool()

        /** ব্লকিং: নতুন জেনারেটর তৈরি (BotGuard চালিয়ে integrity token সহ)। ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকুন। */
        fun create(context: Context): PoTokenWebView {
            val holder = CompletableFuture<PoTokenWebView>()
            val posted = Handler(Looper.getMainLooper()).post {
                try {
                    val wv = PoTokenWebView(context.applicationContext)
                    wv.initFuture.whenComplete { r, e -> if (e != null) holder.completeExceptionally(e) else holder.complete(r) }
                    wv.setup(context.applicationContext)
                } catch (t: Throwable) { holder.completeExceptionally(t) }   // যেমন WebView-ই নেই
            }
            if (!posted) throw PoTokenException("Could not run on main thread")
            return await(holder, INIT_TIMEOUT_SEC)
        }

        private fun <T> await(f: CompletableFuture<T>, sec: Long): T = try {
            f.get(sec, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            throw PoTokenException("Timeout")
        }
    }
}
