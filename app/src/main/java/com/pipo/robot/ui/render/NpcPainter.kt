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
import com.pipo.robot.data.ItemShape
import kotlin.math.abs
import kotlin.math.sin

/**
 * The neighbours: small robots, each built differently, so you know them at a glance. Grumble is
 * boxy and stern, Juniper blinks all her lights, Mrs. Pim is round with a headscarf, Mo wears a
 * chef's hat and flour, Ada carries a giant mug, Old Rook has a monocle and a cane, Fennel a cap
 * and wrench, Tess round glasses and a book, Kip a jersey; at the sports places Coach Raj (cricket),
 * Lin (table tennis) and Bea (badminton).
 */
private data class Look(
    val body: Color, val trim: Color, val head: Color, val eye: Color,
    val boxy: Boolean = false, val tall: Float = 1f, val wide: Float = 1f,
)

private val looks = mapOf(
    "grumble" to Look(Color(0xFF8A8F99), Color(0xFF5E6470), Color(0xFF9AA0AA), Color(0xFFFFC27A), boxy = true, wide = 1.15f),
    "juniper" to Look(Color(0xFF5C6BC0), Color(0xFF3949AB), Color(0xFF7986CB), Color(0xFF8FF5E2), tall = 1.25f, wide = 0.85f),
    "pim" to Look(Color(0xFFE8A0B4), Color(0xFFB86E86), Color(0xFFF2C2CF), Color(0xFF3B2A20), wide = 1.2f),
    "mo" to Look(Color(0xFFF2E6D0), Color(0xFFD9B98A), Color(0xFFFFF4E0), Color(0xFF3B2A20)),
    "ada" to Look(Color(0xFF7FB27A), Color(0xFF4F8A45), Color(0xFFA6D19E), Color(0xFF2B3342)),
    "rook" to Look(Color(0xFF9C8A6E), Color(0xFF6E5E46), Color(0xFFB8A688), Color(0xFFFFE08A), tall = 0.9f),
    "fennel" to Look(Color(0xFFE08A3A), Color(0xFFB0602A), Color(0xFFF2A65A), Color(0xFF2B3342), boxy = true),
    "tess" to Look(Color(0xFF8E7CC3), Color(0xFF6A5AA0), Color(0xFFB3A6DE), Color(0xFF2B3342), tall = 1.1f, wide = 0.9f),
    "kip" to Look(Color(0xFF4F8DE0), Color(0xFFFFFFFF), Color(0xFFD8E6F7), Color(0xFF2B3342), tall = 0.9f),
    "raj" to Look(Color(0xFFF4F4F0), Color(0xFF2E7D32), Color(0xFFE8E2D6), Color(0xFF2B3342), tall = 1.05f),
    "lin" to Look(Color(0xFFE0453A), Color(0xFF1B2230), Color(0xFFF08A7E), Color(0xFF2B3342), wide = 0.9f),
    "bea" to Look(Color(0xFFFFC94A), Color(0xFF3B4658), Color(0xFFFFE08A), Color(0xFF2B3342)),
)

