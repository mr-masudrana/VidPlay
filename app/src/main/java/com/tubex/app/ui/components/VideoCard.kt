package com.tubex.app.ui.components

import com.tubex.app.data.model.VideoItem
import com.tubex.app.data.recommend.Recommender
import com.tubex.app.data.remote.DeArrow
import com.tubex.app.util.L
import com.tubex.app.ui.main.LocalVm
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.util.fmtCount
import com.tubex.app.util.fmtDur

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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

@Composable
fun VideoCard(v: VideoItem, onChannel: () -> Unit = {}, onClick: () -> Unit) {
    val vm = LocalVm.current
    var menu by remember { mutableStateOf(false) }
    var showKw by remember { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf(false) }

    // DeArrow
    val vid = remember(v.url) { DeArrow.videoId(v.url) }
    if (vid != null && (DeArrow.titles || DeArrow.thumbs)) LaunchedEffect(vid) { DeArrow.ensure(vid) }
    val brand = vid?.let { DeArrow.cache[it] }
    val title = if (DeArrow.titles) brand?.title ?: v.title else v.title
    var origThumb by remember(v.url) { mutableStateOf(false) }
    val thumb = if (DeArrow.thumbs && !origThumb) brand?.thumb ?: v.thumb else v.thumb

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        Box {
            AsyncImage(
                model = thumb, contentDescription = null, contentScale = ContentScale.Crop,
                onError = { origThumb = true },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Chip)
            )
            Text(
                fmtDur(v.durationSec), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(if (v.durationSec <= 0) YtRed else Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Row(Modifier.padding(12.dp, 12.dp, 4.dp, 0.dp)) {
            val subAvatar = vm?.subs?.collectAsState()?.value
                ?.firstOrNull { it.url == v.channelUrl || it.name == v.channel }?.avatar
            Box(Modifier.clickable(onClick = onChannel)) {
                ChannelAvatar(v.channelAvatar ?: subAvatar, v.channelUrl, v.channel, 36.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                Text(
                    listOfNotNull(
                        v.channel.ifBlank { null },
                        fmtCount(v.views).ifBlank { null }?.let { L.f("{0} ভিউ", it) },
                        v.uploaded
                    ).joinToString(" • "),
                    color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (vm != null) Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(menu, { menu = false }) {
                    if (v.reason != null) DropdownMenuItem(
                        text = { Text(L.t("কেন সাজেস্ট হলো?")) }, onClick = { menu = false; showWhy = true }
                    )
                    DropdownMenuItem(text = { Text(L.t("পরে চালান")) }, onClick = { menu = false; vm.addToQueue(v, true) })
                    DropdownMenuItem(text = { Text(L.t("কিউতে যোগ")) }, onClick = { menu = false; vm.addToQueue(v, false) })
                    DropdownMenuItem(text = { Text(L.t("আগ্রহ নেই")) }, onClick = { menu = false; vm.notInterested(v) })
                    if (v.channel.isNotBlank()) DropdownMenuItem(
                        text = { Text(L.f("\"{0}\" চ্যানেল ব্লক", v.channel)) },
                        onClick = { menu = false; vm.blockChannel(v.channel) }
                    )
                    DropdownMenuItem(text = { Text(L.t("কিওয়ার্ড ব্লক…")) }, onClick = { menu = false; showKw = true })
                }
            }
        }
    }

    if (showWhy) AlertDialog(
        onDismissRequest = { showWhy = false },
        title = { Text(L.t("কেন সাজেস্ট হলো?")) },
        text = { Text(v.reason ?: "") },
        confirmButton = { TextButton(onClick = { showWhy = false }) { Text(L.t("ঠিক আছে")) } }
    )

    if (showKw && vm != null) {
        var kw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showKw = false },
            title = { Text(L.t("কিওয়ার্ড ব্লক")) },
            text = {
                Column {
                    Text(L.t("যে ভিডিওর টাইটেলে এই শব্দ থাকবে সেগুলো আর দেখাবে না।"), color = Sub, fontSize = 12.sp)
                    OutlinedTextField(kw, { kw = it }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(Recommender.keywords(v.title)) { w ->
                            Text(
                                w, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Chip)
                                    .clickable { kw = w }.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = kw.isNotBlank(), onClick = { vm.blockKeyword(kw); showKw = false }) { Text(L.t("ব্লক করুন")) }
            },
            dismissButton = { TextButton(onClick = { showKw = false }) { Text(L.t("বাতিল")) } }
        )
    }
}
