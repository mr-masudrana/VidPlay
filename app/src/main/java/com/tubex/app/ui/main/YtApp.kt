package com.tubex.app.ui.main

import com.tubex.app.player.ui.FloatingPlayer
import com.tubex.app.player.ui.MorphPlayer
import com.tubex.app.player.ui.PlayerScreen
import com.tubex.app.ui.channel.ChannelScreen
import com.tubex.app.ui.navigation.PredictiveBackScreen
import com.tubex.app.ui.playlist.PlaylistScreen
import com.tubex.app.ui.theme.Bg
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
            PredictiveBackScreen(enabled = vm.channelOpen && !vm.playerShown, onBack = { vm.closeChannel() }) { ChannelScreen(vm) }
        }
        // ইউটিউব প্লেলিস্ট পেজ (চ্যানেল পেজের ওপরেও খোলে)
        AnimatedVisibility(
            visible = vm.playlistOpen, modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)),
            exit = fadeOut(tween(240))
        ) {
            PredictiveBackScreen(enabled = vm.playlistOpen && !vm.playerShown, onBack = { vm.closePlaylist() }) { PlaylistScreen(vm) }
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
