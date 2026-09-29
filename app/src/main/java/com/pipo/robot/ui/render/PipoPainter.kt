package com.pipo.robot.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object PipoColors {
    val shell = Color(0xFFF4F6F9)
    val shellShade = Color(0xFFC9D2DE)
    val belly = Color(0xFFE3E8EF)
    val screen = Color(0xFF141B27)
    val joint = Color(0xFF3B4658)
    val eye = Color(0xFF8FF5E2)
    val blush = Color(0xFFFF8FA3)
    val arm = Color(0xFFD9E0E9)
}

/**
 * Draws Pipo with his feet at ([footX], [footY]) and total height [height] px.
 * [lift] = extra px above the floor (being carried).
 */
fun DrawScope.drawPipo(rig: PipoRig, footX: Float, footY: Float, height: Float, lift: Float = 0f, shadow: Boolean = true) {
    val k = height / 100f
    val p = rig.pose
    val glow = Color(rig.glow)
    val air = p.jump * k + lift
    if (shadow && p.lie < 0.5f) {
        val sw = 1f - (air / (60f * k)).coerceIn(0f, 0.5f)
        drawOval(Color.Black.copy(alpha = 0.22f * sw), Offset(footX - 20f * k * sw, footY - 3f * k), Size(40f * k * sw, 6f * k))
    }
    val c = cos(p.spin)
    val sx = p.stretchX * (if (abs(c) < 0.06f) (if (c < 0f) -0.06f else 0.06f) else c)
    withTransform({
        translate(footX + p.shakeX * k, footY - air - p.bob * k)
        rotate(-90f * p.lie, pivot = Offset(0f, -30f * k))
        rotate(p.lean, pivot = Offset.Zero)
        scale(sx, p.squash, pivot = Offset.Zero)
    }) {
        drawPipoLocal(rig, k, glow)
    }
}

