package com.tubex.app.player.ui

import com.tubex.app.data.model.VideoItem
import com.tubex.app.util.L
import com.tubex.app.player.gesture.HUD_2X
import com.tubex.app.player.gesture.Hud
import com.tubex.app.player.gesture.PlayerGestures
import com.tubex.app.ui.components.PlayerDetailsSkeleton
import com.tubex.app.ui.components.SkeletonBox
import com.tubex.app.ui.theme.Bg
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.LocalPalette
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.util.fmtTime
import com.tubex.app.viewmodel.MainViewModel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
            onRelease = { it.player = null },   // সিঙ্গেলটন প্লেয়ার থেকে ভিউ খুলে নিই (Activity লিক ঠেকাতে)
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

@Composable
internal fun Pill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Chip).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, fontSize = 13.sp)
    }
}

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
