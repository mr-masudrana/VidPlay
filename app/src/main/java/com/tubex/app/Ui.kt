package com.tubex.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
    Palette("Crimson", Color(0xFF140A0C), Color(0xFF2A1519), Color(0xFFBD9AA0), Color(0xFFE53935), false),
    Palette("Sunset Orange", Color(0xFF160E08), Color(0xFF2B1B10), Color(0xFFC9A98F), Color(0xFFFF7043), false),
    Palette("Purple Nebula", Color(0xFF120A1F), Color(0xFF24163A), Color(0xFFB7A3D6), Color(0xFFB388FF), false),
    Palette("Midnight Black", Color(0xFF0A0A12), Color(0xFF16161F), Color(0xFF9A9AB0), Color(0xFF7C83FF), false),
    Palette("Rose Gold", Color(0xFF1C1415), Color(0xFF33242A), Color(0xFFC7A9A9), Color(0xFFE8B4A0), false),
    Palette("Arctic Ice", Color(0xFFF4FAFF), Color(0xFFE3F1FA), Color(0xFF5B7083), Color(0xFF0288D1), true)
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
fun YtTheme(index: Int, auto: Boolean = false, accent: Int = 0, content: @Composable () -> Unit) {
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val chosen = Palettes[index.coerceIn(0, Palettes.lastIndex)]
    // অটো মোডে: ফোন ডার্ক হলে ডার্ক প্যালেট, লাইট হলে লাইট প্যালেট (পছন্দের প্যালেট মিললে সেটাই)
    val base = if (!auto) chosen else if (sysDark) {
        if (!chosen.light) chosen else Palettes[0]
    } else {
        if (chosen.light) chosen else Palettes[2]
    }
    val p = if (accent != 0) base.copy(accent = Color(accent)) else base
    val on = if (p.light) Color.Black else Color.White
    val scheme = (if (p.light) lightColorScheme(
        primary = p.accent, background = p.bg, surface = p.bg, onBackground = on, onSurface = on
    ) else darkColorScheme(
        primary = p.accent, background = p.bg, surface = p.bg, onBackground = on, onSurface = on
    )).copy(
        // ডায়ালগ/শিট/মেনু/চিপ থিমের রঙে আসে, ডিফল্ট M3 রঙে নয়
        surfaceVariant = p.chip, onSurfaceVariant = p.sub, outline = p.sub,
        surfaceContainerLow = p.chip, surfaceContainer = p.chip, surfaceContainerHigh = p.chip,
        surfaceContainerHighest = p.chip, secondaryContainer = p.chip, onSecondaryContainer = on
    )
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(p.bg.toArgb()))
        w.statusBarColor = p.bg.toArgb()
        w.navigationBarColor = p.bg.toArgb()
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = p.light
        c.isAppearanceLightNavigationBars = p.light
    }
    // Scaffold/Surface-এর বাইরের জায়গায় (প্লেয়ার পেজ, চ্যানেল পেজ, ফ্লোটিং প্লেয়ার) LocalContentColor
    // ডিফল্টে কালো থাকে, তাই ডার্ক থিমে লেখা/আইকন কালো দেখাত। এখানে থিমের রঙ দেওয়া হলো।
    CompositionLocalProvider(LocalPalette provides p, LocalContentColor provides on) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
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

fun fixUrl(u: String?): String? = u?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it }

/** লোগো না থাকলে (কার্ড/কমেন্ট) চ্যানেল থেকে লোগো এনে দেখায়; না পেলে নামের প্রথম অক্ষর */
@Composable
fun ChannelAvatar(avatar: String?, channelUrl: String?, name: String, size: Dp) {
    val missing = avatar.isNullOrBlank()
    if (missing && !channelUrl.isNullOrBlank()) LaunchedEffect(channelUrl) { AvatarCache.ensure(channelUrl) }
    val resolved = if (!missing) avatar else channelUrl?.let { AvatarCache.map[it] }?.takeIf { it.isNotBlank() }
    Avatar(resolved, name, size)
}

