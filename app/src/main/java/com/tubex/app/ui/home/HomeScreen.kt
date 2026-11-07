package com.tubex.app.ui.home

import com.tubex.app.data.model.VideoItem
import com.tubex.app.data.search.SearchFilter
import com.tubex.app.util.L
import com.tubex.app.ui.components.VideoCard
import com.tubex.app.ui.components.VideoListSkeleton
import com.tubex.app.ui.main.BarsState
import com.tubex.app.ui.main.ChipsH
import com.tubex.app.ui.main.TopBarH
import com.tubex.app.ui.theme.Bg
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, bars: BarsState, bottomPad: Dp) {
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    var showFilter by remember { mutableStateOf(false) }
    val statusDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val panel = vm.searching && vm.showSuggestions
    val headerDp = TopBarH + ChipsH
    val headerPx = with(density) { headerDp.toPx() }
    SideEffect { bars.headerPx = if (panel) 0f else headerPx }

    // সার্চ খুললে (সাজেশন প্যানেলসহ) ফোকাস; রেজাল্ট পেজে অন্য ট্যাব থেকে ফিরলে ফোকাস নয়,
    // নইলে ফোকাস পেয়ে সাজেশন প্যানেল খুলে রেজাল্ট পেজের বদলে সার্চ পেজ দেখাত
    LaunchedEffect(vm.searching) { if (vm.searching && vm.showSuggestions) runCatching { focus.requestFocus() } }

    // সার্চ রেজাল্ট ↔ হোম বদলালে (ব্যাকসহ) ফিড নতুন, তাই আগের স্ক্রোল পজিশন নয়
    LaunchedEffect(vm.isSearchResult) {
        listState.scrollToItem(0)
        bars.offset = 0f
    }

    // হোম ট্যাবে আবার ট্যাপ করলে স্মুথভাবে ওপরে
    var seenTick by remember { mutableStateOf(vm.homeTopTick) }
    LaunchedEffect(vm.homeTopTick) {
        if (vm.homeTopTick != seenTick) {
            seenTick = vm.homeTopTick
            if (listState.firstVisibleItemIndex > 6) listState.scrollToItem(6)
            listState.animateScrollToItem(0)
        }
    }

    // হোম ফিড নিচে স্ক্রোল করা থাকলে ব্যাকে অ্যাপ বন্ধ না হয়ে স্মুথভাবে ওপরে ওঠে (হোম ট্যাবে ট্যাপের মতোই);
    // ওপরে থাকলে এটি বন্ধ থাকে, তাই ব্যাকে অ্যাপ বন্ধ হয়। প্লেয়ার/চ্যানেল/প্লেলিস্ট খোলা থাকলে সেগুলোর ব্যাক আগে চলে
    val scrolledDown by remember { derivedStateOf { listState.canScrollBackward } }
    BackHandler(
        enabled = scrolledDown && vm.tab == 0 && !vm.searching &&
            !vm.playerShown && !vm.channelOpen && !vm.playlistOpen
    ) { vm.scrollHomeToTop() }

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
                // রিফ্রেশের জন্য টানা/ফেরানোর সময় বার নড়বে না
                val pulling = pullState.distanceFraction > 0f && !listState.canScrollBackward
                if (!pulling && bars.headerPx > 0f) bars.offset = (bars.offset + available.y).coerceIn(-bars.headerPx, 0f)
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
            else HomeFeed(vm, shown, listState, pullState, top = statusDp + headerDp, bottom = bottomPad)
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
internal fun CategoryRow(vm: MainViewModel, selected: Int) {
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
private fun HomeFeed(
    vm: MainViewModel, shown: List<VideoItem>, listState: LazyListState,
    pullState: PullToRefreshState, top: Dp, bottom: Dp
) {
    val hist by vm.history.collectAsState()
    val showShelf = vm.chipIndex == 0 && !vm.isSearchResult
    val resume = remember(hist) { continueWatching(hist) }
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
                if (showShelf && resume.isNotEmpty()) item { ContinueWatchingShelf(resume) { vm.play(it) } }
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
