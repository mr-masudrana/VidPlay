package com.tubex.app.ui.library

import com.tubex.app.data.local.entity.PlaylistRow
import com.tubex.app.util.L
import com.tubex.app.ui.theme.Chip
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage

@Composable
internal fun SectionHeader(i: Int, count: Int, onAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Chip), Alignment.Center) {
            Icon(libraryIcon(i), null, tint = YtRed, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(libraryTitles()[i], fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (count > 0) Text("  $count", color = Sub, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAll) {
            Text(L.t("সব দেখুন"), fontSize = 13.sp)
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(72.dp)
            .clip(RoundedCornerShape(14.dp)).background(Chip.copy(alpha = 0.5f)),
        Alignment.Center
    ) { Text(text, color = Sub, fontSize = 13.sp) }
}

/** ক্যারোসেলের ছোট কার্ড: থাম্বনেইল + (ঐচ্ছিক) ব্যাজ/প্রগ্রেস + শিরোনাম + নিচের লেখা */
@Composable
internal fun MiniCard(
    thumb: String?, title: String, sub: String, onClick: () -> Unit,
    progress: Float? = null, badge: String? = null, overlayIcon: ImageVector? = null
) {
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Chip)) {
            if (thumb != null) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (overlayIcon != null) Icon(
                overlayIcon, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(30.dp)
            )
            if (badge != null) Text(
                badge, color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0xB3000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (progress != null && progress > 0f) LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = YtRed, trackColor = androidx.compose.ui.graphics.Color(0x66000000)
            )
        }
        Text(
            title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        Text(
            sub, color = Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp, bottom = 4.dp)
        )
    }
}

@Composable
internal fun PlaylistMiniCard(vm: MainViewModel, p: PlaylistRow, onOpen: () -> Unit) {
    val items by remember(p.id) { vm.playlistItems(p.id) }.collectAsState(initial = emptyList())
    MiniCard(
        thumb = items.firstOrNull()?.thumb, title = p.name,
        sub = L.f("{0} টি ভিডিও", p.itemCount), onClick = onOpen,
        badge = if (p.itemCount > 0) "${p.itemCount}" else null,
        overlayIcon = if (items.firstOrNull()?.thumb == null) Icons.Outlined.VideoLibrary else null
    )
}

@Composable
internal fun MediaRow(
    title: String, channel: String, thumb: String?, onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            thumb, null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Chip)
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(channel, color = Sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
        trailing()
    }
}

@Composable
internal fun SearchBox(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, null) } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    )
}

@Composable
internal fun RenameDialog(initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.t("প্লেলিস্টের নাম বদলান")) },
        text = {
            OutlinedTextField(
                value = t, onValueChange = { t = it }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done)
            )
        },
        confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { onOk(t) }) { Text(L.t("ঠিক আছে")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L.t("বাতিল")) } }
    )
}
