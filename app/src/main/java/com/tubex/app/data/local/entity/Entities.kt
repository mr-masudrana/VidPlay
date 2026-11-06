package com.tubex.app.data.local.entity

import androidx.room.*

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
