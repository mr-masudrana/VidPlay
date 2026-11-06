package com.tubex.app.ui.settings

import com.tubex.app.util.L
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.update.UpdateInfo
import com.tubex.app.update.Updater
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun AboutSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }

    SectionCard(Icons.Outlined.Info, L.t("অ্যাপ সম্পর্কে")) {
        Text("TubeX", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(L.f("ভার্সন {0} (বিল্ড {1})", Updater.currentVersion, Updater.currentCode), color = Sub, fontSize = 13.sp)

        OutlinedButton(
            onClick = {
                checking = true; failed = false; info = null
                scope.launch {
                    val r = Updater.fetchLatest()
                    if (r == null) failed = true else info = r
                    checking = false
                }
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text(if (checking) L.t("চেক করা হচ্ছে…") else L.t("আপডেট চেক করুন")) }

        val r = info
        when {
            !Updater.configured -> Text(
                L.t("আপডেট সোর্স সেট নেই: GitHub Actions বিল্ডে নিজে সেট হয়, লোকালি -PupdateRepo=owner/repo দিন"),
                color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            failed -> Text(L.t("আপডেট চেক করা যায়নি (ইন্টারনেট বা রিলিজ নেই)"), color = Sub, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
            r != null && Updater.hasUpdate(r) -> Column(Modifier.padding(top = 8.dp)) {
                Text(L.f("নতুন ভার্সন {0} পাওয়া গেছে", r.version), fontWeight = FontWeight.SemiBold, color = YtRed)
                if (r.notes.isNotBlank()) Text(r.notes.trim().take(300), color = Sub, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
                Button(
                    onClick = { Updater.open(ctx, r.apkUrl ?: r.pageUrl) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(L.t("আপডেট ডাউনলোড করুন")) }
            }
            r != null -> Text(L.t("আপনি সর্বশেষ ভার্সনে আছেন"), color = Sub, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
        }

        SwitchRow(
            L.t("আপডেটের নোটিফিকেশন"),
            L.t("নতুন ভার্সন এলে নোটিফিকেশন ও অ্যাপে পপআপ পাবেন (প্রতি ২৪ ঘণ্টায় চেক)"),
            vm.updateNotifyOn
        ) { vm.toggleUpdateNotify(it) }

        if (Updater.configured) TextButton(onClick = { Updater.open(ctx, "https://github.com/${Updater.repo}/releases") }) {
            Text(L.t("সোর্স কোড / রিলিজ পেজ"))
        }
    }
}
