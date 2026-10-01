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
import com.pipo.robot.engine.Festival
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** What Pipo / Nib wear for the occasion. */
enum class Hat { SANTA, WITCH, PARTY, FLOWERS, CAP, PAINT, ANTLERS, PUMPKIN }

fun hatFor(f: Festival?, pet: Boolean): Hat? = when (f) {
    Festival.CHRISTMAS -> if (pet) Hat.ANTLERS else Hat.SANTA
    Festival.HALLOWEEN -> if (pet) Hat.PUMPKIN else Hat.WITCH
    Festival.NEW_YEAR -> Hat.PARTY
    Festival.DIWALI, Festival.ONAM, Festival.VISHU, Festival.PONGAL -> Hat.FLOWERS
    Festival.HOLI -> Hat.PAINT
    Festival.EID -> if (pet) null else Hat.CAP
    null -> null
}

/** String-light colours for the season. */
fun festivalBulbs(f: Festival?): List<Color>? = when (f) {
    Festival.CHRISTMAS -> listOf(Color(0xFFE0453A), Color(0xFF3FAF5A), Color(0xFFFFD27A), Color(0xFF4F8DE0))
    Festival.HALLOWEEN -> listOf(Color(0xFFFF8A2A), Color(0xFF9B59D0), Color(0xFFFF8A2A), Color(0xFF7CE07C))
    Festival.DIWALI, Festival.EID -> listOf(Color(0xFFFFC94A), Color(0xFFFFA23A), Color(0xFFFFE08A))
    Festival.HOLI -> listOf(Color(0xFFFF5FA2), Color(0xFF4FD07A), Color(0xFFFFD34A), Color(0xFF5FA8FF))
    else -> null
}

