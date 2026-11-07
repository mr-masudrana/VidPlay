package com.tubex.app.viewmodel

import com.tubex.app.data.model.CommentItem
import com.tubex.app.data.remote.YtRepository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** মন্তব্য ও রিপ্লাইয়ের লোড, পেজিনেশন ও সাজানো। MainViewModel থেকে আলাদা করা। */
class CommentsController(private val scope: CoroutineScope) {
    var items by mutableStateOf<List<CommentItem>>(emptyList()); private set
    var total by mutableStateOf(0); private set
    var loading by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var hasMore by mutableStateOf(false); private set
    /** 0 শীর্ষ, 1 নতুন, 2 পুরনো */
    var sort by mutableStateOf(0); private set
    var replyStates by mutableStateOf<Map<String, ReplyState>>(emptyMap()); private set

    private var url = ""
    private var nextPage: Page? = null

    /** অন্য ভিডিও/লোকাল ফাইল চালালে পুরনো মন্তব্য সরানো */
    fun clearList() { items = emptyList() }

    fun load(videoUrl: String) = scope.launch {
        url = videoUrl; nextPage = null; hasMore = false
        loading = true; items = emptyList(); total = 0; replyStates = emptyMap()
        runCatching { YtRepository.comments(videoUrl) }.onSuccess { p ->
            items = p.items; nextPage = p.next; hasMore = p.next != null
            total = if (p.total > 0) p.total else 0
        }
        loading = false
    }

    /** স্ক্রোল শেষের কাছে এলে পরের পেজ */
    fun loadMore() {
        val page = nextPage ?: return
        if (loadingMore) return
        scope.launch {
            loadingMore = true
            runCatching { YtRepository.moreComments(url, page) }
                .onSuccess { p ->
                    val have = items.map { it.id }.toSet()
                    items = items + p.items.filter { it.id !in have }
                    nextPage = p.next; hasMore = p.next != null
                }
                .onFailure { hasMore = false }
            loadingMore = false
        }
    }

    fun chooseSort(i: Int) { sort = i }

    /** লোড হওয়া মন্তব্যের মধ্যে সাজানো: 0 শীর্ষ (YouTube-এর ক্রম), 1 নতুন, 2 পুরনো */
    fun sorted(): List<CommentItem> = when (sort) {
        1 -> items.sortedByDescending { it.time ?: Long.MIN_VALUE }
        2 -> items.sortedBy { it.time ?: Long.MAX_VALUE }
        else -> items
    }

    fun toggleReplies(c: CommentItem) {
        val cur = replyStates[c.id]
        if (cur != null && cur.expanded) { replyStates = replyStates + (c.id to cur.copy(expanded = false)); return }
        if (cur != null && cur.items.isNotEmpty()) { replyStates = replyStates + (c.id to cur.copy(expanded = true)); return }
        val page = c.replies ?: return
        loadReplies(c.id, page)
    }

    fun loadMoreReplies(c: CommentItem) {
        val page = replyStates[c.id]?.next ?: return
        loadReplies(c.id, page)
    }

    private fun loadReplies(id: String, page: Page) = scope.launch {
        val cur = replyStates[id] ?: ReplyState()
        replyStates = replyStates + (id to cur.copy(loading = true, expanded = true))
        val res = runCatching { YtRepository.moreComments(url, page) }.getOrNull()
        val st = replyStates[id] ?: cur
        replyStates = replyStates + (id to
                if (res != null) st.copy(items = st.items + res.items, next = res.next, loading = false)
                else st.copy(loading = false))
    }
}
