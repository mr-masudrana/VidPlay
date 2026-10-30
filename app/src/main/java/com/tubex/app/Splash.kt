package com.tubex.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * লঞ্চ অ্যানিমেশন (YouTube-স্টাইল): লাল লোগো স্প্রিং-এ "পপ" করে, প্লে ত্রিভুজ ফুটে ওঠে,
 * নাম ফেড-ইন হয়, তারপর পুরো স্ক্রিন ফেড-আউট হয়ে অ্যাপ দেখা যায়।
 */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val scale = remember { Animatable(0.75f) }
    val play = remember { Animatable(0f) }
    val label = remember { Animatable(0f) }
    val overlay = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
            launch { delay(120); play.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            launch { delay(380); label.animateTo(1f, tween(350)) }
        }
        delay(450)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier.size(width = 104.dp, height = 74.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            ) {
                drawRoundRect(Color(0xFFFF0000), size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height * 0.28f))
                // প্লে ত্রিভুজ: মাঝ থেকে বড় হয়ে ফোটে
                val c = Offset(size.width / 2f, size.height / 2f)
                val h = size.height * 0.46f * play.value
                val w = h * 0.9f
                val tri = Path().apply {
                    moveTo(c.x - w * 0.4f, c.y - h / 2f)
                    lineTo(c.x + w * 0.6f, c.y)
                    lineTo(c.x - w * 0.4f, c.y + h / 2f)
                    close()
                }
                drawPath(tri, Color.White)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "TubeX", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(label.value)
                    .graphicsLayer { translationY = (1f - label.value) * 14.dp.toPx() }
            )
        }
    }
}
