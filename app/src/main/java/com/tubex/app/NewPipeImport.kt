package com.tubex.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.ZipInputStream

/** NewPipe ব্যাকআপ ZIP (newpipe.db) থেকে সাবস্ক্রিপশন ও ইতিহাস ইম্পোর্ট */
object NewPipeImport {
    suspend fun importZip(ctx: Context, dao: AppDao, bytes: ByteArray): Pair<Int, Int> {
        val dbFile = File(ctx.cacheDir, "newpipe_import.db")
        var found = false
        ZipInputStream(bytes.inputStream()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (e.name.endsWith("newpipe.db")) {
                    dbFile.outputStream().use { out -> zin.copyTo(out) }
                    found = true
                    break
                }
                e = zin.nextEntry
            }
        }
        require(found) { L.t("ZIP-এ newpipe.db পাওয়া যায়নি") }

        var subsN = 0
        var histN = 0
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("SELECT url, name, avatar_url FROM subscriptions WHERE service_id = 0", null).use { c ->
                while (c.moveToNext()) {
                    dao.addSub(SubEntity(c.getString(0), c.getString(1) ?: "?", c.getString(2))); subsN++
                }
            }
            val hist = ArrayList<HistoryEntity>()
            db.rawQuery(
                "SELECT s.url, s.title, s.uploader, s.uploader_url, s.thumbnail_url, s.duration, h.access_date, " +
                        "COALESCE(st.progress_time, 0) FROM stream_history h JOIN streams s ON s.uid = h.stream_id " +
                        "LEFT JOIN stream_state st ON st.stream_id = s.uid WHERE s.service_id = 0 " +
                        "ORDER BY h.access_date DESC LIMIT 800", null
            ).use { c ->
                while (c.moveToNext()) {
                    hist.add(
                        HistoryEntity(
                            c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3),
                            c.getString(4), c.getLong(5), c.getLong(7), c.getLong(6)
                        )
                    )
                }
            }
            val uniq = hist.distinctBy { it.url }        // নতুনটাই আগে থাকে
            uniq.forEach { dao.upsertHistory(it) }
            histN = uniq.size
            Recommender.seed(dao, uniq.take(300).map { it.title to it.channel }, 0.5)   // রিকমেন্ডেশন প্রোফাইলের বীজ
        } finally {
            db.close(); dbFile.delete()
        }
        return subsN to histN
    }
}