/** A neighbour, feet at [fx],[fy], [h] tall. [wave] 0..1 raises a hand. */
fun DrawScope.drawNpc(id: String, fx: Float, fy: Float, h: Float, t: Float, facing: Float = 1f, wave: Float = 0f, cheer: Boolean = false) {
    val L = looks[id] ?: looks["kip"]!!
    val k = h / 100f
    val hop = if (cheer) abs(sin(t * 8f)) * 6f * k else 0f
    val bob = sin(t * 2.1f + id.hashCode() % 7) * 1.2f * k
    val base = fy - hop
    val bw = 34f * k * L.wide; val bh = 36f * k * L.tall
    val bodyTop = base - 14f * k - bh
    // shadow, legs
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(fx - 22f * k, fy - 3f * k), Size(44f * k, 6f * k))
    for (sd in listOf(-1f, 1f)) drawRoundRect(L.trim, Offset(fx + sd * 8f * k - 3f * k, base - 15f * k), Size(6f * k, 15f * k), CornerRadius(3f * k))
    // body
    val bodyCol = Brush.verticalGradient(listOf(lerp(L.body, Color.White, 0.15f), L.body, lerp(L.body, Color.Black, 0.2f)), startY = bodyTop, endY = bodyTop + bh)
    drawRoundRect(bodyCol, Offset(fx - bw / 2f, bodyTop + bob), Size(bw, bh), CornerRadius(if (L.boxy) 4f * k else 14f * k))
    // arms (one waves)
    val shoulderY = bodyTop + bob + 8f * k
    val waveAng = if (cheer) -150f + sin(t * 10f) * 20f else -40f - wave * 100f + sin(t * 9f) * 18f * wave
    drawLine(L.trim, Offset(fx - bw / 2f, shoulderY), Offset(fx - bw / 2f - 8f * k, shoulderY + 20f * k), 5f * k, StrokeCap.Round)
    rotate(waveAng * facing, Offset(fx + bw / 2f * facing, shoulderY)) {
        drawLine(L.trim, Offset(fx + bw / 2f * facing, shoulderY), Offset(fx + bw / 2f * facing, shoulderY + 22f * k), 5f * k, StrokeCap.Round)
        drawCircle(L.trim, 3.5f * k, Offset(fx + bw / 2f * facing, shoulderY + 22f * k))
    }
    // head + face screen
    val hw = 40f * k * L.wide; val hh = 30f * k
    val headTop = bodyTop + bob - hh - 2f * k
    drawRoundRect(Brush.verticalGradient(listOf(lerp(L.head, Color.White, 0.25f), L.head), startY = headTop, endY = headTop + hh), Offset(fx - hw / 2f, headTop), Size(hw, hh), CornerRadius(if (L.boxy) 5f * k else 12f * k))
    drawRoundRect(Color(0xFF141B27), Offset(fx - hw / 2f + 5f * k, headTop + 5f * k), Size(hw - 10f * k, hh - 10f * k), CornerRadius(7f * k))
    val blink = if ((t + id.length) % 4.3f < 0.12f) 0.15f else 1f
    val ex = facing * 2f * k
    if (id == "juniper") for (i in 0 until 5) drawCircle(listOf(Color(0xFF8FF5E2), Color(0xFFFF8FD0), Color(0xFFFFE08A))[i % 3].copy(alpha = if (sin(t * 4f + i) > 0f) 1f else 0.3f), 1.6f * k, Offset(fx - hw / 2f + 8f * k + i * (hw - 16f * k) / 4f, headTop + hh * 0.5f))
    else for (sd in listOf(-1f, 1f)) drawOval(Color(0xFF8FF5E2), Offset(fx + sd * 7f * k + ex - 2.5f * k, headTop + 12f * k), Size(5f * k, 6f * k * blink))
    drawArc(Color(0xFF8FF5E2), 20f, 140f, false, Offset(fx - 5f * k + ex, headTop + 16f * k), Size(10f * k, 5f * k), style = Stroke(1.2f * k))
    // antenna
    drawLine(L.trim, Offset(fx, headTop), Offset(fx + sin(t * 2f) * 2f * k, headTop - 8f * k), 1.5f * k)
    drawCircle(L.eye, 2.5f * k, Offset(fx + sin(t * 2f) * 2f * k, headTop - 9f * k))
    // who they are
    when (id) {
        "grumble" -> { // apron and a bushy grey "moustache" vent
            drawRect(Color(0xFF6B4A32), Offset(fx - bw * 0.35f, bodyTop + bob + 10f * k), Size(bw * 0.7f, bh - 12f * k))
            drawRoundRect(Color(0xFFC9CED6), Offset(fx - 9f * k + ex, headTop + 19f * k), Size(18f * k, 4f * k), CornerRadius(2f * k))
        }
        "pim" -> { // headscarf, basket
            drawArc(Color(0xFFE0453A), 180f, 180f, true, Offset(fx - hw / 2f - 2f * k, headTop - 6f * k), Size(hw + 4f * k, 18f * k))
            drawItem(ItemShape.APPLE, Offset(fx - bw / 2f - 8f * k, shoulderY + 22f * k), 10f * k)
        }
        "mo" -> { // chef hat, flour dusting
            drawRect(Color.White, Offset(fx - hw * 0.35f, headTop - 10f * k), Size(hw * 0.7f, 11f * k))
            for (i in -1..1) drawCircle(Color.White, 6f * k, Offset(fx + i * 7f * k, headTop - 12f * k))
            for (i in 0 until 6) drawCircle(Color.White.copy(alpha = 0.6f), 1.2f * k, Offset(fx - bw / 3f + (i * 13 % 20) * k, bodyTop + bob + (8f + i * 4f) * k))
        }
        "ada" -> drawItem(ItemShape.MUG, Offset(fx - bw / 2f - 8f * k, shoulderY + 20f * k), 14f * k)
        "rook" -> { // monocle and cane
            drawCircle(Color(0xFFC9A23A), 4.5f * k, Offset(fx + 7f * k + ex, headTop + 15f * k), style = Stroke(1.2f * k))
            drawLine(Color(0xFF5E4A3A), Offset(fx - bw / 2f - 8f * k, shoulderY + 20f * k), Offset(fx - bw / 2f - 10f * k, fy), 2.5f * k)
        }
        "fennel" -> { // cap and wrench
            drawRoundRect(Color(0xFF3B4658), Offset(fx - hw / 2f, headTop - 3f * k), Size(hw, 7f * k), CornerRadius(3f * k))
            drawRect(Color(0xFF3B4658), Offset(fx + facing * hw * 0.3f, headTop + 1f * k), Size(10f * k * facing, 3f * k))
            drawItem(ItemShape.GEAR, Offset(fx - bw / 2f - 8f * k, shoulderY + 22f * k), 10f * k)
        }
        "tess" -> { // round glasses, a book
            for (sd in listOf(-1f, 1f)) drawCircle(Color(0xFF2B3342), 4.5f * k, Offset(fx + sd * 7f * k + ex, headTop + 15f * k), style = Stroke(1f * k))
            drawRect(Color(0xFF6F8FA6), Offset(fx - bw / 2f - 14f * k, shoulderY + 14f * k), Size(10f * k, 13f * k))
        }
        "kip" -> { // football jersey with a number, cap backwards
            drawRect(Color.White, Offset(fx - bw / 2f, bodyTop + bob + 12f * k), Size(bw, 4f * k))
            drawRoundRect(Color(0xFFE0453A), Offset(fx - hw / 2f, headTop - 3f * k), Size(hw, 6f * k), CornerRadius(3f * k))
        }
        "raj" -> { // cricket whites, a green cap, a bat
            drawRoundRect(Color(0xFF2E7D32), Offset(fx - hw / 2f, headTop - 4f * k), Size(hw, 7f * k), CornerRadius(3f * k))
            drawItem(ItemShape.BAT, Offset(fx - bw / 2f - 10f * k, shoulderY + 18f * k), 22f * k)
        }
        "lin" -> drawItem(ItemShape.PADDLE, Offset(fx - bw / 2f - 9f * k, shoulderY + 20f * k), 16f * k)
        "bea" -> drawItem(ItemShape.RACKET, Offset(fx - bw / 2f - 9f * k, shoulderY + 14f * k), 20f * k)
    }
}

/** Their name tag, small, under them (so you learn who's who). */
fun npcColor(id: String): Color = looks[id]?.body ?: Color.Gray
