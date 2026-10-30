package com.tubex.app

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/** প্রোফাইল ব্যাকআপের ডেটা অংশ (সাবস্ক্রিপশন, আগ্রহ, ইতিহাস, ফেভারিট, ব্লক, প্লেলিস্ট)। সেটিংস ViewModel-এ থাকে। */
object ProfileBackup {
    /** পুরনো "vidplay" মার্কারওয়ালা ব্যাকআপও গ্রহণযোগ্য */
    fun isValid(root: JSONObject) = root.optString("app") in setOf("tubex", "vidplay")

    suspend fun build(dao: AppDao, prefs: JSONObject): JSONObject {
        val root = JSONObject()
        root.put("app", "tubex"); root.put("version", 1)
        root.put("subs", JSONArray().also { a ->
            dao.subsOnce().forEach { a.put(JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar)) }
        })
        root.put("interests", JSONArray().also { a ->
            dao.interests().forEach {
                a.put(JSONObject().put("topic", it.topic).put("score", it.score).put("updatedAt", it.updatedAt))
            }
        })
        root.put("history", JSONArray().also { a ->
            dao.historyOnce().forEach {
                a.put(
                    JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                        .put("channelUrl", it.channelUrl).put("thumb", it.thumb)
                        .put("durationSec", it.durationSec).put("positionMs", it.positionMs)
                        .put("watchedAt", it.watchedAt)
                )
            }
        })
        root.put("favorites", JSONArray().also { a ->
            dao.favoritesOnce().forEach {
                a.put(
                    JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                        .put("thumb", it.thumb).put("durationSec", it.durationSec).put("addedAt", it.addedAt)
                )
            }
        })
        root.put("blocks", JSONArray(dao.blocksOnce().map { it.entry }))
        val byList = dao.allPlaylistItems().groupBy { it.playlistId }
        root.put("playlists", JSONArray().also { a ->
            dao.playlistsOnce().forEach { p ->
                val items = JSONArray()
                byList[p.id].orEmpty().forEach {
                    items.put(
                        JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                            .put("thumb", it.thumb).put("durationSec", it.durationSec).put("addedAt", it.addedAt)
                    )
                }
                a.put(JSONObject().put("name", p.name).put("createdAt", p.createdAt).put("items", items))
            }
        })
        root.put("prefs", prefs)
        return root
    }

    suspend fun restore(dao: AppDao, root: JSONObject) {
        fun s(o: JSONObject, k: String): String? = o.optString(k).takeIf { it.isNotEmpty() && it != "null" }

        root.optJSONArray("subs")?.objects()?.forEach {
            dao.addSub(SubEntity(it.getString("url"), it.optString("name", "?"), s(it, "avatar")))
        }
        root.optJSONArray("interests")?.objects()?.let { list ->
            dao.upsertInterests(list.map { InterestEntity(it.getString("topic"), it.optDouble("score", 0.0), it.optLong("updatedAt")) })
        }
        root.optJSONArray("history")?.objects()?.forEach {
            dao.upsertHistory(
                HistoryEntity(
                    it.getString("url"), it.optString("title"), it.optString("channel"), s(it, "channelUrl"),
                    s(it, "thumb"), it.optLong("durationSec"), it.optLong("positionMs"), it.optLong("watchedAt")
                )
            )
        }
        root.optJSONArray("blocks")?.let { a -> for (i in 0 until a.length()) dao.addBlock(BlockEntity(a.getString(i))) }
        root.optJSONArray("favorites")?.objects()?.forEach {
            dao.addFavorite(
                FavoriteEntity(
                    it.getString("url"), it.optString("title"), it.optString("channel"), s(it, "thumb"),
                    it.optLong("durationSec"), it.optLong("addedAt")
                )
            )
        }
        root.optJSONArray("playlists")?.objects()?.forEach { p ->
            val name = p.getString("name")
            val pid = dao.playlistIdByName(name)
                ?: dao.addPlaylist(PlaylistEntity(name = name, createdAt = p.optLong("createdAt")))
            p.optJSONArray("items")?.objects()?.forEach {
                val url = it.getString("url")
                if (dao.countItem(pid, url) == 0) dao.addPlaylistItem(
                    PlaylistItemEntity(
                        playlistId = pid, url = url, title = it.optString("title"), channel = it.optString("channel"),
                        thumb = s(it, "thumb"), durationSec = it.optLong("durationSec"), addedAt = it.optLong("addedAt")
                    )
                )
            }
        }
    }
}
