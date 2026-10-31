package com.tubex.app

import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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

// ---------- লাইব্রেরি: ইতিহাস | ফেভারিট | প্লেলিস্ট | ডাউনলোড ----------
@Composable
fun LibraryTab(vm: MainViewModel) {
    var sub by remember { mutableStateOf(0) }
    val on = MaterialTheme.colorScheme.onBackground
    Column {
        LazyRow(
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val names = listOf(L.t("ইতিহাস"), L.t("ফেভারিট"), L.t("প্লেলিস্ট"), L.t("ডাউনলোড"))
            items(names.size) { i ->
                val sel = sub == i
                Text(
                    names[i], color = if (sel) Bg else on, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) on else Chip)
                        .clickable { sub = i }.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        when (sub) {
            0 -> HistoryList(vm)
            1 -> FavoriteList(vm)
            2 -> PlaylistSection(vm)
            else -> DownloadList(vm)
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
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).background(Chip)
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
                    HistoryThumb(h, Modifier.width(128.dp).aspectRatio(16f / 9f))
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
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).background(Chip)
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
            when (d.state) {
                0, 1 -> IconButton(onClick = { vm.pauseDownload(d.id) }) { Icon(Icons.Filled.Pause, null) }
                2 -> IconButton(onClick = { vm.resumeDownload(d.id) }) { Icon(Icons.Filled.PlayArrow, null) }
                4 -> IconButton(onClick = { vm.resumeDownload(d.id) }) { Icon(Icons.Filled.Refresh, null) }
            }
            if (done) {
                IconButton(onClick = { vm.saveToGallery(d) }) { Icon(Icons.Filled.SaveAlt, null) }
                IconButton(onClick = { vm.shareDownload(d) }) { Icon(Icons.Filled.Share, null) }
            }
            IconButton(onClick = { vm.deleteDownload(d) }) { Icon(Icons.Filled.Delete, null) }
        }
    }
}
