package com.example.vidplay

import android.app.DownloadManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

// ---------- লাইব্রেরি: ইতিহাস | ডাউনলোড ----------
@Composable
fun LibraryTab(vm: MainViewModel) {
    var sub by remember { mutableStateOf(0) }
    val on = MaterialTheme.colorScheme.onBackground
    Column {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ইতিহাস", "ডাউনলোড").forEachIndexed { i, t ->
                val sel = sub == i
                Text(
                    t, color = if (sel) Bg else on, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) on else Chip)
                        .clickable { sub = i }.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        if (sub == 0) HistoryList(vm) else DownloadList(vm)
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
private fun DownloadList(vm: MainViewModel) {
    val list by vm.downloads.collectAsState()
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("কোনো ডাউনলোড নেই", color = Sub) }
        return
    }
    LazyColumn { items(list, key = { it.id }) { d -> DownloadRow(vm, d) } }
}

@Composable
private fun DownloadRow(vm: MainViewModel, d: DownloadEntity) {
    val ctx = LocalContext.current
    val st by produceState(initialValue = Pair(-2, 0), key1 = d.id) {
        while (true) {
            val r = withContext(Dispatchers.IO) { Downloads.progress(ctx, d.id) }
            value = r
            if (r.first == DownloadManager.STATUS_SUCCESSFUL || r.first == DownloadManager.STATUS_FAILED || r.first == -1) break
            delay(1000)
        }
    }
    val done = st.first == DownloadManager.STATUS_SUCCESSFUL || (st.first == -1 && File(d.path).exists())
    val status = when {
        done -> if (d.audioOnly) "অডিও • ডাউনলোড সম্পন্ন" else "ভিডিও • ডাউনলোড সম্পন্ন"
        st.first == DownloadManager.STATUS_FAILED -> "ডাউনলোড ব্যর্থ"
        st.first == -2 -> "…"
        else -> "ডাউনলোড হচ্ছে ${st.second}%"
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
            Text(status, color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        IconButton(onClick = { vm.deleteDownload(d) }) { Icon(Icons.Filled.Delete, null) }
    }
}
