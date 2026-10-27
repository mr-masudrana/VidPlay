package com.example.vidplay

import androidx.activity.compose.rememberLauncherForActivityResult
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
    LaunchedEffect(Unit) { vm.loadInterests() }

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
            Spacer(Modifier.height(24.dp))
        }
    }
}
