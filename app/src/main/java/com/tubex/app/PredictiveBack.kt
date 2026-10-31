package com.tubex.app

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
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
fun rememberPredictiveBack(enabled: Boolean = true, onBack: () -> Unit): BackAnim {
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
            // ছাড়লে: পরের স্ক্রিনের স্লাইড/ফেড ট্রানজিশন চলতে থাকে, আর ছোট হওয়া স্ক্রিনটি একসাথে স্বাভাবিক আকারে ফেরে
            currentOnBack()
            settle()
        } catch (c: CancellationException) {
            settle(180)
            throw c
        }
    }
    return anim
}

/** স্ক্রিন ছোট হয়ে সোয়াইপের দিকে সরে যায়, কোণা গোল হয় (Material predictive back ডিজাইন) */
fun Modifier.predictiveBackLayer(anim: BackAnim): Modifier = this.graphicsLayer {
    val p = anim.progress.coerceIn(0f, 1f)
    if (p > 0f) {
        val s = 1f - 0.1f * p
        scaleX = s; scaleY = s
        alpha = 1f - 0.25f * p          // ক্রসফেড: নিচের স্ক্রিন আবছা ফুটে ওঠে
        val dir = if (anim.edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        translationX = dir * 8.dp.toPx() * p
        shape = RoundedCornerShape(28.dp * p)
        clip = true
    }
}

/** একটি পুরো স্ক্রিনকে প্রেডিক্টিভ ব্যাক অ্যানিমেশনসহ মোড়ায় */
@Composable
fun PredictiveBackScreen(enabled: Boolean = true, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val anim = rememberPredictiveBack(enabled, onBack)
    Box(Modifier.fillMaxSize().predictiveBackLayer(anim), content = content)
}
