package com.tubex.app.ui.channel

import com.tubex.app.data.model.ChannelData
import com.tubex.app.data.model.VideoItem
import com.tubex.app.data.search.SearchFilter
import com.tubex.app.util.L
import com.tubex.app.player.ui.LinkText
import com.tubex.app.player.ui.rememberSheetMaxHeight
import com.tubex.app.ui.components.Avatar
import com.tubex.app.ui.components.ChannelSkeleton
import com.tubex.app.ui.components.VideoCard
import com.tubex.app.ui.theme.Bg
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.util.TextUtil
import com.tubex.app.util.fmtCount
import com.tubex.app.viewmodel.MainViewModel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** ভিডিও ট্যাবের ফিল্টার: 0 = নতুন (ইউটিউবের ক্রম), 1 = টপ (বেশি ভিউ), 2 = পুরনো (আগের আপলোড আগে) */
private fun sortChannelVideos(list: List<VideoItem>, sort: Int): List<VideoItem> = when (sort) {
    1 -> list.sortedByDescending { it.views }
    2 -> list.reversed().sortedByDescending { SearchFilter.ageHours(it.uploaded) ?: -1L }   // সমান বয়সের মধ্যে উল্টো ক্রম
    else -> list
}

@Composable
fun ChannelScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val subs by vm.subs.collectAsState()
    val ch = vm.channel
    var menu by remember { mutableStateOf(false) }
    var sort by remember(ch?.url) { mutableIntStateOf(0) }
    var showAbout by remember(ch?.url) { mutableStateOf(false) }
    var tab by remember(ch?.url) { mutableIntStateOf(0) }      // 0 = ভিডিও, 1 = প্লেলিস্ট
    val listState = rememberLazyListState()
    val shown = remember(ch?.videos, sort) { sortChannelVideos(ch?.videos ?: emptyList(), sort) }

    // তালিকার শেষের কাছে এলে পরের পেজ লোড
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(tab) { if (tab == 1) vm.loadChannelPlaylists() }
    LaunchedEffect(nearEnd, tab, ch?.videos?.size, ch?.next, vm.channelPlaylists?.size) {
        if (nearEnd) { if (tab == 0) vm.loadMoreChannelVideos() else vm.loadChannelPlaylists(more = true) }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeChannel() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(
                ch?.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (ch != null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(L.t("রিফ্রেশ")) }, onClick = { menu = false; vm.openChannel(ch.url) })
                    DropdownMenuItem(text = { Text(L.t("চ্যানেল শেয়ার")) }, onClick = {
                        menu = false
                        ctx.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, ch.url) }, null
                        ))
                    })
                    DropdownMenuItem(text = { Text(L.t("লিংক কপি")) }, onClick = {
                        menu = false
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("channel", ch.url))
                        Toast.makeText(ctx, L.t("লিংক কপি হয়েছে"), Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text(L.t("ব্রাউজারে খুলুন")) }, onClick = {
                        menu = false
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ch.url))) }
                    })
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
        val subbed = subs.any { it.url == ch.url }
        LazyColumn(state = listState) {
            item {
                ch.banner?.let {
                    AsyncImage(it, null, Modifier.fillMaxWidth().aspectRatio(16f / 5f), contentScale = ContentScale.Crop)
                }
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(ch.avatar, ch.name, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(ch.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        // নাম ও সাবস্ক্রাইবারের মাঝে @ইউজারনেম
                        if (!ch.handle.isNullOrBlank())
                            Text(ch.handle, color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val stats = listOfNotNull(
                            if (ch.subscribers >= 0) L.f("{0} সাবস্ক্রাইবার", fmtCount(ch.subscribers)) else null,
                            ch.videoCount?.let { L.f("{0} টি ভিডিও", it) }
                        ).joinToString(" • ")
                        if (stats.isNotEmpty()) Text(stats, color = Sub, fontSize = 13.sp)
                    }
                }
                // বিবরণের ছোট প্রিভিউ + "...more" → পুরো বিবরণ শিট
                val aboutText = remember(ch.description) { TextUtil.clean(ch.description) }
                if (aboutText.isNotBlank()) Column(
                    Modifier.fillMaxWidth().clickable { showAbout = true }.padding(horizontal = 16.dp)
                ) {
                    Text(aboutText, color = Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(L.t("আরও দেখুন"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Button(
                    onClick = { vm.toggleSub(ch.url, ch.name, ch.avatar) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (subbed) Chip else MaterialTheme.colorScheme.onBackground,
                        contentColor = if (subbed) MaterialTheme.colorScheme.onBackground else Bg
                    )
                ) { Text(if (subbed) L.t("সাবস্ক্রাইবড") else L.t("সাবস্ক্রাইব")) }
                // ট্যাব: ভিডিও / প্লেলিস্ট (চ্যানেলে প্লেলিস্ট ট্যাব থাকলে)
                if (ch.playlistsTab != null) {
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text(L.t("ভিডিও")) })
                        FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(L.t("প্লেলিস্ট")) })
                    }
                } else Text(L.t("ভিডিও"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
                if (tab == 0) {
                    // ফিল্টার: নতুন / টপ / পুরনো
                    val names = if (L.en) listOf("New", "Top", "Old") else listOf("নতুন", "টপ", "পুরনো")
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEachIndexed { i, n ->
                            FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(n) })
                        }
                    }
                }
            }
            if (tab == 0) {
                items(shown) { v -> VideoCard(v) { vm.play(v.url) } }
            } else {
                val pls = vm.channelPlaylists
                if (pls != null && pls.isEmpty() && !vm.channelPlaylistsLoading) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { Text(L.t("কোনো প্লেলিস্ট নেই"), color = Sub) }
                }
                items(pls.orEmpty()) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openPlaylist(p.url) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            p.thumb, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Chip)
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (p.count >= 0) Text(
                                L.f("{0} টি ভিডিও", p.count), color = Sub, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                if (vm.channelPlaylistsLoading) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
            if (tab == 0 && vm.channelMoreLoading) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
        if (showAbout) ChannelAboutSheet(vm, ch, onDismiss = { showAbout = false })
    }
}

/** চ্যানেলের পুরো বিবরণ: নাম, @হ্যান্ডেল, পরিসংখ্যান ও বিবরণ (লিংক, #ট্যাগ, @হ্যান্ডেল ট্যাপযোগ্য) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelAboutSheet(vm: MainViewModel, ch: ChannelData, onDismiss: () -> Unit) {
    val text = remember(ch.description) { TextUtil.clean(ch.description) }
    val maxH = rememberSheetMaxHeight()
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Chip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxH)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(L.t("বিবরণ"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(ch.avatar, ch.name, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(ch.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            if (!ch.handle.isNullOrBlank()) Text(ch.handle, color = Sub, fontSize = 13.sp)
                            val stats = listOfNotNull(
                                if (ch.subscribers >= 0) L.f("{0} সাবস্ক্রাইবার", fmtCount(ch.subscribers)) else null,
                                ch.videoCount?.let { L.f("{0} টি ভিডিও", it) }
                            ).joinToString(" • ")
                            if (stats.isNotEmpty()) Text(stats, color = Sub, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Sub.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                Column(Modifier.padding(16.dp, 0.dp, 16.dp, 32.dp)) {
                    if (text.isBlank()) Text(L.t("কোনো বিবরণ নেই"), color = Sub)
                    else LinkText(text, vm, seek = false, onDone = onDismiss)
                }
            }
        }
    }
}
