package com.tubex.app.notification

import com.tubex.app.data.local.AppDb
import com.tubex.app.data.local.Prefs
import com.tubex.app.data.local.entity.SubEntity
import com.tubex.app.data.model.VideoItem
import com.tubex.app.data.recommend.Blocks
import com.tubex.app.data.remote.YtRepository

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** সাবস্ক্রাইব করা চ্যানেলে নতুন ভিডিও খোঁজে; প্রথমবার শুধু "দেখা" হিসেবে চিহ্নিত করে (নোটিফিকেশন নয়)। */
class NewVideoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val prefs = Prefs(app)
        if (!prefs.notifyEnabled) return Result.success()
        val dao = AppDb.get(app).dao()
        val all = dao.subsOnce().sortedBy { it.url }
        if (all.isEmpty()) return Result.success()
        // প্রতি রানে ৩০টি চ্যানেল; পরের রানে পরের ৩০টি (৩০-এর বেশি সাবস্ক্রিপশনেও সবাই কভার হয়)
        val batch = minOf(30, all.size)
        val start = prefs.notifyCursor.let { if (it >= all.size) 0 else it }
        val subs = List(batch) { all[(start + it) % all.size] }
        prefs.notifyCursor = (start + batch) % all.size

        val blocks = dao.blocksOnce().map { it.entry }.toSet()
        val seen = LinkedHashSet(prefs.seenVideos)
        val base = prefs.baselined.toMutableSet()
        val fresh = ArrayList<Pair<SubEntity, VideoItem>>()

        for (s in subs) {
            val vids = runCatching { YtRepository.channelVideos(s.url).take(6) }.getOrNull() ?: continue
            if (s.url !in base) {
                seen.addAll(vids.map { it.url }); base.add(s.url)
                continue
            }
            vids.filter { it.url !in seen }.forEach { v ->
                seen.add(v.url)
                if (Blocks.allowed(blocks, v.title, v.channel, v.url)) fresh.add(s to v)
            }
        }
        prefs.seenVideos = seen.toList().takeLast(2000)
        prefs.baselined = base
        Notif.newVideos(app, fresh.take(5), fresh.size)
        return Result.success()
    }
}

object NotifyScheduler {
    private const val NAME = "new_videos"
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun apply(ctx: Context, enabled: Boolean, hours: Int, replace: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) { wm.cancelUniqueWork(NAME); return }
        val req = PeriodicWorkRequestBuilder<NewVideoWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(net).build()
        wm.enqueueUniquePeriodicWork(
            NAME, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<NewVideoWorker>().setConstraints(net).build())
    }
}
