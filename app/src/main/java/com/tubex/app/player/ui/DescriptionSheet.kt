package com.tubex.app.player.ui

import com.tubex.app.util.L
import com.tubex.app.ui.components.ChannelAvatar
import com.tubex.app.ui.theme.LocalPalette
import com.tubex.app.ui.theme.Sub
import com.tubex.app.util.TextUtil
import com.tubex.app.util.fmtCount
import com.tubex.app.util.fmtTime
import com.tubex.app.util.relativeTime
import com.tubex.app.viewmodel.MainViewModel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
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
fun StatsLine(
    info: StreamInfo, likes: Long?, dislikes: Long?, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null      // সময়ের পর একই লাইনে (যেমন "...আরও দেখুন")
) {
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
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
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

    val maxH = rememberSheetMaxHeight()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    PlayerSheet(onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = maxH),
            contentPadding = PaddingValues(bottom = navBottom)
        ) {
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
                    else LinkText(text, vm, onDone = onDismiss)
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

/** URL | টাইমস্ট্যাম্প | #হ্যাশট্যাগ (বাংলা সহ) | @হ্যান্ডেল */
private val linkRegex = Regex(
    """(https?://[^\s<>"]+)""" +
    """|(?<![\d:])((?:\d{1,2}:)?\d{1,2}:\d{2})(?![\d:])""" +
    """|(?<![\p{L}\p{M}\p{N}_&/#])#((?=[\p{L}\p{M}\p{N}_]*[\p{L}\p{M}])[\p{L}\p{M}\p{N}_]+)""" +
    """|(?<![\p{L}\p{M}\p{N}_.@])@([A-Za-z0-9_.\-]{3,30})"""
)

private val inAppPaths = listOf("/watch", "/shorts", "/live", "/embed", "/playlist", "/channel/", "/c/", "/user/", "/@")

/**
 * ট্যাপযোগ্য লেখা: লিংক, টাইমস্ট্যাম্প (১:২৩), #হ্যাশট্যাগ (→ সার্চ), @হ্যান্ডেল (→ চ্যানেল)।
 * ইউটিউবের লিংক অ্যাপের ভেতরেই খোলে, বাকিগুলো ব্রাউজারে। [onDone] = কিছু খোলার পর (যেমন শিট বন্ধ করতে)।
 */
@Composable
fun LinkText(
    text: String, vm: MainViewModel, modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 21.sp,
    seek: Boolean = true,                    // টাইমস্ট্যাম্পে চলতি ভিডিও সিক হবে কিনা
    onDone: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val color = linkColor()
    val annotated: AnnotatedString = remember(text, color, seek) {
        buildAnnotatedString {
            val add = { tag: String, item: String, label: String, underline: Boolean ->
                pushStringAnnotation(tag, item)
                pushStyle(SpanStyle(color = color, textDecoration = if (underline) TextDecoration.Underline else null))
                append(label)
                pop(); pop()
            }
            var last = 0
            for (m in linkRegex.findAll(text)) {
                val start = m.range.first
                var end = m.range.last + 1
                append(text.substring(last, start))
                when {
                    m.groups[1] != null -> {
                        var u = m.value
                        while (u.isNotEmpty() && u.last() in ".,;:!?)]}\u2019\u201D") { u = u.dropLast(1); end-- }
                        add("URL", u, u, true)
                    }
                    m.groups[2] != null ->
                        if (seek) {
                            val sec = m.value.split(":").map { it.toLong() }.fold(0L) { acc, p -> acc * 60 + p }
                            add("TS", sec.toString(), m.value, false)
                        } else append(m.value)
                    m.groups[3] != null -> add("TAG", m.groupValues[3], m.value, false)
                    else -> {
                        var h = m.groupValues[4]
                        while (h.endsWith(".")) h = h.dropLast(1)
                        if (h.length >= 3) { end = start + 1 + h.length; add("MEN", h, "@$h", false) }
                        else append(m.value)
                    }
                }
                last = end
            }
            append(text.substring(last))
        }
    }
    fun openUrl(u: String) {
        val uri = runCatching { Uri.parse(u) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        val path = uri?.path.orEmpty()
        val inApp = host == "youtu.be" ||
            ((host == "youtube.com" || host.endsWith(".youtube.com")) && inAppPaths.any { path.startsWith(it) })
        if (inApp) { vm.openLink(u); onDone() }
        else runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
    }
    @Suppress("DEPRECATION")
    ClickableText(
        annotated, modifier = modifier,
        style = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = fontSize, lineHeight = lineHeight),
        onClick = { off ->
            annotated.getStringAnnotations("URL", off, off).firstOrNull()?.let { openUrl(it.item); return@ClickableText }
            annotated.getStringAnnotations("TS", off, off).firstOrNull()?.let { vm.player.seekTo(it.item.toLong() * 1000L); onDone(); return@ClickableText }
            annotated.getStringAnnotations("TAG", off, off).firstOrNull()?.let { vm.searchTag(it.item); onDone(); return@ClickableText }
            annotated.getStringAnnotations("MEN", off, off).firstOrNull()?.let { vm.openMention(it.item); onDone() }
        }
    )
}
