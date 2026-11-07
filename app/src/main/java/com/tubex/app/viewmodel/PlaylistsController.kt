package com.tubex.app.viewmodel

import com.tubex.app.data.local.AppDao
import com.tubex.app.data.local.entity.PlaylistEntity
import com.tubex.app.data.local.entity.PlaylistItemEntity
import com.tubex.app.data.model.ChannelData
import com.tubex.app.data.model.PlaylistData
import com.tubex.app.data.model.PlaylistEntry
import com.tubex.app.data.model.VideoItem
import com.tubex.app.data.remote.YtRepository
import com.tubex.app.util.L

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** চ্যানেলের "প্লেলিস্ট" ট্যাব। বর্তমান চ্যানেল জানতে [channel] ল্যাম্বডা ব্যবহার করে। */
class ChannelPlaylistsController(
    private val scope: CoroutineScope,
    private val channel: () -> ChannelData?
) {
    /** null = এখনো লোড হয়নি */
    var items by mutableStateOf<List<PlaylistEntry>?>(null); private set
    var loading by mutableStateOf(false); private set
    private var next: Page? = null

    /** অন্য চ্যানেল খুললে */
    fun reset() { items = null; next = null }

    fun load(more: Boolean = false) {
        val ch = channel() ?: return
        val tab = ch.playlistsTab ?: return
        if (loading) return
        if (more && next == null) return
        if (!more && items != null) return
        scope.launch {
            loading = true
            try {
                val (list, nx) = if (more) YtRepository.channelPlaylistsMore(tab, next!!)
                else YtRepository.channelPlaylists(tab)
                if (channel()?.url == ch.url) {
                    items = (if (more) items.orEmpty() else emptyList()) + list
                    next = nx
                }
            } catch (e: Exception) {
                if (channel()?.url == ch.url) { items = items ?: emptyList(); next = null }
            }
            loading = false
        }
    }
}

/** ইউটিউব প্লেলিস্ট (অনলাইন): খোলা, আরও লোড, সব পেজ আনা ও লাইব্রেরিতে সেভ। */
class OnlinePlaylistController(
    private val scope: CoroutineScope,
    private val dao: AppDao,
    private val toast: (String) -> Unit
) {
    var isOpen by mutableStateOf(false); private set
    var data by mutableStateOf<PlaylistData?>(null); private set
    var loading by mutableStateOf(false); private set
    var moreLoading by mutableStateOf(false); private set

    fun open(url: String) {
        isOpen = true; data = null
        scope.launch {
            loading = true
            try { data = YtRepository.playlist(url) } catch (e: Exception) { /* স্ক্রিনে "লোড করা যায়নি" দেখায় */ }
            loading = false
        }
    }

    fun close() { isOpen = false }

    fun loadMore() {
        val pl = data ?: return
        val next = pl.next ?: return
        if (moreLoading) return
        scope.launch {
            moreLoading = true
            try {
                val (list, nx) = YtRepository.playlistMore(pl.url, next)
                val cur = data
                if (cur != null && cur.url == pl.url) data = cur.copy(videos = cur.videos + list, next = nx)
            } catch (e: Exception) {
                val cur = data
                if (cur != null && cur.url == pl.url) data = cur.copy(next = null)
            }
            moreLoading = false
        }
    }

    /** বাকি সব পেজ লোড (সর্বোচ্চ ৫০০ ভিডিও) এবং পুরো তালিকা ফেরত */
    suspend fun loadAll(): List<VideoItem> {
        while (moreLoading) delay(100)
        var pl = data ?: return emptyList()
        var guard = 0
        while (pl.next != null && pl.videos.size < 500 && guard++ < 30) {
            val (list, nx) = try { YtRepository.playlistMore(pl.url, pl.next!!) } catch (e: Exception) { Pair(emptyList<VideoItem>(), null) }
            pl = pl.copy(videos = pl.videos + list, next = nx)
        }
        if (data?.url == pl.url) data = pl
        return pl.videos
    }

    /** পুরো প্লেলিস্ট লাইব্রেরির লোকাল প্লেলিস্ট হিসেবে সেভ */
    fun saveToLibrary() {
        val pl = data ?: return
        scope.launch {
            toast(L.t("সেভ হচ্ছে…"))
            val all = loadAll()
            if (all.isEmpty()) { toast(L.t("সেভ করা যায়নি")); return@launch }
            val existing = dao.playlistsOnce().map { it.name }.toSet()
            val base = pl.name.ifBlank { "Playlist" }
            var name = base; var n = 2
            while (name in existing) { name = "$base ($n)"; n++ }
            val now = System.currentTimeMillis()
            val id = dao.addPlaylist(PlaylistEntity(name = name, createdAt = now))
            all.forEachIndexed { i, v ->
                dao.addPlaylistItem(PlaylistItemEntity(
                    playlistId = id, url = v.url, title = v.title, channel = v.channel,
                    thumb = v.thumb, durationSec = v.durationSec, addedAt = now + i
                ))
            }
            toast(L.f("\"{0}\" লাইব্রেরিতে সেভ হয়েছে", name))
        }
    }
}
