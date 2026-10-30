package com.tubex.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "subs")
data class SubEntity(@PrimaryKey val url: String, val name: String, val avatar: String?)

/** state: 0 কিউ, 1 ডাউনলোড হচ্ছে, 2 থামানো, 3 সম্পন্ন, 4 ব্যর্থ, 5 মার্জ হচ্ছে। progress: 0..1000 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoUrl: String,
    val title: String,
    val channel: String,
    val thumb: String?,
    val audioOnly: Boolean,
    val height: Int,
    val videoItag: Int,
    val audioItag: Int,
    val path: String,
    val state: Int,
    val progress: Int,
    val createdAt: Long
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String, val title: String, val channel: String, val channelUrl: String?,
    val thumb: String?, val durationSec: Long, val positionMs: Long, val watchedAt: Long
)

@Entity(tableName = "interests")
data class InterestEntity(@PrimaryKey val topic: String, val score: Double, val updatedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long, val url: String, val title: String, val channel: String,
    val thumb: String?, val durationSec: Long, val addedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val url: String, val title: String, val channel: String,
    val thumb: String?, val durationSec: Long, val addedAt: Long
)

/** entry: "ch:<চ্যানেল>", "kw:<কিওয়ার্ড>", "vid:<url>" */
@Entity(tableName = "blocks")
data class BlockEntity(@PrimaryKey val entry: String)

data class PlaylistRow(val id: Long, val name: String, val createdAt: Long, val itemCount: Int)

@Dao
interface AppDao {
    // সাবস্ক্রিপশন
    @Query("SELECT * FROM subs ORDER BY name") fun subs(): Flow<List<SubEntity>>
    @Query("SELECT * FROM subs") suspend fun subsOnce(): List<SubEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSub(s: SubEntity)
    @Query("DELETE FROM subs WHERE url = :url") suspend fun removeSub(url: String)

    // ডাউনলোড
    @Query("SELECT * FROM downloads ORDER BY id DESC") fun downloads(): Flow<List<DownloadEntity>>
    @Insert suspend fun addDownload(d: DownloadEntity): Long
    @Delete suspend fun removeDownload(d: DownloadEntity)
    @Query("SELECT * FROM downloads WHERE state = 0 ORDER BY id LIMIT 1") suspend fun nextQueued(): DownloadEntity?
    @Query("SELECT * FROM downloads WHERE path = :path LIMIT 1") suspend fun downloadByPath(path: String): DownloadEntity?
    @Query("UPDATE downloads SET state = :state WHERE id = :id") suspend fun setState(id: Long, state: Int)
    @Query("UPDATE downloads SET progress = :p WHERE id = :id") suspend fun setProgress(id: Long, p: Int)
    @Query("UPDATE downloads SET state = 3, progress = 1000 WHERE id = :id") suspend fun markDone(id: Long)
    @Query("UPDATE downloads SET state = 2 WHERE id = :id AND state IN (0, 1)") suspend fun pauseOne(id: Long)
    @Query("UPDATE downloads SET state = 2 WHERE state IN (0, 1)") suspend fun pauseAll()
    @Query("UPDATE downloads SET state = 0 WHERE id = :id AND state IN (2, 4)") suspend fun resumeOne(id: Long)
    @Query("UPDATE downloads SET state = 0 WHERE state IN (2, 4)") suspend fun resumeAll()
    @Query("UPDATE downloads SET state = 2 WHERE state IN (1, 5)") suspend fun resetInterrupted()

    // ইতিহাস
    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 100") fun history(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history") suspend fun historyOnce(): List<HistoryEntity>
    @Query("SELECT * FROM history WHERE url = :url") suspend fun historyOf(url: String): HistoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistory(h: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("SELECT url FROM history WHERE durationSec > 0 AND positionMs >= durationSec * 900")
    suspend fun watchedUrls(): List<String>

    // রিকমেন্ডেশন
    @Query("SELECT * FROM interests") suspend fun interests(): List<InterestEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInterests(list: List<InterestEntity>)
    @Query("DELETE FROM interests") suspend fun clearInterests()
    @Query("DELETE FROM interests WHERE topic NOT IN (SELECT topic FROM interests ORDER BY score DESC LIMIT 400)")
    suspend fun pruneInterests()

    // ব্লক
    @Query("SELECT * FROM blocks ORDER BY entry") fun blocks(): Flow<List<BlockEntity>>
    @Query("SELECT * FROM blocks") suspend fun blocksOnce(): List<BlockEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addBlock(b: BlockEntity)
    @Query("DELETE FROM blocks WHERE entry = :entry") suspend fun removeBlock(entry: String)

    // ফেভারিট
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites") suspend fun favoritesOnce(): List<FavoriteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE url = :url") suspend fun removeFavorite(url: String)

    // প্লেলিস্ট
    @Query(
        "SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, " +
                "(SELECT COUNT(*) FROM playlist_items WHERE playlistId = p.id) AS itemCount " +
                "FROM playlists p ORDER BY p.createdAt DESC"
    )
    fun playlists(): Flow<List<PlaylistRow>>
    @Query("SELECT * FROM playlists") suspend fun playlistsOnce(): List<PlaylistEntity>
    @Query("SELECT id FROM playlists WHERE name = :name LIMIT 1") suspend fun playlistIdByName(name: String): Long?
    @Insert suspend fun addPlaylist(p: PlaylistEntity): Long
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id") suspend fun deletePlaylistItems(id: Long)
    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY addedAt") fun playlistItems(id: Long): Flow<List<PlaylistItemEntity>>
    @Query("SELECT * FROM playlist_items") suspend fun allPlaylistItems(): List<PlaylistItemEntity>
    @Insert suspend fun addPlaylistItem(i: PlaylistItemEntity)
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :pid AND url = :url") suspend fun countItem(pid: Long, url: String): Int
    @Query("DELETE FROM playlist_items WHERE id = :id") suspend fun removePlaylistItem(id: Long)
}

@Database(
    entities = [
        SubEntity::class, DownloadEntity::class, HistoryEntity::class, InterestEntity::class,
        PlaylistEntity::class, PlaylistItemEntity::class, FavoriteEntity::class, BlockEntity::class
    ],
    version = 4, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `channel` TEXT NOT NULL, `thumb` TEXT, `durationSec` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`url` TEXT NOT NULL, `title` TEXT NOT NULL, `channel` TEXT NOT NULL, `thumb` TEXT, `durationSec` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))")
            }
        }

        // v3 -> v4: ব্লক টেবিল + নতুন ডাউনলোড স্কিমা (পুরনো ডাউনলোড "সম্পন্ন" হিসেবে থাকে)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `blocks` (`entry` TEXT NOT NULL, PRIMARY KEY(`entry`))")
                db.execSQL("CREATE TABLE `downloads_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `videoUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `channel` TEXT NOT NULL, `thumb` TEXT, `audioOnly` INTEGER NOT NULL, `height` INTEGER NOT NULL, `videoItag` INTEGER NOT NULL, `audioItag` INTEGER NOT NULL, `path` TEXT NOT NULL, `state` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `downloads_new` (`videoUrl`, `title`, `channel`, `thumb`, `audioOnly`, `height`, `videoItag`, `audioItag`, `path`, `state`, `progress`, `createdAt`) SELECT '', `title`, '', `thumb`, `audioOnly`, 0, 0, 0, `path`, 3, 1000, `id` FROM `downloads`")
                db.execSQL("DROP TABLE `downloads`")
                db.execSQL("ALTER TABLE `downloads_new` RENAME TO `downloads`")
            }
        }

        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "tubex.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}
