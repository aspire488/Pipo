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

private val shapeColors: Map<ItemShape, Color> = ItemShape.entries.associateWith { s ->
    Catalog.items.firstOrNull { it.shape == s }?.let { Color(it.color) } ?: when (s) {
        ItemShape.ANTENNA -> Color(0xFFE0975C)
        ItemShape.LAMP -> Color(0xFFFFC27A)
        ItemShape.MUSIC_BOX -> Color(0xFFB58A6A)
        ItemShape.PERISCOPE -> Color(0xFF7FA7C9)
        ItemShape.FLYER -> Color(0xFFE88B7A)
        ItemShape.RADIO -> Color(0xFF8C9B7A)
        ItemShape.FRIEND -> Color(0xFFDDE3EB)
        ItemShape.HAT -> Color(0xFFC99BFF)
        ItemShape.DRONE -> Color(0xFFE88B7A)
        ItemShape.KICKER -> Color(0xFF8FA3A6)
        ItemShape.FEEDER -> Color(0xFFC9A57A)
        ItemShape.TELESCOPE -> Color(0xFF6F8FA6)
        ItemShape.BAG -> Color(0xFFD8C4A0)
        ItemShape.BOWL, ItemShape.PLATE -> Color(0xFFF2ECE2)
        ItemShape.SKETCHBOOK -> Color(0xFFE0706A)
        ItemShape.CAMERA -> Color(0xFF2C3444)
        ItemShape.NOODLES -> Color(0xFFF3D98A)
        ItemShape.BREAD -> Color(0xFFD9A35F)
        ItemShape.APPLE -> Color(0xFFE0605A)
        ItemShape.BANANA -> Color(0xFFF3D35A)
        ItemShape.CAKE -> Color(0xFFF7C6D0)
        ItemShape.COOKIE -> Color(0xFFC98E55)
        ItemShape.JUICE -> Color(0xFFFFA64D)
        ItemShape.EGG -> Color(0xFFF7F2E6)
        ItemShape.CHEESE -> Color(0xFFF5C84C)
        ItemShape.TOMATO -> Color(0xFFE8503F)
        ItemShape.HONEY -> Color(0xFFE8A93A)
        ItemShape.PIZZA -> Color(0xFFF0B45A)
        ItemShape.MUG -> Color(0xFF8C6A5A)
        ItemShape.RICE -> Color(0xFFF4F1E8)
        ItemShape.CROISSANT -> Color(0xFFE0A55A)
        ItemShape.CHOCOLATE -> Color(0xFF6B4230)
        ItemShape.MILK -> Color(0xFFF2F5F8)
        else -> Color(0xFFB8C2CC)
    }
}

