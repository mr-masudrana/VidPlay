package com.tubex.app

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
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
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


data class QItem(val url: String, val title: String, val channel: String, val thumb: String?)

data class ReplyState(
    val items: List<CommentItem> = emptyList(), val next: Page? = null,
    val loading: Boolean = false, val expanded: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dao = AppDb.get(app).dao()
    private val prefs = Prefs(app)
    val player = PlayerHolder.get(app)
    /** কন্ট্রোলারে পরের/আগের বাটন দেখাতে এটা PlayerView-তে দিতে হয় */
    val controllerPlayer: Player get() = PlayerHolder.navPlayer(app)

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
    var captionColorIndex by mutableStateOf(prefs.captionColor); private set
    var captionBgIndex by mutableStateOf(prefs.captionBg); private set
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
    /** নতুন ভিডিওর তথ্য আসার আগ পর্যন্ত true: প্লেয়ার পেজ স্কেলিটনসহ সাথে সাথে খোলে */
    var loadingNew by mutableStateOf(false); private set
    var loadingHint by mutableStateOf<VideoItem?>(null); private set
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
    var resizeMode by mutableStateOf(prefs.resizeMode); private set
    var autoplay by mutableStateOf(prefs.autoplay); private set
    private val backStack = kotlin.collections.ArrayDeque<String>()   // আগে দেখা ভিডিও ("আগের" বাটনের জন্য)
    private val sessionPlayed = HashSet<String>()                      // এই সেশনে চালানো ভিডিও (অটোপ্লে লুপ ঠেকাতে)
    private var goingBack = false
    var likes by mutableStateOf<Long?>(null); private set
    var dislikes by mutableStateOf<Long?>(null); private set
    var captions by mutableStateOf<List<SubtitlesStream>>(emptyList()); private set
    var captionIndex by mutableStateOf(-1); private set     // -1 = বন্ধ

    // মন্তব্য
    var comments by mutableStateOf<List<CommentItem>>(emptyList()); private set
    var commentsTotal by mutableStateOf(0); private set
    var commentsLoading by mutableStateOf(false); private set
    var commentsLoadingMore by mutableStateOf(false); private set
    var commentsMore by mutableStateOf(false); private set
    var commentSort by mutableStateOf(0); private set      // 0 শীর্ষ, 1 নতুন, 2 পুরনো
    var replyStates by mutableStateOf<Map<String, ReplyState>>(emptyMap()); private set
    private var commentsUrl = ""
    private var commentsNextPage: Page? = null

    // চ্যানেল
    var channelOpen by mutableStateOf(false); private set
    var channel by mutableStateOf<ChannelData?>(null); private set
    var channelLoading by mutableStateOf(false); private set

    // রিকমেন্ডেশন ড্যাশবোর্ড
    var topInterests by mutableStateOf<List<Pair<String, Double>>>(emptyList()); private set
    var contextInterests by mutableStateOf<List<Pair<String, Double>>>(emptyList()); private set
    var langMode by mutableStateOf(prefs.lang); private set
    var floatingMini by mutableStateOf(prefs.floatMini); private set
    var pipX by mutableStateOf(Float.NaN)       // ভাসমান প্লেয়ারের অবস্থান (NaN = ডিফল্ট কোণ)
    var pipY by mutableStateOf(Float.NaN)

    // Chromecast
    var castAvailable by mutableStateOf(false); private set
    var castName by mutableStateOf<String?>(null); private set
    var castPlaying by mutableStateOf(false); private set
    private var castContext: CastContext? = null

    private var segments: List<Segment> = emptyList()
    private var milestone = 0
    var queueItems by mutableStateOf<List<QItem>>(emptyList()); private set
    var queueIndex by mutableStateOf(0); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableStateOf(0); private set          // 0 বন্ধ, 1 কিউ, 2 একটি ভিডিও
    private val playedInQueue = HashSet<String>()

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
        L.apply(prefs.lang)
        DeArrow.titles = prefs.dearrowTitles; DeArrow.thumbs = prefs.dearrowThumbs
        viewModelScope.launch { if (!DownloadEngine.isActive) dao.resetInterrupted() }
        loadForYou()
        warmUp()
        PlayerHolder.navigator = object : Navigator {
            override fun canNext() = this@MainViewModel.canNext()
            override fun canPrev() = this@MainViewModel.canPrev()
            override fun next() { playNextVideo() }
            override fun prev() { playPreviousVideo() }
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) saveProgress()
            }
            override fun onPlaybackStateChanged(state: Int) {
                // প্লেলিস্ট/কিউ: শেষ হলে পরেরটা
                if (state == Player.STATE_ENDED && sleepAtEnd) {
                    sleepAtEnd = false; toast(L.t("স্লিপ টাইমার: প্লেব্যাক বন্ধ")); return
                }
                if (state == Player.STATE_ENDED && !advanceQueue() && autoplay) playRelatedNext()
            }
            override fun onPlayerError(e: PlaybackException) { error = e.message ?: L.t("প্লেব্যাক এরর") }
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
                    sleepEnd = 0; player.pause(); castClient()?.pause(); toast(L.t("স্লিপ টাইমার: প্লেব্যাক বন্ধ"))
                }
                if (active) { positionMs = player.currentPosition; durationMs = player.duration.coerceAtLeast(0L) }
                val info = current
                if (info == null || !player.isPlaying) continue
                val pos = player.currentPosition
                if (sponsorOn) {
                    segments.firstOrNull { pos >= it.startMs && pos < it.endMs - 400 }?.let {
                        player.seekTo(it.endMs)
                        toast(L.t("স্পন্সর অংশ বাদ দেওয়া হয়েছে"))
                    }
                }
                if (info.duration > 0) {
                    val f = pos / (info.duration * 1000.0)
                    if (milestone < 1 && f > 0.5) { milestone = 1; boost(info, 1.5) }
                    if (milestone < 2 && f > 0.9) { milestone = 2; boost(info, 1.0) }
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
        } catch (e: Exception) { error = e.message ?: L.t("লোড করা যায়নি") }
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
            feed = filterBlocked(
                Recommender.rank(
                    merged, interests, dao.watchedUrls().toSet(), history.value,
                    subs.value.map { it.name.lowercase() }.toSet()
                )
            )
            if (feed.isEmpty()) error = L.t("কোনো ভিডিও পাওয়া যায়নি")
        } catch (e: Exception) { error = e.message ?: L.t("লোড করা যায়নি") }
        loading = false
    }

    fun loadSubsFeed(force: Boolean = false) = viewModelScope.launch {
        val list = subs.value.take(10)
        if (list.isEmpty()) { subsFeed = emptyList(); return@launch }
        val key = list.map { it.url }.hashCode()
        val cached = withContext(Dispatchers.IO) { SubsCache.load(ctx) }
        val sameSet = cached != null && cached.key == key
        if (cached != null && sameSet && subsFeed.isEmpty()) subsFeed = filterBlocked(cached.items)   // ক্যাশ থেকে সাথে সাথে
        if (!force && cached != null && sameSet && System.currentTimeMillis() - cached.time < 30 * 60_000L) return@launch

        loading = true
        val lists = list.map { sub ->
            async { runCatching { YtRepository.channel(sub.url).videos.take(4) }.getOrDefault(emptyList()) }
        }.awaitAll()
        val max = lists.maxOfOrNull { it.size } ?: 0
        val merged = (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
        if (merged.isNotEmpty()) {
            subsFeed = filterBlocked(merged)
            withContext(Dispatchers.IO) { SubsCache.save(ctx, key, merged) }
        }
        loading = false
    }

    // ---------- প্লেব্যাক ----------
    fun play(url: String) { clearQueue(); startPlay(url) }

    fun playQueueItems(items: List<QItem>, index: Int, shuffleStart: Boolean = false) {
        if (items.isEmpty()) return
        queueItems = items; playedInQueue.clear()
        if (shuffleStart) { shuffle = true; queueIndex = items.indices.random() }
        else queueIndex = index.coerceIn(0, items.lastIndex)
        startPlay(items[queueIndex].url)
    }

    private fun clearQueue() { queueItems = emptyList(); queueIndex = 0; playedInQueue.clear() }

    /** ভিডিও শেষে: শাফল/রিপিট অনুযায়ী কিউর পরের আইটেম */
    private fun advanceQueue(): Boolean {
        if (queueItems.isEmpty()) return false
        val n = queueItems.size
        queueItems.getOrNull(queueIndex)?.let { playedInQueue.add(it.url) }
        val next: Int = if (shuffle) {
            val remaining = queueItems.indices.filter { queueItems[it].url !in playedInQueue && it != queueIndex }
            if (remaining.isNotEmpty()) remaining.random()
            else if (repeat == 1) {
                playedInQueue.clear()
                queueItems.indices.filter { it != queueIndex }.randomOrNull() ?: queueIndex
            } else -1
        } else if (queueIndex + 1 < n) queueIndex + 1
        else if (repeat == 1) 0
        else -1
        if (next >= 0) { queueIndex = next; startPlay(queueItems[next].url); return true }
        return false
    }

    // ---------- পরের / আগের ভিডিও, অটোপ্লে ----------
    /** কিউতে পরের আছে, অথবা সম্পর্কিত ভিডিওর তালিকায় এখনো না-চালানো কিছু আছে */
    fun canNext(): Boolean = local == null && current != null &&
            ((queueItems.isNotEmpty() && (queueIndex + 1 < queueItems.size || shuffle || repeat == 1)) ||
                    related.any { it.url !in sessionPlayed && it.url != current?.url })

    fun canPrev(): Boolean = (queueItems.isNotEmpty() && queueIndex > 0) || backStack.isNotEmpty()

    /** অটোপ্লে: কিউ শেষ হলে বা কিউ না থাকলে সম্পর্কিত ভিডিওর প্রথম নতুনটা */
    private fun playRelatedNext(): Boolean {
        val v = related.firstOrNull { it.url !in sessionPlayed && it.url != current?.url } ?: return false
        clearQueue()
        startPlay(v.url)
        return true
    }

    fun playNextVideo(): Boolean = advanceQueue() || playRelatedNext()

    /** ৩ সেকেন্ডের বেশি চলে থাকলে শুরু থেকে; নইলে আগের ভিডিও (কিউ বা দেখার ইতিহাস থেকে) */
    fun playPreviousVideo() {
        if (player.currentPosition > 3_000 || !canPrev()) { player.seekTo(0); return }
        if (queueItems.isNotEmpty() && queueIndex > 0) {
            queueIndex -= 1; startPlay(queueItems[queueIndex].url); return
        }
        val prev = backStack.removeLastOrNull() ?: run { player.seekTo(0); return }
        goingBack = true
        startPlay(prev)
    }

    /** কার্ডের মেনু থেকে: পরে চালান (playNext) বা কিউর শেষে যোগ */
    fun addToQueue(v: VideoItem, playNext: Boolean) {
        val item = QItem(v.url, v.title, v.channel, v.thumb)
        if (!active) { playQueueItems(listOf(item), 0); return }
        if (queueItems.isEmpty()) {
            val cur = current
            queueItems = if (cur != null)
                listOf(QItem(cur.url, cur.name, cur.uploaderName ?: "", cur.thumbnails.lastOrNull()?.url))
            else emptyList()
            queueIndex = 0
        }
        val at = if (playNext) (queueIndex + 1).coerceIn(0, queueItems.size) else queueItems.size
        queueItems = queueItems.toMutableList().also { it.add(at, item) }
        toast(L.t("কিউতে যোগ হয়েছে"))
    }

    fun queuePlayAt(i: Int) {
        val q = queueItems.getOrNull(i) ?: return
        queueIndex = i
        startPlay(q.url)
    }

    fun queueRemove(i: Int) {
        if (i !in queueItems.indices) return
        queueItems = queueItems.toMutableList().also { it.removeAt(i) }
        if (i < queueIndex || i == queueIndex) queueIndex -= 1
        if (queueItems.isEmpty()) queueIndex = 0
    }

    fun queueMove(i: Int, delta: Int) {
        val j = i + delta
        if (i !in queueItems.indices || j !in queueItems.indices) return
        val cur = queueItems.getOrNull(queueIndex)?.url
        queueItems = queueItems.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        if (cur != null) queueIndex = queueItems.indexOfFirst { it.url == cur }
    }

    fun queueClear() = clearQueue()
    fun toggleShuffle(v: Boolean) { shuffle = v; playedInQueue.clear() }
    fun chooseRepeat(m: Int) {
        repeat = m; loop = (m == 2)
        player.repeatMode = if (m == 2) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // ---------- দ্রুত লোডের জন্য ক্যাশ ও প্রি-ফেচ ----------
    private val infoCache = object : LinkedHashMap<String, Pair<Long, StreamInfo>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, StreamInfo>>?) = size > 8
    }
    private val inflight = ConcurrentHashMap<String, Deferred<StreamInfo>>()
    private var playJob: Job? = null
    private var playToken = 0

    /** ক্যাশে (৩০ মিনিট) থাকলে সাথে সাথে; আগে থেকে চলমান প্রি-ফেচ থাকলে তার জন্য অপেক্ষা; নইলে নতুন ফেচ */
    private suspend fun infoFor(url: String): StreamInfo {
        infoCache[url]?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000L }?.let { return it.second }
        val d = inflight.getOrPut(url) {
            viewModelScope.async {
                YtRepository.streamInfo(url).also { infoCache[url] = System.currentTimeMillis() to it }
            }.also { job -> job.invokeOnCompletion { inflight.remove(url) } }
        }
        return d.await()
    }

    private fun findHint(url: String): VideoItem? = (related + feed + subsFeed).firstOrNull { it.url == url }

    /** চলার সময়েই পরের সম্ভাব্য ভিডিওর তথ্য আগে থেকে এনে রাখা, যাতে "পরের"/অটোপ্লে প্রায় সাথে সাথে চলে */
    private fun prefetchNext() = viewModelScope.launch {
        delay(1500)
        val next = if (queueItems.isNotEmpty() && queueIndex + 1 < queueItems.size) queueItems[queueIndex + 1].url
        else related.firstOrNull { it.url !in sessionPlayed && it.url != current?.url }?.url
        if (next != null) runCatching { infoFor(next) }
    }

    /** অ্যাপ খোলার পর (শুধু আনমিটার্ড নেটে) ফিডের প্রথম ভিডিও আগে থেকে এনে রাখা: এক্সট্রাক্টর গরম হয় ও প্রথম প্লে দ্রুত হয় */
    private fun warmUp() = viewModelScope.launch {
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        repeat(12) {
            delay(1000)
            val v = feed.firstOrNull { it.durationSec > 0 }
            if (v != null) {
                if (cm?.isActiveNetworkMetered == false) runCatching { infoFor(v.url) }
                return@launch
            }
        }
    }

    private fun startPlay(url: String) {
        current?.url?.let { prev ->
            if (!goingBack && prev != url) { backStack.addLast(prev); while (backStack.size > 30) backStack.removeFirst() }
        }
        goingBack = false
        sessionPlayed.add(url)
        error = null; opening = true
        // ট্যাপ করার সাথে সাথেই প্লেয়ার পেজ খুলে স্কেলিটন দেখানো
        loadingHint = findHint(url); loadingNew = true; expanded = true
        saveProgress(); recordSkip()
        if (castName == null) player.pause()
        playJob?.cancel()
        val token = ++playToken
        playJob = viewModelScope.launch {
            try {
                val info = infoFor(url)
                sessionPlayed.add(info.url)
                val h = dao.historyOf(info.url)
                val start = if (h != null && h.positionMs > 5_000 &&
                    (info.duration <= 0 || h.positionMs < info.duration * 1000 - 15_000)) h.positionMs else 0L

                val hs = YtRepository.heights(info)
                val caps = YtRepository.captions(info)
                val lang = prefs.captionLang
                val capIdx = if (lang.isEmpty()) -1 else caps.indexOfFirst { it.languageTag == lang }
                val q = defaultQuality(hs)

                // আগে প্লেয়ার চালু, বাকি সব (সম্পর্কিত, মন্তব্য ইত্যাদি) পরে
                local = null; current = info; milestone = 0; segments = emptyList()
                quality = q; heights = hs
                captions = caps; captionIndex = capIdx
                likes = info.likeCount.takeIf { it >= 0 }; dislikes = null
                player.setMediaSource(
                    YtRepository.buildSource(info, if (q == 0) 1080 else q, caps.getOrNull(capIdx), audioOnly), start
                )
                applyTextSelection()
                player.prepare(); player.playWhenReady = true
                loadingNew = false

                related = filterBlocked(YtRepository.related(info))
                if (castName != null) castLoad(info, start)
                writeHistory(info, start)
                boost(info, 0.5)
                loadComments(url)
                if (sponsorOn) loadSegments(info.id)
                if (rydOn) loadRyd(info.id)
                prefetchNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: L.t("ভিডিও চালানো যায়নি"); toast(error!!)
                if (token == playToken && !active) expanded = false
            } finally {
                if (token == playToken) { opening = false; loadingNew = false }
            }
        }
    }

    fun playLocal(d: DownloadEntity) {
        saveProgress()
        clearQueue()
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
    fun chooseCaptionColor(i: Int) { captionColorIndex = i; prefs.captionColor = i }
    fun chooseCaptionBg(i: Int) { captionBgIndex = i; prefs.captionBg = i }
    fun chooseSpeed(s: Float) { speed = s; player.setPlaybackSpeed(s) }
    var muted by mutableStateOf(false); private set
    fun toggleMute() { muted = !muted; player.volume = if (muted) 0f else 1f }
    fun toggleAutoplay() { autoplay = !autoplay; prefs.autoplay = autoplay }
    fun cycleResize() { resizeMode = (resizeMode + 1) % 3; prefs.resizeMode = resizeMode }

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
        viewModelScope.launch { Recommender.boostQuery(dao, t, 0.8) }
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
        toast(L.t("লুকানো হয়েছে"))
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
        toast(L.t("চেক শুরু হয়েছে। প্রথমবার শুধু বর্তমান ভিডিওগুলো \"দেখা\" হিসেবে ধরা হয়।"))
    }
    fun toggleDearrowTitles(v: Boolean) { DeArrow.titles = v; prefs.dearrowTitles = v }
    fun toggleDearrowThumbs(v: Boolean) { DeArrow.thumbs = v; prefs.dearrowThumbs = v }

    fun toggleFavoriteItem(v: VideoItem) = viewModelScope.launch {
        if (favorites.value.any { it.url == v.url }) dao.removeFavorite(v.url)
        else {
            dao.addFavorite(FavoriteEntity(v.url, v.title, v.channel, v.thumb, v.durationSec, System.currentTimeMillis()))
            Recommender.boost(dao, v.title, v.channel, 3.0)
        }
    }

    fun chooseLang(m: Int) { langMode = m; prefs.lang = m; L.apply(m) }
    fun toggleFloatingMini(v: Boolean) { floatingMini = v; prefs.floatMini = v }

    fun expand() { if (active) expanded = true }
    fun minimize() { expanded = false }
    fun close() {
        saveProgress(); recordSkip()
        clearQueue()
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
        if (stream == null) { toast(L.t("এই ভিডিও কাস্ট করা যাবে না")); return }
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

    /** শুরুর ১০% এর মধ্যেই ছেড়ে গেলে স্কিপ ধরে হালকা নেগেটিভ সিগন্যাল */
    private fun recordSkip() {
        val info = current ?: return
        if (milestone == 0 && info.duration > 60 && player.currentPosition < info.duration * 100) {
            milestone = 3
            boost(info, -0.8)
        }
    }

    private fun boost(info: StreamInfo, weight: Double) = viewModelScope.launch {
        Recommender.boost(dao, info.name, info.uploaderName ?: "", weight)
    }

    fun loadInterests() = viewModelScope.launch {
        val all = dao.interests()
        topInterests = Recommender.top(all, 12)
        contextInterests = Recommender.topForBucket(all, 8)
    }
    fun resetProfile() = viewModelScope.launch { dao.clearInterests(); topInterests = emptyList(); toast(L.t("প্রোফাইল রিসেট হয়েছে")) }
    fun clearHistory() = viewModelScope.launch { dao.clearHistory(); toast(L.t("ইতিহাস মুছে গেছে")) }

    // ---------- ফেভারিট ও প্লেলিস্ট ----------
    fun toggleFavorite() {
        val info = current ?: return
        viewModelScope.launch {
            if (favorites.value.any { it.url == info.url }) {
                dao.removeFavorite(info.url)
            } else {
                dao.addFavorite(
                    FavoriteEntity(
                        info.url, info.name, info.uploaderName ?: "", info.thumbnails.lastOrNull()?.url,
                        info.duration, System.currentTimeMillis()
                    )
                )
                Recommender.boost(dao, info.name, info.uploaderName ?: "", 3.0)   // ফেভারিট = লাইক
            }
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
            if (dao.countItem(pid, info.url) > 0) { toast(L.t("আগে থেকেই আছে")); return@launch }
            dao.addPlaylistItem(
                PlaylistItemEntity(
                    playlistId = pid, url = info.url, title = info.name, channel = info.uploaderName ?: "",
                    thumb = info.thumbnails.lastOrNull()?.url, durationSec = info.duration,
                    addedAt = System.currentTimeMillis()
                )
            )
            toast(L.t("প্লেলিস্টে যোগ হয়েছে"))
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
        commentsUrl = url; commentsNextPage = null; commentsMore = false
        commentsLoading = true; comments = emptyList(); commentsTotal = 0; replyStates = emptyMap()
        runCatching { YtRepository.comments(url) }.onSuccess { p ->
            comments = p.items; commentsNextPage = p.next; commentsMore = p.next != null
        }
        commentsLoading = false
    }

    /** স্ক্রোল শেষের কাছে এলে পরের পেজ */
    fun loadMoreComments() {
        val page = commentsNextPage ?: return
        if (commentsLoadingMore) return
        viewModelScope.launch {
            commentsLoadingMore = true
            runCatching { YtRepository.moreComments(commentsUrl, page) }
                .onSuccess { p ->
                    val have = comments.map { it.id }.toSet()
                    comments = comments + p.items.filter { it.id !in have }
                    commentsNextPage = p.next; commentsMore = p.next != null
                }
                .onFailure { commentsMore = false }
            commentsLoadingMore = false
        }
    }

    fun chooseCommentSort(i: Int) { commentSort = i }

    /** 0 শীর্ষ (YouTube-এর ক্রম), 1 নতুন, 2 পুরনো। লোড হওয়া মন্তব্যের মধ্যে সাজানো হয়। */
    fun sortedComments(): List<CommentItem> = when (commentSort) {
        1 -> comments.sortedByDescending { it.time ?: Long.MIN_VALUE }
        2 -> comments.sortedBy { it.time ?: Long.MAX_VALUE }
        else -> comments
    }

    fun toggleReplies(c: CommentItem) {
        val cur = replyStates[c.id]
        if (cur != null && cur.expanded) { replyStates = replyStates + (c.id to cur.copy(expanded = false)); return }
        if (cur != null && cur.items.isNotEmpty()) { replyStates = replyStates + (c.id to cur.copy(expanded = true)); return }
        val page = c.replies ?: return
        loadReplies(c.id, page)
    }

    fun loadMoreReplies(c: CommentItem) {
        val page = replyStates[c.id]?.next ?: return
        loadReplies(c.id, page)
    }

    private fun loadReplies(id: String, page: Page) = viewModelScope.launch {
        val cur = replyStates[id] ?: ReplyState()
        replyStates = replyStates + (id to cur.copy(loading = true, expanded = true))
        val res = runCatching { YtRepository.moreComments(commentsUrl, page) }.getOrNull()
        val st = replyStates[id] ?: cur
        replyStates = replyStates + (id to
                if (res != null) st.copy(items = st.items + res.items, next = res.next, loading = false)
                else st.copy(loading = false))
    }

    // ---------- চ্যানেল ও সাবস্ক্রিপশন ----------
    fun openChannel(url: String?) {
        if (url == null) return
        expanded = false; channelOpen = true; channel = null
        viewModelScope.launch {
            channelLoading = true
try {
                val ch = YtRepository.channel(url)
                channel = ch
                subs.value.firstOrNull { it.url == url || it.url == ch.url }?.let { s ->
                    if (s.avatar == null && ch.avatar != null) dao.addSub(s.copy(avatar = ch.avatar))
                }
            } catch (e: Exception) { error = e.message }
            channelLoading = false
        }
    }
    fun closeChannel() { channelOpen = false }

    fun toggleSub(url: String, name: String, avatar: String?) = viewModelScope.launch {
        if (subs.value.any { it.url == url }) dao.removeSub(url)
        else {
            dao.addSub(SubEntity(url, name, avatar))
            Recommender.boost(dao, "", name, 2.0)
        }
    }

    /** NewPipe: সাবস্ক্রিপশন JSON অথবা পুরো ব্যাকআপ ZIP (সাবস্ক্রিপশন + ইতিহাস) */
    fun importNewPipe(uri: Uri) = viewModelScope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            }
            if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                val (subsN, histN) = withContext(Dispatchers.IO) { NewPipeImport.importZip(ctx, dao, bytes) }
                toast(L.f("{0} টি চ্যানেল ও {1} টি ইতিহাস ইম্পোর্ট হয়েছে", subsN, histN))
                loadInterests()
            } else {
                val arr = JSONObject(String(bytes)).getJSONArray("subscriptions")
                var n = 0
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optInt("service_id", 0) == 0) {
                        dao.addSub(SubEntity(o.getString("url"), o.optString("name", "?"), null)); n++
                    }
                }
                toast(L.f("{0} টি চ্যানেল ইম্পোর্ট হয়েছে", n))
            }
        } catch (e: Exception) { toast(L.f("ইম্পোর্ট ব্যর্থ: {0}", e.message)) }
    }

    // ---------- প্রোফাইল এক্সপোর্ট / ইম্পোর্ট ----------
    fun exportProfile(uri: Uri) = viewModelScope.launch {
        try {
            val prefsJson = JSONObject().put("theme", themeIndex).put("sponsorblock", sponsorOn).put("ryd", rydOn)
                .put("captionSize", captionSizeIndex).put("captionLang", prefs.captionLang)
                .put("qualityWifi", qualityWifi).put("qualityMobile", qualityMobile).put("skipSilence", skipSilence)
            val root = ProfileBackup.build(dao, prefsJson)
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(root.toString(2)) }
            }
            toast(L.t("প্রোফাইল এক্সপোর্ট হয়েছে"))
        } catch (e: Exception) { toast(L.f("এক্সপোর্ট ব্যর্থ: {0}", e.message)) }
    }

    fun importProfile(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            val root = JSONObject(text)
            if (!ProfileBackup.isValid(root)) { toast(L.t("এটি TubeX-এর প্রোফাইল ফাইল নয়")); return@launch }
            ProfileBackup.restore(dao, root)
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
            toast(L.t("প্রোফাইল ইম্পোর্ট হয়েছে"))
            loadInterests()
        } catch (e: Exception) { toast(L.f("ইম্পোর্ট ব্যর্থ: {0}", e.message)) }
    }

    // ---------- ডাউনলোড (কিউ, থামানো/চালু, মার্জ) ----------
    fun downloadOptions(): List<DlOption> = current?.let { Downloads.options(it) } ?: emptyList()

    fun enqueueDownload(o: DlOption) {
        val info = current ?: return
        viewModelScope.launch {
            try {
                Downloads.enqueue(ctx, dao, info, o)
                toast(L.t("ডাউনলোড কিউতে যোগ হয়েছে"))
            } catch (ex: Exception) { toast(ex.message ?: L.t("ডাউনলোড ব্যর্থ")) }
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
