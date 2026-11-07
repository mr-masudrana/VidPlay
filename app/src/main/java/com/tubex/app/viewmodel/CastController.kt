package com.tubex.app.viewmodel

import com.tubex.app.data.remote.YtRepository
import com.tubex.app.util.L

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.mediarouter.app.MediaRouteChooserDialog
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.Executors

/**
 * Chromecast: সেশন, রিমোট ক্লায়েন্ট ও কাস্টে লোড। MainViewModel থেকে আলাদা করা হয়েছে।
 * প্লেয়ারের সাথে যোগাযোগ হয় শুধু কনস্ট্রাক্টরের ল্যাম্বডা দিয়ে।
 */
class CastController(
    private val app: Application,
    private val currentInfo: () -> StreamInfo?,
    private val playerPositionMs: () -> Long,
    private val pausePlayer: () -> Unit,
    private val toast: (String) -> Unit
) {
    var available by mutableStateOf(false); private set
    /** কাস্ট ডিভাইসের নাম; null = কানেক্টেড নয় */
    var deviceName by mutableStateOf<String?>(null); private set
    var playing by mutableStateOf(false); private set

    private var castContext: CastContext? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var clientRef: RemoteMediaClient? = null
    private var callback: RemoteMediaClient.Callback? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionStarted(s: CastSession, id: String) { onConnected(s) }
        override fun onSessionStartFailed(s: CastSession, e: Int) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionEnded(s: CastSession, e: Int) { deviceName = null; playing = false }
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) { onConnected(s) }
        override fun onSessionResumeFailed(s: CastSession, e: Int) {}
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }

    /** Google Play Services না থাকলে নীরবে বাদ */
    fun init() {
        runCatching {
            CastContext.getSharedInstance(app, executor)
                .addOnSuccessListener { cc ->
                    castContext = cc; available = true
                    cc.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                }
        }
    }

    private fun client(): RemoteMediaClient? = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    private fun onConnected(s: CastSession) {
        deviceName = s.castDevice?.friendlyName ?: "Cast"
        val c = s.remoteMediaClient ?: return
        callback?.let { clientRef?.unregisterCallback(it) }
        val cb = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() { playing = c.isPlaying }
        }
        c.registerCallback(cb); clientRef = c; callback = cb
        currentInfo()?.let { load(it, playerPositionMs()) }
    }

    fun load(info: StreamInfo, posMs: Long) {
        val client = client() ?: return
        val stream = YtRepository.castStream(info)
        if (stream == null) { toast(L.t("এই ভিডিও কাস্ট করা যাবে না")); return }
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        meta.putString(MediaMetadata.KEY_TITLE, info.name)
        info.thumbnails.lastOrNull()?.url?.let { meta.addImage(WebImage(Uri.parse(it))) }
        val mi = MediaInfo.Builder(stream.content)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(stream.format?.mimeType ?: "video/mp4")
            .setMetadata(meta).build()
        client.load(
            MediaLoadRequestData.Builder().setMediaInfo(mi).setAutoplay(true).setCurrentTime(posMs).build()
        )
        pausePlayer()
    }

    fun showChooser(activityCtx: Context) {
        val sel = castContext?.mergedSelector ?: return
        MediaRouteChooserDialog(activityCtx).apply { routeSelector = sel }.show()
    }

    fun toggle() { client()?.togglePlayback() }
    fun pause() { client()?.pause() }
    fun stop() { castContext?.sessionManager?.endCurrentSession(true) }

    fun release() {
        callback?.let { clientRef?.unregisterCallback(it) }
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        executor.shutdown()
    }
}
