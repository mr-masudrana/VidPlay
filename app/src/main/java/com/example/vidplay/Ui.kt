package com.example.vidplay

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.util.Locale

// ---------- থিম ----------
data class Palette(
    val name: String, val bg: Color, val chip: Color, val sub: Color, val accent: Color, val light: Boolean
)

val Palettes = listOf(
    Palette("ডার্ক", Color(0xFF0F0F0F), Color(0xFF272727), Color(0xFFAAAAAA), Color(0xFFFF0000), false),
    Palette("OLED কালো", Color(0xFF000000), Color(0xFF1A1A1A), Color(0xFF9E9E9E), Color(0xFFFF0000), false),
    Palette("লাইট", Color(0xFFFFFFFF), Color(0xFFF0F0F0), Color(0xFF606060), Color(0xFFFF0000), true),
    Palette("Ocean Blue", Color(0xFF0B1622), Color(0xFF17283B), Color(0xFF8FA8C0), Color(0xFF4FC3F7), false),
    Palette("Forest Green", Color(0xFF0D1A12), Color(0xFF1B2E22), Color(0xFF8FB09A), Color(0xFF66BB6A), false),
    Palette("Crimson", Color(0xFF140A0C), Color(0xFF2A1519), Color(0xFFBD9AA0), Color(0xFFE53935), false)
)

val LocalPalette = staticCompositionLocalOf { Palettes[0] }

val Bg: Color
    @Composable get() = LocalPalette.current.bg
val Chip: Color
    @Composable get() = LocalPalette.current.chip
val Sub: Color
    @Composable get() = LocalPalette.current.sub
val YtRed: Color
    @Composable get() = LocalPalette.current.accent

@Composable
fun YtTheme(index: Int, content: @Composable () -> Unit) {
    val p = Palettes[index.coerceIn(0, Palettes.lastIndex)]
    val scheme = if (p.light) lightColorScheme(
        primary = p.accent, background = p.bg, surface = p.bg,
        onBackground = Color.Black, onSurface = Color.Black
    ) else darkColorScheme(
        primary = p.accent, background = p.bg, surface = p.bg,
        onBackground = Color.White, onSurface = Color.White
    )
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}

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

@Composable
fun HistoryThumb(h: HistoryEntity, modifier: Modifier) {
    Box(modifier.background(Chip)) {
        AsyncImage(h.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (h.durationSec > 0) LinearProgressIndicator(
            progress = { (h.positionMs / (h.durationSec * 1000f)).coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            color = YtRed, trackColor = Color(0x66000000)
        )
    }
}

// ---------- Root ----------
val LocalVm = staticCompositionLocalOf<MainViewModel?> { null }

@Composable
fun YtApp(vm: MainViewModel) = CompositionLocalProvider(LocalVm provides vm) {
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
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val tabs = listOf(
        Triple("হোম", Icons.Outlined.Home, "VidPlay"),
        Triple("সাবস্ক্রিপশন", Icons.Outlined.Subscriptions, "সাবস্ক্রিপশন"),
        Triple("লাইব্রেরি", Icons.Outlined.VideoLibrary, "লাইব্রেরি"),
        Triple("সেটিংস", Icons.Outlined.Settings, "সেটিংস")
    )
    LaunchedEffect(vm.searching) { if (vm.searching) runCatching { focus.requestFocus() } }
    BackHandler(enabled = vm.searching && vm.tab == 0) { vm.closeSearch() }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                title = {
                    if (vm.searching && vm.tab == 0) {
                        TextField(
                            value = vm.query, onValueChange = { vm.onQuery(it) },
                            placeholder = { Text("সার্চ করুন") }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Chip, unfocusedContainerColor = Chip,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                                .onFocusChanged { if (it.isFocused) vm.reopenSuggestions() },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus(); vm.submitSearch(vm.query)
                            })
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
                        if (vm.searching) IconButton(onClick = { vm.closeSearch() }) { Icon(Icons.Filled.Close, null) }
                        else IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Filled.Search, null) }
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
            when (vm.tab) {
                0 -> if (vm.searching && vm.showSuggestions) SearchPanel(vm) else HomeTab(vm)
                1 -> SubsTab(vm)
                2 -> LibraryTab(vm)
                else -> SettingsTab(vm)
            }
        }
    }
}

