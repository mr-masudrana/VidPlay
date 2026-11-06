package com.tubex.app.ui.library

import com.tubex.app.util.L
import com.tubex.app.viewmodel.MainViewModel
import com.tubex.app.viewmodel.QItem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

internal fun libraryTitles() = listOf(L.t("ইতিহাস"), L.t("ফেভারিট"), L.t("প্লেলিস্ট"), L.t("ডাউনলোড"))

internal fun libraryIcon(i: Int): ImageVector = when (i) {
    0 -> Icons.Outlined.History
    1 -> Icons.Filled.Favorite
    2 -> Icons.Outlined.VideoLibrary
    else -> Icons.Outlined.Download
}

/** টপ বারে দেখানোর জন্য সেকশনের নাম */
fun libraryTitle(i: Int): String = libraryTitles().getOrElse(i) { L.t("লাইব্রেরি") }

@Composable
fun LibraryTab(vm: MainViewModel, skipAnim: Boolean = false) {
    AnimatedContent(
        targetState = vm.libraryPage,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            when {
                skipAnim -> EnterTransition.None togetherWith ExitTransition.None
                targetState != null ->
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
                else ->
                    fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(200)))
            }
        },
        label = "libraryPage"
    ) { p ->
        when (p) {
            null -> LibraryOverview(vm) { vm.libraryPage = it }
            0 -> HistoryList(vm)
            1 -> FavoriteList(vm)
            2 -> PlaylistSection(vm)
            else -> DownloadList(vm)
        }
    }
}

/** লাইব্রেরির মূল পেজ: প্রতিটি সেকশনে সর্বশেষ কয়েকটি আইটেম; "সব দেখুন" চাপলে সম্পূর্ণ তালিকার পেজ */
@Composable
fun LibraryOverview(vm: MainViewModel, onOpen: (Int) -> Unit) {
    val history by vm.history.collectAsState()
    val favs by vm.favorites.collectAsState()
    val lists by vm.playlists.collectAsState()
    val dls by vm.downloads.collectAsState()
    val favSorted = remember(favs) { favs.sortedByDescending { it.addedAt } }

    LazyColumn(state = vm.libraryListState, contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)) {
        // ইতিহাস
        item {
            SectionHeader(0, history.size) { onOpen(0) }
            if (history.isEmpty()) EmptyHint(
                if (vm.saveWatchHist) L.t("ইতিহাস খালি") else L.t("ওয়াচ হিস্টরি বন্ধ আছে (সেটিংস থেকে চালু করুন)")
            ) else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history.take(10), key = { it.url }) { h ->
                    MiniCard(
                        h.thumb, h.title, h.channel, onClick = { vm.play(h.url) },
                        progress = if (h.durationSec > 0) h.positionMs / (h.durationSec * 1000f) else null
                    )
                }
            }
        }
        // ফেভারিট
        item {
            SectionHeader(1, favs.size) { onOpen(1) }
            if (favSorted.isEmpty()) EmptyHint(L.t("কোনো ফেভারিট নেই"))
            else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(favSorted.take(10).size) { i ->
                    val f = favSorted[i]
                    MiniCard(f.thumb, f.title, f.channel, onClick = {
                        vm.playQueueItems(favSorted.map { QItem(it.url, it.title, it.channel, it.thumb) }, i)
                    })
                }
            }
        }
        // প্লেলিস্ট
        item {
            SectionHeader(2, lists.size) { onOpen(2) }
            if (lists.isEmpty()) EmptyHint(L.t("কোনো প্লেলিস্ট নেই"))
            else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lists.take(10), key = { it.id }) { p ->
                    PlaylistMiniCard(vm, p) { vm.libraryOpenPlaylist = p.id; onOpen(2) }
                }
            }
        }
        // ডাউনলোড
        item {
            SectionHeader(3, dls.size) { onOpen(3) }
            if (dls.isEmpty()) EmptyHint(L.t("কোনো ডাউনলোড নেই"))
            else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(dls.take(10), key = { it.id }) { d ->
                    val ok = d.state == 3 && File(d.path).exists()
                    val kind = if (d.audioOnly) L.t("অডিও") else if (d.height > 0) "${d.height}p" else L.t("ভিডিও")
                    val status = when (d.state) {
                        0 -> L.t("কিউতে অপেক্ষায়")
                        1 -> L.f("ডাউনলোড হচ্ছে {0}%", d.progress / 10)
                        2 -> L.f("থামানো ({0}%)", d.progress / 10)
                        3 -> if (ok) L.t("সম্পন্ন") else L.t("ফাইল পাওয়া যায়নি")
                        5 -> L.t("মার্জ হচ্ছে…")
                        else -> L.t("ব্যর্থ — আবার চেষ্টা করুন")
                    }
                    MiniCard(
                        d.thumb, d.title, status,
                        onClick = { if (ok) vm.playLocal(d) else onOpen(3) },
                        badge = kind,
                        progress = if (d.state in listOf(0, 1, 2, 5)) d.progress / 1000f else null
                    )
                }
            }
        }
    }
}
