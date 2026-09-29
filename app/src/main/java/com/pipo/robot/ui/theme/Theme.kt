package com.pipo.robot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PipoPalette {
    val night = Color(0xFF1B2233)
    val card = Color(0xFF263049)
    val cardHi = Color(0xFF2F3A57)
    val mint = Color(0xFF8FF5E2)
    val amber = Color(0xFFFFC27A)
    val pink = Color(0xFFFF8FA3)
    val lilac = Color(0xFFC99BFF)
    val text = Color(0xFFEEF3F8)
    val muted = Color(0xFF9AA7B8)
    val paper = Color(0xFFFFF8F0)
    val ink = Color(0xFF2B3342)
}

private val scheme = darkColorScheme(
    primary = PipoPalette.mint, onPrimary = Color(0xFF0F2A2A),
    secondary = PipoPalette.amber, onSecondary = Color(0xFF3A2A12),
    tertiary = PipoPalette.pink,
    background = PipoPalette.night, onBackground = PipoPalette.text,
    surface = PipoPalette.card, onSurface = PipoPalette.text,
    surfaceVariant = PipoPalette.cardHi, onSurfaceVariant = PipoPalette.muted,
    error = Color(0xFFFF8A7A),
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun PipoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
        content = content,
    )
}