@Composable
fun Avatar(url: String?, name: String, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Chip), Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(fixUrl(url), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        MainScaffold(vm)
        // চ্যানেল পেজ: ডান দিক থেকে স্লাইড + ফেড; ব্যাক জেসচারে ধীরে ধীরে মিলিয়ে গিয়ে আগের পেজ দেখা যায়
        AnimatedVisibility(
            visible = vm.channelOpen, modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)),
            exit = fadeOut(tween(240))
        ) {
            PredictiveBackScreen(enabled = vm.channelOpen, onBack = { vm.closeChannel() }) { ChannelScreen(vm) }
        }
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val playerShown = vm.expanded && (vm.active || vm.loadingNew)
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        if (vm.floatingMini) {
            // ভাসমান মোড: প্লেয়ার পেজ ছোট হয়ে ভাসমান উইন্ডো হয়, আবার ওখান থেকে বড় হয়ে খোলে
            val morph = remember { Animatable(if (playerShown) 1f else 0f) }
            val collapsed by remember { derivedStateOf { morph.value == 0f } }
            if (vm.active && !vm.expanded && collapsed) FloatingPlayer(vm, cw, ch)
            MorphPlayer(vm, morph, cw, ch, landscape)
        } else {
            // প্লেয়ার: নিচ থেকে স্লাইড-আপ + ফেড; ব্যাক/মিনিমাইজে মিলিয়ে যায়
            AnimatedVisibility(
                visible = playerShown, modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(300)),
                exit = fadeOut(tween(260))
            ) {
                PredictiveBackScreen(enabled = playerShown && !landscape && !vm.inPip, onBack = { vm.minimize() }) {
                    PlayerScreen(vm)
                }
            }
        }
        if (vm.opening) LinearProgressIndicator(
            Modifier.fillMaxWidth().statusBarsPadding(), color = YtRed, trackColor = Color.Transparent
        )
    }
}

// ---------- বার ও লেআউটের মাপ ----------
private val TopBarH = 48.dp     // আগে ৬৪dp
private val ChipsH = 44.dp
private val NavH = 52.dp        // আগে ৮০dp

