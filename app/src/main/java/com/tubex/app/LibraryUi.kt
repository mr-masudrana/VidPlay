package com.tubex.app

import android.app.DownloadManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// ---------- সাবস্ক্রিপশন ট্যাব ----------
@Composable
fun SubsTab(vm: MainViewModel) {
    val subs by vm.subs.collectAsState()
    LaunchedEffect(subs.map { it.url }) { vm.loadSubsFeed() }

    if (subs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(L.t("এখনো কোনো চ্যানেল সাবস্ক্রাইব করেননি"), color = Sub) }
        return
    }
    LazyColumn {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.loadSubsFeed(true) }) { Text(L.t("রিফ্রেশ")) }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(subs) { s ->
                    Column(
                        Modifier.width(72.dp).clickable { vm.openChannel(s.url) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Avatar(s.avatar, s.name, 56.dp)
                        Text(s.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (vm.loading && vm.subsFeed.isEmpty()) item { VideoListSkeleton(3) }
        items(vm.subsFeed) { v -> VideoCard(v, onChannel = { vm.openChannel(v.channelUrl) }) { vm.play(v.url) } }
    }
}

// ---------- লাইব্রেরি: সেকশন (ইতিহাস | ফেভারিট | প্লেলিস্ট | ডাউনলোড) + "সব দেখুন" পেজ ----------
private fun libraryTitles() = listOf(L.t("ইতিহাস"), L.t("ফেভারিট"), L.t("প্লেলিস্ট"), L.t("ডাউনলোড"))
private fun libraryIcon(i: Int): ImageVector = when (i) {
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

@Composable
private fun SectionHeader(i: Int, count: Int, onAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Chip), Alignment.Center) {
            Icon(libraryIcon(i), null, tint = YtRed, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(libraryTitles()[i], fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (count > 0) Text("  $count", color = Sub, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAll) {
            Text(L.t("সব দেখুন"), fontSize = 13.sp)
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(72.dp)
            .clip(RoundedCornerShape(14.dp)).background(Chip.copy(alpha = 0.5f)),
        Alignment.Center
    ) { Text(text, color = Sub, fontSize = 13.sp) }
}

/** ক্যারোসেলের ছোট কার্ড: থাম্বনেইল + (ঐচ্ছিক) ব্যাজ/প্রগ্রেস + শিরোনাম + নিচের লেখা */
@Composable
private fun MiniCard(
    thumb: String?, title: String, sub: String, onClick: () -> Unit,
    progress: Float? = null, badge: String? = null, overlayIcon: ImageVector? = null
) {
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Chip)) {
            if (thumb != null) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (overlayIcon != null) Icon(
                overlayIcon, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(30.dp)
            )
            if (badge != null) Text(
                badge, color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0xB3000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (progress != null && progress > 0f) LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = YtRed, trackColor = androidx.compose.ui.graphics.Color(0x66000000)
            )
        }
        Text(
            title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        Text(
            sub, color = Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun PlaylistMiniCard(vm: MainViewModel, p: PlaylistRow, onOpen: () -> Unit) {
    val items by remember(p.id) { vm.playlistItems(p.id) }.collectAsState(initial = emptyList())
    MiniCard(
        thumb = items.firstOrNull()?.thumb, title = p.name,
        sub = L.f("{0} টি ভিডিও", p.itemCount), onClick = onOpen,
        badge = if (p.itemCount > 0) "${p.itemCount}" else null,
        overlayIcon = if (items.firstOrNull()?.thumb == null) Icons.Outlined.VideoLibrary else null
    )
}

/** লাইব্রেরির মূল পেজ: প্রতিটি সেকশনে সর্বশেষ কয়েকটি আইটেম; "সব দেখুন" চাপলে সম্পূর্ণ তালিকার পেজ */
@Composable
fun LibraryOverview(vm: MainViewModel, onOpen: (Int) -> Unit) {
    val history by vm.history.collectAsState()
    val favs by vm.favorites.collectAsState()
    val lists by vm.playlists.collectAsState()
    val dls by vm.downloads.collectAsState()
    val favSorted = remember(favs) { favs.sortedByDescending { it.addedAt } }

    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)) {
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

@Composable
private fun MediaRow(
    title: String, channel: String, thumb: String?, onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            thumb, null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Chip)
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(channel, color = Sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
        trailing()
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, null) } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    )
}

@Composable
private fun HistoryList(vm: MainViewModel) {
    val all by vm.history.collectAsState()
    var q by remember { mutableStateOf("") }
    if (all.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(if (vm.saveWatchHist) L.t("ইতিহাস খালি") else L.t("ওয়াচ হিস্টরি বন্ধ আছে (সেটিংস থেকে চালু করুন)"), color = Sub)
        }
        return
    }
    val list = remember(all, q) {
        val t = q.trim()
        if (t.isEmpty()) all else all.filter { it.title.contains(t, true) || it.channel.contains(t, true) }
    }
    Column {
        SearchBox(q, { q = it }, L.t("ইতিহাসে খুঁজুন"))
        if (list.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            Text(L.t("কিছু পাওয়া যায়নি"), color = Sub)
        }
        LazyColumn {
            items(list, key = { it.url }) { h ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.play(h.url) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistoryThumb(h, Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(h.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(h.channel, color = Sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                    IconButton(onClick = { vm.deleteHistoryItem(h.url) }) { Icon(Icons.Filled.Close, null) }
                }
            }
        }
    }
}

@Composable
private fun FavoriteList(vm: MainViewModel) {
    val all by vm.favorites.collectAsState()
    var sort by remember { mutableStateOf(0) }   // 0 নতুন আগে, 1 পুরনো আগে, 2 নাম, 3 চ্যানেল
    if (all.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(L.t("কোনো ফেভারিট নেই"), color = Sub) }
        return
    }
    val list = remember(all, sort) {
        when (sort) {
            1 -> all.sortedBy { it.addedAt }
            2 -> all.sortedBy { it.title.lowercase() }
            3 -> all.sortedBy { it.channel.lowercase() }
            else -> all.sortedByDescending { it.addedAt }
        }
    }
    val names = listOf(L.t("নতুন আগে"), L.t("পুরনো আগে"), L.t("নাম"), L.t("চ্যানেল"))
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(names.size) { i ->
                FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(names[i]) })
            }
        }
        LazyColumn {
            items(list.size, key = { list[it].url }) { i ->
                val f = list[i]
                MediaRow(f.title, f.channel, f.thumb, onClick = { vm.playQueueItems(list.map { QItem(it.url, it.title, it.channel, it.thumb) }, i) })
            }
        }
    }
}

