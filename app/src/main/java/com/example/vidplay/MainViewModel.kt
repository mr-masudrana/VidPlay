package com.example.vidplay

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.mediarouter.app.MediaRouteChooserDialog
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.File
import java.util.concurrent.Executors

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dao = AppDb.get(app).dao()
    private val prefs = Prefs(app)
    val player = PlayerHolder.get(app)

    // Room
    val subs = dao.subs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = dao.downloads().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites = dao.favorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val blocks = dao.blocks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // সেটিংস
    var themeIndex by mutableStateOf(prefs.theme); private set
    var sponsorOn by mutableStateOf(prefs.sponsorBlock); private set
    var rydOn by mutableStateOf(prefs.ryd); private set
    var captionSizeIndex by mutableStateOf(prefs.captionSize); private set
    var qualityWifi by mutableStateOf(prefs.qualityWifi); private set
    var qualityMobile by mutableStateOf(prefs.qualityMobile); private set
    var skipSilence by mutableStateOf(prefs.skipSilence); private set
    var audioOnly by mutableStateOf(prefs.audioOnly); private set
    var notifyOn by mutableStateOf(prefs.notifyEnabled); private set
    var notifyHours by mutableStateOf(prefs.notifyHours); private set
    var loop by mutableStateOf(false); private set
    var sleepEnd by mutableStateOf(0L); private set
    var sleepAtEnd by mutableStateOf(false); private set
    var positionMs by mutableStateOf(0L); private set
    var durationMs by mutableStateOf(0L); private set

    // সার্চ
    var searching by mutableStateOf(false); private set
    var query by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var showSuggestions by mutableStateOf(false); private set
    var searchHistory by mutableStateOf(prefs.searchHistory); private set
    private var suggestJob: Job? = null

    // ফিড
    var tab by mutableStateOf(0)
    var feed by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var subsFeed by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var opening by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    // প্লেয়ার
    var current by mutableStateOf<StreamInfo?>(null); private set
    var local by mutableStateOf<DownloadEntity?>(null); private set
    var expanded by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var inPip by mutableStateOf(false)
    var related by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var heights by mutableStateOf<List<Int>>(emptyList()); private set
    var quality by mutableStateOf(0); private set          // 0 = Auto
    var speed by mutableStateOf(1f); private set
    var likes by mutableStateOf<Long?>(null); private set
    var dislikes by mutableStateOf<Long?>(null); private set
    var captions by mutableStateOf<List<SubtitlesStream>>(emptyList()); private set
    var captionIndex by mutableStateOf(-1); private set     // -1 = বন্ধ

    // মন্তব্য
    var comments by mutableStateOf<List<CommentItem>>(emptyList()); private set
    var commentsTotal by mutableStateOf(0); private set
    var commentsLoading by mutableStateOf(false); private set

    // চ্যানেল
    var channelOpen by mutableStateOf(false); private set
    var channel by mutableStateOf<ChannelData?>(null); private set
    var channelLoading by mutableStateOf(false); private set

    // রিকমেন্ডেশন ড্যাশবোর্ড
    var topInterests by mutableStateOf<List<Pair<String, Double>>>(emptyList()); private set

    // Chromecast
    var castAvailable by mutableStateOf(false); private set
    var castName by mutableStateOf<String?>(null); private set
    var castPlaying by mutableStateOf(false); private set
    private var castContext: CastContext? = null

    private var segments: List<Segment> = emptyList()
    private var boosted = false
    private var queue: List<String> = emptyList()
    private var queueIndex = 0

    val active get() = current != null || local != null
    val title get() = current?.name ?: local?.title ?: ""
    val channelName get() = current?.uploaderName ?: ""

    private val castListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionStarted(s: CastSession, id: String) { onCastConnected(s) }
        override fun onSessionStartFailed(s: CastSession, e: Int) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionEnded(s: CastSession, e: Int) { castName = null; castPlaying = false }
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) { onCastConnected(s) }
        override fun onSessionResumeFailed(s: CastSession, e: Int) {}
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }

    init {
        player.skipSilenceEnabled = skipSilence
        DeArrow.titles = prefs.dearrowTitles; DeArrow.thumbs = prefs.dearrowThumbs
        viewModelScope.launch { if (!DownloadEngine.isActive) dao.resetInterrupted() }
        loadForYou()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) saveProgress()
            }
            override fun onPlaybackStateChanged(state: Int) {
                // প্লেলিস্ট/কিউ: শেষ হলে পরেরটা
                if (state == Player.STATE_ENDED && sleepAtEnd) {
                    sleepAtEnd = false; toast("স্লিপ টাইমার: প্লেব্যাক বন্ধ"); return
                }
                if (state == Player.STATE_ENDED && queue.isNotEmpty() && queueIndex + 1 < queue.size) {
                    queueIndex++
                    startPlay(queue[queueIndex])
                }
            }
            override fun onPlayerError(e: PlaybackException) { error = e.message ?: "প্লেব্যাক এরর" }
        })
        // Chromecast (Google Play Services না থাকলে নীরবে বাদ)
        runCatching {
            CastContext.getSharedInstance(app, Executors.newSingleThreadExecutor())
                .addOnSuccessListener { cc ->
                    castContext = cc; castAvailable = true
                    cc.sessionManager.addSessionManagerListener(castListener, CastSession::class.java)
                }
        }
        // প্রতি ০.৫ সেকেন্ডে: SponsorBlock স্কিপ; প্রতি ১০ সেকেন্ডে: হিস্টরি সেভ
        viewModelScope.launch {
            var n = 0
            while (true) {
                delay(500)
                if (sleepEnd > 0 && System.currentTimeMillis() >= sleepEnd) {
                    sleepEnd = 0; player.pause(); castClient()?.pause(); toast("স্লিপ টাইমার: প্লেব্যাক বন্ধ")
                }
                if (expanded) { positionMs = player.currentPosition; durationMs = player.duration.coerceAtLeast(0L) }
                val info = current
                if (info == null || !player.isPlaying) continue
                val pos = player.currentPosition
                if (sponsorOn) {
                    segments.firstOrNull { pos >= it.startMs && pos < it.endMs - 400 }?.let {
                        player.seekTo(it.endMs)
                        toast("স্পন্সর অংশ বাদ দেওয়া হয়েছে")
                    }
                }
                if (!boosted && info.duration > 0 && pos > info.duration * 500) {
                    boosted = true; boost(info, 2.0)
                }
                n++
                if (n % 20 == 0) saveProgress()
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    // ---------- ফিড ----------
    fun load(query: String?) = viewModelScope.launch {
        loading = true; error = null
        try {
            feed = filterBlocked(if (query.isNullOrBlank()) YtRepository.trending() else YtRepository.search(query))
        } catch (e: Exception) { error = e.message ?: "লোড করা যায়নি" }
        loading = false
    }

    fun loadForYou() = viewModelScope.launch {
        loading = true; error = null
        try {
            val interests = dao.interests()
            val topics = Recommender.top(interests, 8).map { it.first }
                .filter { it.startsWith("kw:") }.take(3).map { it.removePrefix("kw:") }
            val pools = coroutineScope {
                val jobs = listOf(async { runCatching { YtRepository.trending() }.getOrDefault(emptyList()) }) +
                        topics.map { q -> async { runCatching { YtRepository.search(q) }.getOrDefault(emptyList()) } }
                jobs.awaitAll()
            }
            val merged = pools.flatten().distinctBy { it.url }
            feed = filterBlocked(Recommender.rank(merged, interests, dao.watchedUrls().toSet()))
            if (feed.isEmpty()) error = "কোনো ভিডিও পাওয়া যায়নি"
        } catch (e: Exception) { error = e.message ?: "লোড করা যায়নি" }
        loading = false
    }

    fun loadSubsFeed() = viewModelScope.launch {
        val list = subs.value.take(10)
        if (list.isEmpty()) { subsFeed = emptyList(); return@launch }
        loading = true
        val lists = list.map { s ->
            async { runCatching { YtRepository.channel(s.url).videos.take(4) }.getOrDefault(emptyList()) }
        }.awaitAll()
        val max = lists.maxOfOrNull { it.size } ?: 0
        subsFeed = filterBlocked((0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } })
        loading = false
    }

    // ---------- প্লেব্যাক ----------
    fun play(url: String) { queue = emptyList(); startPlay(url) }

    fun playQueue(urls: List<String>, index: Int) {
        if (urls.isEmpty()) return
        queue = urls; queueIndex = index.coerceIn(0, urls.lastIndex)
        startPlay(urls[queueIndex])
    }

    private fun startPlay(url: String) = viewModelScope.launch {
        error = null; opening = true
        saveProgress()
        try {
            val info = YtRepository.streamInfo(url)
            val h = dao.historyOf(info.url)
            val start = if (h != null && h.positionMs > 5_000 &&
                (info.duration <= 0 || h.positionMs < info.duration * 1000 - 15_000)) h.positionMs else 0L

            local = null; current = info; boosted = false; segments = emptyList()
            quality = defaultQuality(YtRepository.heights(info))
            heights = YtRepository.heights(info)
            related = filterBlocked(YtRepository.related(info))
            likes = info.likeCount.takeIf { it >= 0 }; dislikes = null

            captions = YtRepository.captions(info)
            val lang = prefs.captionLang
            captionIndex = if (lang.isEmpty()) -1 else captions.indexOfFirst { it.languageTag == lang }

            player.setMediaSource(
                YtRepository.buildSource(info, if (quality == 0) 1080 else quality, captions.getOrNull(captionIndex), audioOnly), start
            )
            applyTextSelection()
            player.prepare(); player.playWhenReady = true
            expanded = true

            if (castName != null) castLoad(info, start)
            writeHistory(info, start)
            boost(info, 1.0)
            loadComments(url)
            if (sponsorOn) loadSegments(info.id)
            if (rydOn) loadRyd(info.id)
        } catch (e: Exception) { error = e.message ?: "ভিডিও চালানো যায়নি"; toast(error!!) }
        opening = false
    }

    fun playLocal(d: DownloadEntity) {
        saveProgress()
        queue = emptyList()
        current = null; local = d; related = emptyList(); comments = emptyList(); heights = emptyList()
        likes = null; dislikes = null; segments = emptyList(); captions = emptyList(); captionIndex = -1
        applyTextSelection()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(d.path))))
        player.prepare(); player.playWhenReady = true
        expanded = true
    }

    private fun rebuild(info: StreamInfo, pos: Long) {
        player.setMediaSource(
            YtRepository.buildSource(info, if (quality == 0) 1080 else quality, captions.getOrNull(captionIndex), audioOnly), pos
        )
        applyTextSelection()
        player.prepare()
    }

    private fun applyTextSelection() {
        val tag = captions.getOrNull(captionIndex)?.languageTag
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, tag == null)
            .apply { if (tag != null) setPreferredTextLanguage(tag) }
            .build()
    }

    fun selectQuality(h: Int) {
        val info = current ?: return
        quality = h
        rebuild(info, player.currentPosition)
    }

    fun chooseCaption(i: Int) {
        val info = current ?: return
        captionIndex = i
        prefs.captionLang = captions.getOrNull(i)?.languageTag ?: ""
        rebuild(info, player.currentPosition)
    }

    fun chooseCaptionSize(i: Int) { captionSizeIndex = i; prefs.captionSize = i }
    fun chooseSpeed(s: Float) { speed = s; player.setPlaybackSpeed(s) }

    // ---------- ডিফল্ট কোয়ালিটি, লুপ, অডিও-অনলি, স্লিপ টাইমার ----------
    private fun defaultQuality(available: List<Int>): Int {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val target = if (cm.isActiveNetworkMetered) qualityMobile else qualityWifi
        if (target == 0 || available.isEmpty()) return 0
        return available.filter { it <= target }.maxOrNull() ?: available.minOrNull() ?: 0
    }

    fun chooseDefaultQuality(wifi: Boolean, h: Int) {
        if (wifi) { qualityWifi = h; prefs.qualityWifi = h } else { qualityMobile = h; prefs.qualityMobile = h }
    }

    fun toggleLoop(v: Boolean) {
        loop = v
        player.repeatMode = if (v) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun toggleSkipSilence(v: Boolean) { skipSilence = v; prefs.skipSilence = v; player.skipSilenceEnabled = v }

    fun toggleAudioOnly(v: Boolean) {
        audioOnly = v; prefs.audioOnly = v
        current?.let { rebuild(it, player.currentPosition) }
    }

    /** min: 0 = বন্ধ, -1 = ভিডিও শেষে, নাহলে মিনিট */
    fun startSleep(min: Int) {
        when {
            min == 0 -> { sleepEnd = 0; sleepAtEnd = false }
            min < 0 -> { sleepEnd = 0; sleepAtEnd = true }
            else -> { sleepAtEnd = false; sleepEnd = System.currentTimeMillis() + min * 60_000L }
        }
    }

    // ---------- সার্চ ও লিংক ----------
    fun openSearch() { searching = true; showSuggestions = true; suggestions = emptyList() }
    fun closeSearch() { searching = false; showSuggestions = false; query = ""; loadForYou() }
    fun reopenSuggestions() { showSuggestions = true }

    fun onQuery(q: String) {
        query = q; showSuggestions = true
        suggestJob?.cancel()
        if (q.isBlank()) { suggestions = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = YtRepository.suggestions(q)
        }
    }

    fun submitSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        query = t; showSuggestions = false
        searchHistory = (listOf(t) + searchHistory.filter { it != t }).take(20)
        prefs.searchHistory = searchHistory
        load(t)
    }

    fun removeHistory(q: String) {
        searchHistory = searchHistory.filter { it != q }
        prefs.searchHistory = searchHistory
    }

    /** YouTube লিংক/শেয়ার থেকে খোলা */
    fun openLink(raw: String) {
        val url = raw.trim()
        val path = Uri.parse(url).path ?: ""
        if (path.startsWith("/channel/") || path.startsWith("/c/") || path.startsWith("/user/") || path.startsWith("/@"))
            openChannel(url)
        else play(url)
    }

    // ---------- ব্লক / আগ্রহ নেই ----------
    private fun filterBlocked(list: List<VideoItem>, extra: String? = null): List<VideoItem> {
        val set = blocks.value.map { it.entry }.toSet() + listOfNotNull(extra)
        return if (set.isEmpty()) list else list.filter { Blocks.allowed(set, it.title, it.channel, it.url) }
    }

    private fun addBlock(entry: String) = viewModelScope.launch {
        dao.addBlock(BlockEntity(entry))
        feed = filterBlocked(feed, entry); subsFeed = filterBlocked(subsFeed, entry); related = filterBlocked(related, entry)
        toast("লুকানো হয়েছে")
    }

    fun notInterested(v: VideoItem) {
        addBlock("vid:${v.url}")
        viewModelScope.launch { Recommender.boost(dao, v.title, v.channel, -1.5) }
    }
    fun blockChannel(name: String) { if (name.isNotBlank()) addBlock("ch:${name.lowercase()}") }
    fun blockKeyword(k: String) { val t = k.trim().lowercase(); if (t.length >= 2) addBlock("kw:$t") }
    fun removeBlock(entry: String) = viewModelScope.launch { dao.removeBlock(entry) }

    // ---------- নতুন ভিডিওর নোটিফিকেশন ও DeArrow ----------
    fun toggleNotify(v: Boolean) {
        notifyOn = v; prefs.notifyEnabled = v
        NotifyScheduler.apply(ctx, v, notifyHours, replace = true)
    }
    fun chooseNotifyHours(h: Int) {
        notifyHours = h; prefs.notifyHours = h
        NotifyScheduler.apply(ctx, notifyOn, h, replace = true)
    }
    fun checkNewNow() {
        NotifyScheduler.runNow(ctx)
        toast("চেক শুরু হয়েছে। প্রথমবার শুধু বর্তমান ভিডিওগুলো \"দেখা\" হিসেবে ধরা হয়।")
    }
    fun toggleDearrowTitles(v: Boolean) { DeArrow.titles = v; prefs.dearrowTitles = v }
    fun toggleDearrowThumbs(v: Boolean) { DeArrow.thumbs = v; prefs.dearrowThumbs = v }

    fun expand() { if (active) expanded = true }
    fun minimize() { expanded = false }
    fun close() {
        saveProgress()
        queue = emptyList()
        player.stop(); player.clearMediaItems()
        current = null; local = null; expanded = false
    }

    // ---------- Chromecast ----------
    private fun castClient(): RemoteMediaClient? = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    private fun onCastConnected(s: CastSession) {
        castName = s.castDevice?.friendlyName ?: "Cast"
        val client = s.remoteMediaClient ?: return
        client.registerCallback(object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() { castPlaying = client.isPlaying }
        })
        current?.let { castLoad(it, player.currentPosition) }
    }

    private fun castLoad(info: StreamInfo, posMs: Long) {
        val client = castClient() ?: return
        val stream = YtRepository.castStream(info)
        if (stream == null) { toast("এই ভিডিও কাস্ট করা যাবে না"); return }
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        meta.putString(MediaMetadata.KEY_TITLE, info.name)
        info.thumbnails.lastOrNull()?.url?.let { meta.addImage(WebImage(Uri.parse(it))) }
        val mi = MediaInfo.Builder(stream.content)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(stream.format?.mimeType ?: "video/mp4")
            .setMetadata(meta).build()
        client.load(
            MediaLoadRequestData.Builder().setMediaInfo(mi).setAutoplay(true).setCurrentTime(posMs).build()
        )
        player.pause()
    }

    fun showCastChooser(activityCtx: Context) {
        val sel = castContext?.mergedSelector ?: return
        MediaRouteChooserDialog(activityCtx).apply { routeSelector = sel }.show()
    }
    fun castToggle() { castClient()?.togglePlayback() }
    fun castStop() { castContext?.sessionManager?.endCurrentSession(true) }

    // ---------- হিস্টরি ও রিকমেন্ডেশন ----------
    private fun writeHistory(info: StreamInfo, pos: Long) = viewModelScope.launch {
        dao.upsertHistory(
            HistoryEntity(
                url = info.url, title = info.name, channel = info.uploaderName ?: "",
                channelUrl = info.uploaderUrl, thumb = info.thumbnails.lastOrNull()?.url,
                durationSec = info.duration, positionMs = pos, watchedAt = System.currentTimeMillis()
            )
        )
    }

    private fun saveProgress() {
        val info = current ?: return
        val pos = player.currentPosition
        if (pos > 0) writeHistory(info, pos)
    }

    private fun boost(info: StreamInfo, weight: Double) = viewModelScope.launch {
        Recommender.boost(dao, info.name, info.uploaderName ?: "", weight)
    }

    fun loadInterests() = viewModelScope.launch { topInterests = Recommender.top(dao.interests(), 12) }
    fun resetProfile() = viewModelScope.launch { dao.clearInterests(); topInterests = emptyList(); toast("প্রোফাইল রিসেট হয়েছে") }
    fun clearHistory() = viewModelScope.launch { dao.clearHistory(); toast("ইতিহাস মুছে গেছে") }

    // ---------- ফেভারিট ও প্লেলিস্ট ----------
    fun toggleFavorite() {
        val info = current ?: return
        viewModelScope.launch {
            if (favorites.value.any { it.url == info.url }) dao.removeFavorite(info.url)
            else dao.addFavorite(
                FavoriteEntity(
                    info.url, info.name, info.uploaderName ?: "", info.thumbnails.lastOrNull()?.url,
                    info.duration, System.currentTimeMillis()
                )
            )
        }
    }

    fun playlistItems(id: Long): Flow<List<PlaylistItemEntity>> = dao.playlistItems(id)

    fun createPlaylist(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.addPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
    }

    fun createPlaylistAndAdd(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = dao.addPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        addToPlaylist(id)
    }

    fun addToPlaylist(pid: Long) {
        val info = current ?: return
        viewModelScope.launch {
            if (dao.countItem(pid, info.url) > 0) { toast("আগে থেকেই আছে"); return@launch }
            dao.addPlaylistItem(
                PlaylistItemEntity(
                    playlistId = pid, url = info.url, title = info.name, channel = info.uploaderName ?: "",
                    thumb = info.thumbnails.lastOrNull()?.url, durationSec = info.duration,
                    addedAt = System.currentTimeMillis()
                )
            )
            toast("প্লেলিস্টে যোগ হয়েছে")
        }
    }

    fun deletePlaylist(id: Long) = viewModelScope.launch { dao.deletePlaylistItems(id); dao.deletePlaylist(id) }
    fun removeFromPlaylist(itemId: Long) = viewModelScope.launch { dao.removePlaylistItem(itemId) }

    // ---------- SponsorBlock / RYD ----------
    private fun loadSegments(videoId: String) = viewModelScope.launch { segments = Extras.sponsorSegments(videoId) }
    private fun loadRyd(videoId: String) = viewModelScope.launch {
        Extras.ryd(videoId)?.let { likes = it.first; dislikes = it.second }
    }

    fun chooseTheme(i: Int) { themeIndex = i; prefs.theme = i }
    fun toggleSponsor(v: Boolean) {
        sponsorOn = v; prefs.sponsorBlock = v
        if (!v) segments = emptyList() else current?.let { loadSegments(it.id) }
    }
    fun toggleRyd(v: Boolean) {
        rydOn = v; prefs.ryd = v
        if (v) current?.let { loadRyd(it.id) } else { dislikes = null; likes = current?.likeCount?.takeIf { it >= 0 } }
    }

    // ---------- মন্তব্য ----------
    private fun loadComments(url: String) = viewModelScope.launch {
        commentsLoading = true; comments = emptyList(); commentsTotal = 0
        runCatching { YtRepository.comments(url) }.onSuccess { (n, list) -> commentsTotal = n; comments = list }
        commentsLoading = false
    }

    // ---------- চ্যানেল ও সাবস্ক্রিপশন ----------
    fun openChannel(url: String?) {
        if (url == null) return
        expanded = false; channelOpen = true; channel = null
        viewModelScope.launch {
            channelLoading = true
            try { channel = YtRepository.channel(url) } catch (e: Exception) { error = e.message }
            channelLoading = false
        }
    }
    fun closeChannel() { channelOpen = false }

    fun toggleSub(url: String, name: String, avatar: String?) = viewModelScope.launch {
        if (subs.value.any { it.url == url }) dao.removeSub(url) else dao.addSub(SubEntity(url, name, avatar))
    }

    /** NewPipe-এর "সাবস্ক্রিপশন এক্সপোর্ট" JSON ফাইল ইম্পোর্ট */
    fun importNewPipe(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            val arr = JSONObject(text).getJSONArray("subscriptions")
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optInt("service_id", 0) == 0) {
                    dao.addSub(SubEntity(o.getString("url"), o.optString("name", "?"), null)); n++
                }
            }
            toast("$n টি চ্যানেল ইম্পোর্ট হয়েছে")
        } catch (e: Exception) { toast("ইম্পোর্ট ব্যর্থ: ${e.message}") }
    }

    // ---------- প্রোফাইল এক্সপোর্ট / ইম্পোর্ট ----------
    fun exportProfile(uri: Uri) = viewModelScope.launch {
        try {
            val root = JSONObject()
            root.put("app", "vidplay"); root.put("version", 1)
            root.put("subs", JSONArray().also { a ->
                dao.subsOnce().forEach { a.put(JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar)) }
            })
            root.put("interests", JSONArray().also { a ->
                dao.interests().forEach {
                    a.put(JSONObject().put("topic", it.topic).put("score", it.score).put("updatedAt", it.updatedAt))
                }
            })
            root.put("history", JSONArray().also { a ->
                dao.historyOnce().forEach {
                    a.put(
                        JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                            .put("channelUrl", it.channelUrl).put("thumb", it.thumb)
                            .put("durationSec", it.durationSec).put("positionMs", it.positionMs)
                            .put("watchedAt", it.watchedAt)
                    )
                }
            })
            root.put("favorites", JSONArray().also { a ->
                dao.favoritesOnce().forEach {
                    a.put(
                        JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                            .put("thumb", it.thumb).put("durationSec", it.durationSec).put("addedAt", it.addedAt)
                    )
                }
            })
            root.put("blocks", JSONArray(dao.blocksOnce().map { it.entry }))
            val byList = dao.allPlaylistItems().groupBy { it.playlistId }
            root.put("playlists", JSONArray().also { a ->
                dao.playlistsOnce().forEach { p ->
                    val items = JSONArray()
                    byList[p.id].orEmpty().forEach {
                        items.put(
                            JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                                .put("thumb", it.thumb).put("durationSec", it.durationSec).put("addedAt", it.addedAt)
                        )
                    }
                    a.put(JSONObject().put("name", p.name).put("createdAt", p.createdAt).put("items", items))
                }
            })
            root.put(
                "prefs", JSONObject().put("theme", themeIndex).put("sponsorblock", sponsorOn).put("ryd", rydOn)
                    .put("captionSize", captionSizeIndex).put("captionLang", prefs.captionLang)
                    .put("qualityWifi", qualityWifi).put("qualityMobile", qualityMobile).put("skipSilence", skipSilence)
            )
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(root.toString(2)) }
            }
            toast("প্রোফাইল এক্সপোর্ট হয়েছে")
        } catch (e: Exception) { toast("এক্সপোর্ট ব্যর্থ: ${e.message}") }
    }

    fun importProfile(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            val root = JSONObject(text)
            if (root.optString("app") != "vidplay") { toast("এটি VidPlay-এর প্রোফাইল ফাইল নয়"); return@launch }
            fun s(o: JSONObject, k: String): String? = o.optString(k).takeIf { it.isNotEmpty() && it != "null" }

            root.optJSONArray("subs")?.objects()?.forEach {
                dao.addSub(SubEntity(it.getString("url"), it.optString("name", "?"), s(it, "avatar")))
            }
            root.optJSONArray("interests")?.objects()?.let { list ->
                dao.upsertInterests(list.map { InterestEntity(it.getString("topic"), it.optDouble("score", 0.0), it.optLong("updatedAt")) })
            }
            root.optJSONArray("history")?.objects()?.forEach {
                dao.upsertHistory(
                    HistoryEntity(
                        it.getString("url"), it.optString("title"), it.optString("channel"), s(it, "channelUrl"),
                        s(it, "thumb"), it.optLong("durationSec"), it.optLong("positionMs"), it.optLong("watchedAt")
                    )
                )
            }
            root.optJSONArray("blocks")?.let { a -> for (i in 0 until a.length()) dao.addBlock(BlockEntity(a.getString(i))) }
            root.optJSONArray("favorites")?.objects()?.forEach {
                dao.addFavorite(
                    FavoriteEntity(
                        it.getString("url"), it.optString("title"), it.optString("channel"), s(it, "thumb"),
                        it.optLong("durationSec"), it.optLong("addedAt")
                    )
                )
            }
            root.optJSONArray("playlists")?.objects()?.forEach { p ->
                val name = p.getString("name")
                val pid = dao.playlistIdByName(name)
                    ?: dao.addPlaylist(PlaylistEntity(name = name, createdAt = p.optLong("createdAt")))
                p.optJSONArray("items")?.objects()?.forEach {
                    val url = it.getString("url")
                    if (dao.countItem(pid, url) == 0) dao.addPlaylistItem(
                        PlaylistItemEntity(
                            playlistId = pid, url = url, title = it.optString("title"), channel = it.optString("channel"),
                            thumb = s(it, "thumb"), durationSec = it.optLong("durationSec"), addedAt = it.optLong("addedAt")
                        )
                    )
                }
            }
            root.optJSONObject("prefs")?.let {
                chooseTheme(it.optInt("theme", themeIndex))
                toggleSponsor(it.optBoolean("sponsorblock", sponsorOn))
                toggleRyd(it.optBoolean("ryd", rydOn))
                chooseCaptionSize(it.optInt("captionSize", captionSizeIndex))
                chooseDefaultQuality(true, it.optInt("qualityWifi", qualityWifi))
                chooseDefaultQuality(false, it.optInt("qualityMobile", qualityMobile))
                toggleSkipSilence(it.optBoolean("skipSilence", skipSilence))
                prefs.captionLang = it.optString("captionLang", "")
            }
            toast("প্রোফাইল ইম্পোর্ট হয়েছে")
            loadInterests()
        } catch (e: Exception) { toast("ইম্পোর্ট ব্যর্থ: ${e.message}") }
    }

    // ---------- ডাউনলোড (কিউ, থামানো/চালু, মার্জ) ----------
    fun downloadOptions(): List<DlOption> = current?.let { Downloads.options(it) } ?: emptyList()

    fun enqueueDownload(o: DlOption) {
        val info = current ?: return
        viewModelScope.launch {
            try {
                Downloads.enqueue(ctx, dao, info, o)
                toast("ডাউনলোড কিউতে যোগ হয়েছে")
            } catch (ex: Exception) { toast(ex.message ?: "ডাউনলোড ব্যর্থ") }
        }
    }

    fun pauseDownload(id: Long) = viewModelScope.launch { DownloadEngine.pause(dao, id) }
    fun resumeDownload(id: Long) = viewModelScope.launch { DownloadEngine.resume(ctx, dao, id) }
    fun pauseAllDownloads() = viewModelScope.launch { DownloadEngine.pauseAll(dao) }
    fun resumeAllDownloads() = viewModelScope.launch { DownloadEngine.resumeAll(ctx, dao) }

    fun deleteDownload(d: DownloadEntity) = viewModelScope.launch {
        if (local?.id == d.id) close()
        DownloadEngine.remove(dao, d)
    }
}