/** টপ বার + ক্যাটাগরি আঙুলের সাথে সাথে কন্টেন্টের মতো ওঠানামা করে; নিচের বার একই অনুপাতে নামে/ওঠে */
class BarsState {
    var offset by mutableFloatStateOf(0f)       // 0 (পুরো দেখা যায়) .. -headerPx (পুরো লুকানো)
    var headerPx by mutableFloatStateOf(0f)     // যে অংশটা লুকাতে পারে (টপ বার + ক্যাটাগরি)
    var navPx by mutableFloatStateOf(0f)
    var bottomPx by mutableFloatStateOf(0f)     // মিনিপ্লেয়ার + নেভ বার + সিস্টেম ইনসেট
    val fraction: Float get() = if (headerPx <= 0f) 0f else (-offset / headerPx).coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(vm: MainViewModel) {
    val density = LocalDensity.current
    val bars = remember { BarsState() }
    val statusDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInsetDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val on = MaterialTheme.colorScheme.onBackground
    val tabs = listOf(
        Triple(L.t("হোম"), Icons.Outlined.Home, "TubeX"),
        Triple(L.t("সাবস্ক্রিপশন"), Icons.Outlined.Subscriptions, L.t("সাবস্ক্রিপশন")),
        Triple(L.t("লাইব্রেরি"), Icons.Outlined.VideoLibrary, L.t("লাইব্রেরি")),
        Triple(L.t("সেটিংস"), Icons.Outlined.Settings, L.t("সেটিংস"))
    )

    // ট্যাব/সার্চ বদলালে বার স্মুথভাবে আবার পুরো দেখায়
    LaunchedEffect(vm.tab, vm.searching, vm.showSuggestions, vm.homeTopTick, vm.feedLoading) {
        if (bars.offset != 0f) animate(bars.offset, 0f, animationSpec = tween(200)) { v, _ -> bars.offset = v }
    }

    // সার্চ বন্ধ করা, অথবা অন্য ট্যাব থেকে হোমে ফেরা: প্রেডিক্টিভ ব্যাকসহ।
    // হোম থেকে ব্যাক করলে অ্যাপ বন্ধ (সিস্টেমের ব্যাক-টু-হোম অ্যানিমেশনসহ)
    // ব্যাকে প্রিভিউ থেকে সরাসরি আসল হোমে বদলায়, তাই এই সময় ট্যাবের স্লাইড/ফেড ট্রানজিশন বন্ধ
    var skipTabAnim by remember { mutableStateOf(false) }
    val backAnim = rememberPredictiveBack(enabled = vm.tab != 0 || vm.searching, deferBack = true) {
        skipTabAnim = true
        when {
            vm.tab == 3 && vm.settingsPage != null -> vm.settingsPage = null   // টপিক পেজ থেকে তালিকায়
            vm.tab == 2 && vm.libraryPage != null -> vm.libraryPage = null     // "সব দেখুন" পেজ থেকে লাইব্রেরির মূল পেজে
            vm.tab != 0 -> vm.tab = 0
            else -> vm.closeSearch()
        }
    }
    val backIdle = backAnim.progress == 0f
    LaunchedEffect(backIdle) { if (backIdle) skipTabAnim = false }

    val bottomDp = with(density) { bars.bottomPx.toDp() }

    Box(Modifier.fillMaxSize().background(Bg)) {
        // ব্যাক জেসচার চলার সময় নিচে আগের (হোম) পেজের প্রিভিউ; ওপরের পেজ মিলিয়ে গেলে এটি ফুটে ওঠে
        if (backAnim.progress > 0f) {
            // জেসচার শুরুর মুহূর্তে ঠিক হয়: সেটিংস টপিক পেজ থেকে ব্যাকে নিচে টপিকের তালিকা, নাহলে হোম
            val kind = remember(backAnim.progress > 0f) {
                when {
                    vm.tab == 3 && vm.settingsPage != null -> 1
                    vm.tab == 2 && vm.libraryPage != null -> 2
                    else -> 0
                }
            }
            when (kind) {
                1 -> SettingsListPreview(statusDp, bottomDp)
                2 -> LibraryOverviewPreview(vm, statusDp, bottomDp)
                else -> HomePreview(vm, statusDp, bottomDp)
            }
        }
        Box(Modifier.fillMaxSize().predictiveBackLayer(backAnim).background(Bg)) {
            // ট্যাব বদলালে ডানে/বামে স্লাইড + ক্রসফেড (ট্যাবের ক্রম অনুযায়ী দিক ঠিক হয়)
            AnimatedContent(
                targetState = vm.tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (skipTabAnim) EnterTransition.None togetherWith ExitTransition.None
                    else {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(280)) { dir * it / 5 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(280)) { -dir * it / 5 } + fadeOut(tween(160)))
                    }
                },
                label = "tabs"
            ) { tab ->
                if (tab == 0) HomeScreen(vm, bars, bottomDp)
                else Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(top = statusDp + TopBarH, bottom = bottomDp)) {
                        when (tab) {
                            1 -> SubsTab(vm)
                            2 -> LibraryTab(vm, skipTabAnim)
                            else -> SettingsTab(vm, skipTabAnim)
                        }
                    }
                    Box(
                        Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = statusDp)
                            .height(TopBarH).background(Bg),
                        Alignment.CenterStart
                    ) {
                        val sp = when (tab) { 3 -> vm.settingsPage; 2 -> vm.libraryPage; else -> null }
                        if (sp != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (tab == 3) vm.settingsPage = null else vm.libraryPage = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                            }
                            Text(if (tab == 3) settingsTopicTitle(sp) else libraryTitle(sp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        } else Text(tabs[tab].third, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }

        // স্ট্যাটাস বারের জায়গা সবসময় থিমের রঙে ঢাকা (কন্টেন্ট নিচ দিয়ে যায়)
        if (statusDp > 0.dp) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(statusDp).background(Bg))

        // নিচের অংশ: মিনিপ্লেয়ার + নেভ বার (নেভ বার স্ক্রোলের সাথে নিচে নামে), তার নিচে স্থির ইনসেট স্ট্রিপ
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { bars.bottomPx = it.height.toFloat() }
        ) {
            Column(Modifier.graphicsLayer { translationY = bars.fraction * bars.navPx }) {
                if (vm.active && !vm.expanded && !vm.floatingMini) MiniPlayer(vm)
                // সেটিংস টপিক পেজ বা লাইব্রেরির "সব দেখুন" পেজে নিচের নেভ বার লুকানো থাকে
                val navVisible = !((vm.tab == 3 && vm.settingsPage != null) || (vm.tab == 2 && vm.libraryPage != null))
                AnimatedVisibility(
                    visible = navVisible,
                    enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
                ) {
                Row(
                    Modifier.fillMaxWidth().height(NavH).background(Bg)
                        .onSizeChanged { bars.navPx = it.height.toFloat() }
                ) {
                    tabs.forEachIndexed { i, (label, icon, _) ->
                        val sel = vm.tab == i
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clickable {
                                if (i == 0 && vm.tab == 0) vm.scrollHomeToTop()
                                else if (i == 3 && vm.tab == 3) vm.settingsPage = null
                                else if (i == 2 && vm.tab == 2) vm.libraryPage = null
                                else vm.tab = i
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) Chip else Color.Transparent)
                                    .padding(horizontal = 18.dp, vertical = 3.dp)
                            ) { Icon(icon, label, Modifier.size(22.dp), tint = if (sel) on else Sub) }
                            Text(label, fontSize = 10.sp, color = if (sel) on else Sub, maxLines = 1)
                        }
                    }
                }
                }
            }
            Box(Modifier.fillMaxWidth().height(navInsetDp).background(Bg))
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
            Text(L.t("কী খুঁজতে চান লিখুন"), color = Sub, modifier = Modifier.padding(16.dp))
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


