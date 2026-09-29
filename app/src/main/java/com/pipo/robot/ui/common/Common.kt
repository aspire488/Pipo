package com.pipo.robot.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pipo.robot.ui.theme.PipoPalette

enum class Glyph { BACK, CHAT, MIC, GAMES, MENU, SEND, CLOSE, STOP, PHOTO }

/** Tiny hand-drawn icons, so the app doesn't depend on an icon library. */
@Composable
fun GlyphIcon(g: Glyph, modifier: Modifier = Modifier, color: Color = PipoPalette.text, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawGlyph(g, color) }
}

fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val w = size.width; val h = size.height
    val sw = w * 0.09f
    val st = Stroke(width = sw, cap = StrokeCap.Round)
    when (g) {
        Glyph.BACK -> {
            val p = Path().apply { moveTo(w * 0.6f, h * 0.22f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.6f, h * 0.78f) }
            drawPath(p, c, style = st)
        }
        Glyph.CHAT -> {
            drawRoundRect(c, Offset(w * 0.14f, h * 0.2f), Size(w * 0.72f, h * 0.5f), CornerRadius(w * 0.18f), style = st)
            val p = Path().apply { moveTo(w * 0.3f, h * 0.68f); lineTo(w * 0.26f, h * 0.84f); lineTo(w * 0.46f, h * 0.7f) }
            drawPath(p, c, style = st)
            for (i in 0..2) drawCircle(c, sw * 0.6f, Offset(w * (0.36f + i * 0.14f), h * 0.45f))
        }
        Glyph.MIC -> {
            drawRoundRect(c, Offset(w * 0.37f, h * 0.12f), Size(w * 0.26f, h * 0.44f), CornerRadius(w * 0.13f))
            val p = Path().apply { moveTo(w * 0.24f, h * 0.44f); quadraticBezierTo(w * 0.24f, h * 0.72f, w * 0.5f, h * 0.72f); quadraticBezierTo(w * 0.76f, h * 0.72f, w * 0.76f, h * 0.44f) }
            drawPath(p, c, style = st)
            drawLine(c, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.88f), sw, StrokeCap.Round)
        }
        Glyph.GAMES -> {
            drawRoundRect(c, Offset(w * 0.1f, h * 0.3f), Size(w * 0.8f, h * 0.42f), CornerRadius(w * 0.2f), style = st)
            drawLine(c, Offset(w * 0.25f, h * 0.51f), Offset(w * 0.41f, h * 0.51f), sw, StrokeCap.Round)
            drawLine(c, Offset(w * 0.33f, h * 0.43f), Offset(w * 0.33f, h * 0.59f), sw, StrokeCap.Round)
            drawCircle(c, sw * 0.8f, Offset(w * 0.63f, h * 0.47f)); drawCircle(c, sw * 0.8f, Offset(w * 0.72f, h * 0.56f))
        }
        Glyph.MENU -> for (i in 0..2) drawLine(c, Offset(w * 0.22f, h * (0.3f + i * 0.2f)), Offset(w * 0.78f, h * (0.3f + i * 0.2f)), sw, StrokeCap.Round)
        Glyph.SEND -> {
            val p = Path().apply { moveTo(w * 0.18f, h * 0.2f); lineTo(w * 0.84f, h * 0.5f); lineTo(w * 0.18f, h * 0.8f); lineTo(w * 0.3f, h * 0.5f); close() }
            drawPath(p, c)
        }
        Glyph.CLOSE -> {
            drawLine(c, Offset(w * 0.26f, h * 0.26f), Offset(w * 0.74f, h * 0.74f), sw, StrokeCap.Round)
            drawLine(c, Offset(w * 0.74f, h * 0.26f), Offset(w * 0.26f, h * 0.74f), sw, StrokeCap.Round)
        }
        Glyph.STOP -> drawRoundRect(c, Offset(w * 0.28f, h * 0.28f), Size(w * 0.44f, h * 0.44f), CornerRadius(w * 0.08f))
        Glyph.PHOTO -> {
            drawRoundRect(c, Offset(w * 0.12f, h * 0.2f), Size(w * 0.76f, h * 0.6f), CornerRadius(w * 0.1f), style = st)
            val p = Path().apply { moveTo(w * 0.2f, h * 0.72f); lineTo(w * 0.42f, h * 0.46f); lineTo(w * 0.58f, h * 0.62f); lineTo(w * 0.68f, h * 0.52f); lineTo(w * 0.82f, h * 0.72f) }
            drawPath(p, c, style = st)
            drawCircle(c, sw, Offset(w * 0.68f, h * 0.36f))
        }
    }
}

@Composable
fun RoundButton(g: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = PipoPalette.card.copy(alpha = 0.85f), tint: Color = PipoPalette.text, size: Dp = 48.dp) {
    Box(modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        GlyphIcon(g, color = tint, size = size * 0.5f)
    }
}

/** Simple top bar for secondary screens. */
@Composable
fun PipoTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Glyph.BACK, onBack, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = PipoPalette.text)
    }
}

fun relativeDay(ts: Long, now: Long = System.currentTimeMillis()): String {
    val a = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val b = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
    val dayDiff = b.get(java.util.Calendar.DAY_OF_YEAR) - a.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        else -> java.text.SimpleDateFormat(if (sameYear) "EEE, MMM d" else "MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}

fun clockTime(ts: Long): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ts))
