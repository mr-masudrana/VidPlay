package com.tubex.app.viewmodel

import com.tubex.app.data.backup.NewPipeImport
import com.tubex.app.data.backup.ProfileBackup
import com.tubex.app.data.cache.FeedCache
import com.tubex.app.data.cache.SubsCache
import com.tubex.app.data.cache.StreamInfoCache
import com.tubex.app.data.local.AppDb
import com.tubex.app.data.local.Prefs
import com.tubex.app.data.local.entity.BlockEntity
import com.tubex.app.data.local.entity.DownloadEntity
import com.tubex.app.data.local.entity.FavoriteEntity
import com.tubex.app.data.local.entity.HistoryEntity
import com.tubex.app.data.local.entity.PlaylistEntity
import com.tubex.app.data.local.entity.PlaylistItemEntity
import com.tubex.app.data.local.entity.SubEntity
import com.tubex.app.data.model.ChannelData
import com.tubex.app.data.model.CommentItem
import com.tubex.app.data.model.PlaylistData
import com.tubex.app.data.model.PlaylistEntry
import com.tubex.app.data.model.ChannelResult
import com.tubex.app.data.model.VideoItem
import com.tubex.app.data.recommend.Blocks
import com.tubex.app.data.recommend.Recommender
import com.tubex.app.data.remote.DeArrow
import com.tubex.app.data.search.ChannelSearch
import com.tubex.app.data.search.HomePlaylists
import com.tubex.app.data.search.PrefetchPlan
import com.tubex.app.data.search.PlaylistLinks
import com.tubex.app.data.remote.Extras
import com.tubex.app.data.remote.Segment
import com.tubex.app.data.remote.YtRepository
import com.tubex.app.download.DlOption
import com.tubex.app.download.DownloadEngine
import com.tubex.app.download.Downloads
import com.tubex.app.download.DownloadExporter
import com.tubex.app.util.L
import com.tubex.app.notification.NotifyScheduler
import com.tubex.app.player.Navigator
import com.tubex.app.player.PlayerHolder
import com.tubex.app.update.UpdateInfo
import com.tubex.app.update.UpdateScheduler
import com.tubex.app.update.Updater

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
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
import kotlinx.coroutines.sync.withPermit
import androidx.room.withTransaction
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dao = AppDb.get(app).dao()
    private val prefs = Prefs(app)
    val player = PlayerHolder.get(app)
    /** কন্ট্রোলারে পরের/আগের বাটন দেখাতে এটা PlayerView-তে দিতে হয় */
    val controllerPlayer: Player get() = PlayerHolder.navPlayer(ctx)

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
    var poTokenOn by mutableStateOf(prefs.poToken); private set
    var rydOn by mutableStateOf(prefs.ryd); private set
    var captionSizeIndex by mutableStateOf(prefs.captionSize); private set
    var captionColorIndex by mutableStateOf(prefs.captionColor); private set
    var captionBgIndex by mutableStateOf(prefs.captionBg); private set
    var qualityWifi by mutableStateOf(prefs.qualityWifi); private set
    var qualityMobile by mutableStateOf(prefs.qualityMobile); private set
    var skipSilence by mutableStateOf(prefs.skipSilence); private set
    var audioOnly by mutableStateOf(prefs.audioOnly); private set
    var notifyOn by mutableStateOf(prefs.notifyEnabled); private set
    /** সেটিংসের খোলা টপিক পেজ (null = টপিকের তালিকা) */
    var settingsPage by mutableStateOf<Int?>(null)
    /** সেটিংস টপিক তালিকা ও লাইব্রেরির মূল পেজের স্ক্রল অবস্থান: সাব-পেজ থেকে ফিরলে আগের জায়গাতেই থাকে */
    val settingsListScroll = ScrollState(0)
    val libraryListState = LazyListState()
    /** লাইব্রেরির খোলা "সব দেখুন" পেজ: 0 ইতিহাস, 1 ফেভারিট, 2 প্লেলিস্ট, 3 ডাউনলোড (null = মূল পেজ) */
    var libraryPage by mutableStateOf<Int?>(null)
    /** মূল পেজ থেকে কোন প্লেলিস্ট সরাসরি খুলতে হবে */
    var libraryOpenPlaylist by mutableStateOf<Long?>(null)
    var notifyHours by mutableStateOf(prefs.notifyHours); private set
    var updateNotifyOn by mutableStateOf(prefs.updateNotify); private set
    var loop by mutableStateOf(false); private set
    var sleepEnd by mutableStateOf(0L); private set
    var sleepAtEnd by mutableStateOf(false); private set
    var sleepMin by mutableStateOf(0); private set      // নির্বাচিত স্লিপ টাইমার (চিপ হাইলাইটের জন্য)
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
    /** শুধু হোম ফিডের লোডিং (স্কেলিটন দেখানোর জন্য) */
    var feedLoading by mutableStateOf(false); private set
    /** পুল-টু-রিফ্রেশ চলছে (তালিকা না মুছে ওপরে স্পিনার দেখায়) */
    var refreshing by mutableStateOf(false); private set
    /** হোম ট্যাবে আবার ট্যাপ করলে বাড়ে: তালিকা স্মুথভাবে ওপরে ওঠে */
    var homeTopTick by mutableStateOf(0); private set
    fun scrollHomeToTop() { homeTopTick++ }
    /** নির্বাচিত ক্যাটাগরি চিপ (সার্চ খুললে/বন্ধ করলেও মনে থাকে) */
    var chipIndex by mutableStateOf(0); private set
    private var chipQuery: String? = null
    /** ক্যাটাগরি/আপনার-জন্য ফিডের ক্যাশ: একই ফিডে ফিরলে আবার লোড হয় না (পুল-টু-রিফ্রেশে নতুন হয়) */
    private val feedCache = FeedCache(ttlMs = 3 * 60_000L)   // ৩ মিনিট: ট্যাব বদলে ফিরলে ফিড তাজা থাকে
    private fun cachedFeed(key: String): List<VideoItem>? = feedCache.get(key)
    private fun putFeed(key: String, list: List<VideoItem>) = feedCache.put(key, list)
    /** ব্যাক জেসচারের প্রিভিউর জন্য: বর্তমান ক্যাটাগরির হোম ফিড (ক্যাশ থেকে) */
    fun previewFeed(): List<VideoItem> =
        feedCache.peek(if (chipIndex == 0) "foryou" else "cat:${chipQuery.orEmpty()}")
            ?: if (!isSearchResult) feed else emptyList()
    fun pickCategory(i: Int, name: String) {
        chipIndex = i; chipQuery = if (i == 0) null else name
        if (i == 0) loadForYou() else load(name)
    }
    private var feedKind = 0            // 0 আপনার জন্য, 1 ক্যাটাগরি/ট্রেন্ডিং, 2 সার্চ
    private var feedQuery: String? = null
    /** ফিডটি সার্চ রেজাল্ট হলে true (তখন ক্যাটাগরির বদলে ফিল্টার দেখায়) */
    var isSearchResult by mutableStateOf(false); private set
    var fDate by mutableStateOf(0); private set
    var fDuration by mutableStateOf(0); private set
    var fSort by mutableStateOf(0); private set
    private var feedJob: Job? = null

    /** সার্চে মিলে যাওয়া চ্যানেল (ভিডিওর ওপরে দেখায়) */
    var searchChannels by mutableStateOf<List<ChannelResult>>(emptyList()); private set
    /** হোমের নিচে স্ক্রলে "আরও লোড" */
    var loadingMore by mutableStateOf(false); private set
    private var feedEnd = false            // আর নতুন ভিডিও না পেলে থামি
    private var moreJob: Job? = null

    var opening by mutableStateOf(false); private set
    /** নতুন ভিডিওর তথ্য আসার আগ পর্যন্ত true: প্লেয়ার পেজ স্কেলিটনসহ সাথে সাথে খোলে */
    var loadingNew by mutableStateOf(false); private set
    var loadingHint by mutableStateOf<VideoItem?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    // প্লেয়ার
    var current by mutableStateOf<StreamInfo?>(null); private set
    var local by mutableStateOf<DownloadEntity?>(null); private set
    /** শুধু অডিও চলছে (অনলাইন "শুধু অডিও" মোড অথবা ডাউনলোড করা অডিও ফাইল): প্লেয়ারে ভিডিওর বদলে থাম্বনেইল দেখায় */
    val audioArt: Boolean get() = (audioOnly && current != null) || local?.audioOnly == true
    val audioArtUrl: String? get() = local?.thumb ?: current?.thumbnails?.lastOrNull()?.url
    var expanded by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var inPip by mutableStateOf(false)
    var related by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var heights by mutableStateOf<List<Int>>(emptyList()); private set
    var quality by mutableStateOf(0); private set          // 0 = Auto
    var speed by mutableStateOf(1f); private set
    var resizeMode by mutableStateOf(prefs.resizeMode); private set
    var autoplay by mutableStateOf(prefs.autoplay); private set
    var banglaAudio by mutableStateOf(prefs.banglaAudio); private set
    private val backStack = kotlin.collections.ArrayDeque<String>()   // আগে দেখা ভিডিও ("আগের" বাটনের জন্য)
    private val sessionPlayed = HashSet<String>()                      // এই সেশনে চালানো ভিডিও (অটোপ্লে লুপ ঠেকাতে)
    private var goingBack = false
    var likes by mutableStateOf<Long?>(null); private set
    var dislikes by mutableStateOf<Long?>(null); private set
    var captions by mutableStateOf<List<SubtitlesStream>>(emptyList()); private set
    var captionIndex by mutableStateOf(-1); private set     // -1 = বন্ধ
    var audioTracks by mutableStateOf<List<YtRepository.AudioTrack>>(emptyList()); private set
    var audioTrackId by mutableStateOf<String?>(null); private set

    // মন্তব্য (CommentsController-এ; UI আগের নামেই পড়ে)
    private val commentsCtl = CommentsController(viewModelScope)
    val comments get() = commentsCtl.items
    val commentsTotal get() = commentsCtl.total
    val commentsLoading get() = commentsCtl.loading
    val commentsLoadingMore get() = commentsCtl.loadingMore
    val commentsMore get() = commentsCtl.hasMore
    val commentSort get() = commentsCtl.sort
    val replyStates get() = commentsCtl.replyStates

    // চ্যানেলের প্লেলিস্ট ট্যাব ও অনলাইন প্লেলিস্ট (PlaylistsController.kt)
    private val channelPls = ChannelPlaylistsController(viewModelScope) { channel }
    private val playlistCtl = OnlinePlaylistController(viewModelScope, dao) { toast(it) }
    val channelPlaylists get() = channelPls.items
    val channelPlaylistsLoading get() = channelPls.loading
    val playlistOpen get() = playlistCtl.isOpen
    val onlinePlaylist get() = playlistCtl.data
    val playlistLoading get() = playlistCtl.loading
    val playlistMoreLoading get() = playlistCtl.moreLoading

    // চ্যানেল
    var channelOpen by mutableStateOf(false); private set
    var channel by mutableStateOf<ChannelData?>(null); private set
    var channelLoading by mutableStateOf(false); private set

    // রিকমেন্ডেশন ড্যাশবোর্ড
    var topInterests by mutableStateOf<List<Pair<String, Double>>>(emptyList()); private set
    var contextInterests by mutableStateOf<List<Pair<String, Double>>>(emptyList()); private set
    var langMode by mutableStateOf(prefs.lang); private set
    var floatingMini by mutableStateOf(prefs.floatMini); private set
    var themeAuto by mutableStateOf(prefs.themeAuto); private set
    var accentColor by mutableStateOf(prefs.accentColor); private set
    var dataSaver by mutableStateOf(prefs.dataSaver); private set
    var backgroundPlay by mutableStateOf(prefs.backgroundPlay); private set
    var pipOn by mutableStateOf(prefs.pipPlay); private set
    var wifiOnlyDl by mutableStateOf(prefs.wifiOnlyDownload); private set
    var parallelDl by mutableStateOf(prefs.parallelDownloads); private set
    var saveSearchHist by mutableStateOf(prefs.saveSearchHistory); private set
    var saveWatchHist by mutableStateOf(prefs.saveWatchHistory); private set
    var pipX by mutableStateOf(Float.NaN)       // ভাসমান প্লেয়ারের অবস্থান (NaN = ডিফল্ট কোণ)
    var pipY by mutableStateOf(Float.NaN)

    // Chromecast
    private val cast = CastController(
        app, currentInfo = { current }, playerPositionMs = { player.currentPosition },
        pausePlayer = { player.pause() }, toast = { toast(it) }
    )
    val castAvailable get() = cast.available
    val castName get() = cast.deviceName
    val castPlaying get() = cast.playing

    private var segments: List<Segment> = emptyList()
    private var milestone = 0
    private val queue = PlayQueue()
    val queueItems get() = queue.items
    val queueIndex get() = queue.index
    val queueTitle get() = queue.title          // প্লেলিস্ট থেকে এলে তার নাম
    val shuffle get() = queue.shuffle
    val repeat get() = queue.repeat          // 0 বন্ধ, 1 কিউ, 2 একটি ভিডিও
    private val streamCache = StreamInfoCache(viewModelScope)

    val active get() = current != null || local != null
    val title get() = current?.name ?: local?.title ?: ""
    val channelName get() = current?.uploaderName ?: ""

    private var playerListener: Player.Listener? = null

    init {
        player.skipSilenceEnabled = skipSilence
        L.apply(prefs.lang)
        DeArrow.titles = prefs.dearrowTitles; DeArrow.thumbs = prefs.dearrowThumbs
        viewModelScope.launch {
            if (!DownloadEngine.isActive) {
                dao.resetInterrupted()
                if (dao.nextQueued() != null) DownloadEngine.kick(ctx)   // আগের সেশনের কিউ আবার চালু
            }
        }
        loadForYou()
        warmUp()
        PlayerHolder.navigator = object : Navigator {
            override fun canNext() = this@MainViewModel.canNext()
            override fun canPrev() = this@MainViewModel.canPrev()
            override fun next() { playNextVideo() }
            override fun prev() { playPreviousVideo() }
        }
        val pl = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) saveProgress()
            }
            override fun onPlaybackStateChanged(state: Int) {
                // প্লেলিস্ট/কিউ: শেষ হলে পরেরটা
                if (state == Player.STATE_ENDED && sleepAtEnd) {
                    sleepAtEnd = false; sleepMin = 0; toast(L.t("স্লিপ টাইমার: প্লেব্যাক বন্ধ")); return
                }
                if (state == Player.STATE_ENDED && !advanceQueue() && autoplay) playRelatedNext()
            }
            override fun onPlayerError(e: PlaybackException) {
                val info = current
                // ১ম এরর: স্ট্রিম URL মেয়াদোত্তীর্ণ (403) হতে পারে — নতুন তথ্য এনে একই জায়গা থেকে চালাই
                if (info != null && recoveredUrl != info.url) {
                    recoveredUrl = info.url
                    val pos = player.currentPosition
                    streamCache.remove(info.url)
                    viewModelScope.launch {
                        try {
                            val fresh = YtRepository.streamInfo(info.url)
                            streamCache.put(info.url, fresh)
                            if (current?.url != info.url) return@launch    // এর মধ্যে অন্য ভিডিওতে গেছে
                            current = fresh
                            rebuild(fresh, pos); player.playWhenReady = true
                        } catch (ce: CancellationException) { throw ce
                        } catch (ex: Exception) {
                            error = e.message ?: L.t("প্লেব্যাক এরর")
                        }
                    }
                    return
                }
                // ২য় এরর: সাবটাইটেলের কারণ হতে পারে — শুধু এই ভিডিওর জন্য বন্ধ করি (সেভ করা ভাষা মুছি না)
                if (info != null && captionIndex >= 0 && !captionDropped) {
                    captionDropped = true
                    captionIndex = -1
                    val pos = player.currentPosition
                    rebuild(info, pos); player.playWhenReady = true
                    toast(L.t("সাবটাইটেল লোড হয়নি, বন্ধ করা হলো"))
                    return
                }
                error = e.message ?: L.t("প্লেব্যাক এরর")
            }
        }
        playerListener = pl; player.addListener(pl)
        // Chromecast (Google Play Services না থাকলে নীরবে বাদ)
        cast.init()
        // প্রতি ০.৫ সেকেন্ডে: SponsorBlock স্কিপ; প্রতি ১০ সেকেন্ডে: হিস্টরি সেভ
        viewModelScope.launch {
            var n = 0
            while (true) {
                delay(500)
                if (sleepEnd > 0 && System.currentTimeMillis() >= sleepEnd) {
                    sleepEnd = 0; sleepMin = 0; player.pause(); cast.pause(); toast(L.t("স্লিপ টাইমার: প্লেব্যাক বন্ধ"))
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

    override fun onCleared() {
        playerListener?.let { player.removeListener(it) }
        PlayerHolder.navigator = null
        cast.release()
        super.onCleared()
    }

    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    // ---------- ফিড ----------
    fun load(query: String?, search: Boolean = false, silent: Boolean = false): Job {
        feedJob?.cancel(); moreJob?.cancel(); loadingMore = false
        if (!search) searchChannels = emptyList()
        val key = "cat:${query.orEmpty()}"
        if (!search && !silent) cachedFeed(key)?.let { c ->
            feedKind = 1; feedQuery = query
            isSearchResult = false; resetFilters()
            feed = c; error = null; prefetchTop(c)
            loading = false; feedLoading = false; refreshing = false
            return viewModelScope.launch { }
        }
        val job = viewModelScope.launch {
            feedKind = if (search) 2 else 1; feedQuery = query
            if (silent) refreshing = true else {
                loading = true; feedLoading = true; error = null
                isSearchResult = search
                if (search) { resetFilters(); searchChannels = emptyList() }
                feed = emptyList()
            }
            try {
                val r: List<VideoItem>
                if (search && !query.isNullOrBlank()) {
                    // ভিডিও ও চ্যানেল একসাথে; চ্যানেল না এলেও ভিডিও দেখানো হয়
                    val (vids, chans) = coroutineScope {
                        val c = async { runCatching { YtRepository.searchChannels(query) }.getOrDefault(emptyList()) }
                        val v = async { YtRepository.search(query) }
                        v.await() to c.await()
                    }
                    val blocked = dao.blocksOnce().map { it.entry }.toSet()
                    searchChannels = ChannelSearch.filterBlocked(chans, blocked)
                    r = filterBlocked(vids)
                } else {
                    r = filterBlocked(if (query.isNullOrBlank()) YtRepository.trending() else YtRepository.search(query))
                }
                if (!search) putFeed(key, r)
                if (r.isNotEmpty() || !silent) { feed = r; prefetchTop(r) }
                if (feed.isEmpty()) error = L.t("কোনো ভিডিও পাওয়া যায়নি") else error = null
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val msg = e.message ?: L.t("লোড করা যায়নি")
                if (silent && feed.isNotEmpty()) toast(msg) else error = msg
            }
            loading = false; feedLoading = false; refreshing = false
        }
        feedJob = job
        return job
    }

    /**
     * "আপনার জন্য" ফিডের একটি ব্যাচ: নতুন র‍্যান্ডম উৎস থেকে প্রার্থী এনে র‍্যাংক করে।
     * [exclude]: ফিডে আগে থেকেই থাকা ভিডিও (আরও লোডের সময় পুনরাবৃত্তি এড়াতে)।
     */
    private suspend fun buildForYou(exclude: Set<String>): List<VideoItem> {
        val interests = dao.interests()
        val shownCounts = prefs.shownVideos.groupingBy { it }.eachCount()
        // প্রতিবার আলাদা বিষয়: আগ্রহের ওজন ধরে র‍্যান্ডম বাছাই (আগে সবসময় একই ৩টি সার্চ হতো)
        val kwWeights = (Recommender.top(interests, 14) + Recommender.topForBucket(interests, 6))
            .filter { it.first.startsWith("kw:") }
            .groupBy({ it.first.removePrefix("kw:") }, { it.second })
            .map { (k, v) -> k to v.sum() }
        val picked = Recommender.pickTopics(kwWeights, 4)
        val queries = picked.take(3) + (if (picked.size >= 2) listOf("${picked[0]} ${picked[1]}") else emptyList())
        // সাম্প্রতিক দেখার related: সবচেয়ে সাম্প্রতিকটি + আগের ১০টির মধ্যে একটি র‍্যান্ডম
        val hist = history.value
        val relSrc = (listOfNotNull(hist.firstOrNull()?.url) + hist.drop(1).take(9).shuffled().take(1).map { it.url }).distinct()
        // সাবস্ক্রাইব করা চ্যানেলের তাজা ভিডিও (প্রতিবার ৩টি র‍্যান্ডম চ্যানেল)
        val subSrc = subs.value.shuffled().take(3)
        val (pools, relatedPool) = coroutineScope {
            val rel = relSrc.map { u ->
                async { runCatching { YtRepository.related(YtRepository.streamInfo(u)) }.getOrDefault(emptyList()) }
            }
            val subJobs = subSrc.map { sc ->
                async {
                    kotlinx.coroutines.withTimeoutOrNull(8_000) {
                        runCatching { YtRepository.channelVideos(sc.url) }.getOrDefault(emptyList())
                    }.orEmpty().take(8)
                }
            }
            val jobs = listOf(async { runCatching { YtRepository.trending() }.getOrDefault(emptyList()) }) +
                    queries.map { q -> async { runCatching { YtRepository.search(q) }.getOrDefault(emptyList()) } } +
                    subJobs
            jobs.awaitAll() to rel.awaitAll().flatten()
        }
        val merged = (pools.flatten() + relatedPool).distinctBy { it.url }.filter { it.url !in exclude }
        val ranked = filterBlocked(
            Recommender.rank(
                merged, interests, dao.watchedUrls().toSet(), history.value,
                subs.value.map { it.name.lowercase() }.toSet(),
                related = relatedPool.map { it.url }.toSet(),
                shown = shownCounts
            )
        )
        // কম লাইক-অনুপাতের ভিডিও শেষে পাঠানো (সর্বোচ্চ ৪ সেকেন্ড অপেক্ষা; না পেলে যেমন আছে তেমন)
        val r = if (prefs.ryd) kotlinx.coroutines.withTimeoutOrNull(4_000) { demoteLowEngagement(ranked) } ?: ranked
        else ranked
        return r
    }

    fun loadForYou(silent: Boolean = false): Job {
        feedJob?.cancel(); moreJob?.cancel(); loadingMore = false
        searchChannels = emptyList()
        if (!silent) cachedFeed("foryou")?.let { c ->
            feedKind = 0; feedQuery = null
            isSearchResult = false; resetFilters()
            feed = c; error = null; prefetchTop(c)
            loading = false; feedLoading = false; refreshing = false
            return viewModelScope.launch { }
        }
        val job = viewModelScope.launch {
            feedKind = 0; feedQuery = null; feedEnd = false
            if (silent) refreshing = true else {
                loading = true; feedLoading = true; error = null
                isSearchResult = false; resetFilters()
                feed = emptyList()
            }
            try {
                val r = buildForYou(emptySet())
                putFeed("foryou", r)
                prefs.shownVideos = (prefs.shownVideos + r.take(20).map { it.url }).takeLast(300)
                if (r.isNotEmpty() || !silent) { feed = r; prefetchTop(r) }
                if (feed.isEmpty()) error = L.t("কোনো ভিডিও পাওয়া যায়নি") else error = null
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val msg = e.message ?: L.t("লোড করা যায়নি")
                if (silent && feed.isNotEmpty()) toast(msg) else error = msg
            }
            loading = false; feedLoading = false; refreshing = false
        }
        feedJob = job
        return job
    }

    /** "আপনার জন্য" ফিডের শেষে পৌঁছালে নতুন ব্যাচ যোগ করে (সর্বোচ্চ ২০০টি ভিডিও) */
    fun loadMoreForYou() {
        if (feedKind != 0 || isSearchResult || feedLoading || refreshing || loadingMore || feedEnd) return
        if (feed.isEmpty() || feed.size >= 200) return
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            loadingMore = true
            try {
                val have = feed.map { it.url }.toSet()
                val more = buildForYou(have)
                if (feedKind == 0 && !isSearchResult) {
                    if (more.isEmpty()) feedEnd = true
                    else {
                        feed = feed + more
                        putFeed("foryou", feed)
                        prefs.shownVideos = (prefs.shownVideos + more.take(20).map { it.url }).takeLast(300)
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { /* আরও লোড ব্যর্থ হলে চুপচাপ; পরের স্ক্রলে আবার চেষ্টা */ }
            loadingMore = false
        }
    }

    /** ফিডের প্রথম ১৬টির লাইক/ডিসলাইক (RYD) এনে কম মানের ভিডিও শেষে নামায় */
    private suspend fun demoteLowEngagement(list: List<VideoItem>): List<VideoItem> {
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        val stats = coroutineScope {
            list.take(16).map { v ->
                async {
                    val id = DeArrow.videoId(v.url) ?: return@async null
                    gate.withPermit { Extras.ryd(id) }?.let { v.url to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        return Recommender.demoteLowQuality(list, stats)
    }

    /** পুল-টু-রিফ্রেশ: বর্তমান ফিড (আপনার জন্য / ক্যাটাগরি / সার্চ) আবার লোড করে */
    // ---------- হোমের প্লেলিস্ট সারি ----------
    var homePlaylists by mutableStateOf<List<PlaylistEntry>>(emptyList()); private set
    private var homePlaylistsJob: Job? = null
    private var homePlaylistsAt = 0L

    /** আগ্রহের বিষয় ধরে প্লেলিস্ট খুঁজে হোমে দেখায়; ৩০ মিনিট ক্যাশ, ব্যর্থ হলে আগেরটাই থাকে */
    fun loadHomePlaylists(force: Boolean = false) {
        val fresh = System.currentTimeMillis() - homePlaylistsAt < 30 * 60_000L
        if (!force && homePlaylists.isNotEmpty() && fresh) return
        if (homePlaylistsJob?.isActive == true) return
        homePlaylistsJob = viewModelScope.launch {
            val topics = Recommender.top(dao.interests(), 6).map { it.first }
            val groups = coroutineScope {
                HomePlaylists.queries(topics).map { q ->
                    async { runCatching { YtRepository.searchPlaylists(q) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }
            val merged = HomePlaylists.merge(groups)
            if (merged.isNotEmpty()) { homePlaylists = merged; homePlaylistsAt = System.currentTimeMillis() }
        }
    }

    fun refreshFeed() {
        when (feedKind) {
            0 -> { loadForYou(silent = true); loadHomePlaylists(force = true) }
            else -> load(feedQuery, search = feedKind == 2, silent = true)
        }
    }

    // ---------- সার্চ ফিল্টার ----------
    val filtersActive get() = fDate != 0 || fDuration != 0 || fSort != 0
    fun setDateFilter(i: Int) { fDate = i }
    fun setDurationFilter(i: Int) { fDuration = i }
    fun setSortFilter(i: Int) { fSort = i }
    fun resetFilters() { fDate = 0; fDuration = 0; fSort = 0 }

    fun loadSubsFeed(force: Boolean = false) = viewModelScope.launch {
        val list = subs.value
        if (list.isEmpty()) { subsFeed = emptyList(); return@launch }
        val key = list.map { it.url }.hashCode()
        val cached = withContext(Dispatchers.IO) { SubsCache.load(ctx) }
        val sameSet = cached != null && cached.key == key
        if (cached != null && sameSet && subsFeed.isEmpty()) subsFeed = filterBlocked(cached.items)   // ক্যাশ থেকে সাথে সাথে
        if (!force && cached != null && sameSet && System.currentTimeMillis() - cached.time < 30 * 60_000L) return@launch

        loading = true
        val gate = kotlinx.coroutines.sync.Semaphore(5)   // একসাথে সর্বোচ্চ ৫টি চ্যানেল
        val lists = list.map { sub ->
            async {
                gate.withPermit { runCatching { YtRepository.channelVideos(sub.url).take(4) }.getOrDefault(emptyList()) }
            }
        }.awaitAll()
        val max = lists.maxOfOrNull { it.size } ?: 0
        val merged = (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
        if (merged.isNotEmpty()) {
            subsFeed = filterBlocked(merged)
            withContext(Dispatchers.IO) { SubsCache.save(ctx, key, merged) }
        }
        loading = false
    }

    // ---------- সাবস্ক্রিপশন: পুল-টু-রিফ্রেশ ও চ্যানেল ফিল্টার ----------
    var subsRefreshing by mutableStateOf(false); private set
    var subsFilter by mutableStateOf<String?>(null); private set            // নির্বাচিত চ্যানেলের URL (null = সব)
    var subsFilterVideos by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var subsFilterLoading by mutableStateOf(false); private set
    private var subsFilterJob: Job? = null

    /** উপর থেকে টানলে: ফিড (এবং ফিল্টার চালু থাকলে সেই চ্যানেলও) নতুন করে লোড */
    fun refreshSubs() {
        if (subsRefreshing) return
        subsRefreshing = true
        val feedJob = loadSubsFeed(true)
        val f = subsFilter
        val filterJob = if (f != null) loadSubsFilter(f) else null
        viewModelScope.launch {
            feedJob.join(); filterJob?.join()
            subsRefreshing = false
        }
    }

    /** চ্যানেল লোগোতে ট্যাপ: সেই চ্যানেলের ভিডিও দেখায়; একই লোগোতে আবার ট্যাপ করলে ফিল্টার বন্ধ */
    fun selectSubsChannel(url: String?) {
        if (url == null || url == subsFilter) {
            subsFilterJob?.cancel()
            subsFilter = null; subsFilterVideos = emptyList(); subsFilterLoading = false
            return
        }
        subsFilter = url
        subsFilterVideos = subsFeed.filter { it.channelUrl == url }     // ফিডে যা আছে সাথে সাথে
        loadSubsFilter(url)
    }

    private fun loadSubsFilter(url: String): Job {
        subsFilterJob?.cancel()
        subsFilterLoading = true
        val job = viewModelScope.launch {
            val vids = runCatching { filterBlocked(YtRepository.channelVideos(url)) }.getOrNull()
            if (subsFilter == url) {
                if (!vids.isNullOrEmpty()) subsFilterVideos = vids
                subsFilterLoading = false
            }
        }
        subsFilterJob = job
        return job
    }

    // ---------- প্লেব্যাক ----------
    /** নতুন কিছু চালু হলে বাড়ে; পেছনে চলা "লিংকের প্লেলিস্ট কিউতে তোলা" কাজ এটা দেখে বাতিল হয় */
    private var playGen = 0

    fun play(url: String) { playGen++; clearQueue(); startPlay(url) }

    fun playQueueItems(items: List<QItem>, index: Int, shuffleStart: Boolean = false, title: String? = null) {
        if (items.isEmpty()) return
        playGen++
        startPlay(queue.start(items, index, shuffleStart, title).url)
    }

    private fun clearQueue() = queue.clear()

    /** ভিডিও শেষে: শাফল/রিপিট অনুযায়ী কিউর পরের আইটেম */
    private fun advanceQueue(): Boolean {
        val next = queue.advance() ?: return false
        startPlay(next.url, expand = false)
        return true
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
        startPlay(v.url, expand = false)
        return true
    }

    fun playNextVideo(): Boolean = advanceQueue() || playRelatedNext()

    /** ৩ সেকেন্ডের বেশি চলে থাকলে শুরু থেকে; নইলে আগের ভিডিও (কিউ বা দেখার ইতিহাস থেকে) */
    fun playPreviousVideo() {
        if (player.currentPosition > 3_000 || !canPrev()) { player.seekTo(0); return }
        queue.back()?.let { startPlay(it.url, expand = false); return }
        val prev = backStack.removeLastOrNull() ?: run { player.seekTo(0); return }
        goingBack = true
        startPlay(prev, expand = false)
    }

    /** কার্ডের মেনু থেকে: পরে চালান (playNext) বা কিউর শেষে যোগ */
    fun addToQueue(v: VideoItem, playNext: Boolean) {
        val item = QItem(v.url, v.title, v.channel, v.thumb)
        if (!active) { playQueueItems(listOf(item), 0); return }
        val seed = current?.let { QItem(it.url, it.name, it.uploaderName ?: "", it.thumbnails.lastOrNull()?.url) }
        queue.add(item, playNext, seed)
        toast(L.t("কিউতে যোগ হয়েছে"))
    }

    fun queuePlayAt(i: Int) {
        val q = queue.select(i) ?: return
        startPlay(q.url)
    }

    fun queueRemove(i: Int) = queue.remove(i)
    fun queueMove(i: Int, delta: Int) = queue.move(i, delta)
    fun queueClear() = clearQueue()
    fun toggleShuffle(v: Boolean) = queue.setShuffle(v)
    fun chooseRepeat(m: Int) {
        queue.setRepeat(m); loop = (m == 2)
        player.repeatMode = if (m == 2) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // ---------- দ্রুত লোডের জন্য ক্যাশ ও প্রি-ফেচ ----------
    private var playJob: Job? = null
    private var recoveredUrl: String? = null     // যে ভিডিওর জন্য এরর-রিকভারি একবার চলেছে
    private var captionDropped = false
    private var playToken = 0

    /** ক্যাশে থাকলে সাথে সাথে; চলমান প্রি-ফেচ থাকলে তার জন্য অপেক্ষা; নইলে নতুন ফেচ (StreamInfoCache) */
    private suspend fun infoFor(url: String): StreamInfo = streamCache.get(url)

    private fun findHint(url: String): VideoItem? = (related + feed + subsFeed).firstOrNull { it.url == url }

    /** চলার সময়েই পরের সম্ভাব্য ভিডিওর তথ্য আগে থেকে এনে রাখা, যাতে "পরের"/অটোপ্লে প্রায় সাথে সাথে চলে */
    private fun prefetchNext() = viewModelScope.launch {
        if (dataSaver) return@launch
        delay(400)   // আগে ১৫০০ms ছিল; প্লে শুরু হয়ে গেলেই পরেরটা আনা শুরু
        val next = if (queueItems.isNotEmpty() && queueIndex + 1 < queueItems.size) queueItems[queueIndex + 1].url
        else related.firstOrNull { it.url !in sessionPlayed && it.url != current?.url }?.url
        if (next != null) runCatching { infoFor(next) }
    }

    private fun isUnmetered(): Boolean {
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        return cm?.isActiveNetworkMetered == false
    }

    private var prefetchJob: Job? = null

    /**
     * ফিড/সার্চ রেজাল্ট আসার পর ওপরের ২টি ভিডিওর স্ট্রিম-তথ্য পেছনে আনা (শুধু আনমিটার্ড নেটে, ডেটা সেভার বন্ধে)।
     * ট্যাপ করলে StreamInfoCache থেকে সাথে সাথে বা চলমান ফেচ ধরেই প্লে শুরু হয়। প্লে চলছে/খুলছে এমন সময় থেমে থাকে।
     */
    private fun prefetchTop(list: List<VideoItem>, max: Int = 2) {
        if (dataSaver || !isUnmetered()) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(600)
            for (v in PrefetchPlan.pick(list, { streamCache.has(it) }, max)) {
                if (opening) return@launch
                runCatching { infoFor(v.url) }
                delay(400)
            }
        }
    }

    /** অ্যাপ খোলার পর (শুধু আনমিটার্ড নেটে) ফিডের প্রথম ভিডিও আগে থেকে এনে রাখা: এক্সট্রাক্টর গরম হয় ও প্রথম প্লে দ্রুত হয় */
    private fun warmUp() = viewModelScope.launch {
        if (dataSaver) return@launch
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

    /** expand=false: অটোপ্লে/নোটিফিকেশনের পরের-আগের বাটন — মিনি প্লেয়ার থাকলে প্লেয়ার পেজ আবার খুলবে না */
    private fun startPlay(url: String, expand: Boolean = true) {
        current?.url?.let { prev ->
            if (!goingBack && prev != url) { backStack.addLast(prev); while (backStack.size > 30) backStack.removeFirst() }
        }
        goingBack = false
        sessionPlayed.add(url)
        error = null; opening = true
        // ট্যাপ করার সাথে সাথেই প্লেয়ার পেজ খুলে স্কেলিটন দেখানো
        loadingHint = findHint(url); loadingNew = true; if (expand) expanded = true
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
                recoveredUrl = null; captionDropped = false
                quality = q; heights = hs
                captions = caps; captionIndex = capIdx
                audioTracks = YtRepository.audioTracks(info); audioTrackId = YtRepository.initialAudioTrackId(info, banglaAudio)
                likes = info.likeCount.takeIf { it >= 0 }; dislikes = null
                PlayerHolder.setNowPlaying(info.name, info.uploaderName, info.thumbnails.maxByOrNull { it.height }?.url ?: info.thumbnails.lastOrNull()?.url)
                player.setMediaSource(
                    YtRepository.buildSource(info, if (q == 0) 1080 else q, caps.getOrNull(capIdx), audioOnly, audioTrackId, PlayerHolder.metadata), start
                )
                applyTextSelection()
                player.prepare(); player.playWhenReady = true
                loadingNew = false

                related = filterBlocked(YtRepository.related(info))
                if (castName != null) cast.load(info, start)
                writeHistory(info, start)
                boost(info, 0.5)
                commentsCtl.load(url)
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
        playJob?.cancel(); opening = false; loadingNew = false
        saveProgress()
        clearQueue()
        current = null; local = d; related = emptyList(); commentsCtl.clearList(); heights = emptyList()
        likes = null; dislikes = null; segments = emptyList(); captions = emptyList(); captionIndex = -1
        audioTracks = emptyList(); audioTrackId = null
        applyTextSelection()
        PlayerHolder.setNowPlaying(d.title, d.channel, d.thumb)
        player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(File(d.path))).setMediaMetadata(PlayerHolder.metadata).build())
        player.prepare(); player.playWhenReady = true
        expanded = true
    }

    private fun rebuild(info: StreamInfo, pos: Long) {
        player.setMediaSource(
            YtRepository.buildSource(info, if (quality == 0) 1080 else quality, captions.getOrNull(captionIndex), audioOnly, audioTrackId, PlayerHolder.metadata), pos
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

    fun chooseAudioTrack(id: String?) {
        val info = current ?: return
        audioTrackId = id
        rebuild(info, player.currentPosition)
    }

    fun chooseCaptionSize(i: Int) { captionSizeIndex = i; prefs.captionSize = i }
    fun chooseCaptionColor(i: Int) { captionColorIndex = i; prefs.captionColor = i }
    fun chooseCaptionBg(i: Int) { captionBgIndex = i; prefs.captionBg = i }
    fun chooseSpeed(s: Float) { speed = s; player.setPlaybackSpeed(s) }
    var muted by mutableStateOf(false); private set
    fun toggleMute() { muted = !muted; player.volume = if (muted) 0f else 1f }
    fun toggleAutoplay() { autoplay = !autoplay; prefs.autoplay = autoplay }
    /** সেটিংস: বাংলা অডিও ট্র্যাক থাকলে সেটা নিজে চলবে (পরের ভিডিও থেকে কার্যকর) */
    fun applyBanglaAudio(v: Boolean) { banglaAudio = v; prefs.banglaAudio = v }
    fun cycleResize() { resizeMode = (resizeMode + 1) % 3; prefs.resizeMode = resizeMode }
    fun setResize(m: Int) { resizeMode = m.coerceIn(0, 2); prefs.resizeMode = resizeMode }

    // ---------- ডিফল্ট কোয়ালিটি, লুপ, অডিও-অনলি, স্লিপ টাইমার ----------
    private fun defaultQuality(available: List<Int>): Int {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var target = if (cm.isActiveNetworkMetered) qualityMobile else qualityWifi
        // ডেটা সেভার: সব নেটওয়ার্কে সর্বোচ্চ 360p
        if (dataSaver) target = if (target == 0) 360 else minOf(target, 360)
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
        sleepMin = min
        when {
            min == 0 -> { sleepEnd = 0; sleepAtEnd = false }
            min < 0 -> { sleepEnd = 0; sleepAtEnd = true }
            else -> { sleepAtEnd = false; sleepEnd = System.currentTimeMillis() + min * 60_000L }
        }
    }

    // ---------- সার্চ ও লিংক ----------
    fun openSearch() { searching = true; showSuggestions = true; suggestions = emptyList() }
    fun closeSearch() {
        searching = false; showSuggestions = false; query = ""
        // কিছু সার্চ না করে বন্ধ করলে ফিড যেমন ছিল তেমনই থাকে; সার্চ রেজাল্ট থাকলে আগের ক্যাটাগরির ফিড ক্যাশ থেকে ফেরে
        if (isSearchResult) { if (chipIndex == 0) loadForYou() else load(chipQuery) }
    }
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
        // ইউটিউব লিংক পেস্ট করলে সার্চ না করে সরাসরি খোলে (ভিডিও/চ্যানেল/প্লেলিস্ট)
        if (Regex("^https?://(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be)/\\S+$", RegexOption.IGNORE_CASE).matches(t)) {
            showSuggestions = false; openLink(t); return
        }
        query = t; showSuggestions = false
        if (saveSearchHist) {
            searchHistory = (listOf(t) + searchHistory.filter { it != t }).take(20)
            prefs.searchHistory = searchHistory
        }
        viewModelScope.launch { Recommender.boostQuery(dao, t, 0.8) }
        load(t, search = true)
    }

    fun removeHistory(q: String) {
        searchHistory = searchHistory.filter { it != q }
        prefs.searchHistory = searchHistory
    }

    /** YouTube লিংক/শেয়ার থেকে খোলা */
    fun openLink(raw: String) {
        val url = raw.trim()
        val uri = Uri.parse(url)
        val path = uri.path ?: ""
        val link = PlaylistLinks.parse(url)
        if (path == "/playlist" && !uri.getQueryParameter("list").isNullOrBlank()) openPlaylist(url)
        else if (path.startsWith("/channel/") || path.startsWith("/c/") || path.startsWith("/user/") || path.startsWith("/@"))
            openChannel(url)
        else if (link != null) playWithPlaylist(url, link)
        else play(url)
    }

    /**
     * `watch?v=…&list=…` লিংক: ভিডিও সাথে সাথে চলে, পেছনে পুরো প্লেলিস্ট এনে কিউতে বসে (ভিডিওটা যে পজিশনে)।
     * শাফল চালু থাকলে প্লেলিস্টের ভেতরেই এলোমেলো চলে। ততক্ষণে অন্য কিছু চালু হলে কিউ বসানো হয় না।
     */
    private fun playWithPlaylist(url: String, link: PlaylistLinks.Link) {
        playGen++
        val gen = playGen
        clearQueue(); startPlay(url)
        viewModelScope.launch {
            try {
                var pl = YtRepository.playlist(PlaylistLinks.fetchUrl(url, link))
                var guard = 0
                while (pl.next != null && pl.videos.size < 500 && guard++ < 30) {
                    val (more, nx) = try { YtRepository.playlistMore(pl.url, pl.next!!) } catch (e: Exception) { break }
                    pl = pl.copy(videos = pl.videos + more, next = nx)
                }
                val idx = pl.videos.indexOfFirst { PlaylistLinks.videoId(it.url) == link.videoId }
                if (idx < 0 || gen != playGen) return@launch
                queue.start(pl.videos.map { it.toQ() }, idx, false, pl.name.ifBlank { null })   // চালানো ভিডিও আবার শুরু হয় না
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { /* প্লেলিস্ট না এলে ভিডিও একাই চলতে থাকে */ }
        }
    }


    /** #হ্যাশট্যাগ ট্যাপ: সব ওভারলে/প্লেয়ার সরিয়ে "#ট্যাগ" সার্চ */
    fun searchTag(tag: String) {
        expanded = false; channelOpen = false; playlistCtl.close()
        tab = 0; searching = true
        submitSearch("#$tag")
    }

    /** @হ্যান্ডেল ট্যাপ: চ্যানেল পেজ খোলে */
    fun openMention(handle: String) = openChannel("https://www.youtube.com/@$handle")

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
    fun blockChannel(name: String) { if (name.isNotBlank()) addBlock(ChannelSearch.blockKey(name)) }
    fun blockKeyword(k: String) { val t = k.trim().lowercase(); if (t.length >= 2) addBlock("kw:$t") }
    fun removeBlock(entry: String) = viewModelScope.launch { dao.removeBlock(entry) }

    // ---------- নতুন ভিডিওর নোটিফিকেশন ও DeArrow ----------
    /** অ্যাপ খোলার সময় নতুন ভার্সন থাকলে পপআপ (প্রতি ভার্সনে একবার) */
    var updatePopup by mutableStateOf<UpdateInfo?>(null); private set
    fun checkUpdatePopup() {
        if (!Updater.configured || !prefs.updateNotify) return
        viewModelScope.launch {
            val info = Updater.fetchLatest() ?: return@launch
            if (Updater.hasUpdate(info) && prefs.lastPopupUpdate != info.version) updatePopup = info
        }
    }
    fun dismissUpdatePopup() {
        updatePopup?.let { prefs.lastPopupUpdate = it.version }
        updatePopup = null
    }

    fun toggleUpdateNotify(v: Boolean) {
        updateNotifyOn = v; prefs.updateNotify = v
        UpdateScheduler.apply(ctx, v, replace = true)
    }
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

    // ---------- নতুন সেটিংস ----------
    fun applyThemeAuto(v: Boolean) { themeAuto = v; prefs.themeAuto = v }
    /** 0 = থিমের ডিফল্ট রঙ */
    fun chooseAccent(argb: Int) { accentColor = argb; prefs.accentColor = argb }
    fun applyDataSaver(v: Boolean) { dataSaver = v; prefs.dataSaver = v }
    fun applyBackgroundPlay(v: Boolean) { backgroundPlay = v; prefs.backgroundPlay = v }
    fun setPip(v: Boolean) { pipOn = v; prefs.pipPlay = v }
    fun applyAutoplay(v: Boolean) { autoplay = v; prefs.autoplay = v }
    fun setWifiOnlyDownload(v: Boolean) {
        wifiOnlyDl = v; prefs.wifiOnlyDownload = v
        if (downloads.value.any { it.state == 0 || it.state == 1 }) DownloadEngine.kick(ctx)
    }
    fun chooseParallelDownloads(n: Int) { parallelDl = n; prefs.parallelDownloads = n }
    fun setSaveSearchHistory(v: Boolean) { saveSearchHist = v; prefs.saveSearchHistory = v }
    fun setSaveWatchHistory(v: Boolean) { saveWatchHist = v; prefs.saveWatchHistory = v }
    fun clearSearchHistory() {
        searchHistory = emptyList(); prefs.searchHistory = searchHistory
        toast(L.t("সার্চ ইতিহাস মুছে গেছে"))
    }
    fun deleteHistoryItem(url: String) = viewModelScope.launch { dao.deleteHistory(url) }

    // ---------- লাইব্রেরি: প্লেলিস্ট ----------
    fun renamePlaylist(id: Long, name: String) {
        val t = name.trim()
        if (t.isNotEmpty()) viewModelScope.launch { dao.renamePlaylist(id, t) }
    }

    /** প্লেলিস্টের আইটেম ওপরে/নিচে সরানো: পুরো ক্রম নতুন করে লেখা হয় */
    fun movePlaylistItem(list: List<PlaylistItemEntity>, from: Int, to: Int) {
        if (from !in list.indices || to !in list.indices || from == to) return
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        viewModelScope.launch { m.forEachIndexed { i, e -> dao.setItemOrder(e.id, i + 1L) } }
    }

    // ---------- ডাউনলোড: গ্যালারিতে সেভ ও শেয়ার ----------
    private val exporter = DownloadExporter(ctx, viewModelScope) { toast(it) }
    /** MainActivity বসায়: পুরনো অ্যান্ড্রয়েডে স্টোরেজ পারমিশন চাওয়ার জন্য */
    var requestStoragePermission: (() -> Unit)?
        get() = exporter.requestStoragePermission
        set(v) { exporter.requestStoragePermission = v }

    fun saveToGallery(d: DownloadEntity) = exporter.saveToGallery(d)
    fun onStoragePermissionResult(granted: Boolean) = exporter.onPermissionResult(granted)
    fun shareDownload(d: DownloadEntity) = exporter.share(d)

    fun deleteDownloads(list: List<DownloadEntity>) = viewModelScope.launch {
        list.forEach { d ->
            if (local?.id == d.id) close()
            DownloadEngine.remove(dao, d)
        }
    }

    fun expand() { if (active) expanded = true }
    /** প্লেয়ার পেজ নিচে টানার অগ্রগতি (০..১): ভাসমান মোডে পেজ আঙুলের সাথে ছোট হয়ে ভাসমান উইন্ডোর দিকে যায় */
    var playerPull by mutableStateOf(0f); private set
    private var pullJob: Job? = null
    fun setPull(p: Float) { pullJob?.cancel(); playerPull = p }
    fun cancelPull() {
        pullJob?.cancel()
        val from = playerPull
        if (from <= 0f) return
        // viewModelScope-এ MonotonicFrameClock নেই, তাই Compose animate() নয়; সাধারণ coroutine লুপ (~১৮০ms)
        pullJob = viewModelScope.launch {
            val steps = 11
            for (i in 1..steps) {
                delay(16)
                val t = i / steps.toFloat()
                playerPull = from * (1f - t * t * (3f - 2f * t))   // smoothstep
            }
            playerPull = 0f
        }
    }
    /** প্লেয়ার পেজ এখন পর্দায় খোলা আছে কিনা; থাকলে ব্যাক সবার আগে প্লেয়ারকে মিনিমাইজ করবে */
    val playerShown: Boolean get() = expanded && (active || loadingNew)
    fun minimize() { expanded = false }
    fun close() {
        saveProgress(); recordSkip()
        clearQueue()
        player.stop(); player.clearMediaItems()
        PlayerHolder.clearNowPlaying()
        current = null; local = null; expanded = false
    }

    // ---------- Chromecast (CastController-এ ডেলিগেট) ----------
    fun showCastChooser(activityCtx: Context) = cast.showChooser(activityCtx)
    fun castToggle() = cast.toggle()
    fun castStop() = cast.stop()

    // ---------- হিস্টরি ও রিকমেন্ডেশন ----------
    private fun writeHistory(info: StreamInfo, pos: Long) = viewModelScope.launch {
        if (!saveWatchHist) return@launch
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

    // ---------- শুধু রিকমেন্ডেশন প্রোফাইল এক্সপোর্ট / ইম্পোর্ট ----------
    fun exportInterests(uri: Uri) = viewModelScope.launch {
        try {
            val json = ProfileBackup.interestsToJson(dao.interests())
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(json.toString(2)) }
            }
            toast(L.t("রিকমেন্ডেশন প্রোফাইল এক্সপোর্ট হয়েছে"))
        } catch (e: Exception) { toast(L.f("এক্সপোর্ট ব্যর্থ: {0}", e.message)) }
    }

    fun importInterests(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            val list = ProfileBackup.interestsFromJson(JSONObject(text))
            if (list == null) { toast(L.t("এটি TubeX-এর প্রোফাইল ফাইল নয়")); return@launch }
            dao.upsertInterests(list)
            loadInterests()
            toast(L.t("রিকমেন্ডেশন প্রোফাইল ইম্পোর্ট হয়েছে"))
        } catch (e: Exception) { toast(L.f("ইম্পোর্ট ব্যর্থ: {0}", e.message)) }
    }

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
    fun togglePoToken(v: Boolean) {
        poTokenOn = v; prefs.poToken = v
        com.tubex.app.data.potoken.PoTokenProviderImpl.enabled = v
        if (!v) com.tubex.app.data.potoken.PoTokenProviderImpl.reset()   // WebView ও মেমরি ছেড়ে দেয়
    }
    fun toggleRyd(v: Boolean) {
        rydOn = v; prefs.ryd = v
        if (v) current?.let { loadRyd(it.id) } else { dislikes = null; likes = current?.likeCount?.takeIf { it >= 0 } }
    }

    // ---------- মন্তব্য (CommentsController-এ ডেলিগেট) ----------
    fun loadMoreComments() = commentsCtl.loadMore()
    fun chooseCommentSort(i: Int) = commentsCtl.chooseSort(i)
    fun sortedComments(): List<CommentItem> = commentsCtl.sorted()
    fun toggleReplies(c: CommentItem) = commentsCtl.toggleReplies(c)
    fun loadMoreReplies(c: CommentItem) = commentsCtl.loadMoreReplies(c)

    // ---------- চ্যানেল ও সাবস্ক্রিপশন ----------
    fun openChannel(url: String?) {
        if (url == null) return
        expanded = false; channelOpen = true; channel = null
        channelPls.reset()
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

    // ---------- চ্যানেলের প্লেলিস্ট ট্যাব ও অনলাইন প্লেলিস্ট (PlaylistsController.kt-এ ডেলিগেট) ----------
    fun loadChannelPlaylists(more: Boolean = false) = channelPls.load(more)

    fun openPlaylist(url: String) { expanded = false; playlistCtl.open(url) }
    fun closePlaylist() = playlistCtl.close()
    fun loadMorePlaylist() = playlistCtl.loadMore()
    fun saveOnlinePlaylist() = playlistCtl.saveToLibrary()

    private fun VideoItem.toQ() = QItem(url, title, channel, thumb)

    /** index নম্বর থেকে (বা শাফল করে) প্লেলিস্ট চালায়; বাকি পেজগুলো পেছনে লোড হয়ে কিউতে যোগ হয় */
    fun playPlaylistFrom(index: Int, shuffleStart: Boolean = false) {
        val pl = playlistCtl.data ?: return
        if (pl.videos.isEmpty()) return
        playQueueItems(pl.videos.map { it.toQ() }, index, shuffleStart, pl.name.ifBlank { null })
        val queued = queue.items
        if (pl.next != null) viewModelScope.launch {
            val all = playlistCtl.loadAll()
            if (queue.items === queued && all.size > queued.size) queue.replaceItems(all.map { it.toQ() })   // এখনো একই কিউ থাকলে বাড়াই
        }
    }

    var channelMoreLoading by mutableStateOf(false); private set
    /** চ্যানেলের ভিডিও ট্যাবের পরের পেজ লোড (স্ক্রল শেষের কাছে এলে) */
    fun loadMoreChannelVideos() {
        val ch = channel ?: return
        if (channelMoreLoading || ch.next == null || ch.tab == null) return
        viewModelScope.launch {
            channelMoreLoading = true
            try {
                val (list, next) = YtRepository.channelMore(ch)
                val cur = channel
                if (cur != null && cur.url == ch.url) {
                    val seen = cur.videos.mapTo(HashSet()) { it.url }
                    channel = cur.copy(videos = cur.videos + list.filter { seen.add(it.url) }, next = next)
                }
            } catch (e: Exception) {
                val cur = channel   // বারবার ব্যর্থ চেষ্টা এড়াতে এখানেই থামি
                if (cur != null && cur.url == ch.url) channel = cur.copy(next = null)
            }
            channelMoreLoading = false
        }
    }

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
                val (subsN, histN) = withContext(Dispatchers.IO) { AppDb.get(ctx).withTransaction { NewPipeImport.importZip(ctx, dao, bytes) } }
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
            val prefsJson = ProfileSettings.toJson(this@MainViewModel, prefs)
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
            AppDb.get(ctx).withTransaction { ProfileBackup.restore(dao, root) }   // মাঝপথে ফেইল করলে কিছুই বদলাবে না
            root.optJSONObject("prefs")?.let { ProfileSettings.apply(this@MainViewModel, prefs, it) }
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