/** A hat on a head whose top-centre is [top] and width [w]. */
fun DrawScope.drawHat(hat: Hat, top: Offset, w: Float, t: Float) {
    val k = w / 64f
    when (hat) {
        Hat.SANTA -> {
            val tip = Offset(top.x + 22f * k + sin(t * 1.5f) * 2f * k, top.y - 26f * k)
            drawPath(Path().apply { moveTo(top.x - 26f * k, top.y + 3f * k); quadraticBezierTo(top.x - 6f * k, top.y - 30f * k, tip.x, tip.y); lineTo(top.x + 26f * k, top.y + 3f * k); close() }, Color(0xFFD9363E))
            drawRoundRect(Color(0xFFF4F1EA), Offset(top.x - 29f * k, top.y - 2f * k), Size(58f * k, 9f * k), CornerRadius(4.5f * k))
            drawCircle(Color(0xFFF4F1EA), 5f * k, tip)
        }
        Hat.WITCH -> {
            drawOval(Color(0xFF3A2A55), Offset(top.x - 38f * k, top.y - 3f * k), Size(76f * k, 10f * k))
            drawPath(Path().apply { moveTo(top.x - 19f * k, top.y + 1f * k); lineTo(top.x + 4f * k + sin(t) * 2f * k, top.y - 38f * k); lineTo(top.x + 19f * k, top.y + 1f * k); close() }, Color(0xFF4A3570))
            drawRect(Color(0xFFFF8A2A), Offset(top.x - 17f * k, top.y - 5f * k), Size(34f * k, 4f * k))
        }
        Hat.PARTY -> {
            drawPath(Path().apply { moveTo(top.x - 13f * k, top.y + 2f * k); lineTo(top.x + 2f * k, top.y - 30f * k); lineTo(top.x + 15f * k, top.y + 2f * k); close() }, Color(0xFF5FA8FF))
            for (i in 0..2) drawLine(Color(0xFFFFD34A), Offset(top.x - 10f * k + i * 4f * k, top.y - 2f * k - i * 7f * k), Offset(top.x + 12f * k - i * 4f * k, top.y - 2f * k - i * 7f * k), 2f * k)
            drawCircle(Color(0xFFFF5FA2), 4f * k, Offset(top.x + 2f * k, top.y - 31f * k))
        }
        Hat.FLOWERS -> for (i in 0 until 9) {
            val f = i / 8f
            val c = Offset(top.x - 27f * k + f * 54f * k, top.y + 4f * k - sin(f * PI.toFloat()) * 5f * k)
            drawCircle(if (i % 2 == 0) Color(0xFFFF9F1C) else Color(0xFFFFD23F), 4.2f * k, c)
            drawCircle(Color(0xFFB5651D), 1.3f * k, c)
        }
        Hat.CAP -> {
            drawArc(Color(0xFFF2EFE8), 180f, 180f, true, Offset(top.x - 20f * k, top.y - 9f * k), Size(40f * k, 22f * k))
            for (i in 0..3) drawLine(Color(0xFFD9D2C2), Offset(top.x - 16f * k + i * 10f * k, top.y - 1f * k), Offset(top.x - 14f * k + i * 10f * k, top.y - 6f * k), 1.2f * k)
        }
        Hat.PAINT -> {
            drawCircle(Color(0xFFFF5FA2).copy(alpha = 0.8f), 8f * k, Offset(top.x - 14f * k, top.y + 10f * k))
            drawCircle(Color(0xFF4FD07A).copy(alpha = 0.75f), 6f * k, Offset(top.x + 16f * k, top.y + 6f * k))
            drawCircle(Color(0xFFFFD34A).copy(alpha = 0.75f), 4f * k, Offset(top.x + 4f * k, top.y + 3f * k))
        }
        Hat.ANTLERS -> for (side in listOf(-1f, 1f)) {
            val b = Offset(top.x + side * 12f * k, top.y + 2f * k)
            val tip = Offset(b.x + side * 8f * k, b.y - 22f * k)
            drawLine(Color(0xFF8A5A3A), b, tip, 3f * k, StrokeCap.Round)
            drawLine(Color(0xFF8A5A3A), Offset((b.x + tip.x) / 2f, (b.y + tip.y) / 2f), Offset((b.x + tip.x) / 2f + side * 9f * k, (b.y + tip.y) / 2f - 5f * k), 2.6f * k, StrokeCap.Round)
        }
        Hat.PUMPKIN -> {
            val c = Offset(top.x, top.y - 6f * k)
            drawOval(Color(0xFFFF8A2A), Offset(c.x - 14f * k, c.y - 8f * k), Size(28f * k, 16f * k))
            drawLine(Color(0xFFD96A12), Offset(c.x, c.y - 8f * k), Offset(c.x, c.y + 8f * k), 1.5f * k)
            drawLine(Color(0xFF3FAF5A), Offset(c.x, c.y - 8f * k), Offset(c.x + 3f * k, c.y - 13f * k), 2.5f * k, StrokeCap.Round)
        }
    }
}

/** A little oil lamp with a living flame. */
private fun DrawScope.diya(c: Offset, s: Float, t: Float, ph: Float) {
    drawArc(Color(0xFFB5562A), 0f, 180f, true, Offset(c.x - s, c.y - s * 0.55f), Size(s * 2f, s * 1.1f))
    val fl = 1f + 0.15f * sin(t * 9f + ph) + 0.08f * sin(t * 23f + ph * 2f)
    val fc = Offset(c.x, c.y - s * 0.6f * fl)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.45f), Color.Transparent), fc, s * 3f), s * 3f, fc)
    drawOval(Color(0xFFFFB13B), Offset(fc.x - s * 0.28f, fc.y - s * 0.6f * fl), Size(s * 0.56f, s * 0.9f * fl))
    drawOval(Color(0xFFFFF2B0), Offset(fc.x - s * 0.13f, fc.y - s * 0.35f * fl), Size(s * 0.26f, s * 0.5f * fl))
}

