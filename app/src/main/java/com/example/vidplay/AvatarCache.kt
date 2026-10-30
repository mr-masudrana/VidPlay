package com.example.vidplay

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo

/** চ্যানেল URL -> লোগোর URL। কার্ড/কমেন্টে লোগো না থাকলে চ্যানেল থেকে একবার আনে। */
object AvatarCache {
    val map = mutableStateMapOf<String, String>()      // "" = পাওয়া যায়নি
    private val gate = Semaphore(3)

    suspend fun ensure(channelUrl: String?) {
        if (channelUrl.isNullOrBlank() || channelUrl in map) return
        map[channelUrl] = ""
        val url = gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching { ChannelInfo.getInfo(ServiceList.YouTube, channelUrl).avatars.lastOrNull()?.url }.getOrNull()
            }
        }
        if (!url.isNullOrBlank()) map[channelUrl] = url
    }
}
