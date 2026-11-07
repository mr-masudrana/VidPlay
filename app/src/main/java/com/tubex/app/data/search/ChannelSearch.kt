package com.tubex.app.data.search

import com.tubex.app.data.model.ChannelResult

/**
 * সার্চ ফলাফলের চ্যানেল সারির Android/NewPipe-মুক্ত যুক্তি (ইউনিট টেস্টযোগ্য রাখতে আলাদা)।
 * ব্লকের কী ঠিক [com.tubex.app.data.recommend.Blocks]-এর "ch:<নাম, ছোট হাতের>" ফরম্যাটের সাথে মিলতে হবে।
 */
object ChannelSearch {
    const val DEFAULT_LIMIT = 3

    /** চ্যানেল ব্লকের এন্ট্রি; ভিডিও ফিল্টারও (Blocks.allowed) একই কী দেখে */
    fun blockKey(name: String): String = "ch:${name.lowercase()}"

    /** ব্লক করা চ্যানেল বাদ; ক্রম অক্ষুণ্ণ */
    fun filterBlocked(list: List<ChannelResult>, blocked: Set<String>): List<ChannelResult> =
        if (blocked.isEmpty()) list else list.filter { blockKey(it.name) !in blocked }

    /** NewPipe-এর আইটেম থেকে ফলাফল: সবচেয়ে বড় (শেষ) থাম্বনেইল অবতার হিসেবে */
    fun toResult(url: String, name: String, thumbnailUrls: List<String>, subscribers: Long, description: String?) =
        ChannelResult(url = url, name = name, avatar = thumbnailUrls.lastOrNull(), subscribers = subscribers, description = description)

    fun isSubscribed(subscribedUrls: Collection<String>, c: ChannelResult): Boolean = c.url in subscribedUrls
}
