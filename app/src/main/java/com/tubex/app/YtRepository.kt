package com.tubex.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
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
    val uploaded: String?,
    val reason: String? = null,
    val channelAvatar: String? = null
)

data class ChannelData(
    val url: String, val name: String, val avatar: String?, val banner: String?,
    val subscribers: Long, val description: String?, val videos: List<VideoItem>
)

data class CommentItem(
    val id: String, val author: String, val authorUrl: String?, val text: String, val likes: String,
    val date: String, val avatar: String?, val replyCount: Int, val replies: Page?, val time: Long?
)

data class CommentPage(val items: List<CommentItem>, val next: Page?)

private fun StreamInfoItem.toVideo() = VideoItem(
    title = name, url = url, channel = uploaderName ?: "", channelUrl = uploaderUrl,
    views = viewCount, durationSec = duration,
    thumb = thumbnails.lastOrNull()?.url, uploaded = textualUploadDate,
    channelAvatar = uploaderAvatars.lastOrNull()?.url
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
            ChannelTabInfo.getInfo(yt, it).relatedItems.filterIsInstance<StreamInfoItem>().map { v ->
                v.toVideo().let { x ->
                    x.copy(
                        channelAvatar = x.channelAvatar ?: info.avatars.lastOrNull()?.url,
                        channelUrl = x.channelUrl ?: info.url,
                        channel = x.channel.ifBlank { info.name }
                    )
                }
            }
        } ?: emptyList()
        ChannelData(
            url = info.url, name = info.name,
            avatar = info.avatars.lastOrNull()?.url, banner = info.banners.lastOrNull()?.url,
            subscribers = info.subscriberCount, description = info.description, videos = videos
        )
    }

    // ---------- মন্তব্য (পেজ করে) ----------
    suspend fun comments(url: String): CommentPage = withContext(Dispatchers.IO) {
        val ci = CommentsInfo.getInfo(yt, url)
        CommentPage(ci.relatedItems.map { it.toComment() }, ci.nextPage)
    }

    /** পরের পেজ অথবা কোনো মন্তব্যের উত্তরের পেজ */
    suspend fun moreComments(url: String, page: Page): CommentPage = withContext(Dispatchers.IO) {
        val p = CommentsInfo.getMoreItems(yt, url, page)
        CommentPage(p.items.map { it.toComment() }, p.nextPage)
    }

    private fun CommentsInfoItem.toComment() = CommentItem(
        id = commentId ?: "",
        author = uploaderName ?: "",
        authorUrl = uploaderUrl,
        text = commentText?.content ?: "",
        likes = textualLikeCount ?: "",
        date = textualUploadDate ?: "",
        avatar = uploaderAvatars.lastOrNull()?.url,
        replyCount = replyCount,
        replies = replies,
        time = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
    )

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { yt.suggestionExtractor.suggestionList(q) }.getOrDefault(emptyList())
    }

    // ---------- সাবটাইটেল ----------
    private fun mimeFor(s: SubtitlesStream): String? = when (s.format) {
        MediaFormat.VTT -> MimeTypes.TEXT_VTT
        MediaFormat.TTML -> MimeTypes.APPLICATION_TTML
        MediaFormat.SRT -> MimeTypes.APPLICATION_SUBRIP
        else -> null
    }

    /** ভাষা অনুযায়ী ইউনিক সাবটাইটেল; আগে আসল, পরে স্বয়ংক্রিয় */
    fun captions(info: StreamInfo): List<SubtitlesStream> =
        info.subtitles.filter { mimeFor(it) != null }
            .groupBy { it.languageTag + "|" + it.isAutoGenerated }
            .map { (_, l) -> l.minByOrNull { if (it.format == MediaFormat.VTT) 0 else 1 }!! }
            .sortedWith(compareBy<SubtitlesStream>({ it.isAutoGenerated }, { it.displayLanguageName }))

    // ---------- প্লেব্যাক ----------
    fun heights(info: StreamInfo): List<Int> =
        (info.videoOnlyStreams.filter { it.format == MediaFormat.MPEG_4 }.map { it.height } +
                info.videoStreams.map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    private fun http() = DefaultHttpDataSource.Factory()
        .setUserAgent(MyDownloader.USER_AGENT).setAllowCrossProtocolRedirects(true)

    private fun baseSource(info: StreamInfo, maxHeight: Int, audioOnly: Boolean): MediaSource {
        val http = http()
        val progressive = ProgressiveMediaSource.Factory(http)

        if (info.hlsUrl.isNotBlank() && info.videoOnlyStreams.isEmpty() && info.videoStreams.isEmpty()) {
            return HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(info.hlsUrl))
        }
        val video = info.videoOnlyStreams
            .filter { it.format == MediaFormat.MPEG_4 && it.height <= maxHeight }.maxByOrNull { it.height }
        val audio = info.audioStreams
            .filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }

        if (audioOnly) {   // শুধু অডিও: ডেটা বাঁচে
            val a = audio ?: info.audioStreams.maxByOrNull { it.averageBitrate }
            if (a != null) return progressive.createMediaSource(MediaItem.fromUri(a.content))
        }
        if (video != null && audio != null) {
            return MergingMediaSource(
                progressive.createMediaSource(MediaItem.fromUri(video.content)),
                progressive.createMediaSource(MediaItem.fromUri(audio.content))
            )
        }
        val muxed = info.videoStreams.filter { it.height <= maxHeight }.maxByOrNull { it.height }
            ?: info.videoStreams.maxByOrNull { it.height }
            ?: error(L.t("কোনো প্লে করার মতো স্ট্রিম পাওয়া যায়নি"))
        return progressive.createMediaSource(MediaItem.fromUri(muxed.content))
    }

    fun buildSource(
        info: StreamInfo, maxHeight: Int = 1080, caption: SubtitlesStream? = null, audioOnly: Boolean = false
    ): MediaSource {
        val base = baseSource(info, maxHeight, audioOnly)
        val mime = caption?.let { mimeFor(it) }
        if (caption == null || mime == null) return base
        val cfg = MediaItem.SubtitleConfiguration.Builder(Uri.parse(caption.content))
            .setMimeType(mime).setLanguage(caption.languageTag)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        return MergingMediaSource(base, SingleSampleMediaSource.Factory(http()).createMediaSource(cfg, C.TIME_UNSET))
    }

    /** Cast-এর জন্য: একটি মাত্র প্রগ্রেসিভ (muxed) স্ট্রিম */
    fun castStream(info: StreamInfo) = info.videoStreams.maxByOrNull { it.height }
}
