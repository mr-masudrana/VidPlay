package com.tubex.app.player

import com.tubex.app.core.L
import com.tubex.app.core.MainViewModel
import com.tubex.app.data.CommentItem
import com.tubex.app.data.VideoItem
import com.tubex.app.ui.Bg
import com.tubex.app.ui.ChannelAvatar
import com.tubex.app.ui.Chip
import com.tubex.app.ui.CommentSkeletons
import com.tubex.app.ui.LocalPalette
import com.tubex.app.ui.PlayerDetailsSkeleton
import com.tubex.app.ui.SkeletonBox
import com.tubex.app.ui.Sub
import com.tubex.app.ui.VideoCard
import com.tubex.app.ui.YtRed
import com.tubex.app.ui.fmtCount
import com.tubex.app.ui.fmtTime
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.zIndex
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

    // ফুলস্ক্রিন বদলের অ্যানিমেশন: ভিডিও বক্স ঘুরে (±৯০°) ও মাপ বদলে নতুন অবস্থার দিকে যায়, তারপর স্ক্রিন ঘোরে
    val themeBg = Bg.toArgb()
    val themeLight = LocalPalette.current.light
    val scope = rememberCoroutineScope()
    val fsProg = remember { Animatable(0f) }
    var fsToLandscape by remember { mutableStateOf(true) }
    var fsDir by remember { mutableStateOf(1f) }   // ঘূর্ণনের দিক: +1 = ঘড়ির কাঁটার দিকে (ROTATION_90-এ যাওয়া / ROTATION_270 থেকে ফেরা), -1 = উল্টো
    var fsFromLandscape by remember { mutableStateOf(false) }   // কোন অবস্থা থেকে অ্যানিমেশন শুরু (ঘোরার পর ভুল ফ্রেম আটকাতে)
    var lastStatusPx by remember { mutableStateOf(0f) }
    val statusNow = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    SideEffect { if (!landscape && statusNow > 0f) lastStatusPx = statusNow }
    LaunchedEffect(landscape) { fsProg.snapTo(0f) }   // স্ক্রিন ঘোরা শেষ: ট্রান্সফর্ম রিসেট

    fun toggleFullscreen() {
        if (fsProg.value > 0f || fsProg.isRunning) return
        // সিস্টেম আসলে যে দিকে ঘুরবে অ্যানিমেশনও সেই দিকে ঘোরাতে হবে, নইলে প্রথমে উল্টো ঘুরে পরে ঠিক হয়।
        // ঢোকার সময়: স্থির LANDSCAPE (= ROTATION_90) নিই, ঘোরা শেষে নিচে SENSOR_LANDSCAPE করা হয়।
        // বেরোনোর সময়: এখনকার আসল ডিসপ্লে রোটেশন দেখে দিক ঠিক করি।
        val target = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        fsDir = if (landscape) {
            if ((view.display?.rotation ?: android.view.Surface.ROTATION_90) == android.view.Surface.ROTATION_270) 1f else -1f
        } else 1f
        fsToLandscape = !landscape
        fsFromLandscape = landscape
        scope.launch {
            fsProg.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            // বার/কাটআউট বদল ঘোরার সাথে একই মুহূর্তে, যাতে আলাদা করে দ্বিতীয় লাফ না হয়
            activity?.window?.let { w ->
                val c = WindowCompat.getInsetsController(w, view)
                setFullscreenWindow(w, view, fsToLandscape, themeBg, themeLight)
                if (fsToLandscape) {
                    c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(WindowInsetsCompat.Type.systemBars())
                } else c.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = target
            delay(1500); fsProg.snapTo(0f)            // নিরাপত্তা: ঘোরা না হলেও আটকে থাকবে না
        }
    }

    // পোর্ট্রেটে ব্যাক জেসচার YtApp-এর PredictiveBackScreen সামলায়; ফুলস্ক্রিনে (ল্যান্ডস্কেপ) এখানে
    BackHandler(enabled = landscape) { toggleFullscreen() }

    // ফুলস্ক্রিন = ল্যান্ডস্কেপ: সিস্টেম বার লুকানো
    LaunchedEffect(landscape) {
        val win = activity?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(win, view)
        setFullscreenWindow(win, view, landscape, themeBg, themeLight)
        if (landscape) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // বাটন দিয়ে ঢুকলে এখন দুই ল্যান্ডস্কেপ দিকেই ঘোরার অনুমতি দিই
            if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // সিস্টেমের ডিফল্ট "ঘোরা" অ্যানিমেশনের বদলে ক্রসফেড (অ্যাপ নিজেই configChanges সামলায়)
    DisposableEffect(Unit) {
        activity?.window?.let { w ->
            val a = w.attributes
            a.rotationAnimation = android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE
            w.attributes = a
        }
        onDispose {
            activity?.window?.let { w ->
                val a = w.attributes
                a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE   // জেসচারে বদলানো ব্রাইটনেস ফিরিয়ে দিই
                a.rotationAnimation = android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
                w.attributes = a
            }
            activity?.let {
                setFullscreenWindow(it.window, view, false, themeBg, themeLight)
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(it.window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (vm.inPip) {   // PiP উইন্ডোতে শুধু ভিডিও
        PlayerBox(vm, landscape = true, overlay = false, onFullscreen = {}, modifier = Modifier.fillMaxSize())
        return
    }

    // পোর্ট্রেটে ফেরার সময় ভারী Details (LazyColumn) ঘোরার ফ্রেমে না এঁকে দু-ফ্রেম পরে ফেড-ইন
    var detailsReady by remember { mutableStateOf(!landscape) }
    LaunchedEffect(landscape) {
        if (landscape) detailsReady = false
        else { withFrameNanos { }; withFrameNanos { }; detailsReady = true }
    }

    val density = LocalDensity.current.density
    val fsMod = Modifier.zIndex(1f).graphicsLayer {
        val pr = fsProg.value
        // ঘোরা শেষে নতুন লেআউটে পুরোনো ট্রান্সফর্ম এক ফ্রেমও প্রয়োগ হবে না
        if (pr > 0f && landscape == fsFromLandscape) {
            val sw = view.width.toFloat(); val sh = view.height.toFloat()
            if (fsToLandscape) {            // পোর্ট্রেট → ল্যান্ডস্কেপ: ঘুরে (-৯০°) বড় হয়ে পর্দার মাঝে
                val s = lerp(1f, minOf(sh, sw * 16f / 9f) / sw, pr)
                scaleX = s; scaleY = s
                rotationZ = lerp(0f, 90f * fsDir, pr)
                translationY = lerp(0f, sh / 2f - (lastStatusPx + size.height / 2f), pr)
            } else {                        // ল্যান্ডস্কেপ → পোর্ট্রেট: ঘুরে (+৯০°) ছোট হয়ে উপরের জায়গায়
                val wp = sh; val hp = sw
                val st = if (lastStatusPx > 0f) lastStatusPx else 24f * density
                val s = lerp(1f, wp / minOf(sw, sh * 16f / 9f), pr)
                scaleX = s; scaleY = s
                rotationZ = lerp(0f, 90f * fsDir, pr)
                translationX = fsDir * lerp(0f, hp / 2f - st - wp * 9f / 32f, pr)
            }
        }
    }
    val boxModifier = (if (landscape) Modifier.fillMaxSize()
    else Modifier.fillMaxWidth().aspectRatio(16f / 9f).statusBarsPadding()).then(fsMod)

    Column(Modifier.fillMaxSize().background(Bg)) {
        if (vm.loadingNew) {
            // ট্যাপ করার সাথে সাথেই খোলে: থাম্বনেইল + স্কেলিটন, তারপর আসল কনটেন্ট
            LoadingPlayerBox(vm.loadingHint, boxModifier) { vm.minimize() }
            if (!landscape) PlayerDetailsSkeleton(vm.loadingHint?.title)
        } else {
            PlayerBox(vm, landscape, overlay = true, onFullscreen = { toggleFullscreen() }, modifier = boxModifier)
            if (!landscape) AnimatedVisibility(detailsReady, enter = fadeIn(tween(220)), exit = ExitTransition.None) { Details(vm) }
        }
    }
}

/**
 * ফুলস্ক্রিন (ল্যান্ডস্কেপ): সত্যিকারের edge-to-edge। উইন্ডো, স্ট্যাটাস ও নেভিগেশন বার কালো,
 * ভিডিও নচ/কাটআউট পর্যন্ত আঁকা হয়, তাই কোনো পাশে থিমের রঙ (সাদা) দেখা যায় না।
 * বের হলে সব আগের (থিমের) অবস্থায় ফেরে।
 */
private fun setFullscreenWindow(win: android.view.Window, view: View, full: Boolean, themeBg: Int, themeLight: Boolean) {
    val black = android.graphics.Color.BLACK
    val color = if (full) black else themeBg
    WindowCompat.setDecorFitsSystemWindows(win, !full)
    win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
    win.statusBarColor = color
    win.navigationBarColor = color
    val c = WindowCompat.getInsetsController(win, view)
    c.isAppearanceLightStatusBars = !full && themeLight
    c.isAppearanceLightNavigationBars = !full && themeLight
    if (android.os.Build.VERSION.SDK_INT >= 29) win.isNavigationBarContrastEnforced = !full
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        val a = win.attributes
        val mode = if (full)
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        if (a.layoutInDisplayCutoutMode != mode) {   // একই মান হলে অকারণ রিলেআউট নয়
            a.layoutInDisplayCutoutMode = mode
            win.attributes = a
        }
    }
}

@Composable
private fun PlayerBox(
    vm: MainViewModel, landscape: Boolean, overlay: Boolean,
    onFullscreen: () -> Unit, modifier: Modifier
) {
    val ctx = LocalContext.current
    var controls by remember { mutableStateOf(false) }   // কন্ট্রোলার দেখা গেলে তবেই ওভারলে দেখাবে
    var showSettings by remember { mutableStateOf(false) }
    var showCaptions by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val sizes = floatArrayOf(0.04f, 0.0533f, 0.07f)

    // AndroidView-এর factory একবারই চলে, তাই সর্বশেষ মান রাখার জন্য
    val landscapeNow = androidx.compose.runtime.rememberUpdatedState(landscape)
    val fullscreenNow = androidx.compose.runtime.rememberUpdatedState(onFullscreen)

    LaunchedEffect(hud) { if (hud != null && hud != HUD_2X) { kotlinx.coroutines.delay(1000); hud = null } }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    playerView = this
                    player = vm.controllerPlayer
                    // ৫সে পেছনে/১৫সে সামনে বাটনের জায়গায় আগের/পরের ভিডিও বাটন
                    setShowRewindButton(false); setShowFastForwardButton(false)
                    setShowNextButton(true); setShowPreviousButton(true)
                    // কন্ট্রোলারের নিচের ডানের ডিফল্ট সেটিংস গিয়ার বাদ (সেটিংস এখন ওপরে ডানে)
                    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
                    // ExoPlayer-এর নিজস্ব কালো স্ক্রিম/ব্যাকগ্রাউন্ড সরানো
                    findViewById<View>(androidx.media3.ui.R.id.exo_controls_background)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // নিচের সারি (সময় + ফুলস্ক্রিন বাটন) এখন Compose-এ আঁকা; ExoPlayer-এর নিচের বার পুরোটা লুকানো
                    findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)?.visibility = View.GONE
                    findViewById<View>(androidx.media3.ui.R.id.exo_position)?.visibility = View.GONE
                    findViewById<View>(androidx.media3.ui.R.id.exo_duration)?.visibility = View.GONE
                    // ExoPlayer-এর মাঝখানের প্রগ্রেস বার লুকানো; এর বদলে নিচের BottomSeekBar ব্যবহার হয়
                    findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.visibility = View.GONE
                    controls = isControllerFullyVisible
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        controls = v == View.VISIBLE
                    })
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE, 0x99000000.toInt(), android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.WHITE, null
                        )
                    )
                    setOnTouchListener(
                        PlayerGestures(
                            c, vm.player, this, { landscapeNow.value }, { fullscreenNow.value() }, { vm.minimize() },
                            { if (vm.floatingMini) vm.setPull(it) }, { vm.cancelPull() },
                            { zoomIn ->
                                if (landscapeNow.value) {   // ল্যান্ডস্কেপ: বড় = জুম (ক্রপ), ছোট = ফিট
                                    val m = if (zoomIn) 1 else 0
                                    if (vm.resizeMode != m) vm.setResize(m)
                                    hud = Hud(listOf(L.t("ফিট"), L.t("জুম (ক্রপ)"), L.t("ফুল (স্ট্রেচ)"))[m], Icons.Rounded.AspectRatio)
                                } else if (zoomIn) fullscreenNow.value()   // পোর্ট্রেট: বড় করলে ফুলস্ক্রিন
                            }
                        ) { hud = it }
                    )
                }
            },
            update = {
                it.useController = overlay && !vm.audioArt
                it.resizeMode = if (landscape) when (vm.resizeMode) { 1 -> 4; 2 -> 3; else -> 0 } else 0
                it.subtitleView?.setFractionalTextSize(sizes[vm.captionSizeIndex.coerceIn(0, 2)])
                it.subtitleView?.setStyle(captionStyle(vm.captionColorIndex, vm.captionBgIndex))
            },
            modifier = Modifier.fillMaxSize()
        )

        if (vm.audioArt) AudioOnlyLayer(vm)

        // জেসচার HUD (ব্রাইটনেস/ভলিউম/সিক/স্পিড/মিউট): স্ক্রিনের মাঝে উপরে
        hud?.let {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                it.icon?.let { ic ->
                    Icon(ic, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(it.text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Chromecast কন্ট্রোল
        if (overlay && vm.castName != null) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(24.dp)).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CastConnected, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(vm.castName ?: "", color = Color.White, fontSize = 13.sp)
                IconButton(onClick = { vm.castToggle() }) {
                    Icon(if (vm.castPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
                }
                IconButton(onClick = { vm.castStop() }) { Icon(Icons.Filled.Close, null, tint = Color.White) }
            }
        }

        val showBars = controls || vm.audioArt

        // ওপরের সারি: বামে মিনিমাইজ; ডানে ক্যাপশন, ক্রোমকাস্ট, মিউট ও সেটিংস
        if (overlay) AnimatedVisibility(showBars, Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!landscape) PlayerIconButton(Icons.Rounded.KeyboardArrowDown) { vm.minimize() }
                Spacer(Modifier.weight(1f))
                if (vm.captions.isNotEmpty()) {
                    PlayerIconButton(
                        Icons.Rounded.ClosedCaption, tint = if (vm.captionIndex >= 0) YtRed else Color.White
                    ) { showCaptions = true }
                    Spacer(Modifier.width(8.dp))
                }
                if (vm.castAvailable && vm.current != null) PlayerIconButton(
                    if (vm.castName == null) Icons.Rounded.Cast else Icons.Rounded.CastConnected
                ) { if (vm.castName == null) vm.showCastChooser(ctx) else vm.castStop() }
                Spacer(Modifier.width(8.dp))
                PlayerIconButton(if (vm.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp) {
                    vm.toggleMute()
                    hud = if (vm.muted) Hud(L.t("মিউট"), Icons.Rounded.VolumeOff) else Hud(L.t("আনমিউট"), Icons.Rounded.VolumeUp)
                }
                Spacer(Modifier.width(8.dp))
                PlayerIconButton(Icons.Rounded.Settings) { showSettings = true }
            }
        }

        // ভিডিওর একদম নিচে প্রগ্রেস/সিক বার
        if (overlay) BottomSeekBar(vm, controls || vm.audioArt, landscape) { playerView?.showController() }

        // নিচের সারি: বামে সময়, ডানে মাপ (ল্যান্ডস্কেপ) ও ফুলস্ক্রিন/পোর্ট্রেট বাটন — সময়ের লাইন বরাবর
        if (overlay) AnimatedVisibility(showBars, Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = if (landscape) 24.dp else 12.dp, end = if (landscape) 20.dp else 8.dp,
                    bottom = if (landscape) 12.dp else 14.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fmtTime(vm.positionMs / 1000), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("  ·  " + fmtTime(vm.durationMs / 1000), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                if (landscape) PlayerIconButton(
                    Icons.Rounded.AspectRatio, tint = if (vm.resizeMode != 0) YtRed else Color.White
                ) {
                    vm.cycleResize()
                    hud = Hud(listOf(L.t("ফিট"), L.t("জুম (ক্রপ)"), L.t("ফুল (স্ট্রেচ)"))[vm.resizeMode], Icons.Rounded.AspectRatio)
                }
                PlayerIconButton(if (landscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen) { onFullscreen() }
            }
        }
    }

    if (showCaptions) CaptionSheet(vm) { showCaptions = false }
    if (showSettings) PlayerSettingsSheet(vm) { showSettings = false }
}


/** ভিডিওর একদম নিচের কিনারায় প্রগ্রেস/সিক বার (ট্যাপ ও ড্র্যাগ করে সিক করা যায়) */
@Composable
private fun BoxScope.BottomSeekBar(vm: MainViewModel, visible: Boolean, landscape: Boolean, onTouch: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    var w by remember { mutableStateOf(0) }
    val dur = vm.durationMs
    val frac = drag ?: if (dur > 0) (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val r = with(LocalDensity.current) { 6.dp.toPx() }

    // কন্ট্রোল লুকানো থাকলে YouTube-এর মতো পাতলা রঙিন লাইন
    // ল্যান্ডস্কেপে কন্ট্রোল লুকানো থাকলে কিছুই দেখানো হয় না (পোর্ট্রেটে পাতলা লাইন থাকে)
    AnimatedVisibility(!visible && !landscape, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            Box(Modifier.fillMaxSize().background(Color(0x4DFFFFFF)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(YtRed))
        }
    }

    // ল্যান্ডস্কেপে বারটা সময়ের সারির ওপরে ও দুই পাশ থেকে একটু ভেতরে (স্ক্রিনের কিনারায় ধরা কঠিন)
    val seekPad = if (landscape) Modifier.padding(start = 24.dp, end = 24.dp, bottom = 54.dp) else Modifier
    AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(seekPad), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxWidth().height(if (landscape) 28.dp else 22.dp)
                .onSizeChanged { w = it.width }
                .pointerInput(dur) {
                    detectTapGestures { o ->
                        if (dur > 0 && size.width > 0) {
                            vm.player.seekTo(((o.x / size.width).coerceIn(0f, 1f) * dur).toLong())
                            onTouch()
                        }
                    }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f); onTouch() },
                        onDragEnd = { drag?.let { f -> if (dur > 0) vm.player.seekTo((f * dur).toLong()) }; drag = null },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { ch, _ -> ch.consume(); drag = (ch.position.x / size.width).coerceIn(0f, 1f); onTouch() }
                    )
                }
        ) {
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 5.dp).fillMaxWidth().height(3.dp).background(Color(0x4DFFFFFF)))
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 5.dp).fillMaxWidth(frac).height(3.dp).background(YtRed))
            Box(
                Modifier.align(Alignment.BottomStart)
                    .offset { IntOffset((frac * w - r).toInt(), 0) }
                    .size(12.dp).clip(CircleShape).background(YtRed)
            )
        }
    }
}

