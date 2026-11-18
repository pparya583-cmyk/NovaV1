package id.novamusic.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Ivory = Color(0xFFF7F6F0)
val Sage = Color(0xFF9CAF88)
val Olive = Color(0xFF788B68)
val Forest = Color(0xFF344A3A)
val Charcoal = Color(0xFF25332B)
val Muted = Color(0xFF748077)

private val palette = lightColorScheme(
    primary = Forest, onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE9D4), onPrimaryContainer = Forest,
    secondary = Olive, background = Ivory, onBackground = Charcoal,
    surface = Ivory, onSurface = Charcoal, surfaceVariant = Color(0xFFE9ECE2)
)

@Composable fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, typography = Typography(), content = content)
}

@Composable fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.border(1.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(24.dp)),
        color = Color.White.copy(alpha = .72f), shadowElevation = 3.dp,
        shape = RoundedCornerShape(24.dp), content = { Column(Modifier.padding(16.dp), content = content) })
}

fun Modifier.daylight(): Modifier = this.background(
    Brush.verticalGradient(listOf(Ivory, Color(0xFFEAF0E4), Ivory))
)
