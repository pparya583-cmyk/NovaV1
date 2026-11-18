package id.novamusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class NovaPalette(
    val background: Color, val surface: Color, val primary: Color, val secondary: Color,
    val accent: Color, val text: Color, val muted: Color, val gradientEnd: Color,
    val dark: Boolean = false
)

private val daylight = NovaPalette(Color(0xFFF7F6F0), Color(0xFFFFFFFF), Color(0xFF344A3A), Color(0xFF788B68), Color(0xFF9CAF88), Color(0xFF25332B), Color(0xFF748077), Color(0xFFEAF0E4))
private val midnight = NovaPalette(Color(0xFF101411), Color(0xFF1B231D), Color(0xFFB8D7A5), Color(0xFF86A978), Color(0xFF526D4B), Color(0xFFF0F4EC), Color(0xFFA4B0A3), Color(0xFF18251B), true)
private val ocean = NovaPalette(Color(0xFFF0F7FA), Color(0xFFFFFFFF), Color(0xFF14566B), Color(0xFF278CA1), Color(0xFF9BD4DC), Color(0xFF18333B), Color(0xFF67818A), Color(0xFFDDEFF2))
private val rose = NovaPalette(Color(0xFFFFF4F5), Color(0xFFFFFFFF), Color(0xFF8A4358), Color(0xFFB96C82), Color(0xFFE8A9B8), Color(0xFF3E2830), Color(0xFF927780), Color(0xFFF9E2E8))
private val lavender = NovaPalette(Color(0xFFF6F3FC), Color(0xFFFFFFFF), Color(0xFF59477E), Color(0xFF8872B3), Color(0xFFC7B8E8), Color(0xFF30283E), Color(0xFF81788F), Color(0xFFEAE3F7))
private val sunset = NovaPalette(Color(0xFFFFF6ED), Color(0xFFFFFFFF), Color(0xFF8B4828), Color(0xFFC16B3C), Color(0xFFE9B27E), Color(0xFF3D2B22), Color(0xFF927B6D), Color(0xFFF8E4D1))

val LocalNovaPalette = staticCompositionLocalOf { daylight }
val LocalNovaUiStyle = staticCompositionLocalOf { "Glass" }

@Composable fun Ivory() = LocalNovaPalette.current.background
@Composable fun Sage() = LocalNovaPalette.current.accent
@Composable fun Olive() = LocalNovaPalette.current.secondary
@Composable fun Forest() = LocalNovaPalette.current.primary
@Composable fun Charcoal() = LocalNovaPalette.current.text
@Composable fun Muted() = LocalNovaPalette.current.muted

@Composable fun NovaTheme(themeName: String = "Daylight Glass", uiStyle: String = "Glass", content: @Composable () -> Unit) {
    val p = when (themeName) {
        "Midnight AMOLED" -> midnight
        "Ocean Breeze" -> ocean
        "Rose Quartz" -> rose
        "Lavender Haze" -> lavender
        "Sunset" -> sunset
        else -> daylight
    }
    val scheme = if (p.dark) darkColorScheme(
        primary = p.primary, onPrimary = Color(0xFF172016), secondary = p.secondary,
        background = p.background, onBackground = p.text, surface = p.surface,
        onSurface = p.text, surfaceVariant = Color(0xFF29352B), onSurfaceVariant = p.muted
    ) else lightColorScheme(
        primary = p.primary, onPrimary = Color.White, secondary = p.secondary,
        background = p.background, onBackground = p.text, surface = p.surface,
        onSurface = p.text, surfaceVariant = p.gradientEnd, onSurfaceVariant = p.muted
    )
    CompositionLocalProvider(LocalNovaPalette provides p, LocalNovaUiStyle provides uiStyle) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

@Composable fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalNovaPalette.current
    val style = LocalNovaUiStyle.current
    val liquid = style == "Liquid Glass"
    val shape = when (style) { "Minimal" -> RoundedCornerShape(12.dp); "Compact" -> RoundedCornerShape(14.dp); else -> RoundedCornerShape(if (liquid) 30.dp else 24.dp) }
    val fill = when (style) {
        "Minimal", "Compact" -> p.surface
        "Liquid Glass" -> p.surface.copy(alpha = if (p.dark) .48f else .54f)
        else -> p.surface.copy(alpha = if (p.dark) .94f else .76f)
    }
    val outline = if (liquid) Brush.linearGradient(listOf(Color.White.copy(alpha = .78f), p.accent.copy(alpha = .34f), Color.White.copy(alpha = .18f)))
        else Brush.linearGradient(listOf(p.primary.copy(alpha = if (p.dark) .22f else .10f), p.primary.copy(alpha = if (p.dark) .22f else .10f)))
    Surface(modifier = modifier.border(1.dp, outline, shape),
        color = fill, shadowElevation = when (style) { "Minimal" -> 0.dp; "Compact" -> 1.dp; "Liquid Glass" -> 12.dp; else -> 3.dp },
        shape = shape, content = { Column(Modifier.padding(if (style == "Compact") 12.dp else 16.dp), content = content) })
}

@Composable fun Modifier.daylight(): Modifier {
    val p = LocalNovaPalette.current
    return background(Brush.verticalGradient(listOf(p.background, p.gradientEnd, p.background)))
}
