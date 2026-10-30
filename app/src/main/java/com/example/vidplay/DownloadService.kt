package com.example.vidplay

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** ডাউনলোড চলার সময় প্রসেস জীবিত রাখে এবং প্রগ্রেস নোটিফিকেশন দেখায়। */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notif.ensureChannels(this)
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(L.t("ডাউনলোড চলছে"), -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (!collecting) {
            collecting = true
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            scope.launch {
                DownloadEngine.status.collect { s ->
                    if (s != null) nm.notify(NOTIF_ID, build(s.first, s.second))
                }
            }
            scope.launch {
                DownloadEngine.active.collect { on ->
                    if (!on) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, pct: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, Notif.CH_DL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(L.t("ডাউনলোড"))
            .setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(pi)
        if (pct >= 0) b.setProgress(100, pct, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object { private const val NOTIF_ID = 4001 }
}
