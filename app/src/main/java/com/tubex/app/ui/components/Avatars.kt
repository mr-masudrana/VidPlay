package com.tubex.app.ui.components

import com.tubex.app.data.cache.AvatarCache
import com.tubex.app.data.local.entity.HistoryEntity
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.util.fixUrl

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** লোগো না থাকলে (কার্ড/কমেন্ট) চ্যানেল থেকে লোগো এনে দেখায়; না পেলে নামের প্রথম অক্ষর */
@Composable
fun ChannelAvatar(avatar: String?, channelUrl: String?, name: String, size: Dp) {
    val missing = avatar.isNullOrBlank()
    if (missing && !channelUrl.isNullOrBlank()) LaunchedEffect(channelUrl) { AvatarCache.ensure(channelUrl) }
    val resolved = if (!missing) avatar else channelUrl?.let { AvatarCache.map[it] }?.takeIf { it.isNotBlank() }
    Avatar(resolved, name, size)
}

@Composable
fun Avatar(url: String?, name: String, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Chip), Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(fixUrl(url), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HistoryThumb(h: HistoryEntity, modifier: Modifier) {
    Box(modifier.background(Chip)) {
        AsyncImage(h.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (h.durationSec > 0) LinearProgressIndicator(
            progress = { (h.positionMs / (h.durationSec * 1000f)).coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            color = YtRed, trackColor = Color(0x66000000)
        )
    }
}
