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
import androidx.compose.ui.graphics.lerp
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
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
 * How the room lights Pipo this frame. [dir] is the horizontal direction the key light comes
 * FROM on screen (-1 = from the left, +1 = from the right). Computed from the real light
 * sources in his room (window by day, desk lamp at night, charger, torch) — see [pipoLight].
 */
data class PipoLight(
    val dir: Float = -0.55f,
    val key: Color = Color(0xFFFFF6EA),
    val keyStrength: Float = 0.75f,
    val rim: Color = Color(0xFF8FF5E2),
    val rimStrength: Float = 0.3f,
    val ambient: Color = Color(0xFF45506A),
    val shadowStretch: Float = 1f,
)

/**
 * Draws Pipo with his feet at ([footX], [footY]) and total height [height] px.
 * [lift] = extra px above the floor (being carried).
 *
 * Rendering is 2.5D: every body part has a position in a small local 3D space (x across,
 * z towards the viewer). Parts are projected through Pipo's current yaw, depth-sorted, and
 * shaded from [light]. That's what lets him turn to profile, show his back when he sulks,
 * and pick up the lamp's warm light on one side when he walks past it.
 */
fun DrawScope.drawPipo(rig: PipoRig, footX: Float, footY: Float, height: Float, lift: Float = 0f, shadow: Boolean = true, light: PipoLight = PipoLight()) {
    val k = height / 100f
    val p = rig.pose
    val glow = Color(rig.glow)
    val air = p.jump * k + lift
    if (shadow && p.lie < 0.5f) drawContactShadow(footX, footY, k, air, light)
    withTransform({
        translate(footX + p.shakeX * k, footY - air - p.bob * k)
        rotate(-90f * p.lie, pivot = Offset(0f, -30f * k))
        rotate(p.lean, pivot = Offset.Zero)
        scale(rig.stretchNow, rig.squashNow, pivot = Offset.Zero)
    }) {
        drawPipoLocal(rig, k, glow, light)
    }
}

/** Soft contact shadow: tight and dark at the feet, wider and fainter when he's in the air, pushed away from the light. */
private fun DrawScope.drawContactShadow(footX: Float, footY: Float, k: Float, air: Float, light: PipoLight) {
    val up = (air / (60f * k)).coerceIn(0f, 0.6f)
    val stretch = 1f + abs(light.dir) * 0.45f * light.shadowStretch * light.keyStrength
    val w = 46f * k * (1f - up * 0.5f) * stretch
    val cx = footX - light.dir * (5f * k + air * 0.25f) * light.keyStrength
    val c = Offset(cx, footY - 1.5f * k)
    val a = (0.34f + 0.12f * light.keyStrength) * (1f - up)
    withTransform({ scale(1f, 0.16f, pivot = c) }) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = a), Color.Black.copy(alpha = a * 0.45f), Color.Transparent), center = c, radius = w / 2f), w / 2f, c)
    }
    // ambient occlusion right under the feet
    drawOval(Color.Black.copy(alpha = 0.22f * (1f - up * 1.5f).coerceAtLeast(0f)), Offset(footX - 15f * k, footY - 2.6f * k), Size(30f * k, 3.4f * k))
}