@Composable
fun SearchPanel(vm: MainViewModel) {
    val focusManager = LocalFocusManager.current
    val showHistory = vm.query.isBlank()
    val rows = if (showHistory) vm.searchHistory else vm.suggestions
    LazyColumn(Modifier.fillMaxSize()) {
        if (showHistory && rows.isEmpty()) item {
            Text("কী খুঁজতে চান লিখুন", color = Sub, modifier = Modifier.padding(16.dp))
        }
        items(rows) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { focusManager.clearFocus(); vm.submitSearch(r) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (showHistory) Icons.Outlined.History else Icons.Outlined.Search, null, tint = Sub,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(r, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showHistory) IconButton(onClick = { vm.removeHistory(r) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Sub, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun HomeTab(vm: MainViewModel) {
    var chip by remember { mutableStateOf(0) }
    val chips = listOf("আপনার জন্য", "গান", "গেমিং", "খবর", "লাইভ", "রান্না", "ক্রিকেট")
    val hist by vm.history.collectAsState()
    val cont = hist.filter { it.durationSec > 0 && it.positionMs > 5_000 && it.positionMs < it.durationSec * 950 }.take(8)
    val on = MaterialTheme.colorScheme.onBackground

    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(chips.size) { i ->
                val sel = i == chip
                Text(
                    chips[i], color = if (sel) Bg else on,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) on else Chip)
                        .clickable { chip = i; if (i == 0) vm.loadForYou() else vm.load(chips[i]) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        if (vm.loading && vm.feed.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = YtRed)
        }
        vm.error?.let { Text(it, color = Sub, modifier = Modifier.padding(16.dp)) }
        LazyColumn {
            if (cont.isNotEmpty()) item {
                Text("দেখা চালিয়ে যান", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp, 4.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(cont) { h ->
                        Column(Modifier.width(160.dp).clickable { vm.play(h.url) }) {
                            HistoryThumb(h, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
                            Text(h.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            items(vm.feed) { v -> VideoCard(v, onChannel = { vm.openChannel(v.channelUrl) }) { vm.play(v.url) } }
        }
    }
}

// ---------- ভিডিও কার্ড ----------
@Composable
fun VideoCard(v: VideoItem, onChannel: () -> Unit = {}, onClick: () -> Unit) {
    val vm = LocalVm.current
    var menu by remember { mutableStateOf(false) }
    var showKw by remember { mutableStateOf(false) }

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
            Box(Modifier.clickable(onClick = onChannel)) { Avatar(null, v.channel, 36.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
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
            if (vm != null) Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, null, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("আগ্রহ নেই") }, onClick = { menu = false; vm.notInterested(v) })
                    if (v.channel.isNotBlank()) DropdownMenuItem(
                        text = { Text("\"${v.channel}\" চ্যানেল ব্লক") },
                        onClick = { menu = false; vm.blockChannel(v.channel) }
                    )
                    DropdownMenuItem(text = { Text("কিওয়ার্ড ব্লক…") }, onClick = { menu = false; showKw = true })
                }
            }
        }
    }

    if (showKw && vm != null) {
        var kw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showKw = false },
            title = { Text("কিওয়ার্ড ব্লক") },
            text = {
                Column {
                    Text("যে ভিডিওর টাইটেলে এই শব্দ থাকবে সেগুলো আর দেখাবে না।", color = Sub, fontSize = 12.sp)
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
                TextButton(enabled = kw.isNotBlank(), onClick = { vm.blockKeyword(kw); showKw = false }) { Text("ব্লক করুন") }
            },
            dismissButton = { TextButton(onClick = { showKw = false }) { Text("বাতিল") } }
        )
    }
}

// ---------- মিনি-প্লেয়ার ----------
@Composable
fun MiniPlayer(vm: MainViewModel) {
    Row(Modifier.fillMaxWidth().background(Chip), verticalAlignment = Alignment.CenterVertically) {
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

/** 0 সহ সময় দেখায় (চ্যাপ্টারের জন্য) */
fun fmtTime(s: Long): String {
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
