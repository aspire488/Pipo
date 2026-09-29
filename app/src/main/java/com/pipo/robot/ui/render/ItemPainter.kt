package com.pipo.robot.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.ItemShape
import kotlin.math.cos
import kotlin.math.sin

fun shapeColor(s: ItemShape): Color = Catalog.items.firstOrNull { it.shape == s }?.let { Color(it.color) } ?: when (s) {
    ItemShape.ANTENNA -> Color(0xFFE0975C)
    ItemShape.LAMP -> Color(0xFFFFC27A)
    ItemShape.MUSIC_BOX -> Color(0xFFB58A6A)
    ItemShape.PERISCOPE -> Color(0xFF7FA7C9)
    ItemShape.FLYER -> Color(0xFFE88B7A)
    ItemShape.RADIO -> Color(0xFF8C9B7A)
    ItemShape.FRIEND -> Color(0xFFDDE3EB)
    ItemShape.HAT -> Color(0xFFC99BFF)
    else -> Color(0xFFB8C2CC)
}

/** Draws an item centred at [c], roughly [s] px wide. */
fun DrawScope.drawItem(shape: ItemShape, c: Offset, s: Float) {
    val col = shapeColor(shape)
    val dark = Color(0xFF2B3342)
    val u = s / 10f
    fun o(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
    when (shape) {
        ItemShape.SCREW -> rotate(30f, c) {
            drawRoundRect(col, o(-1.2f, -3f), Size(2.4f * u, 7f * u), CornerRadius(0.6f * u))
            drawRoundRect(col, o(-3f, -4.5f), Size(6f * u, 2f * u), CornerRadius(0.8f * u))
            for (i in 0..2) drawLine(dark.copy(alpha = 0.4f), o(-1.2f, -1f + i * 1.8f), o(1.2f, -0.2f + i * 1.8f), 0.4f * u)
        }
        ItemShape.BATTERY -> {
            drawRoundRect(col, o(-2.5f, -4f), Size(5f * u, 8.5f * u), CornerRadius(1f * u))
            drawRect(dark, o(-1f, -5f), Size(2f * u, 1.2f * u))
            drawRect(Color.White.copy(alpha = 0.6f), o(-2.5f, -0.5f), Size(5f * u, 1.2f * u))
        }
        ItemShape.COIL -> for (i in 0..4) drawOval(col, o(-4f + i * 1.6f, -3f), Size(1.8f * u, 6f * u), style = Stroke(0.7f * u))
        ItemShape.CHIP -> {
            for (i in 0..3) {
                drawLine(Color(0xFFD0D6DD), o(-4.2f, -2.4f + i * 1.6f), o(4.2f, -2.4f + i * 1.6f), 0.5f * u)
            }
            drawRoundRect(col, o(-3f, -3.3f), Size(6f * u, 6.6f * u), CornerRadius(0.6f * u))
            drawCircle(Color(0xFFD9E7DD), 0.6f * u, o(-1.8f, -2f))
        }
        ItemShape.SPOON -> {
            drawOval(col, o(-4.5f, -2f), Size(4f * u, 3f * u))
            val p = Path().apply { moveTo(c.x - 1f * u, c.y - 0.5f * u); quadraticBezierTo(c.x + 2f * u, c.y - 2f * u, c.x + 4.5f * u, c.y + 2.5f * u) }
            drawPath(p, col, style = Stroke(1.1f * u, cap = StrokeCap.Round))
        }
        ItemShape.PEBBLE -> {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.6f), Color.Transparent), center = c, radius = 6f * u), 6f * u, c)
            drawOval(col, o(-3.5f, -2.5f), Size(7f * u, 5f * u))
        }
        ItemShape.KEY -> {
            drawCircle(col, 2f * u, o(-3f, 0f), style = Stroke(1f * u))
            drawLine(col, o(-1f, 0f), o(4.5f, 0f), 1f * u)
            drawLine(col, o(3f, 0f), o(3f, 1.8f), 0.9f * u); drawLine(col, o(4.3f, 0f), o(4.3f, 1.5f), 0.9f * u)
        }
        ItemShape.GEAR -> {
            for (i in 0 until 8) {
                val a = i * Math.PI.toFloat() / 4f
                drawLine(col, c, Offset(c.x + cos(a) * 4.4f * u, c.y + sin(a) * 4.4f * u), 1.4f * u)
            }
            drawCircle(col, 3.3f * u, c)
            drawCircle(dark, 1.1f * u, c)
        }
        ItemShape.MARBLE -> {
            drawCircle(col, 3.5f * u, c)
            drawCircle(Color.White.copy(alpha = 0.3f), 2f * u, o(0.6f, 0.6f), style = Stroke(0.5f * u))
            drawCircle(Color.White.copy(alpha = 0.8f), 0.8f * u, o(-1.3f, -1.3f))
        }
        ItemShape.NOTE -> {
            drawRect(col, o(-3.5f, -3.5f), Size(7f * u, 7f * u))
            val fold = Path().apply { moveTo(c.x + 1.5f * u, c.y + 3.5f * u); lineTo(c.x + 3.5f * u, c.y + 1.5f * u); lineTo(c.x + 3.5f * u, c.y + 3.5f * u); close() }
            drawPath(fold, Color(0xFFD9BE55))
        }
        ItemShape.BUTTON -> {
            drawRoundRect(Color(0xFF3B4658), o(-4f, 0f), Size(8f * u, 3f * u), CornerRadius(0.8f * u))
            drawOval(col, o(-2.8f, -2.5f), Size(5.6f * u, 4f * u))
        }
        ItemShape.SPRING -> {
            val p = Path().apply {
                moveTo(c.x - 4f * u, c.y)
                for (i in 0..7) lineTo(c.x - 3.5f * u + i * u, c.y + (if (i % 2 == 0) -2.5f else 2.5f) * u)
                lineTo(c.x + 4f * u, c.y)
            }
            drawPath(p, col, style = Stroke(0.8f * u, cap = StrokeCap.Round))
        }
        ItemShape.LENS -> {
            drawCircle(col.copy(alpha = 0.5f), 3.5f * u, c)
            drawCircle(Color(0xFF3B4658), 3.8f * u, c, style = Stroke(0.9f * u))
            drawLine(Color.White.copy(alpha = 0.9f), o(-2f, -2.5f), o(1.5f, 2f), 0.4f * u)
        }
        ItemShape.TUBE -> {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), center = o(0f, -1f), radius = 5f * u), 5f * u, o(0f, -1f))
            drawRoundRect(Color.White.copy(alpha = 0.45f), o(-2.2f, -5f), Size(4.4f * u, 7.5f * u), CornerRadius(2.2f * u))
            drawRect(Color(0xFF3B4658), o(-2.4f, 2.5f), Size(4.8f * u, 2f * u))
            drawLine(col, o(0f, -3f), o(0f, 1.5f), 0.6f * u)
        }
        ItemShape.FUZZ -> {
            for (i in 0 until 7) {
                val a = i * 0.9f
                drawCircle(col, 1.9f * u, Offset(c.x + cos(a) * 2f * u, c.y + sin(a) * 1.6f * u))
            }
            drawCircle(dark, 0.45f * u, o(-0.9f, -0.2f)); drawCircle(dark, 0.45f * u, o(0.9f, -0.2f))
        }
        ItemShape.BOX -> {
            drawRect(col, o(-4f, -2.5f), Size(8f * u, 6f * u))
            drawRect(Color(0xFF7A5C42), o(-4.3f, -3.5f), Size(8.6f * u, 1.6f * u))
            drawCircle(dark, 0.6f * u, o(0f, 0.6f))
        }
        ItemShape.ANTENNA -> {
            drawLine(Color(0xFF8E99A8), o(0f, 4.5f), o(0f, -2f), 0.9f * u)
            drawOval(Color(0xFFC9D1DA), o(-3.5f, -5f), Size(7f * u, 3.4f * u))
            for (i in 0..2) drawOval(col, o(-1.2f, 0f + i * 1.3f), Size(2.4f * u, 1.1f * u), style = Stroke(0.5f * u))
        }
        ItemShape.LAMP -> {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.6f), Color.Transparent), center = o(0f, -1f), radius = 7f * u), 7f * u, o(0f, -1f))
            drawLine(Color(0xFF8E99A8), o(0f, 4f), o(0f, -1f), 0.8f * u)
            val shade = Path().apply { moveTo(c.x - 1.5f * u, c.y - 4.5f * u); lineTo(c.x + 1.5f * u, c.y - 4.5f * u); lineTo(c.x + 3.5f * u, c.y - 1f * u); lineTo(c.x - 3.5f * u, c.y - 1f * u); close() }
            drawPath(shade, col)
            drawRoundRect(Color(0xFF3B4658), o(-2.5f, 4f), Size(5f * u, 1f * u), CornerRadius(0.5f * u))
        }
        ItemShape.MUSIC_BOX -> {
            drawRoundRect(col, o(-4f, -1f), Size(8f * u, 5f * u), CornerRadius(0.8f * u))
            drawLine(Color(0xFFC9D1DA), o(4f, 1f), o(5.5f, -0.5f), 0.6f * u)
            drawCircle(Color(0xFF8FF5E2), 0.9f * u, o(-0.5f, -3.5f)); drawLine(Color(0xFF8FF5E2), o(0.3f, -3.5f), o(0.3f, -6f), 0.4f * u)
        }
        ItemShape.PERISCOPE -> {
            drawRect(col, o(-1f, -4f), Size(2f * u, 8f * u))
            drawRect(col, o(-1f, -4.5f), Size(4f * u, 2f * u)); drawRect(col, o(-3f, 2.5f), Size(4f * u, 2f * u))
            drawCircle(Color(0xFFA9D8F0), 0.7f * u, o(3f, -3.5f))
        }
        ItemShape.FLYER -> {
            drawOval(col, o(-3.5f, -1f), Size(7f * u, 3.5f * u))
            drawLine(Color(0xFF3B4658), o(0f, -1f), o(0f, -3f), 0.5f * u)
            drawLine(Color(0xFFC9D1DA), o(-4f, -3.2f), o(4f, -2.8f), 0.7f * u, StrokeCap.Round)
        }
        ItemShape.RADIO -> {
            drawRoundRect(col, o(-4f, -2f), Size(8f * u, 5.5f * u), CornerRadius(1f * u))
            drawCircle(Color(0xFF3B4658), 1.4f * u, o(-1.5f, 0.8f)); drawRect(Color(0xFFFFC27A), o(1f, -0.5f), Size(2f * u, 1f * u))
            drawLine(Color(0xFF8E99A8), o(2.5f, -2f), o(4.5f, -5.5f), 0.4f * u)
        }
        ItemShape.FRIEND -> {
            drawRoundRect(col, o(-3.5f, -3f), Size(7f * u, 5.5f * u), CornerRadius(2f * u))
            drawRoundRect(PipoColors.screen, o(-2.6f, -2.2f), Size(5.2f * u, 3.6f * u), CornerRadius(1.3f * u))
            drawCircle(PipoColors.eye, 0.5f * u, o(-1f, -0.5f)); drawCircle(PipoColors.eye, 0.5f * u, o(1f, -0.5f))
            drawLine(PipoColors.joint, o(0f, -3f), o(0f, -4.5f), 0.4f * u); drawCircle(Color(0xFFFFC27A), 0.6f * u, o(0f, -4.8f))
        }
        ItemShape.HAT -> {
            val p = Path().apply { moveTo(c.x, c.y - 5f * u); lineTo(c.x + 3.5f * u, c.y + 3f * u); lineTo(c.x - 3.5f * u, c.y + 3f * u); close() }
            drawPath(p, col)
            drawCircle(Color(0xFFFFC27A), 1f * u, o(0f, -5f))
        }
    }
}
