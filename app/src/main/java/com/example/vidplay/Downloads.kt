package com.example.vidplay

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

/** Android DownloadManager দিয়ে ডাউনলোড (নোটিফিকেশন সহ)। ভিডিও = muxed স্ট্রিম (সাধারণত ≤720p)। */
object Downloads {

    fun enqueue(ctx: Context, info: StreamInfo, audioOnly: Boolean): DownloadEntity {
        val stream = if (audioOnly)
            info.audioStreams.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
                ?: info.audioStreams.maxByOrNull { it.averageBitrate }
        else
            info.videoStreams.maxByOrNull { it.height }
        requireNotNull(stream) { "ডাউনলোডের মতো স্ট্রিম পাওয়া যায়নি" }

        val ext = stream.format?.suffix ?: if (audioOnly) "m4a" else "mp4"
        val safe = info.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifBlank { "video" }
        val fileName = "${safe}_${System.currentTimeMillis()}.$ext"

        val req = DownloadManager.Request(Uri.parse(stream.content))
            .setTitle(info.name)
            .addRequestHeader("User-Agent", MyDownloader.USER_AGENT)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_MOVIES, fileName)

        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(req)
        val path = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName).absolutePath
        return DownloadEntity(id, info.name, path, info.thumbnails.lastOrNull()?.url, audioOnly)
    }

    /** returns (status, percent). status -1 = রেকর্ড নেই */
    fun progress(ctx: Context, id: Long): Pair<Int, Int> {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return -1 to 0
            val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return st to (if (total > 0) (got * 100 / total).toInt() else 0)
        }
    }

    fun remove(ctx: Context, d: DownloadEntity) {
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(d.id)
        File(d.path).delete()
    }
}
