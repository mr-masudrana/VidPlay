package com.tubex.app.data.potoken

import org.json.JSONObject
import java.util.concurrent.CompletableFuture

/*
 * PoTokenWebView-এর যে অংশ Android ছাড়াই চলে: অনুরোধের বডি, WebView-এ চালানো JS, মেয়াদ হিসাব ও
 * অপেক্ষমাণ টোকেনের তালিকা। আলাদা রাখা হয়েছে যাতে JVM ইউনিট টেস্টে যাচাই করা যায়।
 */

internal const val BOTGUARD_CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
internal const val BOTGUARD_GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"

/** BotGuard-এর request key (YouTube ওয়েবের অনুরোধ থেকে নেওয়া সার্বজনীন মান) */
internal const val BOTGUARD_REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

/** WebView-এ JS থেকে যে নামে এই অবজেক্ট ডাকা হয় (po_token.html-এর সাথে মিলতে হবে) */
internal const val JS_BRIDGE = "PoTokenWebView"

/** integrity token-এর মেয়াদ শেষের এত সেকেন্ড আগেই মেয়াদোত্তীর্ণ ধরা হয় */
internal const val INTEGRITY_MARGIN_SEC = 600L

internal fun createRequestBody(): String = "[ \"$BOTGUARD_REQUEST_KEY\" ]"

internal fun generateItRequestBody(botguardResponse: String): String =
    "[ \"$BOTGUARD_REQUEST_KEY\", \"$botguardResponse\" ]"

/** মেয়াদ শেষের সময় (epoch ms); ttl ১০ মিনিটের কম হলে সাথে সাথেই মেয়াদোত্তীর্ণ */
internal fun integrityExpiryMs(nowMs: Long, ttlSec: Long): Long = nowMs + (ttlSec - INTEGRITY_MARGIN_SEC) * 1000

/** পেজ লোড শেষে BotGuard চালু করার ডাক প্রথম </script>-এর আগে বসায় */
internal fun injectAutoStart(html: String): String =
    html.replaceFirst("</script>", "\n$JS_BRIDGE.downloadAndRunBotguard()</script>")

/** Create-এর উত্তর থেকে বানানো চ্যালেঞ্জ অবজেক্ট দিয়ে BotGuard চালানোর JS */
internal fun runBotguardScript(challengeDataJs: String): String =
    """try {
                        data = $challengeDataJs
                        runBotGuard(data).then(function (result) {
                            this.webPoSignalOutput = result.webPoSignalOutput
                            $JS_BRIDGE.onRunBotguardResult(result.botguardResponse)
                        }, function (error) {
                            $JS_BRIDGE.onJsInitializationError(error + "\n" + error.stack)
                        })
                    } catch (error) {
                        $JS_BRIDGE.onJsInitializationError(error + "\n" + error.stack)
                    }"""

/** একটি identifier-এর জন্য poToken মিন্ট করার JS; identifier JSON-কোট করা, তাই বিশেষ অক্ষরেও নিরাপদ */
internal fun mintScript(identifier: String): String =
    """try {
                    identifier = ${JSONObject.quote(identifier)}
                    u8Identifier = ${stringToU8(identifier)}
                    poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                    poTokenU8String = ""
                    for (i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ","
                        poTokenU8String += poTokenU8[i]
                    }
                    $JS_BRIDGE.onObtainPoTokenResult(identifier, poTokenU8String)
                } catch (error) {
                    $JS_BRIDGE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                }"""

/** চলমান poToken অনুরোধের তালিকা: JS-এর উত্তর identifier ধরে সঠিক Future-এ পৌঁছে দেয় */
internal class PendingTokens {
    private val items = mutableListOf<Pair<String, CompletableFuture<String>>>()

    fun add(identifier: String, future: CompletableFuture<String>) {
        synchronized(items) { items.add(identifier to future) }
    }

    fun size(): Int = synchronized(items) { items.size }

    /** একই identifier একাধিক থাকলে আগেরটা (FIFO) */
    fun pop(identifier: String): CompletableFuture<String>? = synchronized(items) {
        items.indexOfFirst { it.first == identifier }.takeIf { it >= 0 }?.let { items.removeAt(it).second }
    }

    fun popAll(): List<Pair<String, CompletableFuture<String>>> = synchronized(items) {
        items.toList().also { items.clear() }
    }

    /** JS-এর `Uint8Array.toString()` ফলাফল → poToken; রূপান্তর ব্যর্থ হলে Future ব্যর্থ */
    fun deliverResult(identifier: String, poTokenU8: String) {
        val token = try { u8ToBase64(poTokenU8) } catch (t: Throwable) {
            pop(identifier)?.completeExceptionally(t); return
        }
        pop(identifier)?.complete(token)
    }

    fun deliverError(identifier: String, error: String) {
        pop(identifier)?.completeExceptionally(buildExceptionForJsError(error))
    }

    fun failAll(error: Throwable) {
        popAll().forEach { (_, f) -> f.completeExceptionally(error) }
    }
}