// ---------- হোম ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, bars: BarsState, bottomPad: Dp) {
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var showFilter by remember { mutableStateOf(false) }
    val statusDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val panel = vm.searching && vm.showSuggestions
    val headerDp = TopBarH + ChipsH
    val headerPx = with(density) { headerDp.toPx() }
    SideEffect { bars.headerPx = if (panel) 0f else headerPx }

    LaunchedEffect(vm.searching) { if (vm.searching) runCatching { focus.requestFocus() } }

    // হোম ট্যাবে আবার ট্যাপ করলে স্মুথভাবে ওপরে
    var seenTick by remember { mutableStateOf(vm.homeTopTick) }
    LaunchedEffect(vm.homeTopTick) {
        if (vm.homeTopTick != seenTick) {
            seenTick = vm.homeTopTick
            if (listState.firstVisibleItemIndex > 6) listState.scrollToItem(6)
            listState.animateScrollToItem(0)
        }
    }

    // আঙুল ছাড়লে বার আধা-লুকানো অবস্থায় থাকে না: কাছের দিকে স্মুথভাবে গুটিয়ে/খুলে যায়
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling && bars.headerPx > 0f) {
                val target = when {
                    !listState.canScrollBackward -> 0f
                    bars.offset < -bars.headerPx / 2f -> -bars.headerPx
                    else -> 0f
                }
                if (target != bars.offset) animate(bars.offset, target, animationSpec = tween(160)) { v, _ -> bars.offset = v }
            }
        }
    }

    val conn = remember(bars) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (bars.headerPx > 0f) bars.offset = (bars.offset + available.y).coerceIn(-bars.headerPx, 0f)
                return Offset.Zero
            }
        }
    }

    val shown = remember(vm.feed, vm.isSearchResult, vm.fDate, vm.fDuration, vm.fSort) {
        if (vm.isSearchResult) SearchFilter.apply(vm.feed, vm.fDate, vm.fDuration, vm.fSort) else vm.feed
    }

    Box(Modifier.fillMaxSize().nestedScroll(conn)) {
        Crossfade(panel, animationSpec = tween(180), label = "search") { isPanel ->
            if (isPanel) Box(Modifier.fillMaxSize().padding(top = statusDp + TopBarH)) { SearchPanel(vm) }
            else HomeFeed(vm, shown, listState, top = statusDp + headerDp, bottom = bottomPad)
        }

        // টপ বার + ক্যাটাগরি/ফিল্টার: কন্টেন্টের সাথে ওপরে উঠে যায়
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = statusDp)
                .graphicsLayer { translationY = bars.offset }.background(Bg)
        ) {
            HomeTopBar(vm, focus)
            if (!panel) {
                if (vm.isSearchResult) SearchFilterRow(vm) { showFilter = true }
                else CategoryRow(vm, vm.chipIndex)
            }
        }
        if (statusDp > 0.dp) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(statusDp).background(Bg))
    }

    if (showFilter) FilterSheet(vm, shown.size) { showFilter = false }
}

@Composable
private fun HomeTopBar(vm: MainViewModel, focus: FocusRequester) {
    val focusManager = LocalFocusManager.current
    val on = MaterialTheme.colorScheme.onBackground
    Row(
        Modifier.fillMaxWidth().height(TopBarH).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (vm.searching) {
            BasicTextField(
                value = vm.query, onValueChange = { vm.onQuery(it) }, singleLine = true,
                textStyle = TextStyle(color = on, fontSize = 15.sp),
                cursorBrush = SolidColor(on),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus(); vm.submitSearch(vm.query)
                }),
                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(Chip)
                    .focusRequester(focus).onFocusChanged { if (it.isFocused) vm.reopenSuggestions() },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), Alignment.CenterStart) {
                        if (vm.query.isEmpty()) Text(L.t("সার্চ করুন"), color = Sub, fontSize = 15.sp)
                        inner()
                    }
                }
            )
            IconButton(onClick = { vm.closeSearch() }) { Icon(Icons.Filled.Close, null) }
        } else {
            Icon(Icons.Filled.PlayCircle, null, tint = YtRed, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Text("TubeX", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Filled.Search, null) }
        }
    }
}

