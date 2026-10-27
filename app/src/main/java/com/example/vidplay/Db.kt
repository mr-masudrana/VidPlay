package com.example.vidplay

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "subs")
data class SubEntity(@PrimaryKey val url: String, val name: String, val avatar: String?)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: Long, val title: String, val path: String, val thumb: String?, val audioOnly: Boolean
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String?,
    val thumb: String?,
    val durationSec: Long,
    val positionMs: Long,
    val watchedAt: Long
)

/** অন-ডিভাইস রিকমেন্ডেশনের আগ্রহ: "ch:<চ্যানেল>" বা "kw:<শব্দ>" */
@Entity(tableName = "interests")
data class InterestEntity(@PrimaryKey val topic: String, val score: Double, val updatedAt: Long)

@Dao
interface AppDao {
    @Query("SELECT * FROM subs ORDER BY name") fun subs(): Flow<List<SubEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSub(s: SubEntity)
    @Query("DELETE FROM subs WHERE url = :url") suspend fun removeSub(url: String)

    @Query("SELECT * FROM downloads ORDER BY id DESC") fun downloads(): Flow<List<DownloadEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addDownload(d: DownloadEntity)
    @Delete suspend fun removeDownload(d: DownloadEntity)

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 100") fun history(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history WHERE url = :url") suspend fun historyOf(url: String): HistoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistory(h: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("SELECT url FROM history WHERE durationSec > 0 AND positionMs >= durationSec * 900")
    suspend fun watchedUrls(): List<String>

    @Query("SELECT * FROM interests") suspend fun interests(): List<InterestEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInterests(list: List<InterestEntity>)
    @Query("DELETE FROM interests") suspend fun clearInterests()
    @Query("DELETE FROM interests WHERE topic NOT IN (SELECT topic FROM interests ORDER BY score DESC LIMIT 400)")
    suspend fun pruneInterests()
}

@Database(
    entities = [SubEntity::class, DownloadEntity::class, HistoryEntity::class, InterestEntity::class],
    version = 2, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "vidplay.db")
                .fallbackToDestructiveMigration()   // v1 -> v2: পুরনো ডেটা মুছে নতুন টেবিল বানায়
                .build().also { inst = it }
        }
    }
}
