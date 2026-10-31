package com.tubex.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String)

/** GitHub Releases থেকে সর্বশেষ ভার্সন খোঁজে */
object Updater {
    val repo: String get() = BuildConfig.UPDATE_REPO
    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val currentCode: Int get() = BuildConfig.VERSION_CODE
    val configured: Boolean get() = repo.contains("/")

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('-', '+')[0].split('.').map { it.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** সর্বশেষ রিলিজ; নেটওয়ার্ক/রিপো সমস্যায় null */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val body = Extras.get("https://api.github.com/repos/$repo/releases/latest") ?: return@withContext null
        runCatching {
            val o = JSONObject(body)
            val tag = o.getString("tag_name")
            var apk: String? = null
            val assets = o.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) { apk = a.optString("browser_download_url"); break }
            }
            UpdateInfo(tag.removePrefix("v"), o.optString("html_url"), apk, o.optString("body"))
        }.getOrNull()
    }

    fun hasUpdate(info: UpdateInfo) = isNewer(info.version, currentVersion)

    fun open(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, info: UpdateInfo) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        Notif.ensureChannels(ctx)
        val pi = PendingIntent.getActivity(
            ctx, 7002,
            Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl ?: info.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = L.f("নতুন ভার্সন {0} এসেছে (আপনার ভার্সন {1})", info.version, currentVersion)
        val n = NotificationCompat.Builder(ctx, Notif.CH_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(L.t("TubeX আপডেট"))
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(7002, n)
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val prefs = Prefs(app)
        if (!prefs.updateNotify || !Updater.configured) return Result.success()
        val info = Updater.fetchLatest() ?: return Result.success()
        if (Updater.hasUpdate(info) && prefs.lastNotifiedUpdate != info.version) {
            Updater.notify(app, info)
            prefs.lastNotifiedUpdate = info.version
        }
        return Result.success()
    }
}

object UpdateScheduler {
    private const val NAME = "app_update_check"

    fun apply(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled || !Updater.configured) { wm.cancelUniqueWork(NAME); return }
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
