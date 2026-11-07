package com.tubex.app

import com.tubex.app.player.service.PlaybackService
import com.tubex.app.ui.main.YtApp
import com.tubex.app.ui.splash.SplashOverlay
import com.tubex.app.ui.theme.YtTheme
import com.tubex.app.update.UpdateDialog
import com.tubex.app.update.Updater
import com.tubex.app.viewmodel.MainViewModel

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
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

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val storagePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.onStoragePermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // শুধু UI খুললে (ব্যাকগ্রাউন্ড ওয়ার্কারে নয়) poToken আগে থেকে প্রস্তুত রাখি
        if (savedInstanceState == null) com.tubex.app.data.potoken.PoTokenProviderImpl.prewarm()

        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.requestStoragePermission = {
            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        // ব্যাকগ্রাউন্ড সার্ভিস চালু/বাইন্ড করার জন্য
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
            .also { f -> f.addListener({ controller = runCatching { f.get() }.getOrNull() }, mainExecutor) }

        val coldStart = savedInstanceState == null
        if (coldStart) vm.checkUpdatePopup()
        setContent {
            // ভিডিও চলার সময় স্ক্রিন জাগিয়ে রাখা (অডিও-অনলি মোডে স্ক্রিন বন্ধ হতে দেওয়া হয়)
            val keepOn = vm.isPlaying && !vm.audioArt
            DisposableEffect(keepOn) {
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            YtTheme(vm.themeIndex, vm.themeAuto, vm.accentColor) {
                var showSplash by rememberSaveable { mutableStateOf(coldStart) }
                Box(Modifier.fillMaxSize()) {
                    YtApp(vm)
                    vm.updatePopup?.let { u ->
                        UpdateDialog(
                            u,
                            onDownload = { Updater.open(this@MainActivity, u.apkUrl ?: u.pageUrl); vm.dismissUpdatePopup() },
                            onLater = { vm.dismissUpdatePopup() }
                        )
                    }
                    if (showSplash) SplashOverlay { showSplash = false }
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    // আসল (অপরিবর্তিত) টাচের স্ক্রিন-কোঅর্ডিনেট রাখে: প্লেয়ার টানার সময় প্লেয়ার নিজে স্কেল/সরে যায়, তাই তার লোকাল কোঅর্ডিনেট ভরসাযোগ্য নয়
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) TouchRaw.downY = ev.rawY
        TouchRaw.y = ev.rawY
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** YouTube লিংক (VIEW) বা শেয়ার করা টেক্সট (SEND) থেকে ভিডিও/চ্যানেল খোলা */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action == ACTION_OPEN_PLAYER) { vm.expand(); return }   // নোটিফিকেশন থেকে: প্লেয়ার পেজ
        val url = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { Regex("https?://\\S+").find(it)?.value }
            else -> null
        } ?: return
        vm.openLink(url)
    }

    /**
     * PiP চালু করে (ভিডিও চলতে থাকলে ও PiP অন থাকলে)। ভাসমান মিনি প্লেয়ার থেকে হলে আগে প্লেয়ার পেজ পূর্ণ করে নেয়,
     * যাতে PiP উইন্ডোতে হোম পেজ নয়, শুধু ভিডিও দেখায়।
     */
    fun enterPipFromApp(): Boolean {
        if (Build.VERSION.SDK_INT < 26 || !vm.pipOn || !vm.active || !vm.player.isPlaying) return false
        if (!vm.expanded) vm.expand()
        return runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }.getOrDefault(false)
    }

    // অ্যাপ নিজে অন্য স্ক্রিন (শেয়ার/ব্রাউজার ইত্যাদি) খুললেও অ্যান্ড্রয়েড onUserLeaveHint পাঠায়; সেটা যেন PiP না চালায়
    private var selfLaunchAt = 0L
    override fun startActivity(intent: Intent?, options: Bundle?) {
        selfLaunchAt = android.os.SystemClock.uptimeMillis()
        super.startActivity(intent, options)
    }

    // ফোনের হোম বাটন/জেসচারে বেরোলে PiP: প্লেয়ার পেজ খোলা থাকুক, বা যেকোনো পেজ/ট্যাবে মিনি প্লেয়ার (ভাসমান/নিচের বার) থাকুক
    override fun onUserLeaveHint() {
        if (android.os.SystemClock.uptimeMillis() - selfLaunchAt < 1500) return
        enterPipFromApp()      // ভিডিও চলছে ও PiP অন থাকলেই চালু হয়
    }

    // ব্যাকগ্রাউন্ড প্লে বন্ধ থাকলে অ্যাপ থেকে বেরোলে (PiP ছাড়া) প্লেব্যাক থামে
    override fun onStop() {
        super.onStop()
        val inPip = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode
        if (!vm.backgroundPlay && !inPip && !isChangingConfigurations) vm.player.pause()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.inPip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        vm.requestStoragePermission = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.tubex.app.OPEN_PLAYER"
    }
}

/** Activity-তে আসা আসল টাচের স্ক্রিন Y (px) */
object TouchRaw {
    @JvmField var downY = 0f
    @JvmField var y = 0f
}
