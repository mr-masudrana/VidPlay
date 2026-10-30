package com.example.vidplay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun SectionTitle(t: String) =
    Text(t, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, color = Sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

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

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        // ভাষা
        item {
            SectionTitle(L.t("ভাষা / Language"))
            val modes = listOf(L.t("বাংলা"), "English", L.t("সিস্টেম"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(modes.size) { i ->
                    FilterChip(selected = vm.langMode == i, onClick = { vm.chooseLang(i) }, label = { Text(modes[i]) })
                }
            }
        }

        // থিম
        item {
            SectionTitle(L.t("থিম"))
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
                        Text(L.t(p.name), color = if (p.light) Color.Black else Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        // প্লেব্যাক
        item {
            SectionTitle(L.t("প্লেব্যাক"))
            SwitchRow("SponsorBlock", L.t("স্পন্সর, ইন্ট্রো, আউট্রো অংশ নিজে বাদ দেয়"), vm.sponsorOn) { vm.toggleSponsor(it) }
            SwitchRow("Return YouTube Dislike", L.t("ডিসলাইকের আনুমানিক সংখ্যা দেখায়"), vm.rydOn) { vm.toggleRyd(it) }
            SwitchRow(L.t("শুধু অডিও (ডিফল্ট)"), L.t("ভিডিও ছাড়া শুধু শব্দ চলবে, ডেটা বাঁচবে"), vm.audioOnly) { vm.toggleAudioOnly(it) }
            SwitchRow(L.t("নীরবতা বাদ"), L.t("কথার ফাঁকের নীরব অংশ বাদ যাবে"), vm.skipSilence) { vm.toggleSkipSilence(it) }
            SwitchRow(L.t("ভাসমান মিনি প্লেয়ার"), L.t("ব্যাক করলে ভিডিও ছোট ভাসমান উইন্ডোতে চলতে থাকবে, টেনে সরানো যায়"), vm.floatingMini) { vm.toggleFloatingMini(it) }
        }

        // ডিফল্ট কোয়ালিটি
        item {
            SectionTitle(L.t("ডিফল্ট ভিডিও কোয়ালিটি"))
            val opts = listOf(0, 144, 240, 360, 480, 720, 1080, 1440, 2160)
            Text(L.t("ওয়াইফাই"), fontSize = 13.sp, color = Sub, modifier = Modifier.padding(bottom = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(opts.size) { i ->
                    FilterChip(
                        selected = vm.qualityWifi == opts[i], onClick = { vm.chooseDefaultQuality(true, opts[i]) },
                        label = { Text(if (opts[i] == 0) "Auto" else "${opts[i]}p") }
                    )
                }
            }
            Text(L.t("মোবাইল ডেটা"), fontSize = 13.sp, color = Sub, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(opts.size) { i ->
                    FilterChip(
                        selected = vm.qualityMobile == opts[i], onClick = { vm.chooseDefaultQuality(false, opts[i]) },
                        label = { Text(if (opts[i] == 0) "Auto" else "${opts[i]}p") }
                    )
                }
            }
        }

        // লিংক
        item {
            val ctx = LocalContext.current
            SectionTitle(L.t("YouTube লিংক"))
            Text(L.t("youtube.com ও youtu.be লিংক VidPlay-তে খুলতে \"ডিফল্টে খুলুন\" থেকে লিংকগুলো চালু করুন। শেয়ার মেনু থেকেও পাঠানো যায়।"),
                color = Sub, fontSize = 12.sp)
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

        // রিকমেন্ডেশন ড্যাশবোর্ড
        item {
            SectionTitle(L.t("আপনার রিকমেন্ডেশন প্রোফাইল"))
            Text(L.t("সব ডেটা শুধু এই ফোনে থাকে। এখানে দেখুন অ্যাপ আপনার সম্পর্কে কী শিখেছে।"), color = Sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (vm.topInterests.isEmpty()) Text(L.t("এখনো কিছু শেখেনি — কয়েকটি ভিডিও দেখুন।"), color = Sub, fontSize = 13.sp)
        }
        val maxScore = vm.topInterests.maxOfOrNull { it.second } ?: 1.0
        items(vm.topInterests) { (topic, score) ->
            val label = if (topic.startsWith("ch:")) L.f("চ্যানেল: {0}", topic.removePrefix("ch:")) else L.f("বিষয়: {0}", topic.removePrefix("kw:"))
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(label, fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { (score / maxScore).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(4.dp),
                    color = YtRed, trackColor = Chip
                )
            }
        }

        // এই সময়ের পছন্দ
        item {
            SectionTitle(L.f("এই সময়ের পছন্দ ({0})", Recommender.bucketLabel()))
            Text(L.t("কর্মদিবস/ছুটির দিন ও দিনের সময় অনুযায়ী আপনার আলাদা পছন্দ।"), color = Sub, fontSize = 12.sp)
            if (vm.contextInterests.isEmpty()) Text(L.t("এই সময়ের জন্য এখনো যথেষ্ট ডেটা নেই।"), color = Sub, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        val maxCtx = vm.contextInterests.maxOfOrNull { it.second } ?: 1.0
        items(vm.contextInterests) { (topic, score) ->
            val label = if (topic.startsWith("ch:")) L.f("চ্যানেল: {0}", topic.removePrefix("ch:")) else L.f("বিষয়: {0}", topic.removePrefix("kw:"))
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(label, fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { (score / maxCtx).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(4.dp),
                    color = YtRed, trackColor = Chip
                )
            }
        }

        // নতুন ভিডিওর নোটিফিকেশন
        item {
            SectionTitle(L.t("নতুন ভিডিওর নোটিফিকেশন"))
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

        // DeArrow
        item {
            SectionTitle("DeArrow")
            Text(L.t("ক্লিকবেইট টাইটেল ও থাম্বনেইল কমিউনিটির দেওয়া সংস্করণে বদলায়"), color = Sub, fontSize = 12.sp)
            SwitchRow(L.t("টাইটেল"), L.t("আরও সৎ টাইটেল দেখায়"), DeArrow.titles) { vm.toggleDearrowTitles(it) }
            SwitchRow(L.t("থাম্বনেইল"), L.t("ভিডিওর আসল দৃশ্য থেকে থাম্বনেইল"), DeArrow.thumbs) { vm.toggleDearrowThumbs(it) }
        }

        // ব্লক তালিকা
        item {
            SectionTitle(L.t("ব্লক তালিকা"))
            Text(L.t("ভিডিও কার্ডের ⋮ মেনু থেকেও \"আগ্রহ নেই\" বা ব্লক করা যায়।"), color = Sub, fontSize = 12.sp)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = kw, onValueChange = { kw = it }, singleLine = true,
                    label = { Text(L.t("কিওয়ার্ড ব্লক")) }, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(enabled = kw.isNotBlank(), onClick = { vm.blockKeyword(kw); kw = "" }) { Text(L.t("যোগ")) }
            }
        }
        items(blocks) { b ->
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

        // ডেটা
        item {
            SectionTitle(L.t("ডেটা"))
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(L.t("NewPipe ইম্পোর্ট (সাবস্ক্রিপশন JSON বা ব্যাকআপ ZIP: সাবস্ক্রিপশন + ইতিহাস)"))
            }
            OutlinedButton(onClick = { vm.resetProfile() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("রিকমেন্ডেশন প্রোফাইল রিসেট"))
            }
            OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("দেখার ইতিহাস মুছুন"))
            }
            OutlinedButton(onClick = { exporter.launch("vidplay-profile.json") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("প্রোফাইল এক্সপোর্ট (সাবস্ক্রিপশন, প্লেলিস্ট, ইতিহাস, রিকমেন্ডেশন)"))
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(L.t("প্রোফাইল ইম্পোর্ট"))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
