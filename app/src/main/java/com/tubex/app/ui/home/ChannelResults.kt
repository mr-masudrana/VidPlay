package com.tubex.app.ui.home

import com.tubex.app.data.model.ChannelResult
import com.tubex.app.ui.components.Avatar
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.util.L
import com.tubex.app.util.fmtCount

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** সার্চ ফলাফলের ওপরে চ্যানেল সারি: ট্যাপে চ্যানেল পেজ, ডানে সাবস্ক্রাইব বোতাম */
@Composable
fun ChannelResultRow(c: ChannelResult, subscribed: Boolean, onOpen: () -> Unit, onToggleSub: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(c.avatar, c.name, 64.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.subscribers >= 0) Text(
                L.f("{0} সাবস্ক্রাইবার", fmtCount(c.subscribers)), color = Sub, fontSize = 12.sp, maxLines = 1
            )
            if (!c.description.isNullOrBlank()) Text(
                c.description, color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Button(
            onClick = onToggleSub,
            colors = if (subscribed) ButtonDefaults.buttonColors(containerColor = Chip, contentColor = Sub)
            else ButtonDefaults.buttonColors(containerColor = YtRed, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) { Text(if (subscribed) L.t("সাবস্ক্রাইবড") else L.t("সাবস্ক্রাইব"), fontSize = 12.sp) }
    }
}
