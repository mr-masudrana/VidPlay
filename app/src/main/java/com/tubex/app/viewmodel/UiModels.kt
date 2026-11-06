package com.tubex.app.viewmodel

import com.tubex.app.data.model.CommentItem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.schabi.newpipe.extractor.Page

data class QItem(val url: String, val title: String, val channel: String, val thumb: String?)

data class ReplyState(
    val items: List<CommentItem> = emptyList(), val next: Page? = null,
    val loading: Boolean = false, val expanded: Boolean = false
)
