package com.example.vidplay

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * প্লেয়ারের উপর জেসচার:
 *  - বাম পাশে উপর/নিচ টানলে ব্রাইটনেস, ডান পাশে ভলিউম
 *  - আড়াআড়ি টানলে সিক
 *  - ডবল ট্যাপ (বাম/ডান) = ১০ সেকেন্ড পেছনে/সামনে; সিঙ্গেল ট্যাপ = কন্ট্রোল দেখানো/লুকানো
 * কন্ট্রোলের বাটন/সিকবার আগের মতোই কাজ করে (শিশু ভিউ আগে টাচ পায়)।
 */
class PlayerGestures(
    private val ctx: Context,
    private val player: Player,
    private val pv: PlayerView,
    private val onHud: (String?) -> Unit
) : View.OnTouchListener {

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mode = 0                 // 0 = নেই, 1 = সিক, 2 = ব্রাইটনেস, 3 = ভলিউম
    private var startPos = 0L
    private var seekTarget = 0L
    private var startValue = 0f

    private fun clearHudLater() { pv.postDelayed({ onHud(null) }, 700) }

    private fun seekBy(deltaMs: Long) {
        val dur = player.duration
        val max = if (dur > 0) dur else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
    }

    private fun brightness(): Float {
        val b = ctx.findActivity()?.window?.attributes?.screenBrightness ?: -1f
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) { 0.5f }
    }

    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val forward = e.x > pv.width / 2f
            seekBy(if (forward) 10_000L else -10_000L)
            onHud(if (forward) "⏩ +10 সে" else "⏪ -10 সে")
            clearHudLater()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e1.y - e2.y          // উপরে টানলে ধনাত্মক
            if (mode == 0) {
                mode = when {
                    abs(dx) > abs(dy) -> 1
                    e1.x < pv.width / 2f -> 2
                    else -> 3
                }
                when (mode) {
                    1 -> startPos = player.currentPosition
                    2 -> startValue = brightness()
                    3 -> startValue = audio.getStreamVolume(AudioManager.STREAM_MUSIC) /
                            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                }
            }
            val h = pv.height.coerceAtLeast(1).toFloat()
            when (mode) {
                1 -> {
                    val delta = (dx / pv.width.coerceAtLeast(1) * 120_000f).toLong()
                    val dur = player.duration
                    seekTarget = (startPos + delta).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                    val sec = (seekTarget - startPos) / 1000
                    onHud((if (sec >= 0) "⏩ +" else "⏪ ") + sec + " সে")
                }
                2 -> {
                    val v = (startValue + dy / h).coerceIn(0.02f, 1f)
                    ctx.findActivity()?.window?.let { w ->
                        val lp = w.attributes; lp.screenBrightness = v; w.attributes = lp
                    }
                    onHud("🔆 ${(v * 100).roundToInt()}%")
                }
                3 -> {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val v = (startValue + dy / h).coerceIn(0f, 1f)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt().coerceIn(0, max), 0)
                    onHud("🔊 ${(v * 100).roundToInt()}%")
                }
            }
            return true
        }
    })

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
            if (mode == 1) player.seekTo(seekTarget)
            if (mode != 0) clearHudLater()
            mode = 0
        }
        return true
    }
}
