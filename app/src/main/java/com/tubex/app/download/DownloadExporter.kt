package com.tubex.app.download

import com.tubex.app.data.local.entity.DownloadEntity
import com.tubex.app.util.L

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ডাউনলোড করা ফাইল গ্যালারিতে সেভ ও শেয়ার (পুরনো অ্যান্ড্রয়েডে স্টোরেজ পারমিশনসহ)। MainViewModel থেকে আলাদা করা। */
class DownloadExporter(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit
) {
    private var pendingSave: DownloadEntity? = null

    /** MainActivity বসায়: পুরনো অ্যান্ড্রয়েডে স্টোরেজ পারমিশন চাওয়ার জন্য */
    var requestStoragePermission: (() -> Unit)? = null

    fun saveToGallery(d: DownloadEntity) {
        if (Build.VERSION.SDK_INT < 29 &&
            ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = d
            requestStoragePermission?.invoke()
            return
        }
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { MediaExport.save(ctx, d) } }
            toast(if (r.isSuccess) L.t("গ্যালারিতে সেভ হয়েছে") else L.f("সেভ ব্যর্থ: {0}", r.exceptionOrNull()?.message))
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val d = pendingSave; pendingSave = null
        if (d == null) return
        if (granted) saveToGallery(d) else toast(L.t("স্টোরেজ পারমিশন ছাড়া সেভ করা যাচ্ছে না"))
    }

    fun share(d: DownloadEntity) {
        runCatching { MediaExport.share(ctx, d) }.onFailure { toast(L.f("শেয়ার ব্যর্থ: {0}", it.message)) }
    }
}
