package com.tubex.app.ui.home

import com.tubex.app.data.model.PlaylistEntry
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.util.L
import com.tubex.app.util.fmtCount

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

/** হোমে "আপনার জন্য প্লেলিস্ট": ট্যাপ করলে প্লেলিস্ট পেজ খোলে (সব চালান / শাফল সেখানে; যেকোনো ভিডিও চালালে পুরো প্লেলিস্ট কিউতে ওঠে) */
@Composable
fun HomePlaylistsShelf(items: List<PlaylistEntry>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            L.t("আপনার জন্য প্লেলিস্ট"), fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.url }) { p ->
                Column(Modifier.width(190.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpen(p.url) }) {
                    Box {
                        AsyncImage(
                            model = p.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Chip)
                        )
                        if (p.count > 0) Box(
                            Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(L.f("{0} টি ভিডিও", fmtCount(p.count)), color = Color.White, fontSize = 11.sp)
                        }
                    }
                    Text(
                        p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp)
                    )
                    if (!p.uploader.isNullOrBlank()) Text(
                        p.uploader, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Sub
                    )
                }
            }
        }
    }
}
