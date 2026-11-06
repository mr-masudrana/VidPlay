package com.tubex.app.player.ui

import com.tubex.app.util.L
import com.tubex.app.ui.components.ChannelAvatar
import com.tubex.app.ui.components.SkeletonBox
import com.tubex.app.ui.components.VideoCard
import com.tubex.app.ui.theme.Bg
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.util.fmtCount
import com.tubex.app.util.fmtTime
import com.tubex.app.viewmodel.MainViewModel

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Details(vm: MainViewModel) {
    val ctx = LocalContext.current
    val subs by vm.subs.collectAsState()
    val favs by vm.favorites.collectAsState()
    val lists by vm.playlists.collectAsState()
    val info = vm.current
    var showDesc by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.clickable(enabled = info != null) { showDesc = true }.padding(12.dp)) {
                Text(vm.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (info != null) {
                    // ভিউ • লাইক • ডিসলাইক • সময় — এরপর একই লাইনে "আরও দেখুন"
                    StatsLine(info, vm.likes, vm.dislikes, Modifier.padding(top = 6.dp)) {
                        Text(L.t("আরও দেখুন"), color = Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                } else Text(L.t("অফলাইন (ডাউনলোড করা)"), color = Sub, fontSize = 12.sp)
            }
        }

        if (info != null) {
            // চ্যানেল সারি
            item {
                val chUrl = info.uploaderUrl
                val avatar = info.uploaderAvatars.lastOrNull()?.url
                val subbed = subs.any { it.url == chUrl }
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clickable { vm.openChannel(chUrl) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelAvatar(avatar, info.uploaderUrl, info.uploaderName, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(info.uploaderName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (info.uploaderSubscriberCount >= 0)
                                Text(L.f("{0} সাবস্ক্রাইবার", fmtCount(info.uploaderSubscriberCount)), color = Sub, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { vm.toggleSub(chUrl, info.uploaderName, avatar) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (subbed) Chip else MaterialTheme.colorScheme.onBackground,
                            contentColor = if (subbed) MaterialTheme.colorScheme.onBackground else Bg
                        )
                    ) { Text(if (subbed) L.t("সাবস্ক্রাইবড") else L.t("সাবস্ক্রাইব")) }
                }
            }
            // অ্যাকশন
            item {
                val isFav = favs.any { it.url == info.url }
                LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Pill(if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, L.t("ফেভারিট")) { vm.toggleFavorite() }
                    }
                    item { Pill(Icons.Outlined.Add, L.t("প্লেলিস্টে যোগ")) { showPlaylists = true } }
                    item {
                        Pill(Icons.Outlined.VideoLibrary, L.t("কিউ") + if (vm.queueItems.isNotEmpty()) " (${vm.queueItems.size})" else "") { showQueue = true }
                    }
                    item { Pill(Icons.Outlined.Download, L.t("ডাউনলোড")) { showDownload = true } }
                    item {
                        Pill(Icons.Outlined.Share, L.t("শেয়ার")) {
                            ctx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, info.url)
                                }, null))
                        }
                    }
                }
            }
            // চ্যাপ্টার
            val chapters = info.streamSegments
            if (chapters.isNotEmpty()) item {
                Text(L.t("অধ্যায়"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp, 0.dp, 12.dp, 6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(chapters) { c ->
                        Column(
                            Modifier.widthIn(max = 180.dp).clip(RoundedCornerShape(10.dp)).background(Chip)
                                .clickable { vm.player.seekTo(c.startTimeSeconds * 1000L) }.padding(10.dp)
                        ) {
                            Text(fmtTime(c.startTimeSeconds.toLong()), color = YtRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(c.title ?: "", fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // মন্তব্য কার্ড
            item {
                Column(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Chip).clickable { showComments = true }.padding(12.dp)
                ) {
                    Text(
                        L.t("মন্তব্য") + if (vm.commentsTotal > 0) "  " + fmtCount(vm.commentsTotal.toLong()) else "",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    val first = vm.comments.firstOrNull()
                    if (vm.commentsLoading) SkeletonBox(Modifier.padding(top = 8.dp).fillMaxWidth(0.8f).height(14.dp))
                    else Text(
                        when {
                            first != null -> first.text
                            else -> L.t("মন্তব্য নেই বা বন্ধ আছে")
                        },
                        color = Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            items(vm.related) { v -> VideoCard(v, onChannel = { vm.openChannel(v.channelUrl) }) { vm.play(v.url) } }
        }
    }

    if (showComments) CommentsSheet(vm) { showComments = false }

    if (showDownload) DownloadSheet(vm) { showDownload = false }
    if (showQueue) QueueSheet(vm) { showQueue = false }
    if (showDesc && info != null) DescriptionSheet(vm, info) { showDesc = false }

    if (showPlaylists) ModalBottomSheet(onDismissRequest = { showPlaylists = false }, containerColor = Chip) {
        var name by remember { mutableStateOf("") }
        Text(L.t("প্লেলিস্টে যোগ করুন"), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(L.t("নতুন প্লেলিস্ট")) }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = { vm.createPlaylistAndAdd(name); name = ""; showPlaylists = false }
            ) { Text(L.t("তৈরি")) }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(lists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id); showPlaylists = false }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.name, modifier = Modifier.weight(1f))
                    Text(L.f("{0} টি", p.itemCount), color = Sub, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
