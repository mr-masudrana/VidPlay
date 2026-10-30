package com.tubex.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** আলাদা ভিডিও (H.264 MP4) ও অডিও (M4A) ফাইলকে রি-এনকোড ছাড়াই একটি MP4-তে জোড়া লাগায়। */
object Muxer {
    fun mux(video: File, audio: File, out: File, webm: Boolean = false) {
        val ve = MediaExtractor()
        val ae = MediaExtractor()
        val muxer = MediaMuxer(
            out.absolutePath,
            if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        try {
            ve.setDataSource(video.absolutePath)
            ae.setDataSource(audio.absolutePath)
            val vt = track(ve, "video/")
            val at = track(ae, "audio/")
            require(vt >= 0 && at >= 0) { L.t("ভিডিও/অডিও ট্র্যাক পাওয়া যায়নি") }
            ve.selectTrack(vt); ae.selectTrack(at)
            val vi = muxer.addTrack(ve.getTrackFormat(vt))
            val ai = muxer.addTrack(ae.getTrackFormat(at))
            muxer.start()

            val vb = ByteBuffer.allocate(16 * 1024 * 1024)
            val ab = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var vTime = ve.sampleTime
            var aTime = ae.sampleTime
            // সময় অনুযায়ী মিশিয়ে লেখা (interleave)
            while (vTime >= 0 || aTime >= 0) {
                if (vTime >= 0 && (aTime < 0 || vTime <= aTime)) {
                    write(ve, muxer, vi, vb, info); ve.advance(); vTime = ve.sampleTime
                } else {
                    write(ae, muxer, ai, ab, info); ae.advance(); aTime = ae.sampleTime
                }
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            ve.release(); ae.release()
        }
    }

    private fun track(e: MediaExtractor, prefix: String): Int {
        for (i in 0 until e.trackCount) {
            if (e.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
        }
        return -1
    }

    private fun write(e: MediaExtractor, m: MediaMuxer, idx: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        buf.clear()
        val size = e.readSampleData(buf, 0)
        val flags = if ((e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        info.set(0, size, e.sampleTime, flags)
        m.writeSampleData(idx, buf, info)
    }
}
