package com.tubex.app.ui.library

import com.tubex.app.data.local.entity.PlaylistRow
import com.tubex.app.util.L
import com.tubex.app.ui.theme.Sub
import com.tubex.app.viewmodel.MainViewModel
import com.tubex.app.viewmodel.QItem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PlaylistSection(vm: MainViewModel) {
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
        // ইউটিউব প্লেলিস্টের লিংক পেস্ট করে খোলা (চালানো বা লাইব্রেরিতে সেভ করা যায়)
        var link by remember { mutableStateOf("") }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = link, onValueChange = { link = it }, singleLine = true,
                label = { Text(L.t("ইউটিউব প্লেলিস্ট লিংক")) }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = link.isNotBlank(), onClick = { vm.openLink(link); link = "" }) { Text(L.t("লিংক খুলুন")) }
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
    BackHandler(enabled = !vm.playerShown) { onBack() }   // প্লেয়ার খোলা থাকলে ব্যাক আগে প্লেয়ার সামলায়
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
                onClick = { vm.playQueueItems(entries.map { QItem(it.url, it.title, it.channel, it.thumb) }, 0, title = name) },
                modifier = Modifier.padding(end = 12.dp)
            ) { Text(L.t("সব চালান")) }
            TextButton(
                enabled = entries.isNotEmpty(),
                onClick = { vm.playQueueItems(entries.map { QItem(it.url, it.title, it.channel, it.thumb) }, 0, shuffleStart = true, title = name) }
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
                    onClick = { vm.playQueueItems(entries.map { QItem(it.url, it.title, it.channel, it.thumb) }, i, title = name) },
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
