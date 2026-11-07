package com.tubex.app.data.potoken

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject

/*
 * BotGuard-এর ডেটা পার্স/এনকোড করার সহায়ক ফাংশন।
 * NewPipe-এর JavaScriptUtil.kt (GPL-3.0) থেকে org.json ও okio দিয়ে রূপান্তরিত।
 */

private fun JSONArray.str(i: Int): String? = opt(i) as? String

/** Create এন্ডপয়েন্টের কাঁচা উত্তর থেকে JS-এ বসানোর মতো অবজেক্ট (JSON স্ট্রিং) বানায় */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)

    val challengeData = if (scrambled.length() > 1 && scrambled.opt(1) is String) {
        JSONArray(descramble(scrambled.getString(1)))
    } else {
        scrambled.getJSONArray(1)
    }

    fun required(i: Int) = challengeData.str(i) ?: throw PoTokenException("Challenge data missing field $i")

    val script = challengeData.optJSONArray(1)?.let { a -> (0 until a.length()).map { a.opt(it) }.firstOrNull { it is String } }
    val scriptUrl = challengeData.optJSONArray(2)?.let { a -> (0 until a.length()).map { a.opt(it) }.firstOrNull { it is String } }

    return JSONObject()
        .put("messageId", required(0))
        .put(
            "interpreterJavascript", JSONObject()
                .put("privateDoNotAccessOrElseSafeScriptWrappedValue", script ?: JSONObject.NULL)
                .put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", scriptUrl ?: JSONObject.NULL)
        )
        .put("interpreterHash", required(3))
        .put("program", required(4))
        .put("globalName", required(5))
        .put("clientExperimentsStateBlob", challengeData.str(7) ?: JSONObject.NULL)
        .toString()
}

/** GenerateIT-এর উত্তর → (JS `Uint8Array` কোড, মেয়াদ সেকেন্ডে) */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val data = JSONArray(rawIntegrityTokenData)
    return base64ToU8(data.getString(0)) to data.getLong(1)
}

/** স্ট্রিং (identifier) → JS `Uint8Array` কোড */
fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray())

/** JS-এর `Uint8Array.toString()` ("97,98,99") → poToken-এর বিশেষ base64 রূপ */
fun u8ToBase64(poToken: String): String =
    poToken.split(",")
        .map { it.trim().toUByte().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace("+", "-")
        .replace("/", "_")

/** স্ক্র্যাম্বল করা চ্যালেঞ্জ: base64 ডিকোড করে প্রতি বাইটে ৯৭ যোগ */
private fun descramble(scrambledChallenge: String): String =
    base64ToByteArray(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()

private fun base64ToU8(base64: String): String = newUint8Array(base64ToByteArray(base64))

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"

/** YouTube-এর base64 রূপ ('-', '_', '.') ডিকোড */
private fun base64ToByteArray(base64: String): ByteArray {
    val mod = base64.replace('-', '+').replace('_', '/').replace('.', '=')
    return (mod.decodeBase64() ?: throw PoTokenException("Cannot base64 decode")).toByteArray()
}
