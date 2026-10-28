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
import androidx.compose.foundation.*
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
import androidx.media3.ui.CaptionStyleCompat
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
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
    val ctx = LocalContext.current
    var controls by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showCaptions by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    val sizes = floatArrayOf(0.04f, 0.0533f, 0.07f)

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = vm.player
                    setShowNextButton(false); setShowPreviousButton(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        controls = v == View.VISIBLE
                    })
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE, 0x99000000.toInt(), android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.WHITE, null
                        )
                    )
                    setOnTouchListener(PlayerGestures(c, vm.player, this) { hud = it })
                }
            },
            update = {
                it.useController = overlay && !(vm.audioOnly && vm.current != null)
                it.subtitleView?.setFractionalTextSize(sizes[vm.captionSizeIndex.coerceIn(0, 2)])
            },
            modifier = Modifier.fillMaxSize()
        )

        if (vm.audioOnly && vm.current != null) AudioOnlyLayer(vm)

        // জেসচার HUD (ব্রাইটনেস/ভলিউম/সিক)
        hud?.let {
            Text(
                it, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Chromecast কন্ট্রোল
        if (overlay && vm.castName != null) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(24.dp)).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📺 ${vm.castName}", color = Color.White, fontSize = 13.sp)
                IconButton(onClick = { vm.castToggle() }) {
                    Icon(if (vm.castPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
                }
                IconButton(onClick = { vm.castStop() }) { Icon(Icons.Filled.Close, null, tint = Color.White) }
            }
        }

        if (overlay) AnimatedVisibility(controls || (vm.audioOnly && vm.current != null), Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!landscape) IconButton(onClick = { vm.minimize() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (vm.castAvailable && vm.current != null) IconButton(onClick = {
                    if (vm.castName == null) vm.showCastChooser(ctx) else vm.castStop()
                }) {
                    Icon(if (vm.castName == null) Icons.Filled.Cast else Icons.Filled.CastConnected, null, tint = Color.White)
                }
                if (vm.captions.isNotEmpty()) IconButton(onClick = { showCaptions = true }) {
                    Icon(Icons.Filled.ClosedCaption, null, tint = if (vm.captionIndex >= 0) YtRed else Color.White)
                }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, null, tint = Color.White) }
                IconButton(onClick = onFullscreen) {
                    Icon(if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, null, tint = Color.White)
                }
            }
        }
    }

    if (showCaptions) CaptionSheet(vm) { showCaptions = false }
    if (showSettings) PlayerSettingsSheet(vm) { showSettings = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptionSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Chip) {
        Text("সাবটাইটেল", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ছোট", "মাঝারি", "বড়").forEachIndexed { i, t ->
                FilterChip(selected = vm.captionSizeIndex == i, onClick = { vm.chooseCaptionSize(i) }, label = { Text(t) })
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            item {
                Text(
                    "বন্ধ" + if (vm.captionIndex < 0) "  ✓" else "",
                    modifier = Modifier.fillMaxWidth().clickable { vm.chooseCaption(-1); onDismiss() }.padding(16.dp)
                )
            }
            items(vm.captions.size) { i ->
                val c = vm.captions[i]
                Text(
                    c.displayLanguageName + (if (c.isAutoGenerated) " (স্বয়ংক্রিয়)" else "") +
                            if (vm.captionIndex == i) "  ✓" else "",
                    modifier = Modifier.fillMaxWidth().clickable { vm.chooseCaption(i); onDismiss() }.padding(16.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
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
    val favs by vm.favorites.collectAsState()
    val lists by vm.playlists.collectAsState()
    val info = vm.current
    var expandedDesc by remember(info) { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }

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
                        listOf(fmtCount(info.viewCount).let { if (it.isBlank()) "" else "$it views" },
                            info.textualUploadDate ?: "").filter { it.isNotBlank() }.joinToString(" • "),
                        color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                    if (vm.likes != null) Text(
                        "👍 ${fmtCount(vm.likes ?: 0L)}" + (vm.dislikes?.let { "   👎 ${fmtCount(it)}" } ?: ""),
                        fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
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
                            containerColor = if (subbed) Chip else MaterialTheme.colorScheme.onBackground,
                            contentColor = if (subbed) MaterialTheme.colorScheme.onBackground else Bg
                        )
                    ) { Text(if (subbed) "সাবস্ক্রাইবড" else "সাবস্ক্রাইব") }
                }
            }
            // অ্যাকশন
            item {
                val isFav = favs.any { it.url == info.url }
                LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Pill(if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "ফেভারিট") { vm.toggleFavorite() }
                    }
                    item { Pill(Icons.Outlined.Add, "প্লেলিস্টে যোগ") { showPlaylists = true } }
                    item { Pill(Icons.Outlined.Download, "ডাউনলোড") { showDownload = true } }
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
            // চ্যাপ্টার
            val chapters = info.streamSegments
            if (chapters.isNotEmpty()) item {
                Text("অধ্যায়", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp, 0.dp, 12.dp, 6.dp))
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

    if (showComments) ModalBottomSheet(onDismissRequest = { showComments = false }, containerColor = Chip) {
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

    if (showDownload) DownloadSheet(vm) { showDownload = false }

    if (showPlaylists) ModalBottomSheet(onDismissRequest = { showPlaylists = false }, containerColor = Chip) {
        var name by remember { mutableStateOf("") }
        Text("প্লেলিস্টে যোগ করুন", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("নতুন প্লেলিস্ট") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = { vm.createPlaylistAndAdd(name); name = ""; showPlaylists = false }
            ) { Text("তৈরি") }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(lists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id); showPlaylists = false }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.name, modifier = Modifier.weight(1f))
                    Text("${p.itemCount} টি", color = Sub, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- শুধু অডিও মোডের লেয়ার ----------
@Composable
private fun BoxScope.AudioOnlyLayer(vm: MainViewModel) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = vm.durationMs
    val frac = drag ?: if (dur > 0) (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f

    Box(Modifier.matchParentSize().background(Color.Black)) {
        AsyncImage(
            vm.current?.thumbnails?.lastOrNull()?.url, null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alpha = 0.45f
        )
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { vm.player.seekTo((vm.player.currentPosition - 10_000).coerceAtLeast(0L)) }) {
                Icon(Icons.Filled.Replay10, null, tint = Color.White)
            }
            IconButton(onClick = { if (vm.isPlaying) vm.player.pause() else vm.player.play() }) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                    tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { vm.player.seekTo(vm.player.currentPosition + 10_000) }) {
                Icon(Icons.Filled.Forward10, null, tint = Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtTime(vm.positionMs / 1000), color = Color.White, fontSize = 11.sp)
            Slider(
                value = frac, onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { f -> vm.player.seekTo((f * dur).toLong()) }; drag = null },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(fmtTime(dur / 1000), color = Color.White, fontSize = 11.sp)
        }
    }
}

// ---------- প্লেয়ার সেটিংস শিট ----------
@Composable
private fun <T> OptionRow(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { i ->
            FilterChip(selected = options[i] == selected, onClick = { onPick(options[i]) }, label = { Text(label(options[i])) })
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, color = Sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Chip) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            OptionRow("প্লেব্যাক গতি", listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), vm.speed, { "${it}x" }) { vm.chooseSpeed(it) }

            if (vm.current != null && !vm.audioOnly) {
                OptionRow("ভিডিও কোয়ালিটি", listOf(0) + vm.heights, vm.quality, { if (it == 0) "Auto" else "${it}p" }) {
                    vm.selectQuality(it)
                }
            }

            OptionRow(
                "স্লিপ টাইমার", listOf(0, 15, 30, 45, 60, -1),
                if (vm.sleepAtEnd) -1 else if (vm.sleepEnd > 0) -2 else 0,
                { when (it) { 0 -> "বন্ধ"; -1 -> "ভিডিও শেষে"; else -> "$it মি" } }
            ) { vm.startSleep(it) }
            if (vm.sleepEnd > 0) {
                val left = ((vm.sleepEnd - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)
                Text("চালু আছে — প্রায় $left মিনিট পরে থামবে", color = Sub, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            Spacer(Modifier.height(8.dp))
            ToggleRow("লুপ", "একই ভিডিও বারবার চলবে", vm.loop) { vm.toggleLoop(it) }
            ToggleRow("শুধু অডিও", "ভিডিও ছাড়া চলবে, ডেটা বাঁচবে", vm.audioOnly, enabled = vm.current != null) {
                vm.toggleAudioOnly(it)
            }
            ToggleRow("নীরবতা বাদ (Skip Silence)", "কথার ফাঁকের নীরব অংশ বাদ যাবে", vm.skipSilence) {
                vm.toggleSkipSilence(it)
            }
        }
    }
}

// ---------- ডাউনলোড শিট ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val opts = remember(vm.current) { vm.downloadOptions() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Chip) {
        Text("ডাউনলোড", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        if (opts.isEmpty()) Text("ডাউনলোডের কোনো অপশন পাওয়া যায়নি", color = Sub, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(opts.size) { i ->
                val o = opts[i]
                Row(
                    Modifier.fillMaxWidth().clickable { vm.enqueueDownload(o); onDismiss() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(o.label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(o.note, color = Sub, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
