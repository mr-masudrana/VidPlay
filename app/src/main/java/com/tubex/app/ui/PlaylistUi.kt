package com.tubex.app.ui

import com.tubex.app.core.L
import com.tubex.app.core.MainViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** ইউটিউব প্লেলিস্ট পেজ: সব চালান, শাফল, লাইব্রেরিতে সেভ; যেকোনো ভিডিও থেকে শুরু */
@Composable
fun PlaylistScreen(vm: MainViewModel) {
    val pl = vm.onlinePlaylist
    val listState = rememberLazyListState()

    // তালিকার শেষের কাছে এলে পরের পেজ লোড
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, pl?.videos?.size, pl?.next) { if (nearEnd) vm.loadMorePlaylist() }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closePlaylist() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(
                pl?.name ?: L.t("প্লেলিস্ট"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
        }
        if (pl == null) {
            if (vm.playlistLoading) VideoListSkeleton(4)
            else Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(L.t("প্লেলিস্ট লোড করা যায়নি"), color = Sub)
            }
            return@Column
        }
        LazyColumn(state = listState) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(pl.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val meta = listOfNotNull(
                        pl.uploader?.takeIf { it.isNotBlank() },
                        if (pl.count >= 0) L.f("{0} টি ভিডিও", pl.count) else null
                    ).joinToString(" • ")
                    if (meta.isNotEmpty()) Text(meta, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = pl.videos.isNotEmpty(),
                            onClick = { vm.playPlaylistFrom(0) }
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text(L.t("সব চালান"))
                        }
                        OutlinedButton(
                            enabled = pl.videos.isNotEmpty(),
                            onClick = { vm.playPlaylistFrom(0, shuffleStart = true) }
                        ) {
                            Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text(L.t("শাফল"))
                        }
                    }
                    OutlinedButton(
                        enabled = pl.videos.isNotEmpty(),
                        onClick = { vm.saveOnlinePlaylist() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text(L.t("লাইব্রেরিতে সেভ"))
                    }
                }
            }
            itemsIndexed(pl.videos) { i, v ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.playPlaylistFrom(i) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AsyncImage(
                            v.thumb, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Chip)
                        )
                        if (v.durationSec > 0) Text(
                            fmtDur(v.durationSec), color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(v.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                v.channel.ifBlank { null },
                                fmtCount(v.views).ifBlank { null }?.let { L.f("{0} ভিউ", it) }
                            ).joinToString(" • "),
                            color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            if (vm.playlistMoreLoading) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
    }
}
