package com.example.vidplay

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "subs")
data class SubEntity(@PrimaryKey val url: String, val name: String, val avatar: String?)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: Long,          // DownloadManager id
    val title: String,
    val path: String,
    val thumb: String?,
    val audioOnly: Boolean
)

@Dao
interface AppDao {
    @Query("SELECT * FROM subs ORDER BY name") fun subs(): Flow<List<SubEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSub(s: SubEntity)
    @Query("DELETE FROM subs WHERE url = :url") suspend fun removeSub(url: String)

    @Query("SELECT * FROM downloads ORDER BY id DESC") fun downloads(): Flow<List<DownloadEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addDownload(d: DownloadEntity)
    @Delete suspend fun removeDownload(d: DownloadEntity)
}

@Database(entities = [SubEntity::class, DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "vidplay.db")
                .build().also { inst = it }
        }
    }
}
