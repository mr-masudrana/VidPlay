package com.tubex.app.ui.settings

import com.tubex.app.util.L
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** একটি সেটিং পেজের কনটেন্ট (শিরোনাম/আইকন এখন টপিক তালিকা ও পেজের হেডারে দেখানো হয়) */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SectionCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) { content() }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, color = Sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun SubLabel(t: String) =
    Text(t, fontSize = 13.sp, color = Sub, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))

internal val AccentSwatches = listOf(
    0xFFFF0000, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF2196F3, 0xFF00BCD4,
    0xFF009688, 0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548
).map { Color(it) }

internal fun hueColor(h: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.9f), 0.75f, 1f)))

@Composable
internal fun InterestRow(topic: String, frac: Double) {
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
