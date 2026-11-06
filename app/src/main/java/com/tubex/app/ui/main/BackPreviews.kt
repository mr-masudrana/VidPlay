package com.tubex.app.ui.main

import com.tubex.app.util.L
import com.tubex.app.ui.components.VideoCard
import com.tubex.app.ui.components.VideoListSkeleton
import com.tubex.app.ui.home.CategoryRow
import com.tubex.app.ui.library.LibraryOverview
import com.tubex.app.ui.settings.SettingsTopicList
import com.tubex.app.ui.theme.Bg
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** লাইব্রেরির "সব দেখুন" পেজ থেকে ব্যাকে নিচে দেখানো মূল পেজের স্থির প্রিভিউ */
@Composable
internal fun LibraryOverviewPreview(vm: MainViewModel, statusDp: Dp, bottomDp: Dp) {
    Box(Modifier.fillMaxSize().background(Bg).padding(top = statusDp + TopBarH, bottom = bottomDp)) {
        LibraryOverview(vm) { }
    }
    Box(
        Modifier.fillMaxWidth().padding(top = statusDp).height(TopBarH).background(Bg),
        Alignment.CenterStart
    ) { Text(L.t("লাইব্রেরি"), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp)) }
}

/** সেটিংস টপিক পেজ থেকে ব্যাকে নিচে দেখানো টপিক তালিকার স্থির প্রিভিউ */
@Composable
internal fun SettingsListPreview(vm: MainViewModel, statusDp: Dp, bottomDp: Dp) {
    Box(Modifier.fillMaxSize().background(Bg).padding(top = statusDp + TopBarH, bottom = bottomDp)) {
        SettingsTopicList(vm.settingsListScroll) { }
    }
    Box(
        Modifier.fillMaxWidth().padding(top = statusDp).height(TopBarH).background(Bg),
        Alignment.CenterStart
    ) { Text(L.t("সেটিংস"), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp)) }
}

/** ব্যাক জেসচারে নিচে দেখানো আগের হোম পেজের স্থির প্রিভিউ (ইন্টারঅ্যাকশন নেই) */
@Composable
internal fun HomePreview(vm: MainViewModel, statusDp: Dp, bottomDp: Dp) {
    val list = remember(vm.chipIndex) { vm.previewFeed() }
    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(
            Modifier.fillMaxSize(), userScrollEnabled = false,
            contentPadding = PaddingValues(top = statusDp + TopBarH + ChipsH, bottom = bottomDp)
        ) {
            if (list.isEmpty()) item { VideoListSkeleton(3) }
            else items(list.take(8)) { v -> VideoCard(v) { } }
        }
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = statusDp).background(Bg)) {
            Row(
                Modifier.fillMaxWidth().height(TopBarH).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayCircle, null, tint = YtRed, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
                Text("TubeX", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(48.dp), Alignment.Center) { Icon(Icons.Filled.Search, null) }
            }
            CategoryRow(vm, vm.chipIndex)
        }
        if (statusDp > 0.dp) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(statusDp).background(Bg))
    }
}