/** A jack-o'-lantern whose face flickers. */
private fun DrawScope.pumpkin(c: Offset, s: Float, t: Float) {
    for (i in -1..1) drawOval(Color(if (i == 0) 0xFFFF8A2A else 0xFFE8761E), Offset(c.x - s * 0.55f + i * s * 0.35f, c.y - s * 0.75f), Size(s * 1.1f, s * 1.5f))
    drawLine(Color(0xFF3FAF5A), Offset(c.x, c.y - s * 0.75f), Offset(c.x + s * 0.15f, c.y - s * 1.05f), s * 0.18f, StrokeCap.Round)
    val glow = Color(0xFFFFE08A).copy(alpha = 0.75f + 0.25f * sin(t * 11f))
    drawPath(Path().apply { moveTo(c.x - s * 0.5f, c.y - s * 0.2f); lineTo(c.x - s * 0.3f, c.y - s * 0.45f); lineTo(c.x - s * 0.15f, c.y - s * 0.2f); close() }, glow)
    drawPath(Path().apply { moveTo(c.x + s * 0.15f, c.y - s * 0.2f); lineTo(c.x + s * 0.3f, c.y - s * 0.45f); lineTo(c.x + s * 0.5f, c.y - s * 0.2f); close() }, glow)
    drawArc(glow, 0f, 180f, true, Offset(c.x - s * 0.45f, c.y - s * 0.15f), Size(s * 0.9f, s * 0.5f))
}

/** A pookalam / rangoli: concentric rings of colour on the floor, seen in perspective. */
private fun DrawScope.floorMandala(c: Offset, r: Float, cols: List<Color>, t: Float) {
    for ((i, col) in cols.withIndex()) {
        val rr = r * (1f - i / cols.size.toFloat())
        drawOval(col, Offset(c.x - rr, c.y - rr * 0.28f), Size(rr * 2f, rr * 0.56f))
    }
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f + t * 0.05f
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.06f, Offset(c.x + cos(a) * r * 0.62f, c.y + sin(a) * r * 0.62f * 0.28f))
    }
}

/**
 * The room dressed for the occasion. [X]/[Y] map world units to pixels (Y is height above the
 * floor), [fy] is the floor line, [u] a world unit in px.
 */
