package com.tubex.app.data.potoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * assets/po_token.html আর Kotlin-এর মধ্যে চুক্তি: Kotlin যে JS ফাংশন ডাকে তা পেজে থাকতে হবে,
 * আর স্টার্ট-কল বসানোর জন্য </script> থাকতে হবে। (আসল WebView ডিভাইসে পরীক্ষা করতে হয়।)
 */
class PoTokenAssetTest {
    private val html: String by lazy {
        val f = listOf("src/main/assets/po_token.html", "app/src/main/assets/po_token.html")
            .map(::File).firstOrNull { it.exists() }
            ?: error("po_token.html পাওয়া যায়নি (কাজের ডিরেক্টরি: ${File(".").absolutePath})")
        f.readText()
    }

    @Test fun definesEveryFunctionKotlinCalls() {
        assertTrue(html.contains("function runBotGuard("))
        assertTrue(html.contains("function obtainPoToken("))
    }

    @Test fun runBotGuardResult_hasBothFieldsKotlinReads() {
        assertTrue(html.contains("webPoSignalOutput"))
        assertTrue(html.contains("botguardResponse"))
    }

    @Test fun hasScriptCloseForAutoStartInjection() {
        assertTrue(html.contains("</script>"))
    }

    @Test fun autoStartCall_landsInsideTheFirstScript() {
        val out = injectAutoStart(html)
        val call = "$JS_BRIDGE.downloadAndRunBotguard()"
        val callAt = out.indexOf(call)
        assertTrue("স্টার্ট-কল বসেনি", callAt > 0)
        assertEquals("কলের পর প্রথম </script> তার ঠিক পরেই", callAt + call.length, out.indexOf("</script>"))
        assertEquals(html.length + call.length + 1, out.length)   // শুধু "\n" + কল যোগ হয়েছে
    }

    @Test fun noExternalNetworkReferences() {
        // WebView-এর blockNetworkLoads চালু; পেজ যেন বাইরের কিছু লোড করতে না চায়
        assertTrue(!Regex("""<script[^>]+src=""").containsMatchIn(html))
    }
}
