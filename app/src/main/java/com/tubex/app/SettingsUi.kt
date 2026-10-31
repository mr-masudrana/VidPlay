package com.tubex.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** প্রতিটি গ্রুপ একটি কার্ডে: আইকন + শিরোনাম + ভেতরের অপশন */
@Composable
private fun SectionCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .border(1.dp, Sub.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = YtRed, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, color = Sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SubLabel(t: String) =
    Text(t, fontSize = 13.sp, color = Sub, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))

private val AccentSwatches = listOf(
    0xFFFF0000, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF2196F3, 0xFF00BCD4,
    0xFF009688, 0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548
).map { Color(it) }

private fun hueColor(h: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.9f), 0.75f, 1f)))

@Composable
fun SettingsTab(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importNewPipe(uri)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportProfile(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importProfile(uri)
    }
    LaunchedEffect(Unit) { vm.loadInterests() }
    val blocks by vm.blocks.collectAsState()
    var kw by remember { mutableStateOf("") }
    var hue by remember { mutableStateOf(0f) }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {

        // ---------- ভাষা ----------
        SectionCard(Icons.Outlined.Language, L.t("ভাষা / Language")) {
            val modes = listOf(L.t("বাংলা"), "English", L.t("সিস্টেম"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(modes.size) { i ->
                    FilterChip(selected = vm.langMode == i, onClick = { vm.chooseLang(i) }, label = { Text(modes[i]) })
                }
            }
        }

        // ---------- চেহারা ও থিম ----------
        SectionCard(Icons.Outlined.Palette, L.t("চেহারা ও থিম")) {
            SwitchRow(
                L.t("সিস্টেম থিম অনুসরণ (অটো)"),
                L.t("ফোন ডার্ক মোডে থাকলে ডার্ক, নাহলে লাইট থিম নিজে বদলাবে"),
                vm.themeAuto
            ) { vm.applyThemeAuto(it) }

            SubLabel(L.t("থিম"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Palettes.size) { i ->
                    val p = Palettes[i]
                    val sel = vm.themeIndex == i
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(p.bg)
                            .border(if (sel) 2.dp else 1.dp, if (sel) p.accent else Sub, RoundedCornerShape(12.dp))
                            .clickable { vm.chooseTheme(i) }.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(p.accent))
                        Text(
                            L.t(p.name), color = if (p.light) Color.Black else Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            SubLabel(L.t("অ্যাকসেন্ট রঙ"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                item {
                    FilterChip(
                        selected = vm.accentColor == 0, onClick = { vm.chooseAccent(0) },
                        label = { Text(L.t("থিমের ডিফল্ট")) }
                    )
                }
                items(AccentSwatches) { c ->
                    val sel = vm.accentColor == c.toArgb()
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c)
                            .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            .clickable { vm.chooseAccent(c.toArgb()) }
                    )
                }
            }
            SubLabel(L.t("কাস্টম রঙ"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(hueColor(hue)))
                Slider(
                    value = hue, onValueChange = { hue = it },
                    onValueChangeFinished = { vm.chooseAccent(hueColor(hue).toArgb()) },
                    valueRange = 0f..360f, modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
            }
        }

        // ---------- প্লেব্যাক ----------
        SectionCard(Icons.Outlined.PlayCircle, L.t("প্লেব্যাক")) {
            SwitchRow(L.t("অটোপ্লে"), L.t("ভিডিও শেষে পরের ভিডিও নিজে চলবে"), vm.autoplay) { vm.applyAutoplay(it) }
            SwitchRow(
                L.t("ব্যাকগ্রাউন্ড প্লে"),
                L.t("অ্যাপ থেকে বেরিয়ে গেলেও বা স্ক্রিন বন্ধ করলেও শব্দ চলতে থাকবে"),
                vm.backgroundPlay
            ) { vm.applyBackgroundPlay(it) }
            SwitchRow(
                L.t("PiP প্লে"),
                L.t("ভিডিও চলার সময় হোম বাটন চাপলে ছোট উইন্ডোতে চলবে"),
                vm.pipOn
            ) { vm.setPip(it) }
            SwitchRow(L.t("ভাসমান মিনি প্লেয়ার"), L.t("ব্যাক করলে ভিডিও ছোট ভাসমান উইন্ডোতে চলতে থাকবে, টেনে সরানো যায়"), vm.floatingMini) { vm.toggleFloatingMini(it) }
            SwitchRow("SponsorBlock", L.t("স্পন্সর, ইন্ট্রো, আউট্রো অংশ নিজে বাদ দেয়"), vm.sponsorOn) { vm.toggleSponsor(it) }
            SwitchRow("Return YouTube Dislike", L.t("ডিসলাইকের আনুমানিক সংখ্যা দেখায়"), vm.rydOn) { vm.toggleRyd(it) }
            SwitchRow(L.t("শুধু অডিও (ডিফল্ট)"), L.t("ভিডিও ছাড়া শুধু শব্দ চলবে, ডেটা বাঁচবে"), vm.audioOnly) { vm.toggleAudioOnly(it) }
            SwitchRow(L.t("নীরবতা বাদ"), L.t("কথার ফাঁকের নীরব অংশ বাদ যাবে"), vm.skipSilence) { vm.toggleSkipSilence(it) }
        }

        // ---------- ডেটা সেভার ও কোয়ালিটি ----------
        SectionCard(Icons.Outlined.DataSaverOn, L.t("ডেটা সেভার ও কোয়ালিটি")) {
            SwitchRow(
                L.t("ডেটা সেভার মোড"),
                L.t("সব নেটওয়ার্কে সর্বোচ্চ 360p এবং পরের ভিডিও আগাম লোড বন্ধ"),
                vm.dataSaver
            ) { vm.applyDataSaver(it) }
            val opts = listOf(0, 144, 240, 360, 480, 720, 1080, 1440, 2160)
            SubLabel(L.t("ডিফল্ট ভিডিও কোয়ালিটি") + " • " + L.t("ওয়াইফাই"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(opts.size) { i ->
                    FilterChip(
                        selected = vm.qualityWifi == opts[i], onClick = { vm.chooseDefaultQuality(true, opts[i]) },
                        label = { Text(if (opts[i] == 0) "Auto" else "${opts[i]}p") }
                    )
                }
            }
            SubLabel(L.t("ডিফল্ট ভিডিও কোয়ালিটি") + " • " + L.t("মোবাইল ডেটা"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(opts.size) { i ->
                    FilterChip(
                        selected = vm.qualityMobile == opts[i], onClick = { vm.chooseDefaultQuality(false, opts[i]) },
                        label = { Text(if (opts[i] == 0) "Auto" else "${opts[i]}p") }
                    )
                }
            }
        }

        // ---------- ডাউনলোড ----------
        SectionCard(Icons.Outlined.Download, L.t("ডাউনলোড")) {
            SwitchRow(
                L.t("শুধু ওয়াইফাইয়ে ডাউনলোড"),
                L.t("মোবাইল ডেটায় ডাউনলোড থেমে থাকবে, ওয়াইফাই পেলে নিজে চলবে"),
                vm.wifiOnlyDl
            ) { vm.setWifiOnlyDownload(it) }
            SubLabel(L.t("একসাথে কয়টা ডাউনলোড চলবে"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(1, 2, 3)) { n ->
                    FilterChip(
                        selected = vm.parallelDl == n, onClick = { vm.chooseParallelDownloads(n) },
                        label = { Text("$n") }
                    )
                }
            }
        }

        // ---------- প্রাইভেসি ও ইতিহাস ----------
        SectionCard(Icons.Outlined.PrivacyTip, L.t("প্রাইভেসি ও ইতিহাস")) {
            SwitchRow(
                L.t("ওয়াচ হিস্টরি সংরক্ষণ"),
                L.t("বন্ধ থাকলে দেখা ভিডিও ইতিহাসে যোগ হবে না (শেষ জায়গা থেকে চালুও হবে না)"),
                vm.saveWatchHist
            ) { vm.setSaveWatchHistory(it) }
            SwitchRow(
                L.t("সার্চ হিস্টরি সংরক্ষণ"),
                L.t("বন্ধ থাকলে সার্চের শব্দ মনে রাখা হবে না"),
                vm.saveSearchHist
            ) { vm.setSaveSearchHistory(it) }
            OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("দেখার ইতিহাস মুছুন"))
            }
            OutlinedButton(onClick = { vm.clearSearchHistory() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("সার্চ ইতিহাস মুছুন"))
            }
        }

        // ---------- নোটিফিকেশন ----------
        SectionCard(Icons.Outlined.Notifications, L.t("নতুন ভিডিওর নোটিফিকেশন")) {
            SwitchRow(L.t("সাবস্ক্রাইব করা চ্যানেল"), L.t("ব্যাকগ্রাউন্ডে মাঝে মাঝে চেক করে নোটিফিকেশন দেয়"), vm.notifyOn) { vm.toggleNotify(it) }
            val hrs = listOf(3, 6, 12, 24)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hrs.size) { i ->
                    FilterChip(
                        selected = vm.notifyHours == hrs[i], onClick = { vm.chooseNotifyHours(hrs[i]) },
                        label = { Text(L.f("প্রতি {0} ঘণ্টা", hrs[i])) }
                    )
                }
            }
            OutlinedButton(onClick = { vm.checkNewNow() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("এখনই চেক করুন"))
            }
        }

        // ---------- DeArrow ----------
        SectionCard(Icons.Outlined.Tune, "DeArrow") {
            Text(L.t("ক্লিকবেইট টাইটেল ও থাম্বনেইল কমিউনিটির দেওয়া সংস্করণে বদলায়"), color = Sub, fontSize = 12.sp)
            SwitchRow(L.t("টাইটেল"), L.t("আরও সৎ টাইটেল দেখায়"), DeArrow.titles) { vm.toggleDearrowTitles(it) }
            SwitchRow(L.t("থাম্বনেইল"), L.t("ভিডিওর আসল দৃশ্য থেকে থাম্বনেইল"), DeArrow.thumbs) { vm.toggleDearrowThumbs(it) }
        }

        // ---------- লিংক ----------
        SectionCard(Icons.Outlined.Link, L.t("YouTube লিংক")) {
            Text(
                L.t("youtube.com ও youtu.be লিংক TubeX-তে খুলতে \"ডিফল্টে খুলুন\" থেকে লিংকগুলো চালু করুন। শেয়ার মেনু থেকেও পাঠানো যায়।"),
                color = Sub, fontSize = 12.sp
            )
            OutlinedButton(
                onClick = {
                    val pkg = Uri.parse("package:${ctx.packageName}")
                    val i = if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                    runCatching { ctx.startActivity(i) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(L.t("ডিফল্টে খুলুন সেটিংস")) }
        }

        // ---------- রিকমেন্ডেশন ----------
        SectionCard(Icons.Outlined.Insights, L.t("আপনার রিকমেন্ডেশন প্রোফাইল")) {
            Text(L.t("সব ডেটা শুধু এই ফোনে থাকে। এখানে দেখুন অ্যাপ আপনার সম্পর্কে কী শিখেছে।"), color = Sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (vm.topInterests.isEmpty()) Text(L.t("এখনো কিছু শেখেনি — কয়েকটি ভিডিও দেখুন।"), color = Sub, fontSize = 13.sp)
            val maxScore = vm.topInterests.maxOfOrNull { it.second } ?: 1.0
            vm.topInterests.forEach { (topic, score) -> InterestRow(topic, score / maxScore) }

            Text(
                L.f("এই সময়ের পছন্দ ({0})", Recommender.bucketLabel()),
                fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp)
            )
            Text(L.t("কর্মদিবস/ছুটির দিন ও দিনের সময় অনুযায়ী আপনার আলাদা পছন্দ।"), color = Sub, fontSize = 12.sp)
            if (vm.contextInterests.isEmpty()) Text(
                L.t("এই সময়ের জন্য এখনো যথেষ্ট ডেটা নেই।"), color = Sub, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            val maxCtx = vm.contextInterests.maxOfOrNull { it.second } ?: 1.0
            vm.contextInterests.forEach { (topic, score) -> InterestRow(topic, score / maxCtx) }
        }

        // ---------- ব্লক তালিকা ----------
        SectionCard(Icons.Outlined.Block, L.t("ব্লক তালিকা")) {
            Text(L.t("ভিডিও কার্ডের ⋮ মেনু থেকেও \"আগ্রহ নেই\" বা ব্লক করা যায়।"), color = Sub, fontSize = 12.sp)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = kw, onValueChange = { kw = it }, singleLine = true,
                    label = { Text(L.t("কিওয়ার্ড ব্লক")) }, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(enabled = kw.isNotBlank(), onClick = { vm.blockKeyword(kw); kw = "" }) { Text(L.t("যোগ")) }
            }
            blocks.forEach { b ->
                val label = when {
                    b.entry.startsWith("ch:") -> L.f("চ্যানেল: {0}", b.entry.removePrefix("ch:"))
                    b.entry.startsWith("kw:") -> L.f("কিওয়ার্ড: {0}", b.entry.removePrefix("kw:"))
                    else -> L.f("ভিডিও: {0}", b.entry.removePrefix("vid:").takeLast(30))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { vm.removeBlock(b.entry) }) { Text(L.t("মুছুন")) }
                }
            }
        }

        // ---------- ডেটা ----------
        SectionCard(Icons.Outlined.Storage, L.t("ডেটা")) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(L.t("NewPipe ইম্পোর্ট (সাবস্ক্রিপশন JSON বা ব্যাকআপ ZIP: সাবস্ক্রিপশন + ইতিহাস)"))
            }
            OutlinedButton(onClick = { vm.resetProfile() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("রিকমেন্ডেশন প্রোফাইল রিসেট"))
            }
            OutlinedButton(onClick = { exporter.launch("tubex-profile.json") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("প্রোফাইল এক্সপোর্ট (সাবস্ক্রিপশন, প্লেলিস্ট, ইতিহাস, রিকমেন্ডেশন)"))
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("প্রোফাইল ইম্পোর্ট"))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InterestRow(topic: String, frac: Double) {
    val label = if (topic.startsWith("ch:")) L.f("চ্যানেল: {0}", topic.removePrefix("ch:")) else L.f("বিষয়: {0}", topic.removePrefix("kw:"))
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp)
        LinearProgressIndicator(
            progress = { frac.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(4.dp),
            color = YtRed, trackColor = Chip
        )
    }
}
