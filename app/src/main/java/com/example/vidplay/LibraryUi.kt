package com.example.vidplay

import android.app.DownloadManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("এখনো কোনো চ্যানেল সাবস্ক্রাইব করেননি", color = Sub) }
        return
    }
    LazyColumn {
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
        if (vm.loading && vm.subsFeed.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = YtRed) }
        }
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
            val names = listOf("ইতিহাস", "ফেভারিট", "প্লেলিস্ট", "ডাউনলোড")
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
private fun HistoryList(vm: MainViewModel) {
    val list by vm.history.collectAsState()
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("ইতিহাস খালি", color = Sub) }
        return
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
            }
        }
    }
}

@Composable
private fun FavoriteList(vm: MainViewModel) {
    val list by vm.favorites.collectAsState()
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("কোনো ফেভারিট নেই", color = Sub) }
        return
    }
    LazyColumn {
        items(list.size, key = { list[it].url }) { i ->
            val f = list[i]
            MediaRow(f.title, f.channel, f.thumb, onClick = { vm.playQueue(list.map { it.url }, i) })
        }
    }
}

// ---------- প্লেলিস্ট ----------
@Composable
private fun PlaylistSection(vm: MainViewModel) {
    val lists by vm.playlists.collectAsState()
    var open by remember { mutableStateOf<PlaylistRow?>(null) }
    var newName by remember { mutableStateOf("") }

    val cur = open
    if (cur != null) {
        PlaylistDetail(vm, cur) { open = null }
        return
    }
    Column {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it }, singleLine = true,
                label = { Text("নতুন প্লেলিস্ট") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = newName.isNotBlank(), onClick = { vm.createPlaylist(newName); newName = "" }) { Text("তৈরি") }
        }
        if (lists.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            Text("কোনো প্লেলিস্ট নেই", color = Sub)
        }
        LazyColumn {
            items(lists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { open = p }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("${p.itemCount} টি ভিডিও", color = Sub, fontSize = 12.sp)
                    }
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
    Column {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Button(
                enabled = entries.isNotEmpty(),
                onClick = { vm.playQueue(entries.map { it.url }, 0) },
                modifier = Modifier.padding(end = 12.dp)
            ) { Text("সব চালান") }
        }
        if (entries.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            Text("প্লেলিস্ট খালি — ভিডিও পেজ থেকে যোগ করুন", color = Sub)
        }
        LazyColumn {
            items(entries.size, key = { entries[it].id }) { i ->
                val e = entries[i]
                MediaRow(
                    e.title, e.channel, e.thumb,
                    onClick = { vm.playQueue(entries.map { it.url }, i) },
                    trailing = { IconButton(onClick = { vm.removeFromPlaylist(e.id) }) { Icon(Icons.Filled.Close, null) } }
                )
            }
        }
    }
}

// ---------- ডাউনলোড ----------
@Composable
private fun DownloadList(vm: MainViewModel) {
    val list by vm.downloads.collectAsState()
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("কোনো ডাউনলোড নেই", color = Sub) }
        return
    }
    Column {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.pauseAllDownloads() }) { Text("সব থামান") }
            OutlinedButton(onClick = { vm.resumeAllDownloads() }) { Text("সব শুরু করুন") }
        }
        LazyColumn { items(list, key = { it.id }) { d -> DownloadRow(vm, d) } }
    }
}

@Composable
private fun DownloadRow(vm: MainViewModel, d: DownloadEntity) {
    val done = d.state == 3 && File(d.path).exists()
    val kind = if (d.audioOnly) "অডিও" else if (d.height > 0) "${d.height}p" else "ভিডিও"
    val status = when (d.state) {
        0 -> "কিউতে অপেক্ষায়"
        1 -> "ডাউনলোড হচ্ছে ${d.progress / 10}%"
        2 -> "থামানো (${d.progress / 10}%)"
        3 -> if (done) "সম্পন্ন" else "ফাইল পাওয়া যায়নি"
        5 -> "মার্জ হচ্ছে…"
        else -> "ব্যর্থ — আবার চেষ্টা করুন"
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = done) { vm.playLocal(d) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        when (d.state) {
            0, 1 -> IconButton(onClick = { vm.pauseDownload(d.id) }) { Icon(Icons.Filled.Pause, null) }
            2 -> IconButton(onClick = { vm.resumeDownload(d.id) }) { Icon(Icons.Filled.PlayArrow, null) }
            4 -> IconButton(onClick = { vm.resumeDownload(d.id) }) { Icon(Icons.Filled.Refresh, null) }
        }
        IconButton(onClick = { vm.deleteDownload(d) }) { Icon(Icons.Filled.Delete, null) }
    }
}
