package com.tubex.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // ব্যাকগ্রাউন্ড সার্ভিস চালু/বাইন্ড করার জন্য
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
            .also { f -> f.addListener({ controller = runCatching { f.get() }.getOrNull() }, mainExecutor) }

        val coldStart = savedInstanceState == null
        setContent {
            YtTheme(vm.themeIndex) {
                var showSplash by rememberSaveable { mutableStateOf(coldStart) }
                Box(Modifier.fillMaxSize()) {
                    YtApp(vm)
                    if (showSplash) SplashOverlay { showSplash = false }
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** YouTube লিংক (VIEW) বা শেয়ার করা টেক্সট (SEND) থেকে ভিডিও/চ্যানেল খোলা */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val url = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { Regex("https?://\\S+").find(it)?.value }
            else -> null
        } ?: return
        vm.openLink(url)
    }

    // হোম বাটন চাপলে (ভিডিও চলাকালীন) PiP
    override fun onUserLeaveHint() {
        if (vm.active && vm.expanded && vm.player.isPlaying && Build.VERSION.SDK_INT >= 26) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.inPip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}
