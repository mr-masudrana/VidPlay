package com.example.vidplay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.util.Locale

val YtRed = Color(0xFFFF0000)
val Bg = Color(0xFF0F0F0F)
val Chip = Color(0xFF272727)
val Sub = Color(0xFFAAAAAA)

@Composable
fun YtTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = YtRed, background = Bg, surface = Bg,
        onBackground = Color.White, onSurface = Color.White
    ), content = content
)

// ---------- Helpers ----------
fun fmtCount(v: Long): String = when {
    v < 0 -> ""
    v >= 1_000_000_000 -> "%.1fB".format(Locale.US, v / 1e9)
    v >= 1_000_000 -> "%.1fM".format(Locale.US, v / 1e6)
    v >= 1_000 -> "%.1fK".format(Locale.US, v / 1e3)
    else -> "$v"
}

fun fmtDur(s: Long): String {
    if (s <= 0) return "LIVE"
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun Avatar(url: String?, name: String, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Chip), Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold)
    }
}

// ---------- Root ----------
@Composable
fun YtApp(vm: MainViewModel) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        MainScaffold(vm)
        if (vm.channelOpen) ChannelScreen(vm)
        if (vm.expanded && vm.active) PlayerScreen(vm)
        if (vm.opening) LinearProgressIndicator(
            Modifier.fillMaxWidth().statusBarsPadding(), color = YtRed, trackColor = Color.Transparent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(vm: MainViewModel) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val tabs = listOf(
        Triple("হোম", Icons.Outlined.Home, "VidPlay"),
        Triple("সাবস্ক্রিপশন", Icons.Outlined.Subscriptions, "সাবস্ক্রিপশন"),
        Triple("ডাউনলোড", Icons.Outlined.Download, "ডাউনলোড")
    )

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                title = {
                    if (searching && vm.tab == 0) {
                        TextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("সার্চ করুন") }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Chip, unfocusedContainerColor = Chip,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.load(query) })
                        )
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.tab == 0) {
                            Icon(Icons.Filled.PlayCircle, null, tint = YtRed, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(tabs[vm.tab].third, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    if (vm.tab == 0) {
                        if (searching) IconButton(onClick = { searching = false; query = ""; vm.load(null) }) {
                            Icon(Icons.Filled.Close, null)
                        } else IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, null) }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (vm.active && !vm.expanded) MiniPlayer(vm)
                NavigationBar(containerColor = Bg) {
                    tabs.forEachIndexed { i, (label, icon, _) ->
                        NavigationBarItem(
                            selected = vm.tab == i, onClick = { vm.tab = i },
                            icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Chip)
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) { 0 -> HomeTab(vm); 1 -> SubsTab(vm); else -> LibraryTab(vm) }
        }
    }
}

@Composable
fun HomeTab(vm: MainViewModel) {
    var chip by remember { mutableStateOf(0) }
    val chips = listOf("সব", "গান", "গেমিং", "খবর", "লাইভ", "রান্না", "ক্রিকেট")
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(chips.size) { i ->
                val sel = i == chip
                Text(
                    chips[i], color = if (sel) Color.Black else Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Color.White else Chip)
                        .clickable { chip = i; vm.load(if (i == 0) null else chips[i]) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        if (vm.loading && vm.feed.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = YtRed)
        }
        vm.error?.let { Text(it, color = Sub, modifier = Modifier.padding(16.dp)) }
        LazyColumn {
            items(vm.feed) { v -> VideoCard(v, onChannel = { vm.openChannel(v.channelUrl) }) { vm.play(v.url) } }
        }
    }
}

// ---------- ভিডিও কার্ড ----------
@Composable
fun VideoCard(v: VideoItem, onChannel: () -> Unit = {}, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        Box {
            AsyncImage(
                model = v.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Chip)
            )
            Text(
                fmtDur(v.durationSec), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(if (v.durationSec <= 0) YtRed else Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Row(Modifier.padding(12.dp, 12.dp, 12.dp, 0.dp)) {
            Box(Modifier.clickable(onClick = onChannel)) { Avatar(null, v.channel, 36.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                Text(
                    listOfNotNull(
                        v.channel.ifBlank { null },
                        fmtCount(v.views).ifBlank { null }?.let { "$it views" },
                        v.uploaded
                    ).joinToString(" • "),
                    color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ---------- মিনি-প্লেয়ার ----------
@Composable
fun MiniPlayer(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF212121)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { player = vm.player; useController = false } },
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
