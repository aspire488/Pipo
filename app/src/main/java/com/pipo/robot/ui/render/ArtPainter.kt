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
import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.engine.Weather
import kotlin.math.cos
import kotlin.math.sin

/*
 * Pipo's art: crayon doodles for his wall, and little photos for the corkboard. Everything is
 * procedural and seeded, so a drawing looks the same every time you look at it.
 */

private val inkCol = Color(0xFF2B3342)
private val crayons = listOf(Color(0xFFE0706A), Color(0xFF6FA8E8), Color(0xFF7FB77E), Color(0xFFF3C35A), Color(0xFFC99BFF), Color(0xFFFF9E6B))

private fun h(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** A child's drawing inside the paper at [tl] ([w]×[hh] px). [u] ≈ one world unit. */
fun DrawScope.drawDoodle(d: WallDrawing, tl: Offset, w: Float, hh: Float, u: Float) {
    val crayon = crayons[(h(d.seed, 1) * crayons.size).toInt().coerceIn(0, crayons.size - 1)]
    val cx = tl.x + w / 2f
    val cy = tl.y + hh * 0.52f
    val s = w / 7f // one "doodle unit"
    val line = 0.32f * s
    fun o(x: Float, y: Float) = Offset(cx + x * s, cy + y * s)
    // a wobbly crayon ground line, because every drawing has one
    drawLine(crayon.copy(alpha = 0.55f), o(-3f, 3.2f), o(3f, 3.3f + (h(d.seed, 2) - 0.5f) * 0.6f), line * 1.5f, StrokeCap.Round)
    when (d.subject) {
        DrawSubject.USER -> {
            drawCircle(inkCol, 1.3f * s, o(0f, -1.3f), style = Stroke(line))
            drawLine(inkCol, o(0f, 0f), o(0f, 2.5f), line)
            drawLine(inkCol, o(-1.5f, 0.7f), o(1.5f, 1.1f), line)
            // giant ears. he tried.
            drawCircle(inkCol, 0.7f * s, o(-1.8f, -1.7f), style = Stroke(line * 0.9f))
            drawCircle(inkCol, 0.7f * s, o(1.8f, -1.7f), style = Stroke(line * 0.9f))
            drawArc(inkCol, 20f, 140f, false, o(-0.6f, -1.4f), Size(1.2f * s, 0.7f * s), style = Stroke(line * 0.8f))
            drawCircle(PipoColors.blush, 0.6f * s, o(2.2f, 2.6f))
        }
        DrawSubject.SELF -> {
            drawRoundRect(inkCol, o(-1.6f, -2f), Size(3.2f * s, 2.6f * s), CornerRadius(0.8f * s), style = Stroke(line))
            drawCircle(crayon, 0.35f * s, o(-0.6f, -0.9f)); drawCircle(crayon, 0.35f * s, o(0.6f, -0.9f))
            drawLine(inkCol, o(0f, -2f), o(0.3f, -3f), line); drawCircle(Color(0xFFFFC27A), 0.4f * s, o(0.3f, -3.1f))
            drawRoundRect(inkCol, o(-1.1f, 0.7f), Size(2.2f * s, 2f * s), CornerRadius(0.5f * s), style = Stroke(line))
            // the cape. "very accurate."
            drawPath(Path().apply { moveTo(cx - 1.1f * s, cy + 0.8f * s); lineTo(cx - 2.6f * s, cy + 2.8f * s); lineTo(cx - 1f * s, cy + 2.6f * s); close() }, crayon.copy(alpha = 0.7f))
        }
        DrawSubject.PET -> {
            drawArc(Color(0xFFFFB86B), 180f, 180f, true, o(-2f, -1.2f), Size(4f * s, 3.6f * s))
            drawArc(inkCol, 180f, 180f, false, o(-2f, -1.2f), Size(4f * s, 3.6f * s), style = Stroke(line))
            drawLine(inkCol, o(-2f, 0.6f), o(2f, 0.6f), line)
            drawCircle(Color.White, 0.8f * s, o(0f, -0.2f)); drawCircle(inkCol, 0.35f * s, o(0.15f, -0.2f))
            drawLine(inkCol, o(-1f, 0.6f), o(-1.1f, 1.8f), line); drawLine(inkCol, o(1f, 0.6f), o(1.1f, 1.8f), line)
            drawLine(inkCol, o(2f, 0f), o(2.8f, -1.2f), line * 0.8f)
        }
        DrawSubject.CREATURE -> {
            // a bird, mostly. Or a very confident cat.
            drawOval(crayon.copy(alpha = 0.8f), o(-1.8f, -1f), Size(3.2f * s, 2.2f * s))
            drawCircle(crayon, 0.9f * s, o(1.3f, -1.3f))
            drawPath(Path().apply { moveTo(cx + 2.1f * s, cy - 1.4f * s); lineTo(cx + 2.9f * s, cy - 1.1f * s); lineTo(cx + 2.1f * s, cy - 0.9f * s); close() }, Color(0xFFF3C35A))
            drawCircle(inkCol, 0.2f * s, o(1.5f, -1.5f))
            drawLine(inkCol, o(-0.3f, 1.1f), o(-0.3f, 2.2f), line * 0.8f); drawLine(inkCol, o(0.4f, 1.1f), o(0.4f, 2.2f), line * 0.8f)
        }
        DrawSubject.PLACE -> {
            drawCircle(Color(0xFFF3C35A), 0.9f * s, o(2f, -2.2f))
            drawPath(Path().apply { moveTo(cx - 3f * s, cy + 2.5f * s); quadraticBezierTo(cx - 1f * s, cy - 1.5f * s, cx + 1f * s, cy + 1f * s); quadraticBezierTo(cx + 2f * s, cy, cx + 3f * s, cy + 2.5f * s); close() }, crayon.copy(alpha = 0.6f))
            // "I added a volcano"
            drawPath(Path().apply { moveTo(cx - 1.8f * s, cy + 1f * s); lineTo(cx - 1.2f * s, cy - 0.6f * s); lineTo(cx - 0.6f * s, cy + 1f * s); close() }, Color(0xFFB8604F).copy(alpha = 0.7f))
            drawLine(Color(0xFFE0706A), o(-1.2f, -0.7f), o(-1.4f, -1.6f), line)
        }
        DrawSubject.INVENTION -> {
            for (i in 0 until 8) {
                val a = i * 0.785f
                drawLine(inkCol, o(0f, 0f), Offset(cx + cos(a) * 1.9f * s, cy + sin(a) * 1.9f * s), line)
            }
            drawCircle(crayon, 1.3f * s, o(0f, 0f))
            drawLine(inkCol, o(-2.6f, -2.4f), o(2.6f, -2.2f), line); drawLine(inkCol, o(0f, -1.3f), o(0f, -2.3f), line)
        }
        DrawSubject.FOOD -> {
            drawArc(crayon.copy(alpha = 0.7f), 0f, 180f, true, o(-2f, -1f), Size(4f * s, 3f * s))
            for (i in 0..2) drawArc(inkCol, 180f, 60f, false, o(-1f + i * 0.9f, -2.8f), Size(0.8f * s, 1.8f * s), style = Stroke(line * 0.8f))
            drawPath(Path().apply { moveTo(cx - 0.4f * s, cy - 0.6f * s); lineTo(cx, cy - 0.2f * s); lineTo(cx + 0.4f * s, cy - 0.6f * s) }, PipoColors.blush, style = Stroke(line))
        }
        DrawSubject.DREAM, DrawSubject.BUILDING -> {
            // a round building with a hole in the roof; a door with the mark on it
            drawArc(Color(0xFFB0A3C9).copy(alpha = 0.6f), 180f, 180f, true, o(-2.4f, -2f), Size(4.8f * s, 4f * s))
            drawArc(inkCol, 180f, 180f, false, o(-2.4f, -2f), Size(4.8f * s, 4f * s), style = Stroke(line))
            drawRect(inkCol, o(-2.4f, 0f), Size(4.8f * s, 2.4f * s), style = Stroke(line))
            drawLine(inkCol, o(-0.3f, -2f), o(0.4f, -0.9f), line * 1.4f) // the slit in the roof
            drawRect(inkCol, o(-0.6f, 0.8f), Size(1.2f * s, 1.6f * s), style = Stroke(line * 0.8f))
            drawSymbol(o(0f, 1.4f), 0.45f * s, Color(0xFF6B8FA6))
            for (i in 0 until 3) drawCircle(inkCol.copy(alpha = 0.5f), 0.12f * s, o(-2.5f + i * 2.5f, -3.2f + h(d.seed, i) * 0.4f)) // stars
        }
        DrawSubject.SYMBOL -> drawSymbol(o(0f, -0.3f), 2.3f * s, inkCol)
    }
    // his signature, a tiny P
    drawLine(inkCol.copy(alpha = 0.7f), Offset(tl.x + w - 1.6f * u, tl.y + hh - 1.8f * u), Offset(tl.x + w - 1.6f * u, tl.y + hh - 0.8f * u), 0.2f * u)
    drawArc(inkCol.copy(alpha = 0.7f), -90f, 180f, false, Offset(tl.x + w - 1.9f * u, tl.y + hh - 1.8f * u), Size(0.8f * u, 0.55f * u), style = Stroke(0.2f * u))
}

/** A polaroid, [w] px wide, top-left at [tl]. */
fun DrawScope.drawPinnedPhoto(p: PinnedPhoto, tl: Offset, w: Float, u: Float, t: Float) {
    val hh = w * 1.18f
    drawRect(Color.Black.copy(alpha = 0.18f), Offset(tl.x + 0.25f * u, tl.y + 0.35f * u), Size(w, hh))
    drawRect(Color(0xFFF7F4EE), tl, Size(w, hh))
    val pad = w * 0.08f
    val img = Offset(tl.x + pad, tl.y + pad)
    val iw = w - pad * 2f
    drawPhotoImage(p, img, iw, iw, t)
    drawCircle(Color(0xFFE0706A), 0.35f * u, Offset(tl.x + w / 2f, tl.y + 0.3f * u))
}

/** The picture inside a photo: sky, ground, and what he was pointing his phone at. */
fun DrawScope.drawPhotoImage(p: PinnedPhoto, tl: Offset, w: Float, hh: Float, t: Float) {
    val sky = when {
        p.night -> Color(0xFF1D2A4F)
        p.weather == Weather.RAIN || p.weather == Weather.STORM -> Color(0xFF8C98A6)
        p.weather == Weather.FOG -> Color(0xFFC9D0D6)
        p.weather == Weather.CLOUDY -> Color(0xFFB4C6D4)
        else -> Color(0xFF9CCBEA)
    }
    drawRect(Brush.verticalGradient(listOf(sky, Color(p.placeColor).copy(alpha = 0.5f)), startY = tl.y, endY = tl.y + hh), tl, Size(w, hh))
    drawRect(Color(p.placeColor), Offset(tl.x, tl.y + hh * 0.68f), Size(w, hh * 0.32f))
    val c = Offset(tl.x + w * (0.4f + h(p.seed, 3) * 0.2f), tl.y + hh * 0.58f)
    val s = w / 10f
    // the anomaly: a door on the hills that wasn't there, with the mark on it
    if (p.anomaly) {
        val dx = tl.x + w * 0.82f; val dy = tl.y + hh * 0.66f
        drawRect(Color(0xFF2B2F3A), Offset(dx - 0.6f * s, dy - 2.2f * s), Size(1.2f * s, 2.2f * s))
        drawSymbol(Offset(dx, dy - 1.4f * s), 0.35f * s, Color(0xFF9FF3E0).copy(alpha = 0.8f))
    }
    when (p.subject) {
        PhotoSubject.CREATURE -> {
            val sp = p.ref.substringBefore(':')
            val col = when (sp) { "cat" -> Color(0xFF8C8F99); "frog" -> Color(0xFF6FA27A); "butterfly" -> Color(0xFFF3C35A); "duck" -> Color(0xFFE8E2D6); "squirrel" -> Color(0xFFB8804F); "bee" -> Color(0xFFF3C35A); "firefly" -> Color(0xFFFFF1A0); else -> Color(0xFF9A7A60) }
            drawOval(col, Offset(c.x - 1.8f * s, c.y - 1f * s), Size(3.4f * s, 2.2f * s))
            drawCircle(col, 1f * s, Offset(c.x + 1.4f * s, c.y - 1.2f * s))
            drawCircle(inkCol, 0.2f * s, Offset(c.x + 1.6f * s, c.y - 1.4f * s))
        }
        PhotoSubject.PET -> {
            drawArc(Color(0xFFFFB86B), 180f, 180f, true, Offset(c.x - 2f * s, c.y - 1.6f * s), Size(4f * s, 3.4f * s))
            drawCircle(Color(0xFF1B2230), 0.9f * s, Offset(c.x, c.y - 0.6f * s)); drawCircle(PipoColors.eye, 0.4f * s, Offset(c.x + 0.2f * s, c.y - 0.6f * s))
            // motion blur: Nib moved
            drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x - 3.5f * s, c.y - 0.5f * s), Offset(c.x - 2.2f * s, c.y - 0.5f * s), 0.3f * s)
        }
        PhotoSubject.SELFIE -> {
            drawRoundRect(PipoColors.shell, Offset(c.x - 2.4f * s, c.y - 3.4f * s), Size(4.8f * s, 3.8f * s), CornerRadius(1.2f * s))
            drawRoundRect(PipoColors.screen, Offset(c.x - 1.9f * s, c.y - 2.9f * s), Size(3.8f * s, 2.8f * s), CornerRadius(0.9f * s))
            drawArc(PipoColors.eye, 200f, 140f, false, Offset(c.x - 1.3f * s, c.y - 2.3f * s), Size(1f * s, 0.8f * s), style = Stroke(0.3f * s))
            drawArc(PipoColors.eye, 200f, 140f, false, Offset(c.x + 0.3f * s, c.y - 2.3f * s), Size(1f * s, 0.8f * s), style = Stroke(0.3f * s))
        }
        PhotoSubject.PLACE -> if (p.ref == "observatory") {
            drawArc(Color(0xFFB0A3C9), 180f, 180f, true, Offset(c.x - 2.6f * s, c.y - 2.2f * s), Size(5.2f * s, 4.4f * s))
            drawRect(Color(0xFFB0A3C9), Offset(c.x - 2.6f * s, c.y), Size(5.2f * s, 1.6f * s))
            drawLine(inkCol, Offset(c.x - 0.2f * s, c.y - 2.2f * s), Offset(c.x + 0.5f * s, c.y - 1f * s), 0.4f * s)
        } else {
            drawPath(Path().apply { moveTo(tl.x, tl.y + hh * 0.7f); quadraticBezierTo(c.x, tl.y + hh * 0.4f, tl.x + w, tl.y + hh * 0.72f); lineTo(tl.x + w, tl.y + hh); lineTo(tl.x, tl.y + hh); close() }, Color(p.placeColor))
        }
        PhotoSubject.FOOD -> {
            drawArc(Color(0xFFF2ECE2), 0f, 180f, true, Offset(c.x - 2.2f * s, c.y - 1.2f * s), Size(4.4f * s, 3f * s))
            drawOval(Color(0xFFF3D98A), Offset(c.x - 2f * s, c.y - 0.3f * s), Size(4f * s, 0.8f * s))
        }
        PhotoSubject.WEATHER, PhotoSubject.SKY -> for (i in 0 until 5) drawCircle(Color.White.copy(alpha = 0.8f), 0.25f * s, Offset(tl.x + h(p.seed, i) * w, tl.y + h(p.seed, i + 9) * hh * 0.5f))
        PhotoSubject.INVENTION -> drawItem(com.pipo.robot.data.ItemShape.GEAR, c, 4f * s)
    }
    if (p.night) drawRect(Color(0xFF0E1733).copy(alpha = 0.25f), tl, Size(w, hh))
    // a whisper of glare, so it reads as a photo
    drawLine(Color.White.copy(alpha = 0.25f), Offset(tl.x + w * 0.1f, tl.y + hh * 0.9f), Offset(tl.x + w * 0.5f, tl.y + hh * 0.1f), 0.2f * s)
}
