package com.tubex.app.data.local

import com.tubex.app.data.local.entity.BlockEntity
import com.tubex.app.data.local.entity.DownloadEntity
import com.tubex.app.data.local.entity.FavoriteEntity
import com.tubex.app.data.local.entity.HistoryEntity
import com.tubex.app.data.local.entity.InterestEntity
import com.tubex.app.data.local.entity.PlaylistEntity
import com.tubex.app.data.local.entity.PlaylistItemEntity
import com.tubex.app.data.local.entity.PlaylistRow
import com.tubex.app.data.local.entity.SubEntity

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
    @Query("UPDATE downloads SET state = 1 WHERE id = :id AND state = 0") suspend fun markRunning(id: Long): Int
    @Query("UPDATE downloads SET progress = :p WHERE id = :id") suspend fun setProgress(id: Long, p: Int)
    @Query("UPDATE downloads SET state = 3, progress = 1000 WHERE id = :id") suspend fun markDone(id: Long)
    @Query("UPDATE downloads SET state = 2 WHERE id = :id AND state IN (0, 1, 5)") suspend fun pauseOne(id: Long)
    @Query("UPDATE downloads SET state = 2 WHERE state IN (0, 1, 5)") suspend fun pauseAll()
    @Query("UPDATE downloads SET state = 0 WHERE id = :id AND state IN (2, 4)") suspend fun resumeOne(id: Long)
    @Query("UPDATE downloads SET state = 0 WHERE state IN (2, 4)") suspend fun resumeAll()
    @Query("UPDATE downloads SET state = 2 WHERE state IN (1, 5)") suspend fun resetInterrupted()
    @Query("UPDATE downloads SET state = 0 WHERE id = :id AND state = 1") suspend fun requeueRunning(id: Long): Int

    // ইতিহাস
    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 100") fun history(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history") suspend fun historyOnce(): List<HistoryEntity>
    @Query("SELECT * FROM history WHERE url = :url") suspend fun historyOf(url: String): HistoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistory(h: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM history WHERE url = :url") suspend fun deleteHistory(url: String)
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
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: Long, name: String)
    @Query("UPDATE playlist_items SET addedAt = :at WHERE id = :id") suspend fun setItemOrder(id: Long, at: Long)
}
