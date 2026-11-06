package com.tubex.app.player.ui

import com.tubex.app.data.model.CommentItem
import com.tubex.app.util.L
import com.tubex.app.ui.components.ChannelAvatar
import com.tubex.app.ui.components.CommentSkeletons
import com.tubex.app.ui.theme.Sub
import com.tubex.app.ui.theme.YtRed
import com.tubex.app.util.fmtCount
import com.tubex.app.viewmodel.MainViewModel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val sorted = vm.sortedComments()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, sorted.size, vm.commentsMore) {
        if (nearEnd && vm.commentsMore && !vm.commentsLoadingMore && sorted.isNotEmpty()) vm.loadMoreComments()
    }

    val maxH = rememberSheetMaxHeight()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    PlayerSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().height(maxH)) {
            Text(L.t("মন্তব্য") + if (vm.commentsTotal > 0) "  " + fmtCount(vm.commentsTotal.toLong()) else "",
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 0.dp))
            OptionRow(L.t("সাজানো"), listOf(0, 1, 2), vm.commentSort, { listOf(L.t("শীর্ষ"), L.t("নতুন"), L.t("পুরনো"))[it] }) {
                vm.chooseCommentSort(it)
            }
            if (vm.commentSort != 0) Text(
                L.t("লোড হওয়া মন্তব্যের মধ্যে সাজানো হয়েছে"), color = Sub, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = navBottom)   // শেষ কমেন্ট নেভ বারের নিচে যাবে না
            ) {
                items(sorted.size) { i -> CommentRow(vm, sorted[i], indent = false) }
                if (vm.commentsLoading || vm.commentsLoadingMore) item { CommentSkeletons(if (sorted.isEmpty()) 5 else 2) }
                if (sorted.isEmpty() && !vm.commentsLoading) item {
                    Text(L.t("মন্তব্য নেই বা বন্ধ আছে"), color = Sub, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentRow(vm: MainViewModel, c: CommentItem, indent: Boolean) {
    val rs = vm.replyStates[c.id]
    Column(Modifier.padding(start = if (indent) 44.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)) {
        Row {
            ChannelAvatar(c.avatar, c.authorUrl, c.author, if (indent) 24.dp else 32.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${c.author} • ${c.date}", color = Sub, fontSize = 12.sp)
                LinkText(c.text, vm, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.likes.isNotBlank()) {
                        Icon(Icons.Rounded.ThumbUp, null, tint = Sub, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(c.likes, color = Sub, fontSize = 12.sp)
                    }
                    if (!indent && c.replies != null && c.replyCount != 0) TextButton(
                        onClick = { vm.toggleReplies(c) }, contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            if (rs?.expanded == true) L.t("উত্তর লুকান")
                            else if (c.replyCount > 0) L.f("{0} টি উত্তর", c.replyCount)
                            else L.t("উত্তর দেখুন"),
                            fontSize = 12.sp, color = YtRed
                        )
                    }
                }
            }
        }
        if (rs?.expanded == true) {
            rs.items.forEach { r -> CommentRow(vm, r, indent = true) }
            if (rs.loading) CircularProgressIndicator(
                Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp, color = YtRed
            ) else if (rs.next != null) TextButton(onClick = { vm.loadMoreReplies(c) }) {
                Text(L.t("আরও উত্তর"), fontSize = 12.sp)
            }
        }
    }
}
