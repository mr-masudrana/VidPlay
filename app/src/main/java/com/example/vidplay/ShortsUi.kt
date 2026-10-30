package com.example.vidplay

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/** শর্টস: উপর-নিচ সোয়াইপ করা ভার্টিকাল ফিড। আলাদা হালকা প্লেয়ার ব্যবহার করে। */
@Composable
fun ShortsTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val favs by vm.favorites.collectAsState()

    val exo = remember { ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) exo.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); exo.release() }
    }
    LaunchedEffect(Unit) {
        vm.pauseMain()
        if (vm.shorts.isEmpty()) vm.loadShorts()
    }

    val items = vm.shorts
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (vm.shortsLoading) CircularProgressIndicator(color = YtRed)
            else TextButton(onClick = { vm.loadShorts() }) { Text(L.t("শর্টস লোড করুন")) }
        }
    } else {
        val pager = rememberPagerState(pageCount = { items.size })
        val current = pager.currentPage
        var failed by remember { mutableStateOf(false) }

        LaunchedEffect(current) {
            val s = items.getOrNull(current) ?: return@LaunchedEffect
            failed = false
            vm.onShortShown(s)
            val info = runCatching { YtRepository.streamInfo(s.url) }.getOrNull()
            if (info == null) { failed = true; return@LaunchedEffect }
            exo.setMediaSource(YtRepository.buildSource(info, 720))
            exo.prepare()
            exo.playWhenReady = true
        }
        LaunchedEffect(current) { if (current >= items.size - 3) vm.loadShorts(more = true) }

        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { p ->
            val v = items[p]
            val isCurrent = p == pager.currentPage
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (isCurrent) {
                    AndroidView(
                        factory = { c -> PlayerView(c).apply { useController = false; player = exo } },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                // ট্যাপ = পজ/প্লে
                Box(Modifier.matchParentSize().clickable { exo.playWhenReady = !exo.playWhenReady })

                if (isCurrent && failed) Text(
                    L.t("লোড করা যায়নি"), color = Color.White, modifier = Modifier.align(Alignment.Center)
                )

                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.78f).padding(16.dp)) {
                    Text(v.channel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(
                        v.title, color = Color.White, fontSize = 14.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val fav = favs.any { it.url == v.url }
                    IconButton(onClick = { vm.toggleFavoriteItem(v) }) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null,
                            tint = if (fav) YtRed else Color.White, modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = {
                        ctx.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, v.url) }, null
                            )
                        )
                    }) { Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    IconButton(onClick = { exo.pause(); vm.play(v.url) }) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
