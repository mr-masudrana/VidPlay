package com.tubex.app.data.cache

import com.tubex.app.data.remote.YtRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * ভিডিওর StreamInfo ক্যাশ (সর্বোচ্চ [maxEntries]টি, [ttlMs] পর্যন্ত বৈধ)।
 * একই URL-এর জন্য চলমান ফেচ থাকলে নতুন করে না এনে তার জন্য অপেক্ষা করে — প্রি-ফেচ ও প্লে একই ফেচ ভাগ করে নেয়।
 */
class StreamInfoCache(
    private val scope: CoroutineScope,
    private val maxEntries: Int = 12,
    private val ttlMs: Long = 30 * 60_000L
) {
    private val cache = object : LinkedHashMap<String, Pair<Long, StreamInfo>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, StreamInfo>>?) = size > maxEntries
    }
    private val inflight = ConcurrentHashMap<String, Deferred<StreamInfo>>()

    suspend fun get(url: String): StreamInfo {
        cache[url]?.takeIf { System.currentTimeMillis() - it.first < ttlMs }?.let { return it.second }
        val d = inflight.getOrPut(url) {
            scope.async {
                YtRepository.streamInfo(url).also { cache[url] = System.currentTimeMillis() to it }
            }.also { job -> job.invokeOnCompletion { inflight.remove(url) } }
        }
        return d.await()
    }

    /** ক্যাশে বৈধভাবে আছে বা এখন আনা হচ্ছে — প্রি-ফেচে একই ভিডিও দুবার না আনতে */
    fun has(url: String): Boolean =
        cache[url]?.let { System.currentTimeMillis() - it.first < ttlMs } == true || inflight.containsKey(url)

    /** মেয়াদোত্তীর্ণ স্ট্রিম URL-এর পর: পুরনো এন্ট্রি বাদ */
    fun remove(url: String) { cache.remove(url) }

    /** সদ্য আনা (fresh) তথ্য বসানো */
    fun put(url: String, info: StreamInfo) { cache[url] = System.currentTimeMillis() to info }
}
