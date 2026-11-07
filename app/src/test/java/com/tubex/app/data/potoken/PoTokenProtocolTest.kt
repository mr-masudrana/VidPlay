package com.tubex.app.data.potoken

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/** PoTokenWebView-এর Android-মুক্ত অংশ: অনুরোধের বডি, JS, মেয়াদ ও অপেক্ষমাণ টোকেনের তালিকা */
class PoTokenProtocolTest {

    // ---------- অনুরোধের বডি ----------
    @Test fun createBody_usesRequestKey() {
        assertEquals("[ \"$BOTGUARD_REQUEST_KEY\" ]", createRequestBody())
    }

    @Test fun generateItBody_carriesBotguardResponse() {
        assertEquals("[ \"$BOTGUARD_REQUEST_KEY\", \"resp123\" ]", generateItRequestBody("resp123"))
    }

    @Test fun endpoints_areYoutubeJnn() {
        assertTrue(BOTGUARD_CREATE_URL.endsWith("/api/jnn/v1/Create"))
        assertTrue(BOTGUARD_GENERATE_IT_URL.endsWith("/api/jnn/v1/GenerateIT"))
    }

    // ---------- মেয়াদ ----------
    @Test fun expiry_subtractsTenMinuteMargin() {
        assertEquals(1_000_000L + 42_600_000L, integrityExpiryMs(1_000_000L, 43_200L))
    }

    @Test fun expiry_shortTtlIsAlreadyExpired() {
        val now = 5_000_000L
        assertEquals(now, integrityExpiryMs(now, 600L))
        assertTrue(integrityExpiryMs(now, 300L) < now)
    }

    // ---------- পেজে স্টার্ট-কল বসানো ----------
    @Test fun injectAutoStart_goesBeforeFirstScriptCloseOnly() {
        val out = injectAutoStart("<script>var a=1;</script><script>b</script>")
        assertEquals("<script>var a=1;\n$JS_BRIDGE.downloadAndRunBotguard()</script><script>b</script>", out)
    }

    @Test fun injectAutoStart_leavesHtmlWithoutScriptUntouched() {
        assertEquals("<html></html>", injectAutoStart("<html></html>"))
    }

    // ---------- WebView-এ চালানো JS ----------
    @Test fun runBotguardScript_embedsChallengeAndReportsBothOutcomes() {
        val js = runBotguardScript("""{"a":1}""")
        assertTrue(js.contains("""data = {"a":1}"""))
        assertTrue(js.contains("runBotGuard(data)"))
        assertTrue(js.contains("$JS_BRIDGE.onRunBotguardResult(result.botguardResponse)"))
        // প্রমিস-ব্যর্থতা ও সিনট্যাক্স/রানটাইম ত্রুটি দুটোই Kotlin-এ জানানো হয়
        assertEquals(2, Regex(Regex.escape("$JS_BRIDGE.onJsInitializationError(")).findAll(js).count())
    }

    @Test fun mintScript_embedsIdentifierAndU8() {
        val js = mintScript("dQw4w9WgXcQ")
        assertTrue(js.contains("identifier = \"dQw4w9WgXcQ\""))
        assertTrue(js.contains("u8Identifier = ${stringToU8("dQw4w9WgXcQ")}"))
        assertTrue(js.contains("obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)"))
        assertTrue(js.contains("$JS_BRIDGE.onObtainPoTokenResult(identifier, poTokenU8String)"))
        assertTrue(js.contains("$JS_BRIDGE.onObtainPoTokenError(identifier, "))
    }

    @Test fun mintScript_escapesHostileIdentifier() {
        val evil = "a\"b\nc';alert(1);//"
        val js = mintScript(evil)
        assertTrue(js.contains("identifier = " + JSONObject.quote(evil)))
        assertFalse("কাঁচা identifier স্ক্রিপ্টে থাকা চলবে না", js.contains(evil))
    }

    // ---------- অপেক্ষমাণ টোকেন ----------
    @Test fun deliverResult_completesMatchingFuture() {
        val p = PendingTokens()
        val f = CompletableFuture<String>()
        p.add("vid1", f)
        p.deliverResult("vid1", "97,98,99")
        assertEquals("YWJj", f.get())
        assertEquals(0, p.size())
    }

    @Test fun deliverResult_routesByIdentifier() {
        val p = PendingTokens()
        val a = CompletableFuture<String>(); val b = CompletableFuture<String>()
        p.add("a", a); p.add("b", b)
        p.deliverResult("b", "97,98,99")
        assertTrue(b.isDone); assertFalse(a.isDone)
        assertEquals(1, p.size())
    }

    @Test fun deliverResult_unknownIdentifierIsIgnored() {
        val p = PendingTokens()
        val f = CompletableFuture<String>()
        p.add("a", f)
        p.deliverResult("zzz", "97,98,99")
        assertFalse(f.isDone)
        assertEquals(1, p.size())
    }

    @Test fun deliverResult_malformedBytesFailsTheFuture() {
        val p = PendingTokens()
        val f = CompletableFuture<String>()
        p.add("a", f)
        p.deliverResult("a", "not,numbers")
        assertTrue(f.isCompletedExceptionally)
        assertEquals(0, p.size())
    }

    @Test fun duplicateIdentifiers_areServedFirstInFirstOut() {
        val p = PendingTokens()
        val first = CompletableFuture<String>(); val second = CompletableFuture<String>()
        p.add("same", first); p.add("same", second)
        p.deliverResult("same", "97,98,99")
        assertTrue(first.isDone); assertFalse(second.isDone)
        assertEquals(1, p.size())
    }

    @Test fun deliverError_syntaxErrorMeansBadWebView() {
        val p = PendingTokens()
        val f = CompletableFuture<String>()
        p.add("a", f)
        p.deliverError("a", "SyntaxError: Unexpected token")
        try { f.get(); fail("exception আশা করা হয়েছিল") }
        catch (e: ExecutionException) { assertTrue(e.cause is BadWebViewException) }
    }

    @Test fun deliverError_otherErrorIsPoTokenException() {
        val p = PendingTokens()
        val f = CompletableFuture<String>()
        p.add("a", f)
        p.deliverError("a", "TypeError: x is undefined")
        try { f.get(); fail("exception আশা করা হয়েছিল") }
        catch (e: ExecutionException) { assertTrue(e.cause is PoTokenException) }
    }

    @Test fun failAll_failsEveryPendingAndEmptiesList() {
        val p = PendingTokens()
        val a = CompletableFuture<String>(); val b = CompletableFuture<String>()
        p.add("a", a); p.add("b", b)
        p.failAll(PoTokenException("closed"))
        assertTrue(a.isCompletedExceptionally); assertTrue(b.isCompletedExceptionally)
        assertEquals(0, p.size())
        assertNull(p.pop("a"))
    }
}
