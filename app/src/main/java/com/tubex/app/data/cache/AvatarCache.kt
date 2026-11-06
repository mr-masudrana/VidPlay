package com.tubex.app.data.cache

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo

/** চ্যানেল URL -> লোগোর URL। কার্ড/কমেন্টে লোগো না থাকলে চ্যানেল থেকে একবার আনে। */
object AvatarCache {
    val map = mutableStateMapOf<String, String>()      // "" = আনা চলছে
    private val failedAt = HashMap<String, Long>()     // ব্যর্থ হলে ১ মিনিট পরে আবার চেষ্টা
    private const val RETRY_MS = 60_000L
    private val gate = Semaphore(3)

    suspend fun ensure(channelUrl: String?) {
        if (channelUrl.isNullOrBlank() || channelUrl in map) return
        val failed = failedAt[channelUrl]
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) return
        map[channelUrl] = ""
        val url = try {
            gate.withPermit {
                withContext(Dispatchers.IO) {
                    runCatching { ChannelInfo.getInfo(ServiceList.YouTube, channelUrl).avatars.lastOrNull()?.url }.getOrNull()
                }
            }
        } catch (e: CancellationException) {
            map.remove(channelUrl)      // স্ক্রল করে সরে গেলে "চলছে" মার্কার রেখে দেওয়া যাবে না, নইলে আর রিট্রাই হয় না
            throw e
        }
        if (!url.isNullOrBlank()) { map[channelUrl] = url; failedAt.remove(channelUrl) }
        else { map.remove(channelUrl); failedAt[channelUrl] = System.currentTimeMillis() }
    }
}
