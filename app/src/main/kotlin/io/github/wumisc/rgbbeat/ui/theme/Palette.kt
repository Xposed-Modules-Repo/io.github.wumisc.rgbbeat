package io.github.wumisc.rgbbeat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 夜店风双主题色板。
 *
 * 深色：黑箱 + 霓虹玫红 / 电光青，用于"黑丝裤袜 + 美系男模"的夜场氛围。
 * 浅色：珍珠白 + 墨黑 + 香槟金，用于"白丝裤袜 + 中山装男模"的冷静质感。
 * 所有组件只从这里取色，保证深浅切换时前景色自动跟随，不出现"白底白字"。
 */
@Immutable
data class RgbPalette(
    val dark: Boolean,
    val baseTop: Color,
    val baseBottom: Color,
    val ink: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val neon: Color,
    val neonAlt: Color,
    val gold: Color,
    val ok: Color,
    val danger: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val scrim: Color,
) {
    /** 在任意底色上取对比度足够的前景色：深色玻璃上用白字，亮色玻璃上用近黑字 */
    fun onColor(background: Color): Color {
        val l = background.luminance()
        return if (l > 0.52f) Color(0xFF0B0A10) else Color(0xFFF7F3FB)
    }
}

val NightPalette = RgbPalette(
    dark = true,
    baseTop = Color(0xFF0B0513),
    baseBottom = Color(0xFF010006),
    ink = Color(0xFFF6EFFB),
    inkDim = Color(0xFFB0A6BE),
    inkFaint = Color(0x66B0A6BE),
    neon = Color(0xFFFF2D9B),
    neonAlt = Color(0xFF23E8FF),
    gold = Color(0xFFFFC46B),
    ok = Color(0xFF45F08B),
    danger = Color(0xFFFF5A5A),
    glassTop = Color(0x1AFFFFFF),
    glassBottom = Color(0x08000000),
    glassBorder = Color(0x33FFFFFF),
    glassHighlight = Color(0x40FFFFFF),
    scrim = Color(0xCC030008),
)

val PearlPalette = RgbPalette(
    dark = false,
    baseTop = Color(0xFFF9F5F0),
    baseBottom = Color(0xFFE3DAD0),
    ink = Color(0xFF141218),
    inkDim = Color(0xFF6A6472),
    inkFaint = Color(0x556A6472),
    neon = Color(0xFFD8326E),
    neonAlt = Color(0xFF2F6BE0),
    gold = Color(0xFFA97A3C),
    ok = Color(0xFF1E9E5A),
    danger = Color(0xFFC43838),
    glassTop = Color(0xB8FFFFFF),
    glassBottom = Color(0x8AFFFFFF),
    glassBorder = Color(0xE6FFFFFF),
    glassHighlight = Color(0xFFFFFFFF),
    scrim = Color(0x33DED5CA),
)

val LocalPalette = staticCompositionLocalOf { NightPalette }

@Composable
fun RgbTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) NightPalette else PearlPalette
    CompositionLocalProvider(LocalPalette provides palette, content = content)
}
