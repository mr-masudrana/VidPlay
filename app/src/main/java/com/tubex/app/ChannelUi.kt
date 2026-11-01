package com.tubex.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private fun shareLink(ctx: Context, name: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, name); putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { ctx.startActivity(Intent.createChooser(send, null)) }
}

private fun copyLink(ctx: Context, url: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("url", url))
    Toast.makeText(ctx, L.t("লিংক কপি হয়েছে"), Toast.LENGTH_SHORT).show()
}

/** TubeX নিজে YouTube লিংক ধরে, তাই চুজার থেকে নিজেকে বাদ দিয়ে ব্রাউজারে খোলা */
private fun openInBrowser(ctx: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    val chooser = Intent.createChooser(view, null).apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(ctx, MainActivity::class.java)))
    }
    runCatching { ctx.startActivity(chooser) }
}

@Composable
fun ChannelScreen(vm: MainViewModel) {
    val subs by vm.subs.collectAsState()
    val ctx = LocalContext.current
    val ch = vm.channel
    var tabIdx by remember(ch?.url) { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(tabIdx, ch?.url) { if (tabIdx == 1 && ch != null) vm.loadChannelPlaylists() }
    val on = MaterialTheme.colorScheme.onBackground
    val handle = ch?.handle ?: vm.channelExtra?.handle
    val videoCount = vm.channelExtra?.videoCount
    val about = vm.channelAboutOpen && ch != null
    val subbed = ch != null && subs.any { it.url == ch.url }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (about) vm.closeChannelAbout() else vm.closeChannel() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
            Text(
                if (about) L.t("বিবরণ") else ch?.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (ch != null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (subbed) L.t("সাবস্ক্রাইবড") else L.t("সাবস্ক্রাইব")) },
                        onClick = { menu = false; vm.toggleSub(ch.url, ch.name, ch.avatar) }
                    )
                    DropdownMenuItem(
                        text = { Text(L.t("সব ভিডিও চালান")) },
                        onClick = { menu = false; vm.playChannelVideos(false) }
                    )
                    DropdownMenuItem(
                        text = { Text(L.t("শাফল")) },
                        onClick = { menu = false; vm.playChannelVideos(true) }
                    )
                    DropdownMenuItem(text = { Text(L.t("শেয়ার")) }, onClick = { menu = false; shareLink(ctx, ch.name, ch.url) })
                    DropdownMenuItem(text = { Text(L.t("লিংক কপি করুন")) }, onClick = { menu = false; copyLink(ctx, ch.url) })
                    DropdownMenuItem(text = { Text(L.t("ব্রাউজারে খুলুন")) }, onClick = { menu = false; openInBrowser(ctx, ch.url) })
                    DropdownMenuItem(
                        text = { Text(L.f("\"{0}\" চ্যানেল ব্লক", ch.name)) },
                        onClick = { menu = false; vm.blockChannel(ch.name) }
                    )
                }
            }
        }
        if (ch == null) {
            if (vm.channelLoading) ChannelSkeleton()
            else Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(vm.error ?: L.t("চ্যানেল লোড করা যায়নি"), color = Sub)
            }
            return@Column
        }
        if (about) {
            ChannelAbout(vm, ch, handle, videoCount, ctx)
            return@Column
        }
        LazyColumn {
            item {
                ch.banner?.let {
                    AsyncImage(it, null, Modifier.fillMaxWidth().aspectRatio(16f / 5f), contentScale = ContentScale.Crop)
                }
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(ch.avatar, ch.name, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(ch.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (!handle.isNullOrBlank()) Text(handle, color = Sub, fontSize = 13.sp, maxLines = 1)
                        val stats = listOfNotNull(
                            if (ch.subscribers >= 0) L.f("{0} সাবস্ক্রাইবার", fmtCount(ch.subscribers)) else null,
                            videoCount?.let { L.f("{0} টি ভিডিও", it) }
                        ).joinToString(" • ")
                        if (stats.isNotEmpty()) Text(stats, color = Sub, fontSize = 13.sp)
                    }
                }
                if (!ch.description.isNullOrBlank()) Column(
                    Modifier.fillMaxWidth().clickable { vm.openChannelAbout() }.padding(horizontal = 16.dp)
                ) {
                    Text(ch.description, color = Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(L.t("আরও দেখুন"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                }
                Button(
                    onClick = { vm.toggleSub(ch.url, ch.name, ch.avatar) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (subbed) Chip else on,
                        contentColor = if (subbed) on else Bg
                    )
                ) { Text(if (subbed) L.t("সাবস্ক্রাইবড") else L.t("সাবস্ক্রাইব")) }
                Row(Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(L.t("ভিডিও"), L.t("প্লেলিস্ট")).forEachIndexed { i, label ->
                        FilterPill(label, i == tabIdx, on) { tabIdx = i }
                    }
                }
                if (tabIdx == 0) Row(
                    Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(L.t("নতুন"), L.t("জনপ্রিয়"), L.t("পুরনো")).forEachIndexed { i, label ->
                        FilterPill(label, i == vm.channelSort, on, small = true) { vm.setChannelSort(i) }
                    }
                }
                if (tabIdx == 0 && vm.channelMoreLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            if (tabIdx == 0) {
                val vids = when (vm.channelSort) {
                    1 -> vm.channelVideos.sortedByDescending { it.views }
                    2 -> vm.channelVideos.reversed()
                    else -> vm.channelVideos
                }
                if (vm.channelSort != 0 && vm.channelHasMore && !vm.channelMoreLoading) item {
                    Text(
                        L.f("শেষ {0} টি ভিডিও থেকে সাজানো", vids.size), color = Sub, fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 8.dp)
                    )
                }
                items(vids) { v -> VideoCard(v) { vm.play(v.url) } }
                if (vm.channelHasMore && !vm.channelMoreLoading) item {
                    TextButton(
                        onClick = { vm.loadMoreChannelVideos(false) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) { Text(L.t("আরও দেখুন")) }
                }
            } else {
                val pls = vm.channelPlaylists
                if (pls == null) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                } else if (pls.isEmpty()) item {
                    Text(L.t("কোনো প্লেলিস্ট নেই"), color = Sub, modifier = Modifier.padding(16.dp))
                } else items(pls) { p -> PlaylistResultRow(p) { vm.openYtPlaylist(p.url) } }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, on: androidx.compose.ui.graphics.Color, small: Boolean = false, onClick: () -> Unit) {
    Text(
        label, color = if (selected) Bg else on, fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) on else Chip)
            .clickable(onClick = onClick).padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 5.dp else 6.dp)
    )
}

/** "আরও দেখুন" চাপলে খোলা চ্যানেলের বিবরণ পেজ */
@Composable
private fun ChannelAbout(vm: MainViewModel, ch: ChannelData, handle: String?, videoCount: String?, ctx: Context) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(ch.avatar, ch.name, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(ch.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (!handle.isNullOrBlank()) Text(handle, color = Sub, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(L.t("বিবরণ"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            SelectableLinkText(
                ch.description ?: "",
                onUrl = { u -> openInBrowser(ctx, u) },
                onSeek = { },
                onTag = { t -> vm.openHashtag(t) },
                onMention = { h -> vm.openMention(h) },
                timestamps = false
            )
            Spacer(Modifier.height(20.dp))
            if (ch.subscribers >= 0) Text(L.f("{0} সাবস্ক্রাইবার", fmtCount(ch.subscribers)), color = Sub, fontSize = 14.sp)
            if (videoCount != null) Text(L.f("{0} টি ভিডিও", videoCount), color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Text(L.t("লিংক"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            SelectionContainer { Text(ch.url, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyLink(ctx, ch.url) }) { Text(L.t("লিংক কপি করুন")) }
                OutlinedButton(onClick = { shareLink(ctx, ch.name, ch.url) }) { Text(L.t("শেয়ার")) }
            }
        }
    }
}