fun DrawScope.drawFestivalRoom(f: Festival, X: (Float) -> Float, Y: (Float) -> Float, fy: Float, u: Float, t: Float, night: Float) {
    when (f) {
        Festival.CHRISTMAS -> {
            // the tree, between the charger and the TV, a bit lopsided (he made it himself)
            val bx = X(101f)
            drawRect(Color(0xFF7A5638), Offset(bx - 1.2f * u, fy - 4f * u), Size(2.4f * u, 4f * u))
            for (i in 0 until 4) {
                val w = (11f - i * 2.4f) * u; val y0 = fy - (4f + i * 6f) * u
                drawPath(Path().apply { moveTo(bx - w / 2f, y0); lineTo(bx + 0.4f * i * u, y0 - 9f * u); lineTo(bx + w / 2f, y0); close() }, Color(0xFF2F8A4A))
            }
            val cols = listOf(Color(0xFFE0453A), Color(0xFFFFD27A), Color(0xFF4F8DE0), Color(0xFFFF8FD0))
            for (i in 0 until 10) {
                val yy = fy - (6f + (i * 7 % 20)) * u; val xx = bx + ((i * 37 % 9) - 4f) * u * (1f - (fy - yy) / (30f * u))
                val on = 0.55f + 0.45f * sin(t * 2.2f + i * 1.7f)
                drawCircle(cols[i % 4].copy(alpha = on), 0.75f * u, Offset(xx, yy))
            }
            val star = Offset(bx + 1.6f * u, fy - 31.5f * u)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE08A).copy(alpha = 0.6f), Color.Transparent), star, 4f * u), 4f * u, star)
            drawCircle(Color(0xFFFFE08A), 1.2f * u, star)
            // presents under it
            drawRect(Color(0xFFE0453A), Offset(bx - 7f * u, fy - 3f * u), Size(4f * u, 3f * u)); drawRect(Color(0xFFFFD27A), Offset(bx - 5.3f * u, fy - 3f * u), Size(0.6f * u, 3f * u))
            drawRect(Color(0xFF4F8DE0), Offset(bx + 3.5f * u, fy - 2.5f * u), Size(3.5f * u, 2.5f * u))
            // wreath on the door
            val wc = Offset(X(286f), Y(40f))
            drawCircle(Color(0xFF2F8A4A), 3.2f * u, wc, style = Stroke(1.6f * u))
            drawCircle(Color(0xFFE0453A), 0.7f * u, Offset(wc.x, wc.y + 3f * u))
            // stockings on the shelf edge
            for (i in 0..1) drawRoundRect(if (i == 0) Color(0xFFE0453A) else Color(0xFF2F8A4A), Offset(X(168f + i * 20f), Y(51f)), Size(2.4f * u, 4.5f * u), CornerRadius(1f * u))
        }
        Festival.HALLOWEEN -> {
            pumpkin(Offset(X(101f), fy - 0.5f * u), 4.5f * u, t)
            pumpkin(Offset(X(274f), fy - 0.5f * u), 3.2f * u, t + 1.3f)
            // paper bats across the wall, swaying on threads
            for (i in 0 until 7) {
                val bx = X(20f + i * 38f); val by = Y(82f + (i % 2) * 4f) + sin(t * 1.2f + i) * 0.8f * u
                drawLine(Color.White.copy(alpha = 0.2f), Offset(bx, Y(96f)), Offset(bx, by), 0.2f * u)
                drawPath(Path().apply {
                    moveTo(bx, by); quadraticBezierTo(bx - 2.5f * u, by - 2f * u, bx - 4.5f * u, by - 0.5f * u); quadraticBezierTo(bx - 2.5f * u, by + 0.2f * u, bx, by + 1.2f * u)
                    quadraticBezierTo(bx + 2.5f * u, by + 0.2f * u, bx + 4.5f * u, by - 0.5f * u); quadraticBezierTo(bx + 2.5f * u, by - 2f * u, bx, by)
                }, Color(0xFF231A2E))
            }
            // a cobweb in the corner over the bed
            val cw = Offset(X(2f), Y(100f))
            for (i in 0..4) drawLine(Color.White.copy(alpha = 0.25f), cw, Offset(cw.x + cos(i * 0.39f) * 12f * u, cw.y + sin(i * 0.39f) * 12f * u), 0.2f * u)
            for (r in 1..3) drawArc(Color.White.copy(alpha = 0.2f), 0f, 90f, false, Offset(cw.x - r * 4f * u, cw.y - r * 4f * u), Size(r * 8f * u, r * 8f * u), style = Stroke(0.2f * u))
        }
        Festival.DIWALI -> {
            // a row of lamps on the floor under the window, and two by the door
            for (i in 0 until 7) diya(Offset(X(88f + i * 5.3f), fy + 1.5f * u), 1.3f * u, t, i * 1.7f)
            diya(Offset(X(280f), fy + 1.5f * u), 1.3f * u, t, 9f); diya(Offset(X(292f), fy + 1.5f * u), 1.3f * u, t, 11f)
            // rangoli in front of the door
            floorMandala(Offset(X(286f), fy + 6f * u), 8f * u, listOf(Color(0xFFE0453A), Color(0xFFFFD23F), Color(0xFF3FAF5A), Color(0xFF9B59D0), Color(0xFFFFFFFF)), t)
            // marigold toran over the door
            for (i in 0 until 9) drawCircle(if (i % 2 == 0) Color(0xFFFF9F1C) else Color(0xFFFFD23F), 1.1f * u, Offset(X(279f + i * 1.75f), Y(58f) + sin(i / 8f * PI.toFloat()) * 2f * u))
            // paper lantern (akash kandil) hanging by the window
            val lc = Offset(X(126f), Y(80f) + sin(t) * 0.5f * u)
            drawLine(Color.White.copy(alpha = 0.3f), Offset(lc.x, Y(96f)), lc, 0.2f * u)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC94A).copy(alpha = 0.4f + 0.3f * night), Color.Transparent), lc, 8f * u), 8f * u, lc)
            drawPath(Path().apply { moveTo(lc.x, lc.y - 3f * u); lineTo(lc.x + 3f * u, lc.y); lineTo(lc.x, lc.y + 3f * u); lineTo(lc.x - 3f * u, lc.y); close() }, Color(0xFFFF7A3A))
        }
        Festival.ONAM -> {
            floorMandala(Offset(X(104f), fy + 5f * u), 12f * u, listOf(Color(0xFFFFD23F), Color(0xFFFF9F1C), Color(0xFFE0453A), Color(0xFFFFFFFF), Color(0xFF9B59D0), Color(0xFF3FAF5A)), t)
            // a nilavilakku (brass lamp) beside it
            val lb = Offset(X(122f), fy)
            drawRect(Color(0xFFC9A23A), Offset(lb.x - 0.5f * u, lb.y - 9f * u), Size(1f * u, 9f * u))
            drawOval(Color(0xFFC9A23A), Offset(lb.x - 2.5f * u, lb.y - 1f * u), Size(5f * u, 1.5f * u))
            diya(Offset(lb.x, lb.y - 9f * u), 1.2f * u, t, 3f)
        }
        Festival.VISHU -> {
            // the kani: a bronze uruli with golden konna flowers, fruit, and a lamp
            val c = Offset(X(101f), fy)
            drawArc(Color(0xFFB8862E), 0f, 180f, true, Offset(c.x - 6f * u, c.y - 3f * u), Size(12f * u, 5f * u))
            for (i in 0 until 9) drawCircle(Color(0xFFFFD23F), 1f * u, Offset(c.x - 4f * u + (i % 5) * 2f * u, c.y - 3.5f * u - (i / 5) * 1.5f * u))
            drawCircle(Color(0xFFE0453A), 1.4f * u, Offset(c.x + 4.5f * u, c.y - 3.5f * u))
            diya(Offset(X(110f), fy + 1f * u), 1.2f * u, t, 2f)
        }
        Festival.PONGAL -> {
            // the pot on the hot plate, boiling over (on purpose), and a kolam on the floor
            val pc = Offset(X(262f), Y(26f))
            drawOval(Color.White.copy(alpha = 0.85f), Offset(pc.x - 3f * u, pc.y - 2f * u + sin(t * 3f) * 0.3f * u), Size(6f * u, 2.5f * u))
            floorMandala(Offset(X(262f), fy + 5f * u), 7f * u, listOf(Color.White, Color(0xFFFFD23F), Color.White), t)
        }
        Festival.HOLI -> {
            val cols = listOf(Color(0xFFFF5FA2), Color(0xFF4FD07A), Color(0xFFFFD34A), Color(0xFF5FA8FF), Color(0xFFB36BFF))
            for (i in 0 until 14) {
                val c = Offset(X(10f + i * 20f + (i * 7 % 9)), Y(10f + (i * 13 % 70)))
                drawCircle(cols[i % cols.size].copy(alpha = 0.45f), (2f + (i % 3)) * u, c)
                drawCircle(cols[i % cols.size].copy(alpha = 0.35f), 1f * u, Offset(c.x + 3f * u, c.y + 2f * u))
            }
        }
        Festival.EID -> {
            for (i in 0 until 4) {
                val lc = Offset(X(30f + i * 70f), Y(82f + (i % 2) * 4f) + sin(t + i) * 0.4f * u)
                drawLine(Color.White.copy(alpha = 0.3f), Offset(lc.x, Y(96f)), lc, 0.2f * u)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC94A).copy(alpha = 0.35f + 0.35f * night), Color.Transparent), lc, 7f * u), 7f * u, lc)
                drawRoundRect(Color(0xFFC9A23A), Offset(lc.x - 1.8f * u, lc.y - 2.5f * u), Size(3.6f * u, 5f * u), CornerRadius(1f * u))
                drawRoundRect(Color(0xFFFFE08A), Offset(lc.x - 1.1f * u, lc.y - 1.6f * u), Size(2.2f * u, 3.2f * u), CornerRadius(0.6f * u))
            }
            // crescent and star on the wall by the window
            val mc = Offset(X(130f), Y(86f))
            drawCircle(Color(0xFFFFE08A), 2.6f * u, mc); drawCircle(Color(0xFF26304A).copy(alpha = 0.95f), 2.3f * u, Offset(mc.x + 1.1f * u, mc.y - 0.4f * u))
            drawCircle(Color(0xFFFFE08A), 0.6f * u, Offset(mc.x + 3.4f * u, mc.y - 1.2f * u))
        }
        Festival.NEW_YEAR -> {
            val cols = listOf(Color(0xFFFF5FA2), Color(0xFF5FA8FF), Color(0xFFFFD34A), Color(0xFF4FD07A))
            for (i in 0 until 40) drawRect(cols[i % 4], Offset(X((i * 53 % 290).toFloat()), fy + ((i * 29 % 9)) * u), Size(0.8f * u, 0.5f * u))
            // streamers from the ceiling
            for (i in 0 until 6) {
                val x0 = X(25f + i * 48f)
                drawPath(Path().apply { moveTo(x0, Y(98f)); quadraticBezierTo(x0 + 3f * u + sin(t + i) * u, Y(92f), x0, Y(86f)); quadraticBezierTo(x0 - 3f * u, Y(82f), x0 + 1f * u, Y(78f)) }, cols[i % 4], style = Stroke(0.5f * u))
            }
        }
    }
}