@Composable
private fun CategoryRow(vm: MainViewModel, selected: Int) {
    val on = MaterialTheme.colorScheme.onBackground
    val chips = listOf(L.t("আপনার জন্য"), L.t("গান"), L.t("গেমিং"), L.t("খবর"), L.t("লাইভ"), L.t("রান্না"), L.t("ক্রিকেট"))
    LazyRow(
        Modifier.fillMaxWidth().height(ChipsH),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(chips.size) { i ->
            val sel = i == selected
            Text(
                chips[i], color = if (sel) Bg else on,
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (sel) on else Chip)
                    .clickable { if (i != selected) vm.pickCategory(i, chips[i]) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

/** সার্চ রেজাল্টে ক্যাটাগরির বদলে ফিল্টার */
@Composable
private fun SearchFilterRow(vm: MainViewModel, onOpen: () -> Unit) {
    val on = MaterialTheme.colorScheme.onBackground
    LazyRow(
        Modifier.fillMaxWidth().height(ChipsH),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (vm.filtersActive) on else Chip)
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp), tint = if (vm.filtersActive) Bg else on)
                Spacer(Modifier.width(6.dp))
                Text(L.t("ফিল্টার"), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = if (vm.filtersActive) Bg else on)
            }
        }
        if (vm.fDate != 0) item { ActiveFilterChip(L.t(SearchFilter.dates[vm.fDate])) { vm.setDateFilter(0) } }
        if (vm.fDuration != 0) item { ActiveFilterChip(L.t(SearchFilter.durations[vm.fDuration])) { vm.setDurationFilter(0) } }
        if (vm.fSort != 0) item { ActiveFilterChip(L.t(SearchFilter.sorts[vm.fSort])) { vm.setSortFilter(0) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeed(vm: MainViewModel, shown: List<VideoItem>, listState: LazyListState, top: Dp, bottom: Dp) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = vm.refreshing, onRefresh = { vm.refreshFeed() }, state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState, isRefreshing = vm.refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top)
            )
        }
    ) {
        LazyColumn(
            state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top, bottom = bottom)
        ) {
            if (vm.feedLoading) {
                // সার্চ/ক্যাটাগরি বদলালে স্কেলিটন লোডিং
                item { VideoListSkeleton(3) }
            } else {
                vm.error?.let { e -> item { Text(e, color = Sub, modifier = Modifier.padding(16.dp)) } }
                if (shown.isEmpty() && vm.feed.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(L.t("ফিল্টারে কোনো ভিডিও মেলেনি"), color = Sub)
                        TextButton(onClick = { vm.resetFilters() }) { Text(L.t("ফিল্টার মুছুন")) }
                    }
                }
                items(shown) { v -> VideoCard(v, onChannel = { vm.openChannel(v.channelUrl) }) { vm.play(v.url) } }
            }
        }
    }
}

/** লাইব্রেরির "সব দেখুন" পেজ থেকে ব্যাকে নিচে দেখানো মূল পেজের স্থির প্রিভিউ */
@Composable
private fun LibraryOverviewPreview(vm: MainViewModel, statusDp: Dp, bottomDp: Dp) {
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
private fun SettingsListPreview(statusDp: Dp, bottomDp: Dp) {
    Box(Modifier.fillMaxSize().background(Bg).padding(top = statusDp + TopBarH, bottom = bottomDp)) {
        SettingsTopicList { }
    }
    Box(
        Modifier.fillMaxWidth().padding(top = statusDp).height(TopBarH).background(Bg),
        Alignment.CenterStart
    ) { Text(L.t("সেটিংস"), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp)) }
}

/** ব্যাক জেসচারে নিচে দেখানো আগের হোম পেজের স্থির প্রিভিউ (ইন্টারঅ্যাকশন নেই) */
@Composable
private fun HomePreview(vm: MainViewModel, statusDp: Dp, bottomDp: Dp) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(vm: MainViewModel, count: Int, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Chip) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("ফিল্টার"), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (vm.filtersActive) TextButton(onClick = { vm.resetFilters() }) { Text(L.t("ফিল্টার মুছুন")) }
            }
            FilterGroup(L.t("আপলোডের সময়"), SearchFilter.dates, vm.fDate) { vm.setDateFilter(it) }
            FilterGroup(L.t("দৈর্ঘ্য"), SearchFilter.durations, vm.fDuration) { vm.setDurationFilter(it) }
            FilterGroup(L.t("সাজান"), SearchFilter.sorts, vm.fSort) { vm.setSortFilter(it) }
            Text(L.f("{0} টি ফলাফল", count), color = Sub, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterGroup(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { i ->
            FilterChip(selected = selected == i, onClick = { onPick(i) }, label = { Text(L.t(options[i])) })
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, onClear: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Chip).clickable(onClick = onClear)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
    }
}

// ---------- ভিডিও কার্ড ----------
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

// ---------- ভাসমান মিনি প্লেয়ার (PiP-এর মতো) ----------
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
