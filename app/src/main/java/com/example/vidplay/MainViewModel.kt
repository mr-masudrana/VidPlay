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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dao = AppDb.get(app).dao()
    val player = PlayerHolder.get(app)

    // Room
    val subs = dao.subs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = dao.downloads().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // হোম / সাবস্ক্রিপশন ফিড
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

    // মন্তব্য
    var comments by mutableStateOf<List<CommentItem>>(emptyList()); private set
    var commentsTotal by mutableStateOf(0); private set
    var commentsLoading by mutableStateOf(false); private set

    // চ্যানেল
    var channelOpen by mutableStateOf(false); private set
    var channel by mutableStateOf<ChannelData?>(null); private set
    var channelLoading by mutableStateOf(false); private set

    val active get() = current != null || local != null
    val title get() = current?.name ?: local?.title ?: ""
    val channelName get() = current?.uploaderName ?: ""

    init {
        load(null)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(e: PlaybackException) { error = e.message ?: "প্লেব্যাক এরর" }
        })
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
        try {
            val info = YtRepository.streamInfo(url)
            local = null; current = info; quality = 0
            heights = YtRepository.heights(info)
            related = YtRepository.related(info)
            player.setMediaSource(YtRepository.buildSource(info))
            player.prepare(); player.playWhenReady = true
            expanded = true
            loadComments(url)
        } catch (e: Exception) { error = e.message ?: "ভিডিও চালানো যায়নি"; toast(error!!) }
        opening = false
    }

    fun playLocal(d: DownloadEntity) {
        current = null; local = d; related = emptyList(); comments = emptyList(); heights = emptyList()
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

    fun expand() { if (active) expanded = true }
    fun minimize() { expanded = false }
    fun close() {
        player.stop(); player.clearMediaItems()
        current = null; local = null; expanded = false
    }

    // ---------- মন্তব্য ----------
    private fun loadComments(url: String) = viewModelScope.launch {
        commentsLoading = true; comments = emptyList(); commentsTotal = 0
        runCatching { YtRepository.comments(url) }.onSuccess { (n, list) ->
            commentsTotal = n; comments = list
        }
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
