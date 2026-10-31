package com.tubex.app

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** ব্যাকগ্রাউন্ড প্লে + নোটিফিকেশন/লকস্ক্রিন কন্ট্রোল */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // নোটিফিকেশন বা লকস্ক্রিন কন্ট্রোলে ট্যাপ করলে অ্যাপ খুলে সরাসরি প্লেয়ার পেজে যায়
        val open = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, PlayerHolder.navPlayer(this)).setSessionActivity(pi).build()
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
