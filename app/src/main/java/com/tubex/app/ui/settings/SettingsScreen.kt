package com.tubex.app.ui.settings

import com.tubex.app.util.L
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class SettingsTopic(val icon: ImageVector, val title: String, val sub: String)

private fun settingsTopics(): List<SettingsTopic> = listOf(
    SettingsTopic(Icons.Outlined.Language, L.t("ভাষা / Language"), L.t("অ্যাপের ভাষা বদলান")),
    SettingsTopic(Icons.Outlined.Palette, L.t("চেহারা ও থিম"), L.t("থিম, রঙ ও চেহারা")),
    SettingsTopic(Icons.Outlined.PlayCircle, L.t("প্লেব্যাক"), L.t("অটোপ্লে, PiP, ভাসমান প্লেয়ার, স্পন্সর বাদ")),
    SettingsTopic(Icons.Outlined.DataSaverOn, L.t("ডেটা সেভার ও কোয়ালিটি"), L.t("ভিডিওর মান ও ডেটা সাশ্রয়")),
    SettingsTopic(Icons.Outlined.Download, L.t("ডাউনলোড"), L.t("শুধু ওয়াইফাই ও একসাথে কয়টা ডাউনলোড")),
    SettingsTopic(Icons.Outlined.PrivacyTip, L.t("প্রাইভেসি ও ইতিহাস"), L.t("সার্চ ও দেখার ইতিহাস")),
    SettingsTopic(Icons.Outlined.Notifications, L.t("নতুন ভিডিওর নোটিফিকেশন"), L.t("সাবস্ক্রাইব করা চ্যানেলের নতুন ভিডিও")),
    SettingsTopic(Icons.Outlined.Tune, "DeArrow", L.t("ক্লিকবেইট শিরোনাম ও থাম্বনেইল ঠিক করা")),
    SettingsTopic(Icons.Outlined.Link, L.t("YouTube লিংক"), L.t("YouTube লিংক খোলার ব্যবস্থা")),
    SettingsTopic(Icons.Outlined.Insights, L.t("আপনার রিকমেন্ডেশন প্রোফাইল"), L.t("আপনার আগ্রহের বিষয়গুলো")),
    SettingsTopic(Icons.Outlined.Block, L.t("ব্লক তালিকা"), L.t("লুকাতে চাওয়া চ্যানেল ও শব্দ")),
    SettingsTopic(Icons.Outlined.Storage, L.t("ডেটা"), L.t("ইম্পোর্ট, এক্সপোর্ট ও রিসেট")),
    SettingsTopic(Icons.Outlined.Info, L.t("অ্যাপ সম্পর্কে"), L.t("ভার্সন ও আপডেট"))
)

/** তালিকায় দেখানোর ক্রম (পেজ আইডি একই থাকে): প্রাইভেসি ও ইতিহাস (৫) এখন YouTube লিংকের (৮) নিচে */
private val topicOrder = listOf(0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11, 12)

/** টপ বারে দেখানোর জন্য টপিকের নাম */
fun settingsTopicTitle(i: Int): String = settingsTopics().getOrNull(i)?.title ?: L.t("সেটিংস")

/** টপিকের তালিকা (প্রিভিউতে onPick খালি দেওয়া হয়) */
@Composable
fun SettingsTopicList(scroll: ScrollState, onPick: (Int) -> Unit) {
    val topics = settingsTopics()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 4.dp)) {
        topicOrder.forEach { i ->
            val t = topics[i]
            Row(
                Modifier.fillMaxWidth().clickable { onPick(i) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Chip), Alignment.Center) {
                    Icon(t.icon, null, tint = YtRed, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(t.sub, color = Sub, fontSize = 12.sp, maxLines = 1)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Sub)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SettingsTab(vm: MainViewModel, skipAnim: Boolean = false) {
    AnimatedContent(
        targetState = vm.settingsPage,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            when {
                skipAnim -> EnterTransition.None togetherWith ExitTransition.None
                targetState != null ->
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
                else ->
                    fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(200)))
            }
        },
        label = "settingsPage"
    ) { p ->
        if (p == null) SettingsTopicList(vm.settingsListScroll) { vm.settingsPage = it } else SettingsPage(vm, p)
    }
}
