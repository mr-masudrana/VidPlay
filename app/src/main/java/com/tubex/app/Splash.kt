package com.tubex.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * লঞ্চ অ্যানিমেশন: সিস্টেম স্প্ল্যাশের বৃত্তাকার লাল লোগো থেকেই শুরু হয় (কোনো লাফ নেই);
 * লোগো স্প্রিং-এ "পপ" করে, প্লে ত্রিভুজ ফোটে, একটি হালকা ঢেউ ছড়ায়, নাম ফেড-ইন হয়,
 * তারপর পুরো স্ক্রিন ফেড-আউট হয়ে অ্যাপ দেখা যায়।
 */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val play = remember { Animatable(1f) }
    val pulse = remember { Animatable(0f) }
    val label = remember { Animatable(0f) }
    val overlay = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            // সিস্টেম স্প্ল্যাশের লোগো যেমন আছে সেখান থেকেই শুরু: একটু ফুলে ওঠে, তারপর স্প্রিং-এ থিতু হয়
            launch {
                scale.animateTo(1.1f, tween(180, easing = FastOutSlowInEasing))
                scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
            }
            launch { delay(260); pulse.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch { delay(380); label.animateTo(1f, tween(350)) }
        }
        delay(400)
        overlay.animateTo(0f, tween(320))
        onDone()
    }

    Box(
        Modifier.fillMaxSize()
            .alpha(overlay.value)
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {   // অ্যানিমেশন চলার সময় নিচের UI-তে টাচ যাবে না
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
        contentAlignment = Alignment.Center
    ) {
        // সিস্টেম স্প্ল্যাশের লোগো (প্রায় ১৮১dp ব্যাসের বৃত্ত) ঠিক একই জায়গায় ও মাপে আঁকা হয়, তাই বদলানোর সময় কোনো লাফ নেই
        run {
            Canvas(Modifier.size(320.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = 90.7.dp.toPx() * scale.value

                // ঢেউ: লোগোর চারপাশ থেকে ছড়িয়ে মিলিয়ে যায়
                if (pulse.value > 0f) drawCircle(
                    Color(0xFFFF0000).copy(alpha = 0.28f * (1f - pulse.value)),
                    radius = r * (1f + 0.9f * pulse.value), center = c
                )
                drawCircle(Color(0xFFFF0000), radius = r, center = c)

                // প্লে ত্রিভুজ: মাঝ থেকে বড় হয়ে ফোটে
                val h = r * 0.94f * play.value
                val w = h * 0.75f
                val tri = Path().apply {
                    moveTo(c.x - w / 3f, c.y - h / 2f)
                    lineTo(c.x + w * 2f / 3f, c.y)
                    lineTo(c.x - w / 3f, c.y + h / 2f)
                    close()
                }
                drawPath(tri, Color.White)
            }
            Text(
                "TubeX", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.offset(y = 140.dp).alpha(label.value)
                    .graphicsLayer { translationY = (1f - label.value) * 14.dp.toPx() }
            )
        }
    }
}