/** Looked up once: the painter calls this every frame for everything on the shelves. */
fun shapeColor(s: ItemShape): Color = shapeColors[s] ?: Color(0xFFB8C2CC)

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
        // ---- bought materials
        ItemShape.MOTOR -> {
            drawRoundRect(col, o(-3.5f, -2.5f), Size(6f * u, 5f * u), CornerRadius(1.2f * u))
            drawRect(Color(0xFFE0975C), o(-3.5f, -1.2f), Size(6f * u, 1f * u))
            drawLine(Color(0xFFC9D1DA), o(2.5f, 0f), o(4.8f, 0f), 0.8f * u)
        }
        ItemShape.WIRE -> {
            drawCircle(col, 3.6f * u, c, style = Stroke(1.4f * u))
            drawCircle(Color(0xFF3B4658), 1.2f * u, c)
            drawLine(col, o(3.2f, 1f), o(5f, 3.5f), 0.5f * u, StrokeCap.Round)
        }
        ItemShape.FRAME -> for (i in 0..3) rotate(-12f + i * 8f, c) { drawRoundRect(col, o(-4.5f, -0.6f + i * 0.9f - 1.5f), Size(9f * u, 0.8f * u), CornerRadius(0.3f * u)) }
        ItemShape.PROP -> {
            rotate(25f, c) { drawOval(col, o(-5f, -1f), Size(10f * u, 2f * u)) }
            drawCircle(dark, 1f * u, c)
        }
        ItemShape.LED -> {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.6f), Color.Transparent), center = o(0f, -1.5f), radius = 5f * u), 5f * u, o(0f, -1.5f))
            drawRoundRect(col, o(-1.6f, -4f), Size(3.2f * u, 4.2f * u), CornerRadius(1.6f * u))
            drawLine(Color(0xFFC9D1DA), o(-0.7f, 0.2f), o(-0.7f, 4f), 0.4f * u); drawLine(Color(0xFFC9D1DA), o(0.7f, 0.2f), o(0.7f, 3.2f), 0.4f * u)
        }
        ItemShape.MAGNET -> {
            val p = Path().apply { moveTo(c.x - 3f * u, c.y + 3f * u); lineTo(c.x - 3f * u, c.y - 1f * u); quadraticBezierTo(c.x - 3f * u, c.y - 4f * u, c.x, c.y - 4f * u)
                quadraticBezierTo(c.x + 3f * u, c.y - 4f * u, c.x + 3f * u, c.y - 1f * u); lineTo(c.x + 3f * u, c.y + 3f * u) }
            drawPath(p, col, style = Stroke(2f * u))
            drawRect(Color(0xFFC9D1DA), o(-4f, 2f), Size(2f * u, 1.5f * u)); drawRect(Color(0xFFC9D1DA), o(2f, 2f), Size(2f * u, 1.5f * u))
        }
        ItemShape.GLUE -> {
            drawRoundRect(Color.White, o(-2f, -2f), Size(4f * u, 6f * u), CornerRadius(1f * u))
            drawRect(col, o(-1f, -4f), Size(2f * u, 2f * u))
        }
        ItemShape.BAT -> rotate(-35f, c) { // a cricket bat
            drawRoundRect(Color(0xFFE2C48E), o(-1.6f, -5f), Size(3.2f * u, 7.5f * u), CornerRadius(0.8f * u))
            drawRoundRect(Color(0xFF3B4658), o(-0.6f, 2.3f), Size(1.2f * u, 3.2f * u), CornerRadius(0.5f * u))
        }
        ItemShape.PADDLE -> { // table tennis
            drawCircle(Color(0xFFD9363E), 3.2f * u, o(0f, -1.5f))
            drawRoundRect(Color(0xFFB98A5A), o(-0.7f, 1.4f), Size(1.4f * u, 3.4f * u), CornerRadius(0.5f * u))
        }
        ItemShape.RACKET -> { // badminton
            drawOval(Color(0xFF3B4658), o(-2.4f, -6f), Size(4.8f * u, 6f * u), style = Stroke(0.6f * u))
            for (i in -1..1) drawLine(Color.White.copy(alpha = 0.6f), o(i * 1.1f, -5.6f), o(i * 1.1f, -0.4f), 0.25f * u)
            drawLine(Color(0xFF3B4658), o(0f, 0f), o(0f, 4.5f), 0.6f * u)
        }
        ItemShape.UMBRELLA -> {
            val p = Path().apply { moveTo(c.x - 5f * u, c.y); quadraticBezierTo(c.x, c.y - 7f * u, c.x + 5f * u, c.y); close() }
            drawPath(p, col)
            drawLine(dark, o(0f, 0f), o(0f, 4.5f), 0.5f * u)
            drawArc(dark, 0f, 180f, false, o(0f, 3.5f), Size(1.6f * u, 1.6f * u), style = Stroke(0.5f * u))
        }
        ItemShape.DUCK -> {
            drawOval(col, o(-4f, -1f), Size(7f * u, 4.5f * u))
            drawCircle(col, 2f * u, o(1.8f, -2.2f))
            drawPath(Path().apply { moveTo(c.x + 3.5f * u, c.y - 2.4f * u); lineTo(c.x + 5.2f * u, c.y - 1.8f * u); lineTo(c.x + 3.5f * u, c.y - 1.3f * u); close() }, Color(0xFFFF8A4C))
            drawCircle(dark, 0.4f * u, o(2.3f, -2.6f))
        }
        ItemShape.TOKEN -> {
            drawCircle(col, 3.8f * u, c)
            drawCircle(lerpC(col, dark, 0.35f), 3.8f * u, c, style = Stroke(0.5f * u))
            drawSymbol(c, 2.4f * u, dark.copy(alpha = 0.7f))
        }
        // ---- things he built (schema 2)
        ItemShape.DRONE -> {
            drawRoundRect(col, o(-2.5f, -1f), Size(5f * u, 2.5f * u), CornerRadius(1f * u))
            drawLine(dark, o(-4.5f, -1.5f), o(4.5f, -1.5f), 0.4f * u)
            drawOval(Color(0xFFC9D1DA), o(-6f, -2.2f), Size(3f * u, 0.8f * u)); drawOval(Color(0xFFC9D1DA), o(3f, -2.2f), Size(3f * u, 0.8f * u))
            drawCircle(PipoColors.eye, 0.5f * u, o(0f, 0.2f))
        }
        ItemShape.KICKER -> {
            drawRect(Color(0xFF8E99A8), o(-4f, 2f), Size(8f * u, 1.5f * u))
            drawLine(col, o(-2f, 2f), o(1f, -3f), 1f * u, StrokeCap.Round)
            drawCircle(Color(0xFFE98A7A), 1.6f * u, o(3f, 0.5f))
        }
        ItemShape.FEEDER -> {
            drawPath(Path().apply { moveTo(c.x - 4f * u, c.y - 1f * u); lineTo(c.x, c.y - 4.5f * u); lineTo(c.x + 4f * u, c.y - 1f * u); close() }, lerpC(col, dark, 0.2f))
            drawRect(col, o(-3f, -1f), Size(6f * u, 4f * u))
            drawCircle(dark, 0.9f * u, o(0f, 0.8f))
            for (i in 0..3) drawCircle(Color(0xFFE8C98A), 0.35f * u, o(-2f + i * 1.3f, 3.4f))
        }
        ItemShape.TELESCOPE -> {
            rotate(-25f, c) {
                drawRoundRect(col, o(-4.5f, -1.2f), Size(8f * u, 2.4f * u), CornerRadius(0.6f * u))
                drawRoundRect(lerpC(col, Color.White, 0.25f), o(3f, -1.6f), Size(2f * u, 3.2f * u), CornerRadius(0.5f * u))
            }
            drawLine(dark, o(0f, 0.5f), o(-2f, 4.5f), 0.4f * u); drawLine(dark, o(0f, 0.5f), o(2f, 4.5f), 0.4f * u)
        }
        // ---- carried / everyday
        ItemShape.BAG -> {
            drawRoundRect(col, o(-4f, -2.5f), Size(8f * u, 7f * u), CornerRadius(0.8f * u))
            drawArc(lerpC(col, dark, 0.4f), 180f, 180f, false, o(-2f, -5f), Size(4f * u, 5f * u), style = Stroke(0.6f * u))
            drawLine(lerpC(col, dark, 0.2f), o(-4f, -1f), o(4f, -1f), 0.3f * u)
        }
        ItemShape.BOWL -> {
            drawArc(col, 0f, 180f, true, o(-4.5f, -3f), Size(9f * u, 7f * u))
            drawOval(Color(0xFFF3D98A), o(-4f, -1.4f), Size(8f * u, 1.6f * u))
            drawLine(Color(0xFFB8604F), o(-1f, -1f), o(1f, -5.5f), 0.5f * u)
        }
        ItemShape.PLATE -> {
            drawOval(col, o(-5f, -0.5f), Size(10f * u, 2.6f * u))
            drawOval(Color(0xFFE8B45A), o(-3f, -2f), Size(6f * u, 2.4f * u))
            drawOval(Color(0xFFE8B45A), o(-2.7f, -3.2f), Size(5.4f * u, 2f * u))
        }
        ItemShape.SKETCHBOOK -> {
            drawRoundRect(col, o(-4.5f, -3f), Size(9f * u, 6f * u), CornerRadius(0.6f * u))
            drawRect(Color(0xFFF4EFE6), o(-4f, -2.6f), Size(8f * u, 5.2f * u))
            drawLine(dark.copy(alpha = 0.5f), o(-2.5f, 0.5f), o(2f, -1.2f), 0.35f * u)
            drawCircle(dark.copy(alpha = 0.5f), 1f * u, o(-1f, -0.5f), style = Stroke(0.3f * u))
        }
        ItemShape.CAMERA -> {
            drawRoundRect(col, o(-2.5f, -4f), Size(5f * u, 8f * u), CornerRadius(0.8f * u))
            drawCircle(Color(0xFF9FE7E0), 0.8f * u, o(0f, -2.5f))
        }
        // ---- food
        ItemShape.NOODLES -> {
            drawArc(Color(0xFFF2ECE2), 0f, 180f, true, o(-4.5f, -3f), Size(9f * u, 7f * u))
            for (i in 0..3) drawArc(col, 180f, 180f, false, o(-3.5f + i * 1.8f, -2.2f), Size(1.8f * u, 1.6f * u), style = Stroke(0.45f * u))
            drawLine(dark, o(1f, -1f), o(3.5f, -5.5f), 0.4f * u); drawLine(dark, o(1.8f, -1f), o(4.3f, -5.2f), 0.4f * u)
        }
        ItemShape.BREAD -> {
            drawRoundRect(col, o(-4f, -2f), Size(8f * u, 4.5f * u), CornerRadius(2f * u))
            for (i in 0..2) drawLine(lerpC(col, Color.White, 0.35f), o(-2f + i * 2f, -1.6f), o(-1.2f + i * 2f, 0.6f), 0.35f * u)
        }
        ItemShape.APPLE -> {
            drawCircle(col, 3.2f * u, o(0f, 0.5f))
            drawLine(Color(0xFF6B4A32), o(0f, -2.5f), o(0.5f, -4f), 0.45f * u)
            drawOval(Color(0xFF6FA27A), o(0.6f, -4.2f), Size(2f * u, 1f * u))
            drawCircle(Color.White.copy(alpha = 0.4f), 0.7f * u, o(-1.2f, -0.6f))
        }
        ItemShape.BANANA -> drawArc(col, 20f, 140f, false, o(-4f, -5f), Size(8f * u, 8f * u), style = Stroke(1.6f * u, cap = StrokeCap.Round))
        ItemShape.CAKE -> {
            drawPath(Path().apply { moveTo(c.x - 4f * u, c.y + 2.5f * u); lineTo(c.x + 4f * u, c.y + 2.5f * u); lineTo(c.x + 4f * u, c.y - 1f * u); lineTo(c.x - 4f * u, c.y - 3f * u); close() }, col)
            drawLine(Color.White, o(-4f, -0.2f), o(4f, 1f), 0.5f * u)
            drawCircle(Color(0xFFE0605A), 0.8f * u, o(-2.5f, -3.4f))
        }
        ItemShape.COOKIE -> {
            drawCircle(col, 3.2f * u, c)
            for ((x, y) in listOf(-1f to -1f, 1.2f to 0.3f, -0.4f to 1.5f)) drawCircle(Color(0xFF5A3A28), 0.5f * u, o(x, y))
        }
        ItemShape.JUICE -> {
            drawRoundRect(col, o(-2.2f, -3f), Size(4.4f * u, 6.5f * u), CornerRadius(0.5f * u))
            drawRect(Color.White.copy(alpha = 0.7f), o(-2.2f, -1f), Size(4.4f * u, 1.6f * u))
            drawLine(Color(0xFFE0706A), o(1f, -3f), o(1.8f, -5.5f), 0.5f * u)
        }
        ItemShape.EGG -> drawOval(col, o(-2.4f, -3.2f), Size(4.8f * u, 6.2f * u))
        ItemShape.CHEESE -> {
            drawPath(Path().apply { moveTo(c.x - 4f * u, c.y + 2f * u); lineTo(c.x + 4f * u, c.y + 2f * u); lineTo(c.x + 4f * u, c.y - 2f * u); close() }, col)
            drawCircle(lerpC(col, dark, 0.2f), 0.6f * u, o(2f, 0.6f)); drawCircle(lerpC(col, dark, 0.2f), 0.4f * u, o(3.2f, -0.6f))
        }
        ItemShape.TOMATO -> {
            drawCircle(col, 3f * u, o(0f, 0.5f))
            for (a in listOf(-40f, 0f, 40f)) rotate(a, o(0f, -2.3f)) { drawOval(Color(0xFF6FA27A), o(-0.4f, -3.4f), Size(0.8f * u, 2f * u)) }
        }
        ItemShape.HONEY -> {
            drawRoundRect(col, o(-3f, -2.5f), Size(6f * u, 5.5f * u), CornerRadius(1.5f * u))
            drawRect(Color(0xFF8C6A5A), o(-2.4f, -3.5f), Size(4.8f * u, 1.3f * u))
            drawRect(Color.White.copy(alpha = 0.6f), o(-2f, -0.6f), Size(4f * u, 1.6f * u))
        }
        ItemShape.PIZZA -> {
            drawPath(Path().apply { moveTo(c.x - 4f * u, c.y - 3f * u); lineTo(c.x + 4f * u, c.y - 3f * u); lineTo(c.x, c.y + 4f * u); close() }, col)
            drawLine(Color(0xFFD9A35F), o(-4f, -3f), o(4f, -3f), 0.9f * u)
            for ((x, y) in listOf(-1.5f to -1.5f, 1.2f to -1.8f, 0f to 0.8f)) drawCircle(Color(0xFFE0605A), 0.6f * u, o(x, y))
        }
        ItemShape.MUG -> {
            drawRoundRect(col, o(-3f, -2.5f), Size(5f * u, 5.5f * u), CornerRadius(0.6f * u))
            drawArc(col, -90f, 180f, false, o(1.2f, -1.5f), Size(2.6f * u, 3f * u), style = Stroke(0.6f * u))
            for (i in 0..1) drawLine(Color.White.copy(alpha = 0.5f), o(-1.5f + i * 1.8f, -3.5f), o(-1f + i * 1.8f, -5.5f), 0.35f * u, StrokeCap.Round)
        }
        ItemShape.RICE -> {
            drawArc(Color(0xFF8FA3A6), 0f, 180f, true, o(-4.5f, -3f), Size(9f * u, 7f * u))
            drawArc(col, 180f, 180f, true, o(-4f, -3f), Size(8f * u, 4f * u))
        }
        ItemShape.CROISSANT -> drawArc(col, 200f, 140f, false, o(-4f, -3f), Size(8f * u, 7f * u), style = Stroke(2.2f * u, cap = StrokeCap.Round))
        ItemShape.CHOCOLATE -> {
            drawRoundRect(col, o(-3.5f, -2f), Size(7f * u, 4.5f * u), CornerRadius(0.4f * u))
            for (i in 1..2) drawLine(lerpC(col, Color.Black, 0.3f), o(-3.5f + i * 2.33f, -2f), o(-3.5f + i * 2.33f, 2.5f), 0.3f * u)
        }
        ItemShape.MILK -> {
            drawRect(col, o(-2.2f, -2f), Size(4.4f * u, 5.5f * u))
            drawPath(Path().apply { moveTo(c.x - 2.2f * u, c.y - 2f * u); lineTo(c.x, c.y - 4f * u); lineTo(c.x + 2.2f * u, c.y - 2f * u); close() }, lerpC(col, dark, 0.1f))
            drawRect(Color(0xFF6FA8E8), o(-2.2f, 0f), Size(4.4f * u, 1.2f * u))
        }
    }
}

private fun lerpC(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)

/**
 * The mark. Three dots in a triangle, one line through them. Original to Pipo's world; it turns up
 * in his dream drawing, on the token, and (much later) on a door.
 */
fun DrawScope.drawSymbol(c: Offset, r: Float, col: Color) {
    val pts = listOf(Offset(c.x, c.y - r * 0.75f), Offset(c.x - r * 0.7f, c.y + r * 0.5f), Offset(c.x + r * 0.7f, c.y + r * 0.5f))
    for (p in pts) drawCircle(col, r * 0.18f, p)
    drawLine(col, Offset(c.x - r * 0.9f, c.y - r * 0.1f), Offset(c.x + r * 0.9f, c.y + r * 0.2f), r * 0.1f, StrokeCap.Round)
}
