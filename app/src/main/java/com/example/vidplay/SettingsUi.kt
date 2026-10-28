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
        // থিম
        item {
            SectionTitle("থিম")
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
                        Text(p.name, color = if (p.light) Color.Black else Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        // প্লেব্যাক
        item {
            SectionTitle("প্লেব্যাক")
            SwitchRow("SponsorBlock", "স্পন্সর, ইন্ট্রো, আউট্রো অংশ নিজে বাদ দেয়", vm.sponsorOn) { vm.toggleSponsor(it) }
            SwitchRow("Return YouTube Dislike", "ডিসলাইকের আনুমানিক সংখ্যা দেখায়", vm.rydOn) { vm.toggleRyd(it) }
            SwitchRow("শুধু অডিও (ডিফল্ট)", "ভিডিও ছাড়া শুধু শব্দ চলবে, ডেটা বাঁচবে", vm.audioOnly) { vm.toggleAudioOnly(it) }
            SwitchRow("নীরবতা বাদ", "কথার ফাঁকের নীরব অংশ বাদ যাবে", vm.skipSilence) { vm.toggleSkipSilence(it) }
        }

        // ডিফল্ট কোয়ালিটি
        item {
            SectionTitle("ডিফল্ট ভিডিও কোয়ালিটি")
            val opts = listOf(0, 144, 240, 360, 480, 720, 1080, 1440, 2160)
            Text("ওয়াইফাই", fontSize = 13.sp, color = Sub, modifier = Modifier.padding(bottom = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(opts.size) { i ->
                    FilterChip(
                        selected = vm.qualityWifi == opts[i], onClick = { vm.chooseDefaultQuality(true, opts[i]) },
                        label = { Text(if (opts[i] == 0) "Auto" else "${opts[i]}p") }
                    )
                }
            }
            Text("মোবাইল ডেটা", fontSize = 13.sp, color = Sub, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
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
            SectionTitle("YouTube লিংক")
            Text("youtube.com ও youtu.be লিংক VidPlay-তে খুলতে \"ডিফল্টে খুলুন\" থেকে লিংকগুলো চালু করুন। শেয়ার মেনু থেকেও পাঠানো যায়।",
                color = Sub, fontSize = 12.sp)
            OutlinedButton(
                onClick = {
                    val pkg = Uri.parse("package:${ctx.packageName}")
                    val i = if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                    runCatching { ctx.startActivity(i) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("ডিফল্টে খুলুন সেটিংস") }
        }

        // রিকমেন্ডেশন ড্যাশবোর্ড
        item {
            SectionTitle("আপনার রিকমেন্ডেশন প্রোফাইল")
            Text("সব ডেটা শুধু এই ফোনে থাকে। এখানে দেখুন অ্যাপ আপনার সম্পর্কে কী শিখেছে।", color = Sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (vm.topInterests.isEmpty()) Text("এখনো কিছু শেখেনি — কয়েকটি ভিডিও দেখুন।", color = Sub, fontSize = 13.sp)
        }
        val maxScore = vm.topInterests.maxOfOrNull { it.second } ?: 1.0
        items(vm.topInterests) { (topic, score) ->
            val label = if (topic.startsWith("ch:")) "চ্যানেল: ${topic.removePrefix("ch:")}" else "বিষয়: ${topic.removePrefix("kw:")}"
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(label, fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { (score / maxScore).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(4.dp),
                    color = YtRed, trackColor = Chip
                )
            }
        }

        // নতুন ভিডিওর নোটিফিকেশন
        item {
            SectionTitle("নতুন ভিডিওর নোটিফিকেশন")
            SwitchRow("সাবস্ক্রাইব করা চ্যানেল", "ব্যাকগ্রাউন্ডে মাঝে মাঝে চেক করে নোটিফিকেশন দেয়", vm.notifyOn) { vm.toggleNotify(it) }
            val hrs = listOf(3, 6, 12, 24)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hrs.size) { i ->
                    FilterChip(
                        selected = vm.notifyHours == hrs[i], onClick = { vm.chooseNotifyHours(hrs[i]) },
                        label = { Text("প্রতি ${hrs[i]} ঘণ্টা") }
                    )
                }
            }
            OutlinedButton(onClick = { vm.checkNewNow() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("এখনই চেক করুন")
            }
        }

        // DeArrow
        item {
            SectionTitle("DeArrow")
            Text("ক্লিকবেইট টাইটেল ও থাম্বনেইল কমিউনিটির দেওয়া সংস্করণে বদলায়", color = Sub, fontSize = 12.sp)
            SwitchRow("টাইটেল", "আরও সৎ টাইটেল দেখায়", DeArrow.titles) { vm.toggleDearrowTitles(it) }
            SwitchRow("থাম্বনেইল", "ভিডিওর আসল দৃশ্য থেকে থাম্বনেইল", DeArrow.thumbs) { vm.toggleDearrowThumbs(it) }
        }

        // ব্লক তালিকা
        item {
            SectionTitle("ব্লক তালিকা")
            Text("ভিডিও কার্ডের ⋮ মেনু থেকেও \"আগ্রহ নেই\" বা ব্লক করা যায়।", color = Sub, fontSize = 12.sp)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = kw, onValueChange = { kw = it }, singleLine = true,
                    label = { Text("কিওয়ার্ড ব্লক") }, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(enabled = kw.isNotBlank(), onClick = { vm.blockKeyword(kw); kw = "" }) { Text("যোগ") }
            }
        }
        items(blocks) { b ->
            val label = when {
                b.entry.startsWith("ch:") -> "চ্যানেল: ${b.entry.removePrefix("ch:")}"
                b.entry.startsWith("kw:") -> "কিওয়ার্ড: ${b.entry.removePrefix("kw:")}"
                else -> "ভিডিও: ${b.entry.removePrefix("vid:").takeLast(30)}"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { vm.removeBlock(b.entry) }) { Text("মুছুন") }
            }
        }

        // ডেটা
        item {
            SectionTitle("ডেটা")
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("NewPipe থেকে সাবস্ক্রিপশন ইম্পোর্ট (JSON)")
            }
            OutlinedButton(onClick = { vm.resetProfile() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("রিকমেন্ডেশন প্রোফাইল রিসেট")
            }
            OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("দেখার ইতিহাস মুছুন")
            }
            OutlinedButton(onClick = { exporter.launch("vidplay-profile.json") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("প্রোফাইল এক্সপোর্ট (সাবস্ক্রিপশন, প্লেলিস্ট, ইতিহাস, রিকমেন্ডেশন)")
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("প্রোফাইল ইম্পোর্ট")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
