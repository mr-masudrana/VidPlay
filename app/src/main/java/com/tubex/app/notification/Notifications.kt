package com.tubex.app.notification

import com.tubex.app.MainActivity
import com.tubex.app.R
import com.tubex.app.data.local.entity.SubEntity
import com.tubex.app.data.model.VideoItem
import com.tubex.app.util.L

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

object Notif {
    const val CH_DL = "downloads"
    const val CH_NEW = "new_videos"
    const val CH_UPDATE = "app_update"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_DL, L.t("ডাউনলোড"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_NEW, L.t("নতুন ভিডিও"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_UPDATE, L.t("অ্যাপ আপডেট"), NotificationManager.IMPORTANCE_DEFAULT))
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
