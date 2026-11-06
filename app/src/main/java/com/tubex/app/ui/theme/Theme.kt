package com.tubex.app.ui.theme

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

data class Palette(
    val name: String, val bg: Color, val chip: Color, val sub: Color, val accent: Color, val light: Boolean
)

val Palettes = listOf(
    Palette("ডার্ক", Color(0xFF0F0F0F), Color(0xFF272727), Color(0xFFAAAAAA), Color(0xFFFF0000), false),
    Palette("OLED কালো", Color(0xFF000000), Color(0xFF1A1A1A), Color(0xFF9E9E9E), Color(0xFFFF0000), false),
    Palette("লাইট", Color(0xFFFFFFFF), Color(0xFFF0F0F0), Color(0xFF606060), Color(0xFFFF0000), true),
    Palette("Ocean Blue", Color(0xFF0B1622), Color(0xFF17283B), Color(0xFF8FA8C0), Color(0xFF4FC3F7), false),
    Palette("Forest Green", Color(0xFF0D1A12), Color(0xFF1B2E22), Color(0xFF8FB09A), Color(0xFF66BB6A), false),
    Palette("Crimson", Color(0xFF140A0C), Color(0xFF2A1519), Color(0xFFBD9AA0), Color(0xFFE53935), false),
    Palette("Sunset Orange", Color(0xFF160E08), Color(0xFF2B1B10), Color(0xFFC9A98F), Color(0xFFFF7043), false),
    Palette("Purple Nebula", Color(0xFF120A1F), Color(0xFF24163A), Color(0xFFB7A3D6), Color(0xFFB388FF), false),
    Palette("Midnight Black", Color(0xFF0A0A12), Color(0xFF16161F), Color(0xFF9A9AB0), Color(0xFF7C83FF), false),
    Palette("Rose Gold", Color(0xFF1C1415), Color(0xFF33242A), Color(0xFFC7A9A9), Color(0xFFE8B4A0), false),
    Palette("Arctic Ice", Color(0xFFF4FAFF), Color(0xFFE3F1FA), Color(0xFF5B7083), Color(0xFF0288D1), true)
)

val LocalPalette = staticCompositionLocalOf { Palettes[0] }

val Bg: Color
    @Composable get() = LocalPalette.current.bg

val Chip: Color
    @Composable get() = LocalPalette.current.chip

val Sub: Color
    @Composable get() = LocalPalette.current.sub

val YtRed: Color
    @Composable get() = LocalPalette.current.accent

@Composable
fun YtTheme(index: Int, auto: Boolean = false, accent: Int = 0, content: @Composable () -> Unit) {
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val chosen = Palettes[index.coerceIn(0, Palettes.lastIndex)]
    // অটো মোডে: ফোন ডার্ক হলে ডার্ক প্যালেট, লাইট হলে লাইট প্যালেট (পছন্দের প্যালেট মিললে সেটাই)
    val base = if (!auto) chosen else if (sysDark) {
        if (!chosen.light) chosen else Palettes[0]
    } else {
        if (chosen.light) chosen else Palettes[2]
    }
    val p = if (accent != 0) base.copy(accent = Color(accent)) else base
    val on = if (p.light) Color.Black else Color.White
    val scheme = (if (p.light) lightColorScheme(
        primary = p.accent, background = p.bg, surface = p.bg, onBackground = on, onSurface = on
    ) else darkColorScheme(
        primary = p.accent, background = p.bg, surface = p.bg, onBackground = on, onSurface = on
    )).copy(
        // ডায়ালগ/শিট/মেনু/চিপ থিমের রঙে আসে, ডিফল্ট M3 রঙে নয়
        surfaceVariant = p.chip, onSurfaceVariant = p.sub, outline = p.sub,
        surfaceContainerLow = p.chip, surfaceContainer = p.chip, surfaceContainerHigh = p.chip,
        surfaceContainerHighest = p.chip, secondaryContainer = p.chip, onSecondaryContainer = on
    )
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(p.bg.toArgb()))
        w.statusBarColor = p.bg.toArgb()
        w.navigationBarColor = p.bg.toArgb()
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = p.light
        c.isAppearanceLightNavigationBars = p.light
    }
    // Scaffold/Surface-এর বাইরের জায়গায় (প্লেয়ার পেজ, চ্যানেল পেজ, ফ্লোটিং প্লেয়ার) LocalContentColor
    // ডিফল্টে কালো থাকে, তাই ডার্ক থিমে লেখা/আইকন কালো দেখাত। এখানে থিমের রঙ দেওয়া হলো।
    CompositionLocalProvider(LocalPalette provides p, LocalContentColor provides on) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
