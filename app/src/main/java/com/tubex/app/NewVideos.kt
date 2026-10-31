package com.tubex.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object Notif {
    const val CH_DL = "downloads"
    const val CH_NEW = "new_videos"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_DL, L.t("ডাউনলোড"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_NEW, L.t("নতুন ভিডিও"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    @SuppressLint("MissingPermission")
    fun newVideos(ctx: Context, list: List<Pair<SubEntity, VideoItem>>, total: Int) {
        if (list.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        list.forEach { (s, v) ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(v.url)).setClass(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                ctx, v.url.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, CH_NEW)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(s.name).setContentText(v.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(v.title))
                .setContentIntent(pi).setAutoCancel(true).setGroup("new_videos").build()
            nm.notify(v.url.hashCode(), n)
        }
        if (total > 1) {
            val summary = NotificationCompat.Builder(ctx, CH_NEW)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(L.f("{0} টি নতুন ভিডিও", total))
                .setGroup("new_videos").setGroupSummary(true).setAutoCancel(true).build()
            nm.notify(7001, summary)
        }
    }
}

/** সাবস্ক্রাইব করা চ্যানেলে নতুন ভিডিও খোঁজে; প্রথমবার শুধু "দেখা" হিসেবে চিহ্নিত করে (নোটিফিকেশন নয়)। */
class NewVideoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val prefs = Prefs(app)
        if (!prefs.notifyEnabled) return Result.success()
        val dao = AppDb.get(app).dao()
        val subs = dao.subsOnce().take(30)
        if (subs.isEmpty()) return Result.success()

        val blocks = dao.blocksOnce().map { it.entry }.toSet()
        val seen = LinkedHashSet(prefs.seenVideos)
        val base = prefs.baselined.toMutableSet()
        val fresh = ArrayList<Pair<SubEntity, VideoItem>>()

        for (s in subs) {
            val vids = runCatching { YtRepository.channel(s.url).videos.take(6) }.getOrNull() ?: continue
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
