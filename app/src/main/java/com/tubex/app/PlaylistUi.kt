package com.tubex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** সার্চ রেজাল্ট ও চ্যানেলের প্লেলিস্ট ট্যাবে দেখানো প্লেলিস্টের সারি */
@Composable
fun PlaylistResultRow(p: PlaylistCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Chip)) {
            AsyncImage(fixUrl(p.thumb), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Row(
                Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(12.dp))
                if (p.count >= 0) {
                    Spacer(Modifier.width(3.dp))
                    Text("${p.count}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            Text(
                listOfNotNull(
                    p.uploader.ifBlank { null },
                    p.count.takeIf { it >= 0 }?.let { L.f("{0} টি ভিডিও", it) }
                ).joinToString(" • "),
                color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** ইউটিউব প্লেলিস্টের পেজ: ভিডিওর তালিকা, সব চালান, শাফল, লাইব্রেরিতে সেভ */
@Composable
fun YtPlaylistScreen(vm: MainViewModel) {
    val pl = vm.ytPl
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeYtPlaylist() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(
                pl?.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (pl == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (vm.ytPlLoading) CircularProgressIndicator()
                else Text(vm.ytPlError ?: L.t("প্লেলিস্ট লোড করা যায়নি"), color = Sub, modifier = Modifier.padding(24.dp))
            }
            return@Column
        }
        var saved by remember(pl.url) { mutableStateOf(false) }
        LazyColumn {
            item {
                Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Chip)) {
                        AsyncImage(fixUrl(pl.thumb ?: pl.videos.firstOrNull()?.thumb), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(pl.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                pl.uploader.ifBlank { null },
                                L.f("{0} টি ভিডিও", if (pl.count >= 0) pl.count else pl.videos.size.toLong())
                            ).joinToString(" • "),
                            color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = pl.videos.isNotEmpty(), onClick = { vm.playYtPlaylist(0) }, modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(L.t("সব চালান"))
                    }
                    OutlinedButton(
                        enabled = pl.videos.isNotEmpty(), onClick = { vm.playYtPlaylist(0, shuffleStart = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(L.t("শাফল"))
                    }
                }
                OutlinedButton(
                    enabled = pl.videos.isNotEmpty() && !saved,
                    onClick = { saved = true; vm.saveYtPlaylist() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 12.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (saved) L.t("লাইব্রেরিতে সেভ হয়েছে") else L.t("লাইব্রেরিতে সেভ করুন"))
                }
                if (pl.videos.isEmpty()) Text(
                    L.t("প্লেলিস্ট খালি"), color = Sub, modifier = Modifier.padding(16.dp)
                )
            }
            itemsIndexed(pl.videos) { i, v ->
                VideoCard(v, onChannel = { vm.closeYtPlaylist(); vm.openChannel(v.channelUrl) }) { vm.playYtPlaylist(i) }
            }
        }
    }
}
