package com.tubex.app.ui.main

import com.tubex.app.util.L
import com.tubex.app.player.ui.MiniPlayer
import com.tubex.app.ui.home.HomeScreen
import com.tubex.app.ui.library.LibraryTab
import com.tubex.app.ui.library.SubsTab
import com.tubex.app.ui.library.libraryTitle
import com.tubex.app.ui.navigation.predictiveBackLayer
import com.tubex.app.ui.navigation.rememberPredictiveBack
import com.tubex.app.ui.settings.SettingsTab
import com.tubex.app.ui.settings.settingsTopicTitle
import com.tubex.app.ui.theme.Bg
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val TopBarH = 48.dp     // আগে ৬৪dp

internal val ChipsH = 44.dp

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
    val backActive by remember { derivedStateOf { backAnim.progress > 0f } }   // জেসচার চলছে কি না (প্রতি ফ্রেমে রিকম্পোজ এড়াতে)
    LaunchedEffect(backIdle) { if (backIdle) skipTabAnim = false }

    val bottomDp = with(density) { bars.bottomPx.toDp() }

    Box(Modifier.fillMaxSize().background(Bg)) {
        // ব্যাক জেসচার চলার সময় নিচে আগের (হোম) পেজের প্রিভিউ; ওপরের পেজ মিলিয়ে গেলে এটি ফুটে ওঠে
        if (backActive) {
            // জেসচার শুরুর মুহূর্তে ঠিক হয়: সেটিংস টপিক পেজ থেকে ব্যাকে নিচে টপিকের তালিকা, নাহলে হোম
            val kind = remember(backActive) {
                when {
                    vm.tab == 3 && vm.settingsPage != null -> 1
                    vm.tab == 2 && vm.libraryPage != null -> 2
                    else -> 0
                }
            }
            when (kind) {
                1 -> SettingsListPreview(vm, statusDp, bottomDp)
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
                // সার্চ পেজে (সাজেশন/হিস্টোরি প্যানেল খোলা) নেভ বার থাকে না; রেজাল্ট পেজে থাকে
                val searchPanelOpen = vm.tab == 0 && vm.searching && vm.showSuggestions
                val navVisible = !searchPanelOpen && (backActive ||
                    !((vm.tab == 3 && vm.settingsPage != null) || (vm.tab == 2 && vm.libraryPage != null)))
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