private fun DrawScope.drawPipoLocal(rig: PipoRig, k: Float, glow: Color) {
    val p = rig.pose
    val f = rig.face
    val C = PipoColors
    val drop = p.sit * 9f * k + p.crouch * 8f * k
    val headDrop = drop + p.crouch * 6f * k + p.headBob * k

    // ---- legs + feet
    for (side in intArrayOf(-1, 1)) {
        val lift = if (side < 0) p.legL else p.legR
        val hip = Offset(side * 8f * k, -12f * k + drop)
        val spread = p.legSpread + p.sit * 3f
        val foot = Offset(side * (8f + spread) * k, (-2.5f - lift) * k)
        drawLine(C.joint, hip, foot, strokeWidth = 6f * k, cap = StrokeCap.Round)
        drawOval(C.shellShade, Offset(foot.x - 6f * k, foot.y - 2f * k), Size(12f * k, 5.5f * k))
        drawOval(C.shell, Offset(foot.x - 5.5f * k, foot.y - 2.4f * k), Size(11f * k, 4.2f * k))
    }

    // ---- body
    val bodyTop = -37f * k + drop
    val bodyBot = -12f * k + drop
    drawRoundRect(Brush.verticalGradient(listOf(C.shell, C.shellShade), startY = bodyTop, endY = bodyBot),
        Offset(-15f * k, bodyTop), Size(30f * k, bodyBot - bodyTop), CornerRadius(11f * k))
    drawRoundRect(C.belly, Offset(-9f * k, bodyTop + 6f * k), Size(18f * k, 13f * k), CornerRadius(6f * k))
    val chest = Offset(0f, -24.5f * k + drop)
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.5f), Color.Transparent), center = chest, radius = 8f * k), 8f * k, chest)
    drawCircle(glow, 2.8f * k, chest)
    drawRoundRect(C.joint, Offset(-5f * k, -41f * k + drop), Size(10f * k, 5f * k), CornerRadius(2f * k))

    // ---- head
    fun hy(v: Float) = v * k + headDrop
    rotate(p.headTilt, pivot = Offset(0f, hy(-40f))) {
        // antenna
        val base = Offset(0f, hy(-89f))
        rotate(p.antenna, pivot = base) {
            drawLine(C.joint, base, Offset(0f, base.y - 11f * k), strokeWidth = 2.4f * k, cap = StrokeCap.Round)
            val bulb = Offset(0f, base.y - 14f * k)
            val halo = if (rig.torch) 34f * k else 11f * k
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = if (rig.torch) 0.85f else 0.45f), Color.Transparent), center = bulb, radius = halo), halo, bulb)
            drawCircle(if (rig.torch) Color.White else glow, 4f * k, bulb)
            drawCircle(Color.White.copy(alpha = 0.7f), 1.4f * k, Offset(bulb.x - 1.2f * k, bulb.y - 1.2f * k))
        }
        // ear pods
        for (side in intArrayOf(-1, 1)) {
            val ec = Offset(side * 33f * k, hy(-65f))
            drawCircle(C.shellShade, 6.5f * k, ec)
            drawCircle(glow.copy(alpha = 0.8f), 3.1f * k, ec)
        }
        // shell
        drawRoundRect(Brush.verticalGradient(listOf(C.shell, C.shellShade), startY = hy(-90f), endY = hy(-40f)),
            Offset(-32f * k, hy(-90f)), Size(64f * k, 50f * k), CornerRadius(22f * k))
        drawOval(Color.White.copy(alpha = 0.55f), Offset(-24f * k, hy(-87.5f)), Size(16f * k, 4.5f * k))
        // screen
        val sl = -26f * k; val st = hy(-83f); val sw = 52f * k; val sh = 37f * k
        drawRoundRect(C.screen, Offset(sl, st), Size(sw, sh), CornerRadius(14f * k))
        val eyeCol = C.eye.copy(alpha = (0.55f + 0.45f * rig.eyeGlow).coerceIn(0f, 1f))
        val clip = Path().apply { addRoundRect(RoundRect(sl, st, sl + sw, st + sh, CornerRadius(14f * k))) }
        clipPath(clip) {
            drawRect(Brush.radialGradient(listOf(eyeCol.copy(alpha = 0.10f), Color.Transparent), center = Offset(0f, hy(-65f)), radius = 30f * k),
                Offset(sl, st), Size(sw, sh))
            for (side in intArrayOf(-1, 1)) {
                val sideScale = if (side > 0) 1f + f.asym else 1f - f.asym * 0.5f
                val cx = side * 12f * k + rig.lookX * 4.5f * k
                val cy = hy(-66f) + rig.lookY * 3f * k
                drawEye(cx, cy, 5.2f * k * f.scale * sideScale, 7f * k * f.scale * sideScale, f, rig.blink, side, eyeCol)
                if (f.blush > 0.01f) drawOval(C.blush.copy(alpha = 0.6f * f.blush), Offset(side * 19f * k - 4.5f * k, hy(-57.5f)), Size(9f * k, 3.6f * k))
            }
            drawMouth(rig, k, hy(-53.5f), eyeCol)
        }
    }

    // ---- held item / book (between the hands)
    if (p.hold > 0.5f) {
        val hc = Offset(0f, -42f * k + drop)
        val shape = rig.holdItem
        if (shape != null) drawItem(shape, hc, 13f * k)
        else if (rig.anim == AnimState.READING) {
            drawRoundRect(Color(0xFF6F8FA6), Offset(hc.x - 8f * k, hc.y - 5f * k), Size(16f * k, 10f * k), CornerRadius(1.5f * k))
            drawLine(Color(0xFFF4EFE6), Offset(hc.x, hc.y - 4.5f * k), Offset(hc.x, hc.y + 4.5f * k), 1.2f * k)
        }
    }

    // ---- arms (drawn last so hands can cover the face)
    for (side in intArrayOf(-1, 1)) {
        val a = Math.toRadians((if (side < 0) p.armL else p.armR).toDouble())
        val sh = Offset(side * 14f * k, -31f * k + drop)
        var hand = Offset(sh.x + side * sin(a).toFloat() * 13f * k, sh.y + cos(a).toFloat() * 13f * k)
        if (p.cover > 0.01f) hand = lerp(hand, Offset(side * 12f * k, hy(-64f)), p.cover)
        if (p.hold > 0.01f) hand = lerp(hand, Offset(side * 7f * k, -42f * k + drop), p.hold)
        drawLine(C.arm, sh, hand, strokeWidth = 5.2f * k, cap = StrokeCap.Round)
        drawCircle(C.shellShade, 3.9f * k, hand)
        drawCircle(C.shell, 3.2f * k, Offset(hand.x - 0.4f * k, hand.y - 0.4f * k))
    }
}

