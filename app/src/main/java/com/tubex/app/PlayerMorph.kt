package com.tubex.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlin.math.min

/** ভাসমান মিনি প্লেয়ারের অবস্থান/মাপ (FloatingPlayer-এর হিসাবের সাথে হুবহু এক) */
private class FRect(val x: Float, val y: Float, val w: Float, val h: Float)

private fun floatRect(vm: MainViewModel, cw: Float, ch: Float, d: Float): FRect {
    val margin = 12f * d
    val bottomInset = 92f * d
    val w = (cw * 0.55f).coerceIn(160f * d, 260f * d)
    val h = w * 9f / 16f
    val x = if (vm.pipX.isNaN()) cw - w - margin else vm.pipX.coerceIn(0f, (cw - w).coerceAtLeast(0f))
    val y = if (vm.pipY.isNaN()) ch - h - bottomInset else vm.pipY.coerceIn(0f, (ch - h).coerceAtLeast(0f))
    return FRect(x, y, w, h)
}

/** প্লেয়ার পেজের দৃশ্যমান অংশ: ওপরে ভিডিও, নিচে বিবরণ; ছোট হওয়ার সময় বিবরণ কেটে শুধু ভিডিও অংশ থাকে */
private class MorphClip(val top: Float, val bottom: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, top, size.width, bottom, CornerRadius(radius)))
}

/**
 * প্লেয়ার পেজ ↔ ভাসমান মিনি প্লেয়ার: পেজটি ভাসমান উইন্ডোর জায়গায় ছোট হয়ে যায়, আবার ওখান থেকে বড় হয়ে খোলে।
 * morph: 0 = ভাসমান উইন্ডোর মাপ, 1 = পুরো পেজ
 */
@Composable
fun MorphPlayer(
    vm: MainViewModel,
    morph: Animatable<Float, AnimationVector1D>,
    cw: Float, ch: Float, landscape: Boolean
) {
    val density = LocalDensity.current
    val d = density.density
    val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
    val shown = vm.expanded && (vm.active || vm.loadingNew)

    // ব্যাক জেসচারে পেজ আঙুলের সাথে সাথে ভাসমান উইন্ডোর দিকে ছোট হয়; ছাড়লে বাকি পথটুকু যায়
    val backAnim = rememberPredictiveBack(enabled = shown && !landscape && !vm.inPip, fadeOutOnCommit = true) {
        vm.minimize()
    }

    LaunchedEffect(shown, landscape, vm.inPip) {
        if (shown) backAnim.progress = 0f
        if (landscape || vm.inPip) morph.snapTo(if (shown) 1f else 0f)
        else morph.animateTo(if (shown) 1f else 0f, tween(320, easing = FastOutSlowInEasing))
    }

    val visible by remember { derivedStateOf { morph.value > 0f } }
    if (shown || visible) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val m = min(morph.value, 1f - backAnim.progress).coerceIn(0f, 1f)
                if (m < 1f) {
                    val r = floatRect(vm, cw, ch, d)
                    val s0 = r.w / size.width
                    val s = lerp(s0, 1f, m)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s; scaleY = s
                    translationX = lerp(r.x, 0f, m)
                    translationY = lerp(r.y, statusPx, m) - statusPx * s
                    clip = true
                    shape = MorphClip(
                        top = lerp(statusPx, 0f, m),
                        bottom = lerp(statusPx + size.width * 9f / 16f, size.height, m),
                        radius = 12f * d * (1f - m) / s
                    )
                }
            }
        ) { PlayerScreen(vm) }
    }
}
