package com.tubex.app.ui.home

import com.tubex.app.data.local.entity.HistoryEntity
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.util.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** অর্ধেক দেখা (৫ সেকেন্ডের বেশি, ৯০%-এর কম) ভিডিওগুলো, নতুন আগে; সর্বোচ্চ ১০টি */
fun continueWatching(history: List<HistoryEntity>): List<HistoryEntity> =
    history.filter { h ->
        h.durationSec > 0 && h.positionMs > 5_000 && h.positionMs < h.durationSec * 900L
    }.sortedByDescending { it.watchedAt }.take(10)

@Composable
fun ContinueWatchingShelf(items: List<HistoryEntity>, onPlay: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            L.t("দেখা চালিয়ে যান"), fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.url }) { h ->
                val frac = (h.positionMs / (h.durationSec * 1000f)).coerceIn(0f, 1f)
                Column(Modifier.width(190.dp).clip(RoundedCornerShape(8.dp)).clickable { onPlay(h.url) }) {
                    Box {
                        AsyncImage(
                            model = h.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Chip)
                        )
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                                .background(Color(0x66FFFFFF))
                        ) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(YtRed))
                        }
                    }
                    Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                    Text(h.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Sub)
                }
            }
        }
    }
}
