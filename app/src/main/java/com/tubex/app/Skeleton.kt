package com.tubex.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * স্কেলিটন লোডিং: ধূসর প্লেসহোল্ডারের ওপর দিয়ে আলোর ঝিলিক (shimmer) চলে।
 * থিমের (লাইট/ডার্ক) রঙে মানিয়ে যায়।
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val p = LocalPalette.current
    val x by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "x"
    )
    val highlight = if (p.light) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.09f)
    Box(
        modifier.clip(shape).background(p.chip).drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val brush = Brush.horizontalGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                startX = w * x, endX = w * x + w * 0.6f
            )
            onDrawBehind { drawRect(brush) }
        }
    )
}

@Composable
private fun Line(width: Float, height: Dp = 14.dp, modifier: Modifier = Modifier) =
    SkeletonBox(modifier.fillMaxWidth(width).height(height), RoundedCornerShape(6.dp))

/** ভিডিও কার্ডের স্কেলিটন: থাম্বনেইল + অ্যাভাটার + দুই লাইন */
@Composable
fun VideoCardSkeleton() {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RectangleShape)
        Row(Modifier.padding(12.dp, 12.dp, 12.dp, 0.dp)) {
            SkeletonBox(Modifier.size(36.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Line(0.92f, 15.dp)
                Spacer(Modifier.height(8.dp))
                Line(0.6f, 12.dp)
            }
        }
    }
}

@Composable
fun VideoListSkeleton(count: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) { repeat(count) { VideoCardSkeleton() } }
}

/** প্লেয়ার পেজের নিচের অংশ (শিরোনাম, চ্যানেল, বাটন, মন্তব্য, সম্পর্কিত ভিডিও) লোড হওয়ার সময় */
@Composable
fun PlayerDetailsSkeleton(knownTitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (!knownTitle.isNullOrBlank()) {
                androidx.compose.material3.Text(
                    knownTitle, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else {
                Line(0.95f, 18.dp)
                Spacer(Modifier.height(8.dp))
                Line(0.7f, 18.dp)
            }
            Spacer(Modifier.height(12.dp))
            Line(0.45f, 12.dp)
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(44.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Line(0.6f, 14.dp)
                Spacer(Modifier.height(8.dp))
                Line(0.35f, 12.dp)
            }
            Spacer(Modifier.width(12.dp))
            SkeletonBox(Modifier.width(96.dp).height(40.dp), RoundedCornerShape(50))
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { SkeletonBox(Modifier.width(100.dp).height(36.dp), RoundedCornerShape(50)) }
        }
        Column(Modifier.padding(12.dp)) {
            SkeletonBox(Modifier.fillMaxWidth().height(72.dp), RoundedCornerShape(14.dp))
        }
        VideoListSkeleton(2)
    }
}

/** মন্তব্যের সারি */
@Composable
fun CommentSkeletons(count: Int = 4) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) {
            Row {
                SkeletonBox(Modifier.size(32.dp), CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Line(0.3f, 11.dp)
                    Spacer(Modifier.height(8.dp))
                    Line(0.95f, 13.dp)
                    Spacer(Modifier.height(6.dp))
                    Line(0.7f, 13.dp)
                }
            }
        }
    }
}

/** চ্যানেল পেজের স্কেলিটন */
@Composable
fun ChannelSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 4f), RectangleShape)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(64.dp), CircleShape)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Line(0.6f, 18.dp)
                Spacer(Modifier.height(8.dp))
                Line(0.4f, 12.dp)
            }
        }
        VideoListSkeleton(2)
    }
}
