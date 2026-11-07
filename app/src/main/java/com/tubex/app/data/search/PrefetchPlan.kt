package com.tubex.app.data.search

import com.tubex.app.data.model.VideoItem

/**
 * ফিড/সার্চ রেজাল্টের কোন ভিডিওগুলোর স্ট্রিম-তথ্য আগে থেকে আনা হবে তা বাছাই (Android-মুক্ত, টেস্টযোগ্য)।
 * ব্যবহারকারী সাধারণত ওপরের দিকেরটাই ট্যাপ করে, তাই শুধু শুরুর [window]টির মধ্য থেকে বাছা হয়।
 */
object PrefetchPlan {
    const val WINDOW = 6

    /** লাইভ (durationSec <= 0) ও আগে থেকেই ক্যাশে/ফেচে থাকা বাদ; সর্বোচ্চ [max]টি, তালিকার ক্রমে */
    fun pick(list: List<VideoItem>, isCached: (String) -> Boolean, max: Int, window: Int = WINDOW): List<VideoItem> =
        list.asSequence().take(window).filter { it.durationSec > 0 && !isCached(it.url) }.take(max).toList()
}
