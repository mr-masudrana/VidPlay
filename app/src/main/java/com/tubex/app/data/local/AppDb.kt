package com.tubex.app.data.local

import com.tubex.app.data.local.entity.BlockEntity
import com.tubex.app.data.local.entity.DownloadEntity
import com.tubex.app.data.local.entity.FavoriteEntity
import com.tubex.app.data.local.entity.HistoryEntity
import com.tubex.app.data.local.entity.InterestEntity
import com.tubex.app.data.local.entity.PlaylistEntity
import com.tubex.app.data.local.entity.PlaylistItemEntity
import com.tubex.app.data.local.entity.SubEntity

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubEntity::class, DownloadEntity::class, HistoryEntity::class, InterestEntity::class,
        PlaylistEntity::class, PlaylistItemEntity::class, FavoriteEntity::class, BlockEntity::class
    ],
    version = 4, exportSchema = true
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
                .fallbackToDestructiveMigrationFrom(1)   // শুধু v1 থেকে; বাকি মাইগ্রেশন ব্যর্থ হলে ডেটা চুপচাপ মুছবে না
                .build().also { inst = it }
        }
    }
}