private fun DrawScope.drawPipoLocal(rig: PipoRig, k: Float, glow: Color, L: PipoLight) {
    val p = rig.pose
    val f = rig.face
    val C = PipoColors
    val drop = p.sit * 9f * k + p.crouch * 8f * k
    val headDrop = drop + p.crouch * 6f * k + p.headBob * k

    val bodyYaw = rig.yaw + p.spin
    val cy = cos(bodyYaw); val sy = sin(bodyYaw)
    fun px(x: Float, z: Float) = (x * cy + z * sy) * k
    fun depth(x: Float, z: Float) = z * cy - x * sy

    // ---------- lighting helpers
    val lightSide = if (L.dir == 0f) -1f else sign(L.dir)
    val keyTint = lerp(Color.White, L.key, 0.55f)
    fun litColor(base: Color, amt: Float) = lerp(base, keyTint, (amt * L.keyStrength * 0.5f).coerceIn(0f, 1f))
    fun darkColor(base: Color, amt: Float) = lerp(base, L.ambient, (amt * (0.35f + 0.25f * L.keyStrength)).coerceIn(0f, 0.8f))

    /** A lit, rounded shell part: horizontal key/shade gradient, top sheen, rim light and a specular glint. */
    fun shell(l: Float, t: Float, w: Float, h: Float, r: Float, base: Color, spec: Boolean = true, rimOn: Boolean = true) {
        if (w <= 0.5f || h <= 0.5f) return
        val rr = CornerRadius(min(r, min(w, h) / 2f))
        val a = litColor(base, 0.6f); val b = darkColor(base, 0.9f)
        val cols = if (lightSide < 0) listOf(a, base, b) else listOf(b, base, a)
        drawRoundRect(Brush.horizontalGradient(cols, startX = l, endX = l + w), Offset(l, t), Size(w, h), rr)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, L.ambient.copy(alpha = 0.22f)), startY = t, endY = t + h),
            Offset(l, t), Size(w, h), rr)
        if (rimOn && L.rimStrength > 0.02f) {
            val rimCols = if (lightSide < 0) listOf(Color.Transparent, Color.Transparent, L.rim.copy(alpha = L.rimStrength)) else listOf(L.rim.copy(alpha = L.rimStrength), Color.Transparent, Color.Transparent)
            drawRoundRect(Brush.horizontalGradient(rimCols, startX = l, endX = l + w), Offset(l, t), Size(w, h), rr, style = Stroke(1.5f * k))
        }
        if (spec) {
            val sx = l + w * (if (lightSide < 0) 0.22f else 0.58f)
            drawOval(Color.White.copy(alpha = 0.35f + 0.3f * L.keyStrength), Offset(sx, t + h * 0.06f), Size(w * 0.2f, h * 0.09f))
        }
    }

    // ---------- legs + feet (far leg first)
    val legs = intArrayOf(-1, 1).sortedBy { depth(it * 8f, 0f) }
    for (side in legs) {
        val lift = if (side < 0) p.legL else p.legR
        val far = depth(side * 8f, 0f) < -2f
        val spread = p.legSpread + p.sit * 3f
        val hip = Offset(px(side * 8f, 0f), -12f * k + drop)
        val foot = Offset(px(side * (8f + spread), 2f), (-2.5f - lift) * k)
        drawLine(if (far) lerp(C.joint, Color.Black, 0.25f) else C.joint, hip, foot, strokeWidth = 6f * k, cap = StrokeCap.Round)
        val fw = (12f * abs(cy) + 15f * abs(sy)) * k
        val fc = Offset(px(side * (8f + spread), 3.5f), foot.y)
        val base = if (far) darkColor(C.shell, 0.6f) else C.shell
        drawOval(darkColor(C.shellShade, 0.5f), Offset(fc.x - fw / 2f - 0.5f * k, fc.y - 2f * k), Size(fw + k, 5.5f * k))
        drawOval(Brush.horizontalGradient(if (lightSide < 0) listOf(litColor(base, 0.5f), base) else listOf(base, litColor(base, 0.5f)), startX = fc.x - fw / 2f, endX = fc.x + fw / 2f),
            Offset(fc.x - fw / 2f, fc.y - 2.4f * k), Size(fw, 4.2f * k))
    }

    // ---------- arms: compute both, draw the far one behind the body
    data class Arm(val side: Int, val sh: Offset, val hand: Offset, val d: Float)
    val headTiltPivot = Offset(0f, -40f * k + headDrop)
    val arms = intArrayOf(-1, 1).map { side ->
        val a = Math.toRadians((if (side < 0) p.armL else p.armR).toDouble())
        val sh = Offset(px(side * 14f, 0f), -31f * k + drop)
        val lateral = side * sin(a).toFloat() * 13f
        val down = cos(a).toFloat() * 13f
        val fwd = sin(a).toFloat().coerceAtLeast(0f) * 3f
        var hand = Offset(sh.x + (lateral * cy + fwd * sy) * k, sh.y + down * k)
        if (p.cross > 0.01f) hand = lerp(hand, Offset(px(-side * 6f, 13f), -27f * k + drop), p.cross)
        if (p.hold > 0.01f) hand = lerp(hand, Offset(px(side * 7f, 14f), -42f * k + drop), p.hold)
        if (p.cover > 0.01f) hand = lerp(hand, Offset(px(side * 12f, 26f), -64f * k + headDrop), p.cover)
        val d = depth(side * 14f, 0f) + p.cross * 14f + p.hold * 14f + p.cover * 20f
        Arm(side, sh, hand, d)
    }
    fun drawArm(arm: Arm, far: Boolean) {
        val col = if (far) darkColor(C.arm, 0.7f) else C.arm
        drawLine(col, arm.sh, arm.hand, strokeWidth = 5.2f * k, cap = StrokeCap.Round)
        drawCircle(darkColor(C.shellShade, if (far) 0.8f else 0.3f), 3.9f * k, arm.hand)
        drawCircle(if (far) darkColor(C.shell, 0.6f) else litColor(C.shell, 0.3f), 3.2f * k, Offset(arm.hand.x - 0.4f * k * lightSide, arm.hand.y - 0.4f * k))
    }
    val sideOn = abs(sy) > 0.22f
    val behind = arms.filter { sideOn && it.d < -3f }
    behind.forEach { drawArm(it, true) }

    // ---------- torso
    run {
        val bodyTop = -37f * k + drop
        val bodyBot = -12f * k + drop
        val bw = (30f * abs(cy) + 20f * abs(sy)) * k
        shell(-bw / 2f, bodyTop, bw, bodyBot - bodyTop, 11f * k, C.shell)
        // head's shadow on the top of the body (ambient occlusion)
        drawOval(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), startY = bodyTop, endY = bodyTop + 6f * k),
            Offset(-bw / 2f, bodyTop - 1f * k), Size(bw, 7f * k))
        val fx = px(0f, 10f)
        if (cy > 0.05f) {
            val sc = cy.pow(0.8f)
            drawRoundRect(darkColor(C.belly, 0.25f), Offset(fx - 9f * k * sc, bodyTop + 6f * k), Size(18f * k * sc, 13f * k), CornerRadius(6f * k * sc))
            val chest = Offset(fx, -24.5f * k + drop)
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), center = chest, radius = 9f * k), 9f * k, chest)
            drawOval(glow, Offset(chest.x - 2.8f * k * sc, chest.y - 2.8f * k), Size(5.6f * k * sc, 5.6f * k))
            drawCircle(Color.White.copy(alpha = 0.6f), 0.9f * k, Offset(chest.x - 0.9f * k, chest.y - 1f * k))
        } else if (cy < -0.05f) {
            // his back: a battery hatch with two tiny screws
            val bx = px(0f, -10f); val sc = (-cy).pow(0.8f)
            drawRoundRect(darkColor(C.shellShade, 0.3f), Offset(bx - 8f * k * sc, bodyTop + 5f * k), Size(16f * k * sc, 14f * k), CornerRadius(3f * k))
            for (i in 0..2) drawLine(darkColor(C.shellShade, 0.9f), Offset(bx - 5f * k * sc, bodyTop + (8f + i * 3f) * k), Offset(bx + 5f * k * sc, bodyTop + (8f + i * 3f) * k), 0.8f * k)
            drawCircle(C.joint, 0.7f * k, Offset(bx - 6.5f * k * sc, bodyTop + 6.5f * k)); drawCircle(C.joint, 0.7f * k, Offset(bx + 6.5f * k * sc, bodyTop + 6.5f * k))
        }
        drawRoundRect(C.joint, Offset(-5f * k * (0.6f + 0.4f * abs(cy)), -41f * k + drop), Size(10f * k * (0.6f + 0.4f * abs(cy)), 5f * k), CornerRadius(2f * k))
    }

    // ---------- head (uses its own yaw so the head can lead the body)
    fun hy(v: Float) = v * k + headDrop
    rotate(p.headTilt, pivot = headTiltPivot) {
        val hYaw = rig.headYaw + p.spin
        val hc = cos(hYaw); val hs = sin(hYaw)
        fun hx(x: Float, z: Float) = (x * hc + z * hs) * k
        fun hdepth(x: Float, z: Float) = z * hc - x * hs

        // ear pods: far one first
        val ears = intArrayOf(-1, 1).sortedBy { hdepth(it * 33f, 0f) }
        fun ear(side: Int) {
            val ec = Offset(hx(side * 33f, 0f), hy(-65f))
            val far = hdepth(side * 33f, 0f) < 0f
            drawCircle(darkColor(C.shellShade, if (far) 0.8f else 0.2f), 6.5f * k, ec)
            drawCircle(glow.copy(alpha = if (far) 0.45f else 0.85f), 3.1f * k, ec)
            if (!far) drawCircle(Color.White.copy(alpha = 0.4f), 1f * k, Offset(ec.x - 1f * k, ec.y - 1f * k))
        }
        ear(ears[0])

        // antenna (spring-driven angle)
        val base = Offset(hx(0f, -2f), hy(-89f))
        rotate(rig.antenna, pivot = base) {
            drawLine(C.joint, base, Offset(base.x, base.y - 11f * k), strokeWidth = 2.4f * k, cap = StrokeCap.Round)
            val bulb = Offset(base.x, base.y - 14f * k)
            val halo = if (rig.torch) 34f * k else 11f * k
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = if (rig.torch) 0.85f else 0.45f), Color.Transparent), center = bulb, radius = halo), halo, bulb)
            drawCircle(if (rig.torch) Color.White else glow, 4f * k, bulb)
            drawCircle(Color.White.copy(alpha = 0.7f), 1.4f * k, Offset(bulb.x - 1.2f * k, bulb.y - 1.2f * k))
        }

        // shell: silhouette of a rounded box seen from this yaw
        val hw = (64f * abs(hc) + 46f * abs(hs)) * k
        shell(-hw / 2f, hy(-90f), hw, 50f * k, 22f * k, C.shell)
        // visible side panel (the side of his head), slightly darker, with a seam line
        if (abs(hs) > 0.06f) {
            val sideSign = -sign(hs) * sign(hc).let { if (it == 0f) 1f else it }
            val pw = 46f * abs(hs) * k
            val edge = sideSign * hw / 2f
            val l = if (sideSign < 0) edge else edge - pw
            val sideLit = sideSign == lightSide
            drawRoundRect((if (sideLit) litColor(C.shellShade, 0.4f) else darkColor(C.shellShade, 0.4f)).copy(alpha = 0.55f),
                Offset(l, hy(-86f)), Size(pw, 42f * k), CornerRadius(min(14f * k, pw / 2f)))
            val seamX = if (sideSign < 0) l + pw else l
            drawLine(Color.Black.copy(alpha = 0.08f), Offset(seamX, hy(-84f)), Offset(seamX, hy(-44f)), 0.8f * k)
        }

        if (hc > 0.03f) {
            // ---- face screen, projected onto the front of the head
            val sc = hc.pow(0.75f)
            val scx = hx(0f, 23f)
            val sw = 52f * k * sc; val sh = 37f * k; val sl = scx - sw / 2f; val st = hy(-83f)
            val rad = CornerRadius(min(14f * k, sw / 2f))
            drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(sl - 1f * k, st - 1f * k), Size(sw + 2f * k, sh + 2f * k), CornerRadius(rad.x + k))
            drawRoundRect(C.screen, Offset(sl, st), Size(sw, sh), rad)
            val eyeCol = C.eye.copy(alpha = (0.55f + 0.45f * rig.eyeGlow + 0.15f * rig.speakGlow).coerceIn(0f, 1f))
            val clip = Path().apply { addRoundRect(RoundRect(sl, st, sl + sw, st + sh, rad)) }
            clipPath(clip) {
                drawRect(Brush.radialGradient(listOf(eyeCol.copy(alpha = 0.12f), Color.Transparent), center = Offset(scx, hy(-65f)), radius = 30f * k),
                    Offset(sl, st), Size(sw, sh))
                for (side in intArrayOf(-1, 1)) {
                    val sideScale = if (side > 0) 1f + f.asym else 1f - f.asym * 0.5f
                    val near = 1f - side * hs * 0.14f
                    val ex = scx + (side * 12f * sc + rig.lookX * 4.5f * sc) * k
                    val ey = hy(-66f) + rig.lookY * 3f * k
                    drawEye(ex, ey, 5.2f * k * f.scale * sideScale * near * sc.pow(0.4f), 7f * k * f.scale * sideScale * near, f, rig, side, eyeCol)
                    if (f.blush > 0.01f) drawOval(C.blush.copy(alpha = 0.6f * f.blush), Offset(scx + side * 19f * k * sc - 4.5f * k * sc, hy(-57.5f)), Size(9f * k * sc, 3.6f * k))
                }
                drawMouth(rig, k, scx, sc, hy(-53.5f), eyeCol)
                // glass: a diagonal reflection band + a hint of the room light
                val g0 = Offset(sl + sw * (if (lightSide < 0) 0.05f else 0.95f), st)
                val g1 = Offset(sl + sw * (if (lightSide < 0) 0.45f else 0.55f), st + sh)
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.03f), Color.Transparent), start = g0, end = g1), Offset(sl, st), Size(sw, sh))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, L.key.copy(alpha = 0.05f * L.keyStrength)), startY = st + sh * 0.7f, endY = st + sh), Offset(sl, st), Size(sw, sh))
            }
            drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(sl + 3f * k, st + 0.6f * k), Size(max(0f, sw - 6f * k), 1f * k), CornerRadius(0.5f * k))
        } else if (hc < -0.03f) {
            // back of his head: vents
            val bx = hx(0f, -23f); val sc = (-hc).pow(0.75f)
            for (i in 0..3) drawRoundRect(darkColor(C.shellShade, 0.7f), Offset(bx - 12f * k * sc, hy(-78f + i * 6f)), Size(24f * k * sc, 2f * k), CornerRadius(1f * k))
            drawCircle(darkColor(C.shellShade, 0.5f), 2.5f * k * sc, Offset(bx, hy(-50f)))
        }
        ear(ears[1])
    }

    // ---- held item / book (between the hands)
    if (p.hold > 0.5f) {
        val hc = Offset(px(0f, 15f), -42f * k + drop)
        val shape = rig.holdItem
        if (shape != null) drawItem(shape, hc, 13f * k)
        else if (rig.anim == AnimState.READING) {
            drawRoundRect(Color(0xFF6F8FA6), Offset(hc.x - 8f * k, hc.y - 5f * k), Size(16f * k, 10f * k), CornerRadius(1.5f * k))
            drawLine(Color(0xFFF4EFE6), Offset(hc.x, hc.y - 4.5f * k), Offset(hc.x, hc.y + 4.5f * k), 1.2f * k)
        }
    }

    // ---- near arms last so hands can cover the face / cross in front
    arms.filter { it !in behind }.sortedBy { it.d }.forEach { drawArm(it, false) }
}

