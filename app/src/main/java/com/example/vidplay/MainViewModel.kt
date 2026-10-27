package com.example.vidplay

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dao = AppDb.get(app).dao()
    private val prefs = Prefs(app)
    val player = PlayerHolder.get(app)

    // Room
    val subs = dao.subs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = dao.downloads().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // সেটিংস
    var themeIndex by mutableStateOf(prefs.theme); private set
    var sponsorOn by mutableStateOf(prefs.sponsorBlock); private set
    var rydOn by mutableStateOf(prefs.ryd); private set

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

    private var segments: List<Segment> = emptyList()
    private var boosted = false

    val active get() = current != null || local != null
    val title get() = current?.name ?: local?.title ?: ""
    val channelName get() = current?.uploaderName ?: ""

    init {
        loadForYou()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) saveProgress()
            }
            override fun onPlayerError(e: PlaybackException) { error = e.message ?: "প্লেব্যাক এরর" }
        })
        // প্রতি ০.৫ সেকেন্ডে: SponsorBlock স্কিপ; প্রতি ১০ সেকেন্ডে: হিস্টরি সেভ
        viewModelScope.launch {
            var n = 0
            while (true) {
                delay(500)
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
            feed = if (query.isNullOrBlank()) YtRepository.trending() else YtRepository.search(query)
        } catch (e: Exception) { error = e.message ?: "লোড করা যায়নি" }
        loading = false
    }

    /** "আপনার জন্য": ট্রেন্ডিং + আপনার শীর্ষ আগ্রহের সার্চ, তারপর অন-ডিভাইস র‍্যাংকিং */
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
            feed = Recommender.rank(merged, interests, dao.watchedUrls().toSet())
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
        subsFeed = (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
        loading = false
    }

    // ---------- প্লেব্যাক ----------
    fun play(url: String) = viewModelScope.launch {
        error = null; opening = true
        saveProgress()
        try {
            val info = YtRepository.streamInfo(url)
            val h = dao.historyOf(info.url)
            val start = if (h != null && h.positionMs > 5_000 &&
                (info.duration <= 0 || h.positionMs < info.duration * 1000 - 15_000)) h.positionMs else 0L

            local = null; current = info; quality = 0; boosted = false; segments = emptyList()
            heights = YtRepository.heights(info)
            related = YtRepository.related(info)
            likes = info.likeCount.takeIf { it >= 0 }; dislikes = null

            player.setMediaSource(YtRepository.buildSource(info), start)
            player.prepare(); player.playWhenReady = true
            expanded = true

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
        current = null; local = d; related = emptyList(); comments = emptyList(); heights = emptyList()
        likes = null; dislikes = null; segments = emptyList()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(d.path))))
        player.prepare(); player.playWhenReady = true
        expanded = true
    }

    fun selectQuality(h: Int) {
        val info = current ?: return
        quality = h
        val pos = player.currentPosition
        player.setMediaSource(YtRepository.buildSource(info, if (h == 0) 1080 else h), pos)
        player.prepare()
    }

    fun chooseSpeed(s: Float) { speed = s; player.setPlaybackSpeed(s) }

    fun expand() { if (active) expanded = true }
    fun minimize() { expanded = false }
    fun close() {
        saveProgress()
        player.stop(); player.clearMediaItems()
        current = null; local = null; expanded = false
    }

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

    // ---------- ডাউনলোড ----------
    fun download(audioOnly: Boolean) {
        val info = current ?: return
        viewModelScope.launch {
            try {
                val e = withContext(Dispatchers.IO) { Downloads.enqueue(ctx, info, audioOnly) }
                dao.addDownload(e)
                toast("ডাউনলোড শুরু হয়েছে")
            } catch (ex: Exception) { toast(ex.message ?: "ডাউনলোড ব্যর্থ") }
        }
    }

    fun deleteDownload(d: DownloadEntity) = viewModelScope.launch {
        if (local?.id == d.id) close()
        withContext(Dispatchers.IO) { Downloads.remove(ctx, d) }
        dao.removeDownload(d)
    }
}
