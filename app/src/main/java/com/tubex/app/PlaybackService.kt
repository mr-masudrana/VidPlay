package com.tubex.app

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** ব্যাকগ্রাউন্ড প্লে + নোটিফিকেশন/লকস্ক্রিন কন্ট্রোল */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, PlayerHolder.navPlayer(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
