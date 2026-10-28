package com.example.vidplay

import android.content.Context
import android.os.Environment
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

data class DlOption(
    val label: String, val note: String,
    val height: Int, val videoItag: Int, val audioItag: Int, val audioOnly: Boolean
)

object Downloads {

    /** ডাউনলোডের অপশন: H.264 ভিডিও + M4A অডিও (মার্জ), সরাসরি muxed, শুধু অডিও */
    fun options(info: StreamInfo): List<DlOption> {
        val out = ArrayList<DlOption>()
        val audio = info.audioStreams.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }

        if (audio != null) {
            info.videoOnlyStreams
                .filter { it.format == MediaFormat.MPEG_4 && it.codec?.startsWith("avc1") == true }
                .groupBy { it.height }
                .map { (h, list) -> h to list.first() }
                .sortedByDescending { it.first }
                .forEach { (h, v) ->
                    out += DlOption("${h}p", "ভিডিও + অডিও মার্জ", h, v.itag, audio.itag, false)
                }
        }
        info.videoStreams.sortedByDescending { it.height }.forEach { v ->
            if (out.none { it.height == v.height }) out += DlOption("${v.height}p", "সরাসরি (মার্জ ছাড়া)", v.height, v.itag, 0, false)
        }
        (audio ?: info.audioStreams.maxByOrNull { it.averageBitrate })?.let {
            out += DlOption("শুধু অডিও", "m4a", 0, 0, it.itag, true)
        }
        return out
    }

    suspend fun enqueue(ctx: Context, dao: AppDao, info: StreamInfo, o: DlOption) {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
        val safe = info.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(50).ifBlank { "video" }
        val tag = if (o.audioOnly) "audio" else "${o.height}p"
        val ext = if (o.audioOnly) "m4a" else "mp4"
        val path = File(dir, "${safe}_${info.id}_$tag.$ext").absolutePath

        val existing = dao.downloadByPath(path)
        require(existing == null || existing.state == 4) { "এটি আগে থেকেই ডাউনলোড তালিকায় আছে" }
        if (existing != null) dao.removeDownload(existing)

        dao.addDownload(
            DownloadEntity(
                videoUrl = info.url, title = info.name, channel = info.uploaderName ?: "",
                thumb = info.thumbnails.lastOrNull()?.url, audioOnly = o.audioOnly, height = o.height,
                videoItag = o.videoItag, audioItag = o.audioItag, path = path,
                state = 0, progress = 0, createdAt = System.currentTimeMillis()
            )
        )
        DownloadEngine.kick(ctx)
    }
}
