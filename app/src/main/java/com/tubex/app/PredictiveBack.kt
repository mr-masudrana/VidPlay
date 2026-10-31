package com.tubex.app

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** প্রেডিক্টিভ ব্যাক জেসচারের অবস্থা: progress 0..1, edge = কোন কিনারা থেকে সোয়াইপ */
class BackAnim {
    var progress by mutableFloatStateOf(0f)
    var edge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
}

/**
 * ব্যাক জেসচার চলার সময় progress আপডেট করে; ছেড়ে দিলে [onBack] চলে, বাতিল করলে স্মুথভাবে আগের অবস্থায় ফেরে।
 * Android 13+ এ (ম্যানিফেস্টে enableOnBackInvokedCallback চালু থাকলে) জেসচার প্রিভিউ পাওয়া যায়;
 * পুরনো ফোনে সাধারণ ব্যাক বাটনের মতোই কাজ করে।
 */
@Composable
fun rememberPredictiveBack(
    enabled: Boolean = true,
    fadeOutOnCommit: Boolean = false,
    deferBack: Boolean = false,
    onBack: () -> Unit
): BackAnim {
    val anim = remember { BackAnim() }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    fun settle(ms: Int = 240) {
        val from = anim.progress
        scope.launch { animate(from, 0f, animationSpec = tween(ms)) { v, _ -> anim.progress = v } }
    }
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { e -> anim.progress = e.progress; anim.edge = e.swipeEdge }
            if (deferBack) {
                // মূল পেজ (ট্যাব/সার্চ): আগে বর্তমান পেজ পুরো মিলিয়ে যায় (নিচে আগের পেজ দেখা যায়), তারপর পেজ বদলায়
                scope.launch {
                    animate(anim.progress, 1f, animationSpec = tween(220)) { v, _ -> anim.progress = v }
                    currentOnBack()
                    delay(260)          // নতুন পেজের নিজস্ব ট্রানজিশন শেষ হওয়া পর্যন্ত প্রিভিউ থাকে
                    anim.progress = 0f
                }
            } else if (fadeOutOnCommit) {
                // ওভারলে স্ক্রিন (প্লেয়ার/চ্যানেল): ছাড়লে বর্তমান পেজ পুরোপুরি মিলিয়ে যায়, নিচের আগের পেজ ফুটে ওঠে
                scope.launch { animate(anim.progress, 1f, animationSpec = tween(240)) { v, _ -> anim.progress = v } }
                currentOnBack()
            } else {
                // ট্যাব/সার্চ: পেজ বদলে যায়, আর ফিকে হয়ে থাকা কন্টেন্ট স্বাভাবিক অবস্থায় ফেরে (নতুন পেজ ফেড-ইন হয়)
                currentOnBack()
                settle()
            }
        } catch (c: CancellationException) {
            settle(180)
            throw c
        }
    }
    return anim
}

/** ব্যাক জেসচারে বর্তমান পেজ আঙুলের সাথে সাথে ধীরে ধীরে মিলিয়ে যায় (স্কেল/সরানো/গোল কোণা নেই) */
fun Modifier.predictiveBackLayer(anim: BackAnim): Modifier = this.graphicsLayer {
    val p = anim.progress.coerceIn(0f, 1f)
    if (p > 0f) alpha = 1f - p
}

/** একটি পুরো স্ক্রিনকে প্রেডিক্টিভ ব্যাক অ্যানিমেশনসহ মোড়ায় */
@Composable
fun PredictiveBackScreen(enabled: Boolean = true, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val anim = rememberPredictiveBack(enabled, fadeOutOnCommit = true, onBack = onBack)
    Box(Modifier.fillMaxSize().predictiveBackLayer(anim), content = content)
}
