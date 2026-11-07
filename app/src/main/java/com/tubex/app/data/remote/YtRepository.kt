package com.tubex.app.data.remote

import com.tubex.app.data.model.ChannelData
import com.tubex.app.data.model.ChannelResult
import com.tubex.app.data.model.CommentItem
import com.tubex.app.data.model.CommentPage
import com.tubex.app.data.model.PlaylistData
import com.tubex.app.data.model.PlaylistEntry
import com.tubex.app.data.model.VideoItem
import com.tubex.app.data.search.ChannelSearch
import com.tubex.app.util.L
import com.tubex.app.util.TextUtil

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

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

    /** সার্চে চ্যানেলের ফলাফল (নাম লিখে খুঁজলে চ্যানেল নিজেও যেন আসে); সর্বোচ্চ [limit]টি */
    suspend fun searchChannels(query: String, limit: Int = ChannelSearch.DEFAULT_LIMIT): List<ChannelResult> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf("channels"), "")
        SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<ChannelInfoItem>().take(limit).map {
            ChannelSearch.toResult(it.url, it.name, it.thumbnails.map { t -> t.url }, it.subscriberCount, it.description)
        }
    }

    /** সার্চে প্লেলিস্টের ফলাফল (হোমের প্লেলিস্ট সারির জন্য); সর্বোচ্চ [limit]টি */
    suspend fun searchPlaylists(query: String, limit: Int = 6): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf("playlists"), "")
        SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<PlaylistInfoItem>().take(limit).map { it.toEntry() }
    }

    suspend fun streamInfo(url: String): StreamInfo = withContext(Dispatchers.IO) { StreamInfo.getInfo(yt, url) }

    fun related(info: StreamInfo): List<VideoItem> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }

    // ---------- চ্যানেল ----------
    suspend fun channel(url: String): ChannelData = withContext(Dispatchers.IO) {
        coroutineScope {
            val info = ChannelInfo.getInfo(yt, url)
            // ইউজারনেম ও ভিডিও সংখ্যা চ্যানেল পেজ থেকে (সম্ভব না হলে বাদ), ভিডিও লোডের সাথে একসাথে চলে
            val metaJob = async { withTimeoutOrNull(6000) { runInterruptible { runCatching { channelMeta(info) }.getOrNull() } } }
            val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            val first = tab?.let { ChannelTabInfo.getInfo(yt, it) }
            val plTab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.PLAYLISTS) }
            val avatar = info.avatars.lastOrNull()?.url
            val videos = first?.relatedItems.orEmpty().filterIsInstance<StreamInfoItem>()
                .map { it.toVideo().fillChannel(avatar, info.url, info.name) }
            val meta = metaJob.await()
            ChannelData(
                url = info.url, name = info.name,
                avatar = avatar, banner = info.banners.lastOrNull()?.url,
                subscribers = info.subscriberCount, description = info.description, videos = videos,
                handle = meta?.handle, videoCount = meta?.videos,
                tab = tab, next = first?.nextPage, playlistsTab = plTab
            )
        }
    }

    /** শুধু ভিডিও ট্যাবের প্রথম পেজ (মেটা/প্লেলিস্ট ট্যাব ছাড়া): ফিড ও নতুন-ভিডিও চেকের জন্য হালকা */
    suspend fun channelVideos(url: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) } ?: return@withContext emptyList()
        val avatar = info.avatars.lastOrNull()?.url
        ChannelTabInfo.getInfo(yt, tab).relatedItems.filterIsInstance<StreamInfoItem>()
            .map { it.toVideo().fillChannel(avatar, info.url, info.name) }
    }

    /** ভিডিও ট্যাবের পরের পেজ */
    suspend fun channelMore(ch: ChannelData): Pair<List<VideoItem>, Page?> = withContext(Dispatchers.IO) {
        val tab = ch.tab ?: return@withContext Pair(emptyList(), null)
        val page = ch.next ?: return@withContext Pair(emptyList(), null)
        val res = ChannelTabInfo.getMoreItems(yt, tab, page)
        val list = res.items.filterIsInstance<StreamInfoItem>().map { it.toVideo().fillChannel(ch.avatar, ch.url, ch.name) }
        Pair(list, res.nextPage)
    }

    // ---------- ইউটিউব প্লেলিস্ট ----------
    suspend fun playlist(url: String): PlaylistData = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(yt, url)
        PlaylistData(
            url = info.url, name = info.name, uploader = info.uploaderName,
            thumb = info.thumbnails.lastOrNull()?.url, count = info.streamCount,
            videos = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() },
            next = info.nextPage
        )
    }

    suspend fun playlistMore(url: String, page: Page): Pair<List<VideoItem>, Page?> = withContext(Dispatchers.IO) {
        val res = PlaylistInfo.getMoreItems(yt, url, page)
        Pair(res.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }, res.nextPage)
    }

    private fun PlaylistInfoItem.toEntry() = PlaylistEntry(
        url = url, name = name, thumb = thumbnails.lastOrNull()?.url, count = streamCount, uploader = uploaderName
    )

    /** চ্যানেলের প্লেলিস্ট ট্যাব: প্রথম পেজ */
    suspend fun channelPlaylists(tab: ListLinkHandler): Pair<List<PlaylistEntry>, Page?> = withContext(Dispatchers.IO) {
        val info = ChannelTabInfo.getInfo(yt, tab)
        Pair(info.relatedItems.filterIsInstance<PlaylistInfoItem>().map { it.toEntry() }, info.nextPage)
    }

    suspend fun channelPlaylistsMore(tab: ListLinkHandler, page: Page): Pair<List<PlaylistEntry>, Page?> = withContext(Dispatchers.IO) {
        val res = ChannelTabInfo.getMoreItems(yt, tab, page)
        Pair(res.items.filterIsInstance<PlaylistInfoItem>().map { it.toEntry() }, res.nextPage)
    }

    private fun VideoItem.fillChannel(avatar: String?, url: String, name: String) = copy(
        channelAvatar = channelAvatar ?: avatar,
        channelUrl = channelUrl ?: url,
        channel = channel.ifBlank { name }
    )

    private class ChannelMeta(val handle: String?, val videos: String?)

    /** চ্যানেল পেজের HTML থেকে @ইউজারনেম ও ভিডিও সংখ্যা (এক্সট্র্যাক্টর এগুলো দেয় না)। না পেলে null */
    private fun channelMeta(info: ChannelInfo): ChannelMeta {
        var handle = listOf(info.originalUrl, info.url)
            .firstNotNullOfOrNull { Regex("/(@[^/?#]+)").find(it ?: "")?.groupValues?.get(1) }
        val base = info.url
        val page = base + (if (base.contains('?')) "&" else "?") + "hl=en"
        val html = NewPipe.getDownloader().get(
            page,
            mapOf("Accept-Language" to listOf("en-US,en;q=0.9"), "Cookie" to listOf("SOCS=CAI"))
        ).responseBody()
        if (handle == null) handle = Regex("\"(?:vanityChannelUrl|canonicalBaseUrl|canonicalChannelUrl)\":\"[^\"]*?/(@[^\"/\\\\]+)\"")
            .find(html)?.groupValues?.get(1)
        handle = handle?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val count = listOf(
            "\"content\":\"([0-9][0-9.,]*[KMB]?) videos?\"",
            "\"videoCountText\":\"([0-9][0-9.,]*[KMB]?) videos?\"",
            "\"videoCountText\":\\{\"runs\":\\[\\{\"text\":\"([0-9][0-9.,]*[KMB]?)\"\\}"
        ).firstNotNullOfOrNull { Regex(it).find(html)?.groupValues?.get(1) }
        return ChannelMeta(handle, count)
    }

    // ---------- মন্তব্য (পেজ করে) ----------
    suspend fun comments(url: String): CommentPage = withContext(Dispatchers.IO) {
        val ci = CommentsInfo.getInfo(yt, url)
        // মোট মন্তব্যের সংখ্যা (এক্সট্র্যাক্টরের ভার্সনে না থাকলে -১)
        val total = runCatching { ci.javaClass.getMethod("getCommentCount").invoke(ci) as? Int }.getOrNull() ?: -1
        CommentPage(ci.relatedItems.map { it.toComment() }, ci.nextPage, total)
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
        text = TextUtil.clean(commentText?.content),
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

    // WEB ক্লায়েন্টের স্ট্রিমিং URL (poToken সহ) ব্রাউজারের মতো Origin/Referer আশা করে
    private fun http() = DefaultHttpDataSource.Factory()
        .setUserAgent(MyDownloader.USER_AGENT).setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/"))

    // ---------- অডিও ট্র্যাক (ভাষা/ডাব) ----------
    data class AudioTrack(val id: String?, val label: String)

    fun audioTracks(info: StreamInfo): List<AudioTrack> =
        info.audioStreams.filter { it.format == MediaFormat.M4A }
            .groupBy { it.audioTrackId }
            .map { (id, l) ->
                val s = l.first()
                AudioTrack(
                    id,
                    s.audioTrackName?.takeIf { it.isNotBlank() }
                        ?: s.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
                        ?: id ?: "Default"
                )
            }

    private fun pickAudio(info: StreamInfo, trackId: String?) =
        info.audioStreams.filter { it.format == MediaFormat.M4A }.let { m4a ->
            val pool = if (trackId != null) m4a.filter { it.audioTrackId == trackId }
            else m4a.filter { it.audioTrackType?.name == "ORIGINAL" }
            pool.ifEmpty { m4a }.maxByOrNull { it.averageBitrate }
        }

    /** ডিফল্টে যে ট্র্যাক চলবে (আসল অডিও) */
    fun defaultAudioTrackId(info: StreamInfo): String? = pickAudio(info, null)?.audioTrackId

    /** বাংলা অডিও ট্র্যাকের আইডি (ভাষা বা ট্র্যাকের নাম/আইডি দেখে); না থাকলে null */
    fun banglaAudioTrackId(info: StreamInfo): String? =
        info.audioStreams.filter { it.format == MediaFormat.M4A && it.audioTrackId != null }
            .firstOrNull { s ->
                val lang = s.audioLocale?.language?.lowercase()
                val id = s.audioTrackId?.lowercase().orEmpty()
                val name = s.audioTrackName?.lowercase().orEmpty()
                lang == "bn" || id.startsWith("bn") ||
                    name.contains("bengali") || name.contains("bangla") || name.contains("বাংলা")
            }?.audioTrackId

    /** শুরুতে কোন অডিও ট্র্যাক: বাংলা অন থাকলে ও ভিডিওতে বাংলা ট্র্যাক থাকলে সেটা, নইলে আসল অডিও */
    fun initialAudioTrackId(info: StreamInfo, preferBangla: Boolean): String? =
        (if (preferBangla) banglaAudioTrackId(info) else null) ?: defaultAudioTrackId(info)

    /** টাইটেল/চ্যানেল/থাম্বনেইলসহ MediaItem: পরের ভিডিওতে গেলে নোটিফিকেশন/লকস্ক্রিন নিজে থেকে আপডেট হয় */
    private fun item(uri: String, meta: androidx.media3.common.MediaMetadata?): MediaItem =
        MediaItem.Builder().setUri(uri).apply { if (meta != null) setMediaMetadata(meta) }.build()

    private fun baseSource(
        info: StreamInfo, maxHeight: Int, audioOnly: Boolean, trackId: String?,
        meta: androidx.media3.common.MediaMetadata? = null
    ): MediaSource {
        val http = http()
        val progressive = ProgressiveMediaSource.Factory(http)

        if (info.hlsUrl.isNotBlank() && info.videoOnlyStreams.isEmpty() && info.videoStreams.isEmpty()) {
            return HlsMediaSource.Factory(http).createMediaSource(item(info.hlsUrl, meta))
        }
        val mp4 = info.videoOnlyStreams.filter { it.format == MediaFormat.MPEG_4 && it.height <= maxHeight }
        // H.264 (avc1) আগে: AV1-এর হার্ডওয়্যার ডিকোডার না থাকলে প্লে ব্যর্থ হয়
        val video = mp4.filter { it.codec?.startsWith("avc1") == true }.ifEmpty { mp4 }.maxByOrNull { it.height }
        val audio = pickAudio(info, trackId)

        if (audioOnly) {   // শুধু অডিও: ডেটা বাঁচে
            val a = audio ?: info.audioStreams.maxByOrNull { it.averageBitrate }
            if (a != null) return progressive.createMediaSource(item(a.content, meta))
        }
        if (video != null && audio != null) {
            return MergingMediaSource(
                progressive.createMediaSource(item(video.content, meta)),
                progressive.createMediaSource(item(audio.content, meta))
            )
        }
        val muxed = info.videoStreams.filter { it.height <= maxHeight }.maxByOrNull { it.height }
            ?: info.videoStreams.maxByOrNull { it.height }
            ?: error(L.t("কোনো প্লে করার মতো স্ট্রিম পাওয়া যায়নি"))
        return progressive.createMediaSource(item(muxed.content, meta))
    }

    fun buildSource(
        info: StreamInfo, maxHeight: Int = 1080, caption: SubtitlesStream? = null, audioOnly: Boolean = false,
        audioTrackId: String? = null, meta: androidx.media3.common.MediaMetadata? = null
    ): MediaSource {
        val base = baseSource(info, maxHeight, audioOnly, audioTrackId, meta)
        val mime = caption?.let { mimeFor(it) }
        if (caption == null || mime == null) return base
        val cfg = MediaItem.SubtitleConfiguration.Builder(Uri.parse(caption.content))
            .setMimeType(mime).setLanguage(caption.languageTag)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        return MergingMediaSource(base, SingleSampleMediaSource.Factory(http()).setTreatLoadErrorsAsEndOfStream(true)
            .createMediaSource(cfg, C.TIME_UNSET))
    }

    /** Cast-এর জন্য: একটি মাত্র প্রগ্রেসিভ (muxed) স্ট্রিম */
    fun castStream(info: StreamInfo) = info.videoStreams.maxByOrNull { it.height }
}
