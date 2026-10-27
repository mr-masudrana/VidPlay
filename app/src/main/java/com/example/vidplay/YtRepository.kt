package com.example.vidplay

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoItem(
    val title: String,
    val url: String,
    val channel: String,
    val channelUrl: String?,
    val views: Long,
    val durationSec: Long,
    val thumb: String?,
    val uploaded: String?
)

data class ChannelData(
    val url: String, val name: String, val avatar: String?, val banner: String?,
    val subscribers: Long, val description: String?, val videos: List<VideoItem>
)

data class CommentItem(
    val author: String, val text: String, val likes: String, val date: String, val avatar: String?
)

private fun StreamInfoItem.toVideo() = VideoItem(
    title = name, url = url, channel = uploaderName ?: "", channelUrl = uploaderUrl,
    views = viewCount, durationSec = duration,
    thumb = thumbnails.lastOrNull()?.url, uploaded = textualUploadDate
)

object YtRepository {
    private val yt get() = ServiceList.YouTube

    suspend fun trending(): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = runCatching {
            val ex = yt.kioskList.defaultKioskExtractor
            ex.fetchPage()
            ex.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
        }.getOrDefault(emptyList())
        // ট্রেন্ডিং বন্ধ/খালি থাকলে সার্চ দিয়ে ফিড ভরাই
        if (list.isNotEmpty()) list else search("trending")
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf("videos"), "")
        SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    }

    suspend fun streamInfo(url: String): StreamInfo = withContext(Dispatchers.IO) { StreamInfo.getInfo(yt, url) }

    fun related(info: StreamInfo): List<VideoItem> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }

    // ---------- চ্যানেল ----------
    suspend fun channel(url: String): ChannelData = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
        val videos = tab?.let {
            ChannelTabInfo.getInfo(yt, it).relatedItems.filterIsInstance<StreamInfoItem>().map { v -> v.toVideo() }
        } ?: emptyList()
        ChannelData(
            url = info.url, name = info.name,
            avatar = info.avatars.lastOrNull()?.url, banner = info.banners.lastOrNull()?.url,
            subscribers = info.subscriberCount, description = info.description, videos = videos
        )
    }

    // ---------- মন্তব্য (প্রথম পেজ) ----------
    suspend fun comments(url: String): Pair<Int, List<CommentItem>> = withContext(Dispatchers.IO) {
        val ci = CommentsInfo.getInfo(yt, url)
        0 to ci.relatedItems.map {   // মোট সংখ্যা এই ভার্সনে নেই
            CommentItem(
                author = it.uploaderName ?: "",
                text = it.commentText?.content ?: "",
                likes = it.textualLikeCount ?: "",
                date = it.textualUploadDate ?: "",
                avatar = it.uploaderAvatars.lastOrNull()?.url
            )
        }
    }

    // ---------- প্লেব্যাক ----------
    fun heights(info: StreamInfo): List<Int> =
        (info.videoOnlyStreams.filter { it.format == MediaFormat.MPEG_4 }.map { it.height } +
                info.videoStreams.map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    fun buildSource(info: StreamInfo, maxHeight: Int = 1080): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(MyDownloader.USER_AGENT).setAllowCrossProtocolRedirects(true)
        val progressive = ProgressiveMediaSource.Factory(http)

        if (info.hlsUrl.isNotBlank() && info.videoOnlyStreams.isEmpty() && info.videoStreams.isEmpty()) {
            return HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(info.hlsUrl))
        }
        val video = info.videoOnlyStreams
            .filter { it.format == MediaFormat.MPEG_4 && it.height <= maxHeight }.maxByOrNull { it.height }
        val audio = info.audioStreams
            .filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }

        if (video != null && audio != null) {
            return MergingMediaSource(
                progressive.createMediaSource(MediaItem.fromUri(video.content)),
                progressive.createMediaSource(MediaItem.fromUri(audio.content))
            )
        }
        val muxed = info.videoStreams.filter { it.height <= maxHeight }.maxByOrNull { it.height }
            ?: info.videoStreams.maxByOrNull { it.height }
            ?: error("কোনো প্লে করার মতো স্ট্রিম পাওয়া যায়নি")
        return progressive.createMediaSource(MediaItem.fromUri(muxed.content))
    }
}
