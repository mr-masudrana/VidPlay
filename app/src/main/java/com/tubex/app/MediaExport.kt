package com.tubex.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** ডাউনলোড করা ফাইল গ্যালারি/মিউজিকে সেভ করা ও অন্য অ্যাপে শেয়ার করা */
object MediaExport {
    private fun mime(d: DownloadEntity): String = when {
        d.audioOnly -> "audio/mp4"
        d.path.endsWith(".webm") -> "video/webm"
        else -> "video/mp4"
    }

    /** ভিডিও: Movies/TubeX, অডিও: Music/TubeX। ব্যাকগ্রাউন্ড থ্রেডে ডাকুন। */
    fun save(ctx: Context, d: DownloadEntity) {
        val src = File(d.path)
        require(src.exists()) { L.t("ফাইল পাওয়া যায়নি") }
        val name = src.name
        val audio = d.audioOnly
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime(d))
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/TubeX"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val coll = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val resolver = ctx.contentResolver
            val uri = resolver.insert(coll, values) ?: error(L.t("সেভ করা যায়নি"))
            try {
                resolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val base = Environment.getExternalStoragePublicDirectory(
                if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            )
            val dir = File(base, "TubeX").also { it.mkdirs() }
            val dest = File(dir, name)
            src.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime(d)), null)
        }
    }

    fun share(ctx: Context, d: DownloadEntity) {
        val f = File(d.path)
        require(f.exists()) { L.t("ফাইল পাওয়া যায়নি") }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime(d)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