// ---------- প্লেলিস্ট ----------
@Composable
private fun PlaylistSection(vm: MainViewModel) {
    val lists by vm.playlists.collectAsState()
    var open by remember { mutableStateOf<PlaylistRow?>(null) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<PlaylistRow?>(null) }

    val pendingId = vm.libraryOpenPlaylist
    LaunchedEffect(pendingId, lists) {
        if (pendingId != null) lists.firstOrNull { it.id == pendingId }?.let { open = it; vm.libraryOpenPlaylist = null }
    }
    val cur = open
    if (cur != null) {
        PlaylistDetail(vm, cur) { open = null }
        return
    }
    renaming?.let { r -> RenameDialog(r.name, onDismiss = { renaming = null }) { vm.renamePlaylist(r.id, it); renaming = null } }
    Column {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it }, singleLine = true,
                label = { Text(L.t("নতুন প্লেলিস্ট")) }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = newName.isNotBlank(), onClick = { vm.createPlaylist(newName); newName = "" }) { Text(L.t("তৈরি")) }
        }
        if (lists.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            Text(L.t("কোনো প্লেলিস্ট নেই"), color = Sub)
        }
        LazyColumn {
            items(lists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { open = p }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(L.f("{0} টি ভিডিও", p.itemCount), color = Sub, fontSize = 12.sp)
                    }
                    IconButton(onClick = { renaming = p }) { Icon(Icons.Filled.Edit, null) }
                    IconButton(onClick = { vm.deletePlaylist(p.id) }) { Icon(Icons.Filled.Delete, null) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(vm: MainViewModel, p: PlaylistRow, onBack: () -> Unit) {
    BackHandler { onBack() }
    val entries by remember(p.id) { vm.playlistItems(p.id) }.collectAsState(initial = emptyList())
    var name by remember(p.id) { mutableStateOf(p.name) }
    var renameOpen by remember { mutableStateOf(false) }
    if (renameOpen) RenameDialog(name, onDismiss = { renameOpen = false }) { vm.renamePlaylist(p.id, it); name = it.trim(); renameOpen = false }
    Column {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { renameOpen = true }) { Icon(Icons.Filled.Edit, null) }
            Button(
                enabled = entries.isNotEmpty(),
                onClick = { vm.playQueueItems(entries.map { QItem(it.url, it.title, it.channel, it.thumb) }, 0) },
                modifier = Modifier.padding(end = 12.dp)
            ) { Text(L.t("সব চালান")) }
            TextButton(
                enabled = entries.isNotEmpty(),
                onClick = { vm.playQueueItems(entries.map { QItem(it.url, it.title, it.channel, it.thumb) }, 0, shuffleStart = true) }
            ) { Text(L.t("শাফল")) }
        }
        if (entries.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            Text(L.t("প্লেলিস্ট খালি — ভিডিও পেজ থেকে যোগ করুন"), color = Sub)
        }
        LazyColumn {
            items(entries.size, key = { entries[it].id }) { i ->
                val e = entries[i]
                MediaRow(
                    e.title, e.channel, e.thumb,
                    onClick = { vm.playQueueItems(entries.map { QItem(it.url, it.title, it.channel, it.thumb) }, i) },
                    trailing = {
                        Column {
                            IconButton(enabled = i > 0, onClick = { vm.movePlaylistItem(entries, i, i - 1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.KeyboardArrowUp, null)
                            }
                            IconButton(enabled = i < entries.lastIndex, onClick = { vm.movePlaylistItem(entries, i, i + 1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, null)
                            }
                        }
                        IconButton(onClick = { vm.removeFromPlaylist(e.id) }) { Icon(Icons.Filled.Close, null) }
                    }
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.t("প্লেলিস্টের নাম বদলান")) },
        text = {
            OutlinedTextField(
                value = t, onValueChange = { t = it }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done)
            )
        },
        confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { onOk(t) }) { Text(L.t("ঠিক আছে")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L.t("বাতিল")) } }
    )
}

