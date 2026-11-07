package com.tubex.app.data.potoken

import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JavaScriptUtilTest {

    /** YouTube-এর base64 রূপে এনকোড (ইউনিট টেস্টের সহায়ক) */
    private fun yt64(bytes: ByteArray) =
        bytes.toByteString().base64().replace('+', '-').replace('/', '_').replace('=', '.')

    @Test fun `u8ToBase64 ঠিকভাবে রূপান্তর করে`() {
        // "abc" = 97,98,99 → "YWJj"
        assertEquals("YWJj", u8ToBase64("97,98,99"))
    }

    @Test fun `u8ToBase64 url-safe অক্ষর ব্যবহার করে ও ২৫৫ সামলায়`() {
        // 251,255,191 → standard base64 "+/+/" → url-safe "-_-_"
        assertEquals("-_-_", u8ToBase64("251,255,191"))
    }

    @Test fun `stringToU8 Uint8Array কোড বানায়`() {
        assertEquals("new Uint8Array([97,98,99])", stringToU8("abc"))
    }

    @Test fun `parseIntegrityTokenData টোকেন ও মেয়াদ আলাদা করে`() {
        val token = byteArrayOf(1, 2, -1)          // -1 → JS-এ 255
        val raw = """["${yt64(token)}", 43200, null]"""
        val (js, ttl) = parseIntegrityTokenData(raw)
        assertEquals("new Uint8Array([1,2,255])", js)
        assertEquals(43200L, ttl)
    }

    @Test fun `parseChallengeData সরাসরি অ্যারে থেকে অবজেক্ট বানায়`() {
        val raw = """[null, ["msg1", ["var x=1;"], ["https://example/i.js"], "hash9", "PROGRAM", "glob", null, "blob"]]"""
        val o = JSONObject(parseChallengeData(raw))
        assertEquals("msg1", o.getString("messageId"))
        assertEquals("var x=1;", o.getJSONObject("interpreterJavascript")
            .getString("privateDoNotAccessOrElseSafeScriptWrappedValue"))
        assertEquals("hash9", o.getString("interpreterHash"))
        assertEquals("PROGRAM", o.getString("program"))
        assertEquals("glob", o.getString("globalName"))
        assertEquals("blob", o.getString("clientExperimentsStateBlob"))
    }

    @Test fun `parseChallengeData স্ক্র্যাম্বল করা চ্যালেঞ্জ ডিস্ক্র্যাম্বল করে`() {
        val inner = """["m2", [null, "S"], null, "h", "P", "g", null, null]"""
        // স্ক্র্যাম্বল = প্রতি বাইট থেকে ৯৭ বিয়োগ, তারপর YouTube-base64
        val scrambled = yt64(inner.toByteArray().map { (it - 97).toByte() }.toByteArray())
        val o = JSONObject(parseChallengeData("""[null, "$scrambled"]"""))
        assertEquals("m2", o.getString("messageId"))
        assertEquals("S", o.getJSONObject("interpreterJavascript")
            .getString("privateDoNotAccessOrElseSafeScriptWrappedValue"))
        assertEquals("P", o.getString("program"))
        assertTrue(o.isNull("clientExperimentsStateBlob"))
    }

    @Test fun `parseChallengeData জরুরি ফিল্ড না থাকলে PoTokenException`() {
        try {
            parseChallengeData("""[null, ["only-id"]]""")
            fail("exception আশা করা হয়েছিল")
        } catch (e: PoTokenException) { /* ঠিক আছে */ }
    }

    @Test fun `buildExceptionForJsError সিনট্যাক্স ত্রুটিকে BadWebView ধরে`() {
        assertTrue(buildExceptionForJsError("SyntaxError: x") is BadWebViewException)
        assertTrue(buildExceptionForJsError("Error: PMD:Undefined") is PoTokenException)
    }
}