private fun DrawScope.drawEye(cx: Float, cy: Float, hw: Float, hh: Float, f: Face, blink: Float, side: Int, col: Color) {
    val scr = PipoColors.screen
    val pill = (1f - max(f.arc, f.closed)).coerceIn(0f, 1f)
    if (pill > 0.01f) {
        val w = hw * (1f + f.round * 0.2f)
        val h = ((hh + (w - hh) * f.round) * (f.open * blink)).coerceAtLeast(hw * 0.12f)
        drawRoundRect(col.copy(alpha = 0.16f * pill), Offset(cx - w * 1.6f, cy - h * 1.4f), Size(w * 3.2f, h * 2.8f), CornerRadius(w * 1.6f))
        drawRoundRect(col.copy(alpha = pill), Offset(cx - w, cy - h), Size(2f * w, 2f * h), CornerRadius(min(w, h)))
        if (f.round > 0.3f) drawCircle(scr.copy(alpha = pill * f.round), w * 0.38f, Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = pill * (0.5f + 0.4f * f.sparkle)), w * (0.27f + 0.15f * f.sparkle), Offset(cx - w * 0.35f, cy - h * 0.45f))
        if (f.sparkle > 0.3f) drawCircle(Color.White.copy(alpha = pill * f.sparkle * 0.8f), w * 0.14f, Offset(cx + w * 0.35f, cy + h * 0.35f))
        // eyelids: screen-coloured shapes covering the eye
        if (f.lidTop > 0.01f || abs(f.lidAngle) > 0.05f) {
            val base = cy - h + f.lidTop * 2f * h
            val slope = -side * f.lidAngle * 0.7f
            val lid = Path().apply {
                moveTo(cx - 2f * w, base - 2f * w * slope)
                lineTo(cx + 2f * w, base + 2f * w * slope)
                lineTo(cx + 2f * w, cy - h - 4f * w)
                lineTo(cx - 2f * w, cy - h - 4f * w)
                close()
            }
            drawPath(lid, scr)
        }
        if (f.squint > 0.01f) {
            val b = cy + h - f.squint * 1.2f * h
            drawRect(scr, Offset(cx - 2f * w, b), Size(4f * w, cy + h + 2f * w - b))
        }
    }
    if (f.arc > 0.01f) {
        val path = Path().apply {
            moveTo(cx - hw * 1.1f, cy + hh * 0.35f)
            quadraticBezierTo(cx, cy - hh * 1.1f, cx + hw * 1.1f, cy + hh * 0.35f)
        }
        drawPath(path, col.copy(alpha = f.arc), style = Stroke(width = hw * 0.75f, cap = StrokeCap.Round))
    }
    if (f.closed > 0.01f) {
        val path = Path().apply {
            moveTo(cx - hw * 1.1f, cy - hh * 0.1f)
            quadraticBezierTo(cx, cy + hh * 0.9f, cx + hw * 1.1f, cy - hh * 0.1f)
        }
        drawPath(path, col.copy(alpha = f.closed * 0.9f), style = Stroke(width = hw * 0.6f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMouth(rig: PipoRig, k: Float, my: Float, col: Color) {
    val f = rig.face
    val mx = rig.lookX * 2f * k
    val mw = 6.5f * k * f.mouthWidth
    val open = max(f.mouthOpen, rig.talkLevel * 0.9f)
    val skew = f.mouthSkew * 1.6f * k
    val curve = f.mouthCurve * 4.5f * k
    val mcol = col.copy(alpha = 0.92f)
    when {
        f.mouthWave > 0.3f && open < 0.2f -> {
            val w = 2f * k * f.mouthWave
            val path = Path().apply {
                moveTo(mx - mw, my)
                quadraticBezierTo(mx - mw * 0.5f, my - w, mx, my)
                quadraticBezierTo(mx + mw * 0.5f, my + w, mx + mw, my)
            }
            drawPath(path, mcol, style = Stroke(width = 1.8f * k, cap = StrokeCap.Round))
        }
        open < 0.08f -> {
            val path = Path().apply {
                moveTo(mx - mw, my + skew)
                quadraticBezierTo(mx, my + curve, mx + mw, my - skew)
            }
            drawPath(path, mcol, style = Stroke(width = 1.9f * k, cap = StrokeCap.Round))
        }
        else -> {
            val path = Path().apply {
                moveTo(mx - mw, my + skew)
                quadraticBezierTo(mx, my + curve * 0.6f - open * 1.5f * k, mx + mw, my - skew)
                quadraticBezierTo(mx, my + curve + open * 8f * k, mx - mw, my + skew)
                close()
            }
            drawPath(path, mcol)
        }
    }
}

/** Floating emote above Pipo's head ([hx],[hy] = top of head in screen px). */
fun DrawScope.drawEmote(rig: PipoRig, hx: Float, hy: Float, k: Float) {
    val e = rig.emote ?: return
    val t = rig.emoteT
    val a = (if (t < 0.2f) t / 0.2f else if (t > 2.0f) (2.6f - t) / 0.6f else 1f).coerceIn(0f, 1f)
    val c = Offset(hx + 18f * k, hy - 8f * k - t * 6f * k)
    val white = Color(0xFFEEF3F8).copy(alpha = a)
    val amber = Color(0xFFFFC27A).copy(alpha = a)
    val sw = 2.2f * k
    when (e) {
        EmoteKind.ZZZ -> for (i in 0..2) {
            val s = (3.5f + i * 1.8f) * k
            val o = Offset(c.x + i * 6f * k, c.y - i * 7f * k + sin(rig.time * 2f + i) * k)
            val z = Path().apply { moveTo(o.x - s, o.y - s); lineTo(o.x + s, o.y - s); lineTo(o.x - s, o.y + s); lineTo(o.x + s, o.y + s) }
            drawPath(z, white.copy(alpha = a * (0.5f + 0.25f * i)), style = Stroke(width = sw * 0.8f, cap = StrokeCap.Round))
        }
        EmoteKind.QUESTION -> {
            val q = Path().apply {
                moveTo(c.x - 4f * k, c.y - 5f * k)
                quadraticBezierTo(c.x - 4f * k, c.y - 10f * k, c.x, c.y - 10f * k)
                quadraticBezierTo(c.x + 5f * k, c.y - 10f * k, c.x + 4f * k, c.y - 5f * k)
                quadraticBezierTo(c.x + 3f * k, c.y - 2f * k, c.x, c.y)
                lineTo(c.x, c.y + 2f * k)
            }
            drawPath(q, white, style = Stroke(width = sw, cap = StrokeCap.Round))
            drawCircle(white, 1.4f * k, Offset(c.x, c.y + 6f * k))
        }
        EmoteKind.EXCLAIM -> {
            drawLine(amber, Offset(c.x, c.y - 10f * k), Offset(c.x, c.y + 1f * k), sw * 1.3f, StrokeCap.Round)
            drawCircle(amber, 1.7f * k, Offset(c.x, c.y + 6f * k))
        }
        EmoteKind.NOTES -> for (i in 0..1) {
            val o = Offset(c.x + i * 9f * k, c.y - i * 5f * k + sin(rig.time * 4f + i * 2f) * 2f * k)
            val col = Color(rig.glow).copy(alpha = a)
            drawCircle(col, 2.6f * k, o)
            drawLine(col, Offset(o.x + 2.3f * k, o.y), Offset(o.x + 2.3f * k, o.y - 10f * k), sw * 0.7f)
            drawLine(col, Offset(o.x + 2.3f * k, o.y - 10f * k), Offset(o.x + 6f * k, o.y - 7f * k), sw * 0.7f)
        }
        EmoteKind.HEART -> {
            val s = 5f * k * (1f + 0.1f * sin(rig.time * 8f))
            val h = Path().apply {
                moveTo(c.x, c.y + s)
                cubicTo(c.x - 2f * s, c.y - 0.2f * s, c.x - s, c.y - 1.5f * s, c.x, c.y - 0.6f * s)
                cubicTo(c.x + s, c.y - 1.5f * s, c.x + 2f * s, c.y - 0.2f * s, c.x, c.y + s)
                close()
            }
            drawPath(h, PipoColors.blush.copy(alpha = a))
        }
        EmoteKind.SPARKLE -> for (i in 0..2) {
            val o = Offset(c.x + (i - 1) * 8f * k, c.y - (i % 2) * 6f * k)
            val s = (3f + 1.5f * sin(rig.time * 6f + i)) * k
            val st = Path().apply {
                moveTo(o.x, o.y - s); quadraticBezierTo(o.x, o.y, o.x + s, o.y); quadraticBezierTo(o.x, o.y, o.x, o.y + s)
                quadraticBezierTo(o.x, o.y, o.x - s, o.y); quadraticBezierTo(o.x, o.y, o.x, o.y - s); close()
            }
            drawPath(st, amber)
        }
        EmoteKind.SWEAT -> {
            val o = Offset(c.x - 4f * k, c.y + 6f * k + t * 3f * k)
            val d = Path().apply {
                moveTo(o.x, o.y - 5f * k)
                quadraticBezierTo(o.x + 4f * k, o.y + 1f * k, o.x, o.y + 3f * k)
                quadraticBezierTo(o.x - 4f * k, o.y + 1f * k, o.x, o.y - 5f * k)
                close()
            }
            drawPath(d, Color(0xFF9AD0FF).copy(alpha = a))
        }
        EmoteKind.ANGER -> {
            val red = Color(0xFFFF6B6B).copy(alpha = a)
            val s = 4f * k * (1f + 0.15f * sin(rig.time * 12f))
            for (q in 0..3) {
                val dx = if (q % 2 == 0) -1 else 1; val dy = if (q < 2) -1 else 1
                val arc = Path().apply {
                    moveTo(c.x + dx * s * 0.3f, c.y + dy * s)
                    quadraticBezierTo(c.x + dx * s * 0.3f, c.y + dy * s * 0.3f, c.x + dx * s, c.y + dy * s * 0.3f)
                }
                drawPath(arc, red, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
        }
        EmoteKind.DOTS -> for (i in 0..2) {
            val on = ((rig.time * 2.5f).toInt() % 4) > i
            drawCircle(white.copy(alpha = if (on) a else a * 0.25f), 1.8f * k, Offset(c.x + (i - 1) * 5.5f * k, c.y))
        }
        EmoteKind.IDEA -> {
            drawCircle(Brush.radialGradient(listOf(amber.copy(alpha = 0.5f * a), Color.Transparent), center = c, radius = 12f * k), 12f * k, c)
            drawCircle(amber, 4.5f * k, c)
            drawRoundRect(white, Offset(c.x - 2.2f * k, c.y + 4f * k), Size(4.4f * k, 3f * k), CornerRadius(1f * k))
        }
    }
}
