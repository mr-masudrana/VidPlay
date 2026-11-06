package com.tubex.app.ui.library

import com.tubex.app.util.L
import com.tubex.app.ui.components.Avatar
import com.tubex.app.ui.components.VideoCard
import com.tubex.app.ui.components.VideoListSkeleton
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsTab(vm: MainViewModel) {
    val subs by vm.subs.collectAsState()
    LaunchedEffect(subs.map { it.url }) { vm.loadSubsFeed() }

    if (subs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(L.t("এখনো কোনো চ্যানেল সাবস্ক্রাইব করেননি"), color = Sub) }
        return
    }
    // আনসাবস্ক্রাইব করা চ্যানেল ফিল্টারে থাকলে সেটা ধরা হয় না
    val selected = vm.subsFilter?.takeIf { f -> subs.any { it.url == f } }
    val videos = if (selected != null) vm.subsFilterVideos else vm.subsFeed

    PullToRefreshBox(isRefreshing = vm.subsRefreshing, onRefresh = { vm.refreshSubs() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(subs) { s ->
                        val on = s.url == selected
                        Column(
                            Modifier.width(72.dp).clickable { vm.selectSubsChannel(s.url) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(62.dp).then(if (on) Modifier.border(2.5.dp, YtRed, CircleShape) else Modifier),
                                Alignment.Center
                            ) { Avatar(s.avatar, s.name, 56.dp) }
                            Text(
                                s.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            if (selected != null) {
                if (vm.subsFilterLoading && videos.isEmpty()) item { VideoListSkeleton(3) }
                else if (videos.isEmpty()) item {
                    Text(L.t("কোনো ভিডিও পাওয়া যায়নি"), color = Sub, modifier = Modifier.padding(16.dp))
                }
            } else if (vm.loading && vm.subsFeed.isEmpty()) item { VideoListSkeleton(3) }
            items(videos) { v -> VideoCard(v, onChannel = { vm.openChannel(v.channelUrl) }) { vm.play(v.url) } }
        }
    }
}
