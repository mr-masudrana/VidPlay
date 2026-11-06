package com.tubex.app.ui.library

import com.tubex.app.data.local.entity.DownloadEntity
import com.tubex.app.util.L
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(java.util.Locale.US, b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(java.util.Locale.US, b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(java.util.Locale.US, b / (1L shl 10).toDouble())
    else -> "$b B"
}

@Composable
internal fun DownloadList(vm: MainViewModel) {
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