// ---------- ডাউনলোড ----------
private fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(java.util.Locale.US, b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(java.util.Locale.US, b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(java.util.Locale.US, b / (1L shl 10).toDouble())
    else -> "$b B"
}

@Composable
private fun DownloadList(vm: MainViewModel) {
    val list by vm.downloads.collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(L.t("কোনো ডাউনলোড নেই"), color = Sub) }
        return
    }
    // অস্তিত্বহীন আইটেম সিলেকশন থেকে বাদ
    val ids = list.map { it.id }.toSet()
    if (selected.any { it !in ids }) selected = selected.filter { it in ids }.toSet()

    val running = list.filter { it.state != 3 }
    val done = list.filter { it.state == 3 }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }

    // মোট জায়গা (সম্পন্ন ফাইলের)
    val total by produceState(0L, done.map { it.path to it.id }) {
        value = withContext(Dispatchers.IO) { done.sumOf { d -> File(d.path).takeIf { it.exists() }?.length() ?: 0L } }
    }

    Column {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.f("{0} টি বাছাই করা", selected.size), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = list.map { it.id }.toSet() }) { Text(L.t("সব")) }
                TextButton(onClick = { selected = emptySet() }) { Text(L.t("বাতিল")) }
                IconButton(onClick = {
                    vm.deleteDownloads(list.filter { it.id in selected })
                    selected = emptySet()
                }) { Icon(Icons.Filled.Delete, null) }
            }
        } else {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.pauseAllDownloads() }) { Text(L.t("সব থামান")) }
                OutlinedButton(onClick = { vm.resumeAllDownloads() }) { Text(L.t("সব শুরু করুন")) }
            }
        }
        LazyColumn {
            if (running.isNotEmpty()) {
                item { DlHeader(L.f("চলছে / অপেক্ষায় ({0})", running.size)) }
                items(running, key = { it.id }) { d -> DownloadRow(vm, d, selected, selecting) { id -> selected = if (id in selected) selected - id else selected + id } }
            }
            if (done.isNotEmpty()) {
                item { DlHeader(L.f("সম্পন্ন ({0}) • {1}", done.size, fmtSize(total))) }
                items(done, key = { it.id }) { d -> DownloadRow(vm, d, selected, selecting) { id -> selected = if (id in selected) selected - id else selected + id } }
            }
        }
    }
}

