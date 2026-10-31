package com.tubex.app

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

/** চেপে ধরে থাকলে ২x স্পিডের HUD লেখা (ছাড়া পর্যন্ত থাকে) */
const val HUD_2X = "⏩ 2x"

/**
 * প্লেয়ারের উপর জেসচার:
 *  - ল্যান্ডস্কেপ: বাম ৩৩% এ উপর/নিচ টানলে ব্রাইটনেস, ডান ৩৩% এ ভলিউম (পোর্ট্রেটে এ দুটো বন্ধ)
 *  - পোর্ট্রেট: উপরে সোয়াইপ = ফুলস্ক্রিন (ল্যান্ডস্কেপ), নিচে সোয়াইপ = মিনি প্লেয়ার ও হোম
 *  - ল্যান্ডস্কেপ: মাঝের অংশে নিচে সোয়াইপ = পোর্ট্রেটে ফেরা
 *  - চেপে ধরে রাখলে ২x স্পিড, ছাড়লে আগের স্পিড
 *  - আড়াআড়ি টানলে সিক
 *  - ডবল ট্যাপ (বাম/ডান) = ১০ সেকেন্ড পেছনে/সামনে। এরপর ৭০০ মিলিসেকেন্ডের মধ্যে আরও ট্যাপ করলে
 *    প্রতি ট্যাপে আরও ১০ সেকেন্ড (১০, ২০, ৩০…); সিঙ্গেল ট্যাপ = কন্ট্রোল দেখানো/লুকানো
 * কন্ট্রোলের বাটন/সিকবার আগের মতোই কাজ করে (শিশু ভিউ আগে টাচ পায়)।
 */
class PlayerGestures(
    private val ctx: Context,
    private val player: Player,
    private val pv: PlayerView,
    private val isLandscape: () -> Boolean,
    private val onToggleFullscreen: () -> Unit,
    private val onMinimize: () -> Unit,
    private val onHud: (String?) -> Unit
) : View.OnTouchListener {

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mode = 0                 // 0 = নেই, 1 = সিক, 2 = ব্রাইটনেস, 3 = ভলিউম, 4 = উল্লম্ব সোয়াইপ (ফুলস্ক্রিন/মিনি)
    private var swipeDy = 0f             // উপরে ধনাত্মক
    private var holding = false
    private var holdPrevSpeed = 1f
    private var startPos = 0L
    private var seekTarget = 0L
    private var startValue = 0f

    private val clearHud = Runnable { onHud(null) }
    private fun clearHudLater() { pv.removeCallbacks(clearHud); pv.postDelayed(clearHud, 700) }

    // সিক-সিরিজ: ডবল ট্যাপের পর দ্রুত পরের ট্যাপগুলো যোগ হয়
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val runWindowMs = 700L
    private var runSide = 0            // -1 বাম, 1 ডান, 0 নেই
    private var runCount = 0
    private var lastSeekTime = 0L
    private var runGesture = false     // বর্তমান টাচ সিরিজের অংশ
    private var runMoved = false
    private var downX = 0f
    private var downY = 0f

    private fun runActive(t: Long) = runSide != 0 && t - lastSeekTime < runWindowMs

    private fun seekRun(side: Int, count: Int, t: Long) {
        runSide = side; runCount = count; lastSeekTime = t
        seekBy(side * 10_000L)
        val sec = count * 10
        onHud(if (side > 0) "⏩ +" + sec + L.t(" সে") else "⏪ -" + sec + L.t(" সে"))
        clearHudLater()
    }

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

        override fun onLongPress(e: MotionEvent) {
            if (mode != 0 || holding) return
            holdPrevSpeed = player.playbackParameters.speed
            holding = true
            player.setPlaybackSpeed(2f)
            onHud(HUD_2X)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            seekRun(if (e.x > pv.width / 2f) 1 else -1, 1, e.eventTime)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e1.y - e2.y          // উপরে টানলে ধনাত্মক
            if (mode == 0) {
                mode = when {
                    abs(dx) > abs(dy) -> 1
                    !isLandscape() -> 4                       // পোর্ট্রেটে ভলিউম/ব্রাইটনেস নেই
                    e1.x < pv.width * 0.33f -> 2
                    e1.x > pv.width * 0.67f -> 3
                    else -> 4
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
                4 -> swipeDy = dy
                1 -> {
                    val delta = (dx / pv.width.coerceAtLeast(1) * 120_000f).toLong()
                    val dur = player.duration
                    seekTarget = (startPos + delta).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                    val sec = (seekTarget - startPos) / 1000
                    onHud((if (sec >= 0) "⏩ +" else "⏪ ") + sec + L.t(" সে"))
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
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            runGesture = runActive(e.eventTime); runMoved = false
            downX = e.x; downY = e.y
        }
        if (runGesture) {   // সিক-সিরিজ চলছে: প্রতিটি ট্যাপ = আরও ১০ সেকেন্ড (ডিটেক্টরে যায় না)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE ->
                    if (abs(e.x - downX) > slop || abs(e.y - downY) > slop) runMoved = true
                MotionEvent.ACTION_UP -> {
                    if (!runMoved && runActive(e.eventTime)) {
                        val side = if (e.x > pv.width / 2f) 1 else -1
                        seekRun(side, if (side == runSide) runCount + 1 else 1, e.eventTime)
                    } else runSide = 0
                    runGesture = false
                }
                MotionEvent.ACTION_CANCEL -> { runGesture = false; runSide = 0 }
            }
            return true
        }
        detector.onTouchEvent(e)
        if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
            if (holding) {                       // চেপে ধরা ছাড়লে আগের স্পিড
                holding = false
                player.setPlaybackSpeed(holdPrevSpeed)
                onHud(null)
            }
            if (mode == 1) player.seekTo(seekTarget)
            if (mode == 4 && e.action == MotionEvent.ACTION_UP) {
                val thr = 64f * ctx.resources.displayMetrics.density
                if (isLandscape()) { if (swipeDy < -thr) onToggleFullscreen() }          // নিচে সোয়াইপ → পোর্ট্রেট
                else if (swipeDy > thr) onToggleFullscreen()                              // উপরে → ল্যান্ডস্কেপ
                else if (swipeDy < -thr) onMinimize()                                     // নিচে → মিনি প্লেয়ার
            }
            swipeDy = 0f
            if (mode != 0 && mode != 4) clearHudLater()
            mode = 0
        }
        return true
    }
}
