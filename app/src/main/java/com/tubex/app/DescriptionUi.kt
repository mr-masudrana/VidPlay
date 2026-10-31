package com.tubex.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** শিরোনামের নিচে এক লাইনে: ভিউ • লাইক • ডিসলাইক • প্রকাশের সময় (জায়গা না হলে আড়াআড়ি স্ক্রোল) */
@Composable
fun StatsLine(info: StreamInfo, likes: Long?, dislikes: Long?, modifier: Modifier = Modifier) {
    val views = fmtCount(info.viewCount).takeIf { it.isNotBlank() }?.let { L.f("{0} ভিউ", it) }
    val whenText = info.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()?.let { relativeTime(it) }
        ?: info.textualUploadDate?.takeIf { it.isNotBlank() }

    val parts = mutableListOf<@Composable () -> Unit>()
    views?.let { parts += { StatText(it) } }
    likes?.let {
        parts += {
            Icon(Icons.Rounded.ThumbUp, null, tint = Sub, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp)); StatText(fmtCount(it))
        }
    }
    dislikes?.let {
        parts += {
            Icon(Icons.Rounded.ThumbDown, null, tint = Sub, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp)); StatText(fmtCount(it))
        }
    }
    whenText?.let { parts += { StatText(it) } }

    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, p ->
            if (i > 0) StatText("  •  ")
            p()
        }
    }
}

@Composable
private fun StatText(t: String) = Text(t, color = Sub, fontSize = 13.sp, maxLines = 1, softWrap = false)

/** বিবরণ মডাল: পুরো শিরোনাম, পরিসংখ্যান, চ্যানেল, অধ্যায়, পরিষ্কার বিবরণ (লিংক ও টাইমস্ট্যাম্প ট্যাপযোগ্য) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionSheet(vm: MainViewModel, info: StreamInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember(info) { TextUtil.clean(info.description?.content) }
    val fullDate = remember(info) {
        info.uploadDate?.offsetDateTime()?.let {
            runCatching {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    .withLocale(if (L.en) Locale.ENGLISH else Locale("bn")).format(it)
            }.getOrNull()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Chip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(L.t("বিবরণ"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(vm.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Stat(if (info.viewCount >= 0) java.text.NumberFormat.getInstance().format(info.viewCount) else "—", L.t("ভিউ"))
                        vm.likes?.let { Stat(fmtCount(it), L.t("লাইক")) }
                        vm.dislikes?.let { Stat(fmtCount(it), L.t("ডিসলাইক")) }
                        fullDate?.let { Stat(it, L.t("প্রকাশ")) }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Sub.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { onDismiss(); vm.openChannel(info.uploaderUrl) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelAvatar(info.uploaderAvatars.lastOrNull()?.url, info.uploaderUrl, info.uploaderName, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(info.uploaderName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (info.uploaderSubscriberCount >= 0)
                                Text(L.f("{0} সাবস্ক্রাইবার", fmtCount(info.uploaderSubscriberCount)), color = Sub, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            val chapters = info.streamSegments
            if (chapters.isNotEmpty()) {
                item {
                    Text(L.t("অধ্যায়"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 6.dp))
                }
                items(chapters.size) { i ->
                    val c = chapters[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.player.seekTo(c.startTimeSeconds * 1000L); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(fmtTime(c.startTimeSeconds.toLong()), color = linkColor(), fontSize = 13.sp, modifier = Modifier.width(56.dp))
                        Text(c.title ?: "", fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            item {
                Column(Modifier.padding(16.dp, 0.dp, 16.dp, 32.dp)) {
                    if (text.isBlank()) Text(L.t("কোনো বিবরণ নেই"), color = Sub)
                    else SelectableLinkText(
                        text,
                        onUrl = { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } },
                        onSeek = { sec -> vm.player.seekTo(sec * 1000L); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Sub, fontSize = 12.sp)
    }
}

@Composable
private fun linkColor(): Color = if (LocalPalette.current.light) Color(0xFF065FD4) else Color(0xFF3EA6FF)

private val linkRegex = Regex("""(https?://[^\s<>"]+)|(?<![\d:])((?:\d{1,2}:)?\d{1,2}:\d{2})(?![\d:])""")

/** URL ও টাইমস্ট্যাম্প (১:২৩, ০১:২৩:৪৫) ট্যাপযোগ্য লেখা */
@Composable
fun SelectableLinkText(text: String, onUrl: (String) -> Unit, onSeek: (Long) -> Unit) {
    val color = linkColor()
    val annotated: AnnotatedString = remember(text, color) {
        buildAnnotatedString {
            var last = 0
            for (m in linkRegex.findAll(text)) {
                var start = m.range.first
                var end = m.range.last + 1
                if (m.groups[1] != null) {
                    var u = m.value
                    while (u.isNotEmpty() && u.last() in ".,;:!?)]}\u2019\u201D") { u = u.dropLast(1); end-- }
                    append(text.substring(last, start))
                    pushStringAnnotation("URL", u)
                    pushStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
                    append(u)
                    pop(); pop()
                } else {
                    val parts = m.value.split(":").map { it.toLong() }
                    val sec = parts.fold(0L) { acc, p -> acc * 60 + p }
                    append(text.substring(last, start))
                    pushStringAnnotation("TS", sec.toString())
                    pushStyle(SpanStyle(color = color))
                    append(m.value)
                    pop(); pop()
                }
                last = end
            }
            append(text.substring(last))
        }
    }
    @Suppress("DEPRECATION")
    ClickableText(
        annotated,
        style = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, lineHeight = 21.sp),
        onClick = { off ->
            annotated.getStringAnnotations("URL", off, off).firstOrNull()?.let { onUrl(it.item); return@ClickableText }
            annotated.getStringAnnotations("TS", off, off).firstOrNull()?.let { onSeek(it.item.toLong()) }
        }
    )
}
