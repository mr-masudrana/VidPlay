package com.example.vidplay

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // ব্যাকগ্রাউন্ড সার্ভিস চালু/বাইন্ড করার জন্য
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        MediaController.Builder(this, token).buildAsync()
            .also { f -> f.addListener({ controller = f.get() }, mainExecutor) }

        setContent { YtTheme(vm.themeIndex) { YtApp(vm) } }
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
        controller?.release()
        super.onDestroy()
    }
}
