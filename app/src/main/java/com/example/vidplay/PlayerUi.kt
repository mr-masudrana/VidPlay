package com.example.vidplay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun PlayerScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun toggleFullscreen() {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    BackHandler { if (landscape) toggleFullscreen() else vm.minimize() }

    // ফুলস্ক্রিন = ল্যান্ডস্কেপ: সিস্টেম বার লুকানো
    LaunchedEffect(landscape) {
        val win = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(win, view)
        if (landscape) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(it.window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (vm.inPip) {   // PiP উইন্ডোতে শুধু ভিডিও
        PlayerBox(vm, landscape = true, overlay = false, onFullscreen = {}, modifier = Modifier.fillMaxSize())
        return
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        PlayerBox(
            vm, landscape, overlay = true, onFullscreen = { toggleFullscreen() },
            modifier = if (landscape) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().aspectRatio(16f / 9f).statusBarsPadding()
        )
        if (!landscape) Details(vm)
    }
}

@Composable
private fun PlayerBox(
    vm: MainViewModel, landscape: Boolean, overlay: Boolean,
    onFullscreen: () -> Unit, modifier: Modifier
) {
    var controls by remember { mutableStateOf(true) }
    var qMenu by remember { mutableStateOf(false) }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = vm.player
                    setShowNextButton(false); setShowPreviousButton(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        controls = v == View.VISIBLE
                    })
                }
            },
            update = { it.useController = overlay },
            modifier = Modifier.fillMaxSize()
        )
        if (overlay) AnimatedVisibility(controls, Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!landscape) IconButton(onClick = { vm.minimize() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (vm.current != null) Box {
                    IconButton(onClick = { qMenu = true }) { Icon(Icons.Filled.Settings, null, tint = Color.White) }
                    DropdownMenu(qMenu, { qMenu = false }) {
                        (listOf(0) + vm.heights).forEach { h ->
                            DropdownMenuItem(
                                text = {
                                    Text((if (h == 0) "Auto" else "${h}p") + if (vm.quality == h) "  ✓" else "")
                                },
                                onClick = { vm.selectQuality(h); qMenu = false }
                            )
                        }
                    }
                }
                IconButton(onClick = onFullscreen) {
                    Icon(if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Chip).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Details(vm: MainViewModel) {
    val ctx = LocalContext.current
    val subs by vm.subs.collectAsState()
    val info = vm.current
    var expandedDesc by remember(info) { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var dlMenu by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(12.dp)) {
                Text(
                    vm.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = if (expandedDesc) Int.MAX_VALUE else 2,
                    modifier = Modifier.clickable { expandedDesc = !expandedDesc }
                )
                if (info != null) {
                    Text(
                        listOf(fmtCount(info.viewCount).ifBlank { "" }.let { if (it.isBlank()) "" else "$it views" },
                            info.textualUploadDate ?: "").filter { it.isNotBlank() }.joinToString(" • "),
                        color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                    if (expandedDesc) Text(
                        info.description?.content ?: "", color = Sub, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else Text("অফলাইন (ডাউনলোড করা)", color = Sub, fontSize = 12.sp)
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
                        Avatar(avatar, info.uploaderName, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(info.uploaderName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (info.uploaderSubscriberCount >= 0)
                                Text("${fmtCount(info.uploaderSubscriberCount)} subscribers", color = Sub, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { vm.toggleSub(chUrl, info.uploaderName, avatar) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (subbed) Chip else Color.White,
                            contentColor = if (subbed) Color.White else Color.Black
                        )
                    ) { Text(if (subbed) "সাবস্ক্রাইবড" else "সাবস্ক্রাইব") }
                }
            }
            // অ্যাকশন
            item {
                LazyRow(
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Box {
                            Pill(Icons.Outlined.Download, "ডাউনলোড") { dlMenu = true }
                            DropdownMenu(dlMenu, { dlMenu = false }) {
                                DropdownMenuItem(text = { Text("ভিডিও (সর্বোচ্চ ৭২০p)") },
                                    onClick = { dlMenu = false; vm.download(false) })
                                DropdownMenuItem(text = { Text("শুধু অডিও (m4a)") },
                                    onClick = { dlMenu = false; vm.download(true) })
                            }
                        }
                    }
                    item {
                        Pill(Icons.Outlined.Share, "শেয়ার") {
                            ctx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, info.url)
                                }, null))
                        }
                    }
                }
            }
            // মন্তব্য কার্ড
            item {
                Column(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A)).clickable { showComments = true }.padding(12.dp)
                ) {
                    Text(
                        "মন্তব্য" + if (vm.commentsTotal > 0) "  ${vm.commentsTotal}" else "",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    val first = vm.comments.firstOrNull()
                    Text(
                        when {
                            vm.commentsLoading -> "লোড হচ্ছে…"
                            first != null -> first.text
                            else -> "মন্তব্য নেই বা বন্ধ আছে"
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

    if (showComments) ModalBottomSheet(
        onDismissRequest = { showComments = false }, containerColor = Color(0xFF212121)
    ) {
        Text("মন্তব্য", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(vm.comments) { c ->
                Row(Modifier.padding(16.dp, 8.dp)) {
                    Avatar(c.avatar, c.author, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${c.author} • ${c.date}", color = Sub, fontSize = 12.sp)
                        Text(c.text, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        if (c.likes.isNotBlank()) Text("👍 ${c.likes}", color = Sub, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