@Composable
private fun DlHeader(t: String) =
    Text(t, color = Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
    vm: MainViewModel, d: DownloadEntity, selected: Set<Long>, selecting: Boolean, toggle: (Long) -> Unit
) {
    val done = d.state == 3 && File(d.path).exists()
    val kind = if (d.audioOnly) L.t("অডিও") else if (d.height > 0) "${d.height}p" else L.t("ভিডিও")
    val size by produceState(0L, d.id, d.state) {
        value = if (d.state == 3) withContext(Dispatchers.IO) { File(d.path).takeIf { it.exists() }?.length() ?: 0L } else 0L
    }
    val status = when (d.state) {
        0 -> L.t("কিউতে অপেক্ষায়")
        1 -> L.f("ডাউনলোড হচ্ছে {0}%", d.progress / 10)
        2 -> L.f("থামানো ({0}%)", d.progress / 10)
        3 -> if (done) L.t("সম্পন্ন") + if (size > 0) " • " + fmtSize(size) else "" else L.t("ফাইল পাওয়া যায়নি")
        5 -> L.t("মার্জ হচ্ছে…")
        else -> L.t("ব্যর্থ — আবার চেষ্টা করুন")
    }
    val isSel = d.id in selected
    var showInfo by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (isSel) Chip else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(
                onClick = { if (selecting) toggle(d.id) else if (done) vm.playLocal(d) },
                onLongClick = { toggle(d.id) }
            ).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) Icon(
            if (isSel) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
            tint = if (isSel) YtRed else Sub, modifier = Modifier.padding(end = 10.dp)
        )
        AsyncImage(
            d.thumb, null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Chip)
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$kind • $status", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            if (d.state in listOf(0, 1, 2, 5)) LinearProgressIndicator(
                progress = { d.progress / 1000f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                color = YtRed, trackColor = Chip
            )
        }
        if (!selecting) {
            // ৩ ডট মেনু: সব অ্যাকশন এক জায়গায়
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    @Composable
                    fun Item(text: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) =
                        DropdownMenuItem(
                            text = { Text(text, color = tint ?: MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menu = false; onClick() }
                        )
                    if (done) Item(L.t("প্লে করুন"), Icons.Filled.PlayArrow) { vm.playLocal(d) }
                    when (d.state) {
                        0, 1 -> Item(L.t("থামান"), Icons.Filled.Pause) { vm.pauseDownload(d.id) }
                        2 -> Item(L.t("আবার শুরু করুন"), Icons.Filled.PlayArrow) { vm.resumeDownload(d.id) }
                        4 -> Item(L.t("আবার চেষ্টা করুন"), Icons.Filled.Refresh) { vm.resumeDownload(d.id) }
                    }
                    if (done) {
                        Item(L.t("গ্যালারিতে সেভ করুন"), Icons.Filled.SaveAlt) { vm.saveToGallery(d) }
                        Item(L.t("শেয়ার করুন"), Icons.Filled.Share) { vm.shareDownload(d) }
                    }
                    Item(L.t("অনলাইনে দেখুন"), Icons.Filled.OpenInNew) { vm.play(d.videoUrl) }
                    Item(L.t("বাছাই করুন"), Icons.Filled.CheckCircle) { toggle(d.id) }
                    Item(L.t("বিস্তারিত"), Icons.Filled.Info) { showInfo = true }
                    Item(L.t("মুছুন"), Icons.Filled.Delete, tint = YtRed) { vm.deleteDownload(d) }
                }
            }
        }
    }
    if (showInfo) AlertDialog(
        onDismissRequest = { showInfo = false },
        title = { Text(d.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(L.f("চ্যানেল: {0}", d.channel), fontSize = 13.sp)
                Text(L.f("ধরন: {0}", kind), fontSize = 13.sp)
                Text(L.f("অবস্থা: {0}", status), fontSize = 13.sp)
                Text(L.f("ফাইলের অবস্থান: {0}", d.path), fontSize = 12.sp, color = Sub)
            }
        },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text(L.t("বন্ধ করুন")) } }
    )
}
