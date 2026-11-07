package com.tubex.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * প্লে কিউ: তালিকা, বর্তমান পজিশন, শাফল ও রিপিট। প্লেয়ার বা নেটওয়ার্কের কিছু জানে না —
 * কোন আইটেম চলবে তা ঠিক করে ফেরত দেয়, চালানোর কাজ MainViewModel-এর।
 */
class PlayQueue {
    var items by mutableStateOf<List<QItem>>(emptyList()); private set
    var index by mutableStateOf(0); private set
    var shuffle by mutableStateOf(false); private set
    /** 0 বন্ধ, 1 কিউ, 2 একটি ভিডিও */
    var repeat by mutableStateOf(0); private set
    private val played = HashSet<String>()

    val isEmpty get() = items.isEmpty()

    /** নতুন কিউ বসিয়ে প্রথম চালানোর আইটেম ফেরত দেয় */
    fun start(list: List<QItem>, startIndex: Int, shuffleStart: Boolean = false): QItem {
        items = list; played.clear()
        if (shuffleStart) { shuffle = true; index = list.indices.random() }
        else index = startIndex.coerceIn(0, list.lastIndex)
        return list[index]
    }

    fun clear() { items = emptyList(); index = 0; played.clear() }

    fun setShuffle(v: Boolean) { shuffle = v; played.clear() }
    fun setRepeat(mode: Int) { repeat = mode }

    /** ভিডিও শেষে পরের আইটেম (শাফল/রিপিট মেনে); না থাকলে null। পজিশন নিজে এগোয়। */
    fun advance(): QItem? {
        if (items.isEmpty()) return null
        val n = items.size
        items.getOrNull(index)?.let { played.add(it.url) }
        val next: Int = if (shuffle) {
            val remaining = items.indices.filter { items[it].url !in played && it != index }
            if (remaining.isNotEmpty()) remaining.random()
            else if (repeat == 1) {
                played.clear()
                items.indices.filter { it != index }.randomOrNull() ?: index
            } else -1
        } else if (index + 1 < n) index + 1
        else if (repeat == 1) 0
        else -1
        if (next < 0) return null
        index = next
        return items[next]
    }

    /** আগের আইটেমে ফেরা; কিউর শুরুতে থাকলে null */
    fun back(): QItem? {
        if (items.isEmpty() || index <= 0) return null
        index -= 1
        return items[index]
    }

    fun select(i: Int): QItem? {
        val q = items.getOrNull(i) ?: return null
        index = i
        return q
    }

    /** কিউ খালি থাকলে [seed] (বর্তমানে চলা ভিডিও) প্রথমে বসে */
    fun add(item: QItem, playNext: Boolean, seed: QItem?) {
        if (items.isEmpty()) {
            items = if (seed != null) listOf(seed) else emptyList()
            index = 0
        }
        val at = if (playNext) (index + 1).coerceIn(0, items.size) else items.size
        items = items.toMutableList().also { it.add(at, item) }
    }

    fun remove(i: Int) {
        if (i !in items.indices) return
        items = items.toMutableList().also { it.removeAt(i) }
        if (i <= index) index = (index - 1).coerceAtLeast(0)
        if (items.isEmpty()) index = 0
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (i !in items.indices || j !in items.indices) return
        val cur = items.getOrNull(index)?.url
        items = items.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        if (cur != null) index = items.indexOfFirst { it.url == cur }
    }

    /** প্লেলিস্টের বাকি পেজ আসার পর কিউ বাড়ানো (পজিশন অপরিবর্তিত) */
    fun replaceItems(list: List<QItem>) { items = list }
}