private fun DrawScope.drawEye(cx: Float, cy: Float, hw: Float, hh: Float, f: Face, rig: PipoRig, side: Int, col: Color) {
    val scr = PipoColors.screen
    val winkHere = if (f.wink * side > 0f) abs(f.wink) else 0f
    val closedHere = max(f.closed, winkHere)
    if (f.spiral > 0.3f) {
        // dizzy: rotating spiral
        val a = f.spiral
        val path = Path()
        val turns = 2.2f
        val steps = 28
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val ang = u * turns * 2f * PI.toFloat() + rig.time * 6f * side
            val r = u * hw * 1.1f
            val x = cx + cos(ang) * r; val y = cy + sin(ang) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, col.copy(alpha = a), style = Stroke(width = hw * 0.32f, cap = StrokeCap.Round))
        if (a > 0.95f) return
    }
    val pill = (1f - max(f.arc, closedHere) - f.spiral).coerceIn(0f, 1f)
    val blink = rig.blink
    if (pill > 0.01f) {
        val w = hw * (1f + f.round * 0.2f)
        val h = ((hh + (w - hh) * f.round) * (f.open * blink)).coerceAtLeast(hw * 0.12f)
        drawRoundRect(col.copy(alpha = 0.16f * pill), Offset(cx - w * 1.6f, cy - h * 1.4f), Size(w * 3.2f, h * 2.8f), CornerRadius(w * 1.6f))
        drawRoundRect(col.copy(alpha = pill), Offset(cx - w, cy - h), Size(2f * w, 2f * h), CornerRadius(min(w, h)))
        // pupil core: brighter, and it travels a little further than the eye → reads as looking
        if (h > w * 0.35f) {
            val pr = w * 0.5f * f.pupil
            val pc = Offset(cx + rig.lookX * w * 0.32f, cy + rig.lookY * h * 0.3f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * pill), col.copy(alpha = 0f)), center = pc, radius = pr.coerceAtLeast(0.1f)), pr.coerceAtLeast(0.1f), pc)
        }
        if (f.round > 0.3f) drawCircle(scr.copy(alpha = pill * f.round), w * 0.38f * (0.7f + 0.3f * f.pupil), Offset(cx + rig.lookX * w * 0.2f, cy))
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
    if (closedHere > 0.01f) {
        val path = Path().apply {
            moveTo(cx - hw * 1.1f, cy - hh * 0.1f)
            quadraticBezierTo(cx, cy + hh * 0.9f, cx + hw * 1.1f, cy - hh * 0.1f)
        }
        drawPath(path, col.copy(alpha = closedHere * 0.9f), style = Stroke(width = hw * 0.6f, cap = StrokeCap.Round))
    }
    if (f.tear > 0.05f && side < 0) {
        val cyc = (rig.time * 0.35f) % 1f
        val ty = cy + hh * 1.2f + cyc * hh * 1.6f
        val d = Path().apply {
            moveTo(cx, ty - hw * 0.5f)
            quadraticBezierTo(cx + hw * 0.4f, ty + hw * 0.15f, cx, ty + hw * 0.35f)
            quadraticBezierTo(cx - hw * 0.4f, ty + hw * 0.15f, cx, ty - hw * 0.5f)
            close()
        }
        drawPath(d, Color(0xFF9AD0FF).copy(alpha = f.tear * (1f - cyc)))
    }
}

private fun DrawScope.drawMouth(rig: PipoRig, k: Float, cx: Float, sc: Float, my: Float, col: Color) {
    val f = rig.face
    val mx = cx + rig.lookX * 2f * k * sc
    val mw = 6.5f * k * f.mouthWidth * sc
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
            // speaker grille glow inside the open mouth
            drawCircle(Color.White.copy(alpha = 0.18f * open), mw * 0.35f, Offset(mx, my + open * 3f * k))
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
