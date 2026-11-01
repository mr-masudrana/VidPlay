package com.tubex.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ChannelScreen(vm: MainViewModel) {
    val subs by vm.subs.collectAsState()
    val ch = vm.channel

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeChannel() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(ch?.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1)
        }
        if (ch == null) {
            if (vm.channelLoading) ChannelSkeleton()
            else Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(vm.error ?: L.t("চ্যানেল লোড করা যায়নি"), color = Sub)
            }
            return@Column
        }
        val subbed = subs.any { it.url == ch.url }
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
                        if (ch.subscribers >= 0)
                            Text(L.f("{0} সাবস্ক্রাইবার", fmtCount(ch.subscribers)), color = Sub, fontSize = 13.sp)
                    }
                }
                if (!ch.description.isNullOrBlank()) Text(
                    ch.description, color = Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Button(
                    onClick = { vm.toggleSub(ch.url, ch.name, ch.avatar) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (subbed) Chip else MaterialTheme.colorScheme.onBackground,
                        contentColor = if (subbed) MaterialTheme.colorScheme.onBackground else Bg
                    )
                ) { Text(if (subbed) L.t("সাবস্ক্রাইবড") else L.t("সাবস্ক্রাইব")) }
                Text(L.t("ভিডিও"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
            }
            items(ch.videos) { v -> VideoCard(v) { vm.play(v.url) } }
        }
    }
}