/** ভিডিও লোড হওয়া পর্যন্ত: জানা থাকলে থাম্বনেইল, তার ওপর shimmer ও লোডিং রিং */
@Composable
private fun LoadingPlayerBox(hint: VideoItem?, modifier: Modifier, onBack: () -> Unit) {
    Box(modifier.background(Color.Black)) {
        if (hint != null) AsyncImage(hint.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        SkeletonBox(Modifier.fillMaxSize().alpha(0.35f), RectangleShape)
        CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), strokeWidth = 3.dp, color = Color.White)
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { PlayerIconButton(Icons.Rounded.KeyboardArrowDown, onClick = onBack) }
    }
}

/** প্লেয়ারের ওপরের রাউন্ডেড আইকন বাটন (আইকনের নিচে কোনো কালো ওভারলে/ব্যাকগ্রাউন্ড নেই) */
@Composable
private fun PlayerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptionSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Chip) {
        Text(L.t("সাবটাইটেল"), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(L.t("ছোট"), L.t("মাঝারি"), L.t("বড়")).forEachIndexed { i, t ->
                FilterChip(selected = vm.captionSizeIndex == i, onClick = { vm.chooseCaptionSize(i) }, label = { Text(t) })
            }
        }
        OptionRow(L.t("রঙ"), listOf(0, 1, 2, 3), vm.captionColorIndex, { listOf(L.t("সাদা"), L.t("হলুদ"), L.t("সবুজ"), L.t("নীল"))[it] }) {
            vm.chooseCaptionColor(it)
        }
        OptionRow(L.t("ব্যাকগ্রাউন্ড"), listOf(0, 1, 2), vm.captionBgIndex, { listOf(L.t("নেই"), L.t("আধা-স্বচ্ছ"), L.t("কালো"))[it] }) {
            vm.chooseCaptionBg(it)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            item {
                Text(
                    L.t("বন্ধ") + if (vm.captionIndex < 0) "  ✓" else "",
                    modifier = Modifier.fillMaxWidth().clickable { vm.chooseCaption(-1); onDismiss() }.padding(16.dp)
                )
            }
            items(vm.captions.size) { i ->
                val c = vm.captions[i]
                Text(
                    c.displayLanguageName + (if (c.isAutoGenerated) L.t(" (স্বয়ংক্রিয়)") else "") +
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
    var showDesc by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.clickable(enabled = info != null) { showDesc = true }.padding(12.dp)) {
                Text(vm.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (info != null) {
                    // ভিউ • লাইক • ডিসলাইক • সময় — এরপর একই লাইনে "আরও দেখুন"
                    StatsLine(info, vm.likes, vm.dislikes, Modifier.padding(top = 6.dp)) {
                        Text(L.t("আরও দেখুন"), color = Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                } else Text(L.t("অফলাইন (ডাউনলোড করা)"), color = Sub, fontSize = 12.sp)
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
                        ChannelAvatar(avatar, info.uploaderUrl, info.uploaderName, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(info.uploaderName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (info.uploaderSubscriberCount >= 0)
                                Text(L.f("{0} সাবস্ক্রাইবার", fmtCount(info.uploaderSubscriberCount)), color = Sub, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { vm.toggleSub(chUrl, info.uploaderName, avatar) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (subbed) Chip else MaterialTheme.colorScheme.onBackground,
                            contentColor = if (subbed) MaterialTheme.colorScheme.onBackground else Bg
                        )
                    ) { Text(if (subbed) L.t("সাবস্ক্রাইবড") else L.t("সাবস্ক্রাইব")) }
                }
            }
            // অ্যাকশন
            item {
                val isFav = favs.any { it.url == info.url }
                LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Pill(if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, L.t("ফেভারিট")) { vm.toggleFavorite() }
                    }
                    item { Pill(Icons.Outlined.Add, L.t("প্লেলিস্টে যোগ")) { showPlaylists = true } }
                    item {
                        Pill(Icons.Outlined.VideoLibrary, L.t("কিউ") + if (vm.queueItems.isNotEmpty()) " (${vm.queueItems.size})" else "") { showQueue = true }
                    }
                    item { Pill(Icons.Outlined.Download, L.t("ডাউনলোড")) { showDownload = true } }
                    item {
                        Pill(Icons.Outlined.Share, L.t("শেয়ার")) {
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
                Text(L.t("অধ্যায়"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp, 0.dp, 12.dp, 6.dp))
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
                        L.t("মন্তব্য") + if (vm.commentsTotal > 0) "  " + fmtCount(vm.commentsTotal.toLong()) else "",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    val first = vm.comments.firstOrNull()
                    if (vm.commentsLoading) SkeletonBox(Modifier.padding(top = 8.dp).fillMaxWidth(0.8f).height(14.dp))
                    else Text(
                        when {
                            first != null -> first.text
                            else -> L.t("মন্তব্য নেই বা বন্ধ আছে")
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

    if (showComments) CommentsSheet(vm) { showComments = false }

    if (showDownload) DownloadSheet(vm) { showDownload = false }
    if (showQueue) QueueSheet(vm) { showQueue = false }
    if (showDesc && info != null) DescriptionSheet(vm, info) { showDesc = false }

    if (showPlaylists) ModalBottomSheet(onDismissRequest = { showPlaylists = false }, containerColor = Chip) {
        var name by remember { mutableStateOf("") }
        Text(L.t("প্লেলিস্টে যোগ করুন"), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(L.t("নতুন প্লেলিস্ট")) }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = { vm.createPlaylistAndAdd(name); name = ""; showPlaylists = false }
            ) { Text(L.t("তৈরি")) }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(lists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id); showPlaylists = false }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.name, modifier = Modifier.weight(1f))
                    Text(L.f("{0} টি", p.itemCount), color = Sub, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- শুধু অডিও মোডের লেয়ার ----------
@Composable
private fun BoxScope.AudioOnlyLayer(vm: MainViewModel) {
    val canPrev = remember(vm.current?.url, vm.queueIndex, vm.queueItems.size) { vm.canPrev() }

    Box(Modifier.matchParentSize().background(Color.Black)) {
        AsyncImage(
            vm.audioArtUrl, null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alpha = 0.45f
        )
    }
    // প্রি/প্লে-পজ/নেক্সট: ভিডিওর ঠিক মাঝখানে (সাধারণ ভিডিও কন্ট্রোলারের মতো)
    Row(
        Modifier.align(Alignment.Center),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        IconButton(onClick = { vm.playPreviousVideo() }, enabled = canPrev, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipPrevious, null, tint = Color.White.copy(alpha = if (canPrev) 1f else 0.3f), modifier = Modifier.size(34.dp))
        }
        IconButton(onClick = { if (vm.isPlaying) vm.player.pause() else vm.player.play() }, modifier = Modifier.size(64.dp)) {
            Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                tint = Color.White, modifier = Modifier.size(48.dp))
        }
        IconButton(onClick = { vm.playNextVideo() }, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
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

private class PTopic(
    val id: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String,
    val toggle: Boolean? = null          // null = পেজে যাওয়ার সারি; নইলে সরাসরি অন/অফ সুইচ
)

/** প্লেয়ার সেটিংস: প্রথমে শুধু টপিকের তালিকা; টপিকে ট্যাপ করলে সেই টপিকের অপশন */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = page != null) { page = null }

    fun sleepLabel(m: Int) = when (m) { 0 -> L.t("বন্ধ"); -1 -> L.t("ভিডিও শেষে"); else -> L.f("{0} মি", m) }

    val topics = buildList {
        add(PTopic(0, Icons.Outlined.Speed, L.t("প্লেব্যাক গতি"), "${vm.speed}x"))
        if (vm.current != null && !vm.audioOnly)
            add(PTopic(1, Icons.Outlined.HighQuality, L.t("ভিডিও কোয়ালিটি"), if (vm.quality == 0) "Auto" else "${vm.quality}p"))
        if (vm.current != null && vm.audioTracks.size > 1)
            add(PTopic(2, Icons.Outlined.Audiotrack, L.t("অডিও ট্র্যাক"),
                vm.audioTracks.firstOrNull { it.id == vm.audioTrackId }?.label ?: ""))
        add(PTopic(7, Icons.Outlined.Headphones, L.t("শুধু অডিও"), L.t("ভিডিও ছাড়া চলবে, ডেটা বাঁচবে"), toggle = vm.audioOnly))
        add(PTopic(6, Icons.Outlined.PlayCircle, L.t("অটোপ্লে"), L.t("ভিডিও শেষে পরের ভিডিও নিজে চলবে"), toggle = vm.autoplay))
        add(PTopic(3, Icons.Outlined.Bedtime, L.t("স্লিপ টাইমার"), sleepLabel(vm.sleepMin)))
        add(PTopic(5, Icons.Outlined.MoreHoriz, L.t("আরও সেটিংস"), "SponsorBlock, Skip silence"))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Chip) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState != null)
                    (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(240))) togetherWith fadeOut(tween(140))
                else
                    fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(160)))
            },
            label = "playerSettings"
        ) { pg ->
            if (pg == null) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    topics.forEach { t ->
                        val canToggle = t.id != 7 || vm.current != null   // শুধু অডিও-র জন্য ভিডিও লাগে; অটোপ্লে সবসময়
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = t.toggle == null || canToggle) {
                                    if (t.toggle == null) page = t.id
                                    else if (t.id == 6) vm.applyAutoplay(!t.toggle)
                                    else vm.toggleAudioOnly(!t.toggle)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Bg.copy(alpha = 0.6f)), Alignment.Center) {
                                Icon(t.icon, null, tint = YtRed, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                if (t.sub.isNotBlank()) Text(t.sub, color = Sub, fontSize = 12.sp, maxLines = 1)
                            }
                            if (t.toggle != null) Switch(
                                checked = t.toggle,
                                onCheckedChange = { if (t.id == 6) vm.applyAutoplay(it) else vm.toggleAudioOnly(it) },
                                enabled = canToggle
                            )
                            else Icon(Icons.Filled.ChevronRight, null, tint = Sub)
                        }
                    }
                }
            } else {
                val title = topics.firstOrNull { it.id == pg }?.title ?: ""
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    when (pg) {
                        0 -> OptionRow(L.t("প্লেব্যাক গতি"), listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), vm.speed, { "${it}x" }) { vm.chooseSpeed(it) }
                        1 -> OptionRow(L.t("ভিডিও কোয়ালিটি"), listOf(0) + vm.heights, vm.quality, { if (it == 0) "Auto" else "${it}p" }) {
                            vm.selectQuality(it)
                        }
                        2 -> OptionRow(
                            L.t("অডিও ট্র্যাক"), vm.audioTracks.map { it.id }, vm.audioTrackId,
                            { id -> vm.audioTracks.firstOrNull { it.id == id }?.label ?: "" }
                        ) { vm.chooseAudioTrack(it) }
                        3 -> {
                            OptionRow(L.t("স্লিপ টাইমার"), listOf(0, 15, 30, 45, 60, -1), vm.sleepMin, { sleepLabel(it) }) { vm.startSleep(it) }
                            if (vm.sleepEnd > 0) {
                                val left = ((vm.sleepEnd - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)
                                Text(L.f("চালু আছে — প্রায় {0} মিনিট পরে থামবে", left), color = Sub, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                        }
                        5 -> {
                            ToggleRow("SponsorBlock", L.t("স্পন্সর, ইন্ট্রো, আউট্রো অংশ নিজে বাদ দেয়"), vm.sponsorOn) { vm.toggleSponsor(it) }
                            ToggleRow(L.t("নীরবতা বাদ (Skip Silence)"), L.t("কথার ফাঁকের নীরব অংশ বাদ যাবে"), vm.skipSilence) {
                                vm.toggleSkipSilence(it)
                            }
                        }
                    }
                }
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
        Text(L.t("ডাউনলোড"), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        if (opts.isEmpty()) Text(L.t("ডাউনলোডের কোনো অপশন পাওয়া যায়নি"), color = Sub, modifier = Modifier.padding(16.dp))
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

// ---------- সাবটাইটেল স্টাইল ----------
private val capColors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF69F0AE.toInt(), 0xFF40C4FF.toInt())

private fun captionStyle(color: Int, bg: Int): CaptionStyleCompat {
    val fg = capColors[color.coerceIn(0, capColors.lastIndex)]
    val clear = android.graphics.Color.TRANSPARENT
    return when (bg) {
        0 -> CaptionStyleCompat(fg, clear, clear, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null)
        1 -> CaptionStyleCompat(fg, 0x99000000.toInt(), clear, CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.WHITE, null)
        else -> CaptionStyleCompat(fg, android.graphics.Color.BLACK, clear, CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.WHITE, null)
    }
}

// ---------- কিউ শিট ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Chip) {
        Text(L.t("কিউ"), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp))
        OptionRow(L.t("রিপিট"), listOf(0, 1, 2), vm.repeat, { listOf(L.t("বন্ধ"), L.t("কিউ"), L.t("একটি ভিডিও"))[it] }) { vm.chooseRepeat(it) }
        ToggleRow(L.t("শাফল"), L.t("কিউ থেকে এলোমেলো ভিডিও চলবে"), vm.shuffle) { vm.toggleShuffle(it) }
        if (vm.queueItems.isEmpty()) Text(
            L.t("কিউ খালি। ভিডিও কার্ডের ⋮ মেনু থেকে পরে চালান বা কিউতে যোগ করুন।"),
            color = Sub, fontSize = 13.sp, modifier = Modifier.padding(16.dp)
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            items(vm.queueItems.size) { i ->
                val q = vm.queueItems[i]
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == vm.queueIndex) YtRed.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { vm.queuePlayAt(i) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", color = Sub, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(q.channel, color = Sub, fontSize = 12.sp, maxLines = 1)
                    }
                    IconButton(onClick = { vm.queueMove(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                    IconButton(onClick = { vm.queueMove(i, 1) }, enabled = i < vm.queueItems.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                    IconButton(onClick = { vm.queueRemove(i) }) { Icon(Icons.Filled.Close, null) }
                }
            }
        }
        if (vm.queueItems.isNotEmpty()) TextButton(onClick = { vm.queueClear() }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(L.t("কিউ খালি করুন"))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- কমেন্ট শিট: স্ক্রোল লোডিং, রিপ্লাই, সাজানো ----------
/** বিবরণ/মন্তব্য শিটের সর্বোচ্চ উচ্চতা: স্ক্রিনের নিচ থেকে প্লেয়ারের নিচের কিনারা পর্যন্ত (প্লেয়ার ঢাকা পড়ে না) */
@Composable
fun rememberSheetMaxHeight(): androidx.compose.ui.unit.Dp {
    val density = LocalDensity.current
    val view = LocalView.current
    // decorFitsSystemWindows = true হলে Compose ইনসেট ০ আসে (ডেকর খেয়ে ফেলে) — তখন রুট ইনসেট/রিসোর্স থেকে আসল মাপ নেওয়া হয়,
    // নইলে শিট স্ট্যাটাস বারের সমান ওপরে উঠে প্লেয়ারের নিচের অংশ ঢেকে দেয়
    val statusPx = maxOf(
        WindowInsets.statusBars.getTop(density),
        androidx.core.view.ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0,
        run {
            val id = view.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) view.resources.getDimensionPixelSize(id) else 0
        }
    )
    val w = view.rootView.width.toFloat()
    val h = view.rootView.height.toFloat()
    return with(density) { (h - statusPx - w * 9f / 16f).toDp() - 28.dp }.coerceAtLeast(240.dp)   // ২৮dp = শিটের হ্যান্ডেল
}

/** বিবরণ/মন্তব্য শিট: হ্যান্ডেল ঠিক ২৮dp ও নিজস্ব ইনসেট শূন্য, যাতে শিটের মাথা ঠিক প্লেয়ারের নিচে থামে */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Chip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent,                       // প্লেয়ার পরিষ্কার দেখা যায়
        contentWindowInsets = { WindowInsets(0) },            // নিজস্ব ইনসেট বন্ধ
        dragHandle = {                                        // ঠিক ২৮dp (12 + 4 + 12)
            Box(
                Modifier.padding(vertical = 12.dp).size(32.dp, 4.dp)
                    .background(Sub.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val sorted = vm.sortedComments()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, sorted.size, vm.commentsMore) {
        if (nearEnd && vm.commentsMore && !vm.commentsLoadingMore && sorted.isNotEmpty()) vm.loadMoreComments()
    }

    val maxH = rememberSheetMaxHeight()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    PlayerSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().height(maxH)) {
            Text(L.t("মন্তব্য") + if (vm.commentsTotal > 0) "  " + fmtCount(vm.commentsTotal.toLong()) else "",
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 0.dp))
            OptionRow(L.t("সাজানো"), listOf(0, 1, 2), vm.commentSort, { listOf(L.t("শীর্ষ"), L.t("নতুন"), L.t("পুরনো"))[it] }) {
                vm.chooseCommentSort(it)
            }
            if (vm.commentSort != 0) Text(
                L.t("লোড হওয়া মন্তব্যের মধ্যে সাজানো হয়েছে"), color = Sub, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = navBottom)   // শেষ কমেন্ট নেভ বারের নিচে যাবে না
            ) {
                items(sorted.size) { i -> CommentRow(vm, sorted[i], indent = false) }
                if (vm.commentsLoading || vm.commentsLoadingMore) item { CommentSkeletons(if (sorted.isEmpty()) 5 else 2) }
                if (sorted.isEmpty() && !vm.commentsLoading) item {
                    Text(L.t("মন্তব্য নেই বা বন্ধ আছে"), color = Sub, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentRow(vm: MainViewModel, c: CommentItem, indent: Boolean) {
    val rs = vm.replyStates[c.id]
    Column(Modifier.padding(start = if (indent) 44.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)) {
        Row {
            ChannelAvatar(c.avatar, c.authorUrl, c.author, if (indent) 24.dp else 32.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${c.author} • ${c.date}", color = Sub, fontSize = 12.sp)
                LinkText(c.text, vm, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.likes.isNotBlank()) {
                        Icon(Icons.Rounded.ThumbUp, null, tint = Sub, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(c.likes, color = Sub, fontSize = 12.sp)
                    }
                    if (!indent && c.replies != null && c.replyCount != 0) TextButton(
                        onClick = { vm.toggleReplies(c) }, contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            if (rs?.expanded == true) L.t("উত্তর লুকান")
                            else if (c.replyCount > 0) L.f("{0} টি উত্তর", c.replyCount)
                            else L.t("উত্তর দেখুন"),
                            fontSize = 12.sp, color = YtRed
                        )
                    }
                }
            }
        }
        if (rs?.expanded == true) {
            rs.items.forEach { r -> CommentRow(vm, r, indent = true) }
            if (rs.loading) CircularProgressIndicator(
                Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp, color = YtRed
            ) else if (rs.next != null) TextButton(onClick = { vm.loadMoreReplies(c) }) {
                Text(L.t("আরও উত্তর"), fontSize = 12.sp)
            }
        }
    }
}
