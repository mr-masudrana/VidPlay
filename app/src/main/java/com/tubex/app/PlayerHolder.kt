package com.tubex.app

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import android.net.Uri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/** পরের/আগের ভিডিওর লজিক ViewModel দেয়; প্লেয়ার কন্ট্রোলার ও নোটিফিকেশন এটাই ব্যবহার করে। */
interface Navigator {
    fun canNext(): Boolean
    fun canPrev(): Boolean
    fun next()
    fun prev()
}

/**
 * একটাই মিডিয়া আইটেমের ExoPlayer-কে "পরের/আগের" বাটনসহ দেখায়।
 * PlayerView ও MediaSession (নোটিফিকেশন/লকস্ক্রিন) দুটোই এটা ব্যবহার করে।
 */
private class NavPlayer(p: Player) : ForwardingPlayer(p) {
    private val nav get() = PlayerHolder.navigator
    override fun getAvailableCommands(): Player.Commands {
        val n = nav?.canNext() == true
        val pr = nav?.canPrev() == true
        return super.getAvailableCommands().buildUpon()
            .addIf(Player.COMMAND_SEEK_TO_NEXT, n).addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, n)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, true).addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, pr)
            .build()
    }
    override fun isCommandAvailable(command: Int) = availableCommands.contains(command)

    /** নোটিফিকেশন/লকস্ক্রিনে এখন যেটা চলছে তার টাইটেল, চ্যানেল ও থাম্বনেইল */
    override fun getMediaMetadata(): MediaMetadata {
        val m = PlayerHolder.metadata
        return if (m === MediaMetadata.EMPTY) super.getMediaMetadata() else m
    }
    override fun hasNextMediaItem() = nav?.canNext() == true
    override fun hasPreviousMediaItem() = nav?.canPrev() == true
    override fun seekToNext() { nav?.next() }
    override fun seekToNextMediaItem() { nav?.next() }
    override fun seekToPrevious() { nav?.prev() ?: super.seekToPrevious() }
    override fun seekToPreviousMediaItem() { nav?.prev() ?: super.seekToPrevious() }
}

/** UI আর ব্যাকগ্রাউন্ড সার্ভিস একই প্লেয়ার শেয়ার করে। */
object PlayerHolder {
    private var player: ExoPlayer? = null
    private var navPlayer: Player? = null
    @Volatile var navigator: Navigator? = null

    /** এখন চলতে থাকা ভিডিওর মেটাডেটা (নোটিফিকেশনে দেখায়) */
    @Volatile var metadata: MediaMetadata = MediaMetadata.EMPTY

    fun setNowPlaying(title: String?, channel: String?, thumbUrl: String?) {
        metadata = MediaMetadata.Builder()
            .setTitle(title).setDisplayTitle(title)
            .setArtist(channel).setSubtitle(channel)
            .apply { if (!thumbUrl.isNullOrBlank()) setArtworkUri(Uri.parse(thumbUrl)) }
            .build()
    }

    fun clearNowPlaying() { metadata = MediaMetadata.EMPTY }

    /** PlayerView ও MediaSession-এর জন্য পরের/আগের বাটনসহ প্লেয়ার */
    fun navPlayer(ctx: Context): Player = navPlayer ?: NavPlayer(get(ctx)).also { navPlayer = it }

    fun get(ctx: Context): ExoPlayer = player ?: ExoPlayer.Builder(ctx.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true
        )
        .setHandleAudioBecomingNoisy(true)
        // দ্রুত শুরু: ১ সেকেন্ড বাফার হলেই চালু (ডিফল্ট ২.৫ সে), বাফারিং-এর পর ২ সেকেন্ডে ফেরা
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 60_000, 1_000, 2_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .build().also { player = it }
}
