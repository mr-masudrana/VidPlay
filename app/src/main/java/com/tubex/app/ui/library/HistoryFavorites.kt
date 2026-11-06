package com.tubex.app.ui.library

import com.tubex.app.util.L
import com.tubex.app.ui.components.HistoryThumb
import com.tubex.app.ui.theme.Sub
import com.tubex.app.viewmodel.MainViewModel
import com.tubex.app.viewmodel.QItem

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HistoryList(vm: MainViewModel) {
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
internal fun FavoriteList(vm: MainViewModel) {
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