/** The same decorations outside (garden, park), and on the other side — in the wrong colours. */
fun DrawScope.drawFestivalOutside(f: Festival, gy: Float, night: Float, t: Float, mirror: Boolean) {
    val w = size.width; val h = size.height
    fun c(col: Color) = if (mirror) Color(1f - col.red * 0.6f, col.blue, col.green, col.alpha) else col
    when (f) {
        Festival.CHRISTMAS -> for (i in 0 until 16) { // lights strung between the trees
            val x = w * (0.08f + i * 0.055f); val y = gy - h * (0.3f - abs(i - 7.5f) * 0.01f)
            drawCircle(c(listOf(Color(0xFFE0453A), Color(0xFF3FAF5A), Color(0xFFFFD27A))[i % 3]).copy(alpha = 0.6f + 0.4f * sin(t * 3f + i)), h * 0.008f, Offset(x, y))
        }
        Festival.HALLOWEEN -> for (i in 0 until 3) pumpkin(Offset(w * (0.12f + i * 0.33f), gy + h * 0.05f), h * 0.035f, t + i)
        Festival.DIWALI, Festival.VISHU -> for (i in 0 until 9) diya(Offset(w * (0.06f + i * 0.11f), gy + h * 0.13f), h * 0.012f, t, i * 1.3f)
        Festival.ONAM, Festival.PONGAL -> floorMandala(Offset(w * 0.5f, gy + h * 0.15f), w * 0.16f, listOf(Color(0xFFFFD23F), Color(0xFFFF9F1C), Color(0xFFE0453A), Color.White, Color(0xFF9B59D0)).map { c(it) }, t)
        Festival.HOLI -> for (i in 0 until 18) {
            val ph = (t * 0.15f + i * 0.13f) % 1f
            drawCircle(c(listOf(Color(0xFFFF5FA2), Color(0xFF4FD07A), Color(0xFFFFD34A), Color(0xFF5FA8FF))[i % 4]).copy(alpha = 0.35f * (1f - ph)), h * (0.02f + ph * 0.05f), Offset(w * ((i * 0.17f) % 1f), gy - h * (0.1f + ph * 0.3f)))
        }
        Festival.EID -> for (i in 0 until 5) {
            val lc = Offset(w * (0.1f + i * 0.2f), gy - h * 0.35f + sin(t + i) * h * 0.005f)
            drawCircle(Brush.radialGradient(listOf(c(Color(0xFFFFC94A)).copy(alpha = 0.5f), Color.Transparent), lc, h * 0.05f), h * 0.05f, lc)
            drawRoundRect(c(Color(0xFFC9A23A)), Offset(lc.x - h * 0.012f, lc.y - h * 0.018f), Size(h * 0.024f, h * 0.036f), CornerRadius(h * 0.006f))
        }
        Festival.NEW_YEAR -> if (night > 0.4f) for (b in 0 until 3) { // fireworks
            val ph = ((t * 0.35f + b * 0.33f) % 1f)
            val cc = Offset(w * (0.25f + b * 0.25f), h * (0.18f + (b % 2) * 0.08f))
            val col = c(listOf(Color(0xFFFF5FA2), Color(0xFFFFD34A), Color(0xFF5FA8FF))[b])
            for (i in 0 until 12) { val a = i * PI.toFloat() / 6f; drawCircle(col.copy(alpha = (1f - ph)), h * 0.005f, Offset(cc.x + cos(a) * ph * h * 0.12f, cc.y + sin(a) * ph * h * 0.12f + ph * ph * h * 0.03f)) }
        }
    }
}
