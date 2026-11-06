package com.tubex.app.player.ui

import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
fun MiniPlayer(vm: MainViewModel) {
    Row(Modifier.fillMaxWidth().background(Chip), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { player = vm.player; useController = false } },
            onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.matchParentSize().clickable { vm.expand() })
        }
        Column(Modifier.weight(1f).clickable { vm.expand() }.padding(horizontal = 10.dp)) {
            Text(vm.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(vm.channelName, color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { if (vm.isPlaying) vm.player.pause() else vm.player.play() }) {
            Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
        }
        IconButton(onClick = { vm.close() }) { Icon(Icons.Filled.Close, null) }
    }
}

/** ব্যাক করলে ভিডিও ছোট ভাসমান উইন্ডোতে চলে। টেনে সরানো যায়, ছাড়লে পাশে লেগে যায়। ট্যাপ = প্লেয়ার পেজ খোলা; বামে উপরে প্লে/পজ, ডানে উপরে বন্ধ। */
@Composable
fun FloatingPlayer(vm: MainViewModel, cw: Float, ch: Float) {
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.toPx() }
    val bottomInset = with(density) { 92.dp.toPx() }          // নেভিগেশন বারের উপরে
    val w = (cw * 0.55f).coerceIn(with(density) { 160.dp.toPx() }, with(density) { 260.dp.toPx() })
    val h = w * 9f / 16f
    val defaultX = cw - w - margin
    val defaultY = ch - h - bottomInset
    val x = if (vm.pipX.isNaN()) defaultX else vm.pipX.coerceIn(0f, (cw - w).coerceAtLeast(0f))
    val y = if (vm.pipY.isNaN()) defaultY else vm.pipY.coerceIn(0f, (ch - h).coerceAtLeast(0f))

    val frac = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { player = vm.player; useController = false } },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        if (vm.audioArt) AsyncImage(
            vm.audioArtUrl, null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alpha = 0.6f
        )

        // জেসচার স্তর: ট্যাপ, ডবল ট্যাপ, টেনে সরানো
        Box(
            Modifier.matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { vm.expand() })
                }
                .pointerInput(cw, ch, w, h) {
                    detectDragGestures(
                        onDragEnd = {
                            val cx = if (vm.pipX.isNaN()) defaultX else vm.pipX
                            vm.pipX = if (cx + w / 2 < cw / 2) margin else cw - w - margin
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val cx = if (vm.pipX.isNaN()) defaultX else vm.pipX
                            val cy = if (vm.pipY.isNaN()) defaultY else vm.pipY
                            vm.pipX = (cx + drag.x).coerceIn(0f, (cw - w).coerceAtLeast(0f))
                            vm.pipY = (cy + drag.y).coerceIn(0f, (ch - h).coerceAtLeast(0f))
                        }
                    )
                }
        )

        // শুধু ভাসমান প্লেয়ারে ট্যাপ করলে প্লেয়ার পেজ খোলে; বাটনগুলো সবসময় দেখা যায়
        Box(Modifier.matchParentSize()) {
            IconButton(
                onClick = { if (vm.isPlaying) vm.player.pause() else vm.player.play() },
                modifier = Modifier.align(Alignment.TopStart).size(36.dp)
            ) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
            }
            IconButton(onClick = { vm.close() }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                Icon(Icons.Filled.Close, null, tint = Color.White)
            }
        }

        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            color = YtRed, trackColor = Color(0x55FFFFFF)
        )
    }
}
