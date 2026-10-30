package com.tubex.app

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/** UI আর ব্যাকগ্রাউন্ড সার্ভিস একই প্লেয়ার শেয়ার করে। */
object PlayerHolder {
    private var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer = player ?: ExoPlayer.Builder(ctx.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true
        )
        .setHandleAudioBecomingNoisy(true)
        .build().also { player = it }
}
