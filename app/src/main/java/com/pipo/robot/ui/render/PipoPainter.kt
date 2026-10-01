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

/** Head depth front-to-back (Pipo units; head is 64 wide). Shallow enough that his silhouette stays round when he turns. */
private const val HEAD_DEPTH = 42f

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
    // rocket boots: flames under his feet whenever he leaves the ground
    if (rig.rocketBoots && air > 2f * k) for (side in listOf(-1f, 1f)) {
        val fc = Offset(footX + side * 7f * k, footY - air + 4f * k)
        val fl = 1f + 0.3f * kotlin.math.sin(rig.time * 30f + side)
        drawOval(Color(0xFFFF9A4A).copy(alpha = 0.85f), Offset(fc.x - 2.5f * k, fc.y), Size(5f * k, 9f * k * fl))
        drawOval(Color(0xFFFFE9A0), Offset(fc.x - 1.2f * k, fc.y), Size(2.4f * k, 5f * k * fl))
    }
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
        // a soft edge line in the room's ambient colour: crisp silhouette, no black cartoon outline
        drawRoundRect(lerp(L.ambient, Color.Black, 0.3f).copy(alpha = 0.28f), Offset(l, t), Size(w, h), rr, style = Stroke(0.8f * k))
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
        // Depth follows the HAND: crossed/holding/covering hands sit in front of his chest, which may face away.
        var d = depth(side * 14f, 0f)
        if (p.cross > 0.01f) d += (depth(-side * 6f, 13f) - d) * p.cross
        if (p.hold > 0.01f) d += (depth(side * 7f, 14f) - d) * p.hold
        if (p.cover > 0.01f) d += (depth(side * 12f, 26f) - d) * p.cover
        Arm(side, sh, hand, d)
    }
    fun drawArm(arm: Arm, far: Boolean) {
        val col = if (far) darkColor(C.arm, 0.7f) else C.arm
        drawLine(col, arm.sh, arm.hand, strokeWidth = 5.2f * k, cap = StrokeCap.Round)
        drawCircle(darkColor(C.shellShade, if (far) 0.8f else 0.3f), 3.9f * k, arm.hand)
        drawCircle(if (far) darkColor(C.shell, 0.6f) else litColor(C.shell, 0.3f), 3.2f * k, Offset(arm.hand.x - 0.4f * k * lightSide, arm.hand.y - 0.4f * k))
    }
    val behind = arms.filter { it.d < -3f }
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
        // the armor: red plates, gold trim, and (Mk III) a glowing core
        if (rig.suit > 0) {
            val red = Color(0xFFB8262E); val gold = Color(0xFFE2B24A)
            drawRoundRect(Brush.verticalGradient(listOf(lerp(red, Color.White, 0.18f), red, lerp(red, Color.Black, 0.3f)), startY = bodyTop, endY = bodyBot),
                Offset(-bw / 2f + 1f * k, bodyTop + 1f * k), Size(bw - 2f * k, bodyBot - bodyTop - 2f * k), CornerRadius(9f * k))
            drawRoundRect(gold, Offset(-bw / 2f + 1f * k, bodyBot - 6f * k), Size(bw - 2f * k, 3f * k), CornerRadius(1.5f * k)) // belt
            if (cy > 0.05f) {
                val core = Offset(px(0f, 11f), -25f * k + drop)
                val coreCol = if (rig.suit >= 3) Color(0xFF9FF3FF) else gold
                if (rig.suit >= 3) drawCircle(Brush.radialGradient(listOf(coreCol.copy(alpha = 0.7f), Color.Transparent), core, 11f * k), 11f * k, core)
                drawCircle(Color(0xFF2B3342), 4.4f * k, core); drawCircle(coreCol, 3.4f * k, core); drawCircle(Color.White.copy(alpha = 0.7f), 1.2f * k, Offset(core.x - 1f * k, core.y - 1f * k))
            }
            for (side in listOf(-1f, 1f)) drawCircle(gold, 5f * k, Offset(side * bw / 2f, bodyTop + 4f * k)) // shoulder plates
        }
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
        val hw = (64f * abs(hc) + HEAD_DEPTH * abs(hs)) * k
        shell(-hw / 2f, hy(-90f), hw, 50f * k, 22f * k, C.shell)
        val headClip = Path().apply { addRoundRect(RoundRect(-hw / 2f, hy(-90f), hw / 2f, hy(-40f), CornerRadius(min(22f * k, hw / 2f)))) }
        // visible side panel (the side of his head), slightly darker, with a seam line
        if (abs(hs) > 0.06f) clipPath(headClip) {
            val sideSign = -sign(hs) * sign(hc).let { if (it == 0f) 1f else it }
            val pw = HEAD_DEPTH * abs(hs) * k
            val edge = sideSign * hw / 2f
            val l = if (sideSign < 0) edge else edge - pw
            val sideLit = sideSign == lightSide
            drawRoundRect((if (sideLit) litColor(C.shellShade, 0.4f) else darkColor(C.shellShade, 0.4f)).copy(alpha = 0.55f),
                Offset(l, hy(-86f)), Size(pw, 42f * k), CornerRadius(min(14f * k, pw / 2f)))
            val seamX = if (sideSign < 0) l + pw else l
            drawLine(Color.Black.copy(alpha = 0.08f), Offset(seamX, hy(-84f)), Offset(seamX, hy(-44f)), 0.8f * k)
        }

        if (hc > -0.08f && hc < 0.35f && abs(hs) > 0.5f) clipPath(headClip) {
            // near profile: the dark edge of the face screen hugs the front of his head
            val front = sign(hs)
            val edgeX = front * hw / 2f
            val bw = (4.5f * k) * (1f - abs(hc) / 0.35f).coerceIn(0.3f, 1f)
            val l = if (front > 0f) edgeX - bw else edgeX
            drawRoundRect(C.screen.copy(alpha = 0.9f), Offset(l, hy(-83f)), Size(bw, 37f * k), CornerRadius(bw / 2f))
            drawRoundRect(C.eye.copy(alpha = 0.35f * rig.eyeGlow), Offset(l + (if (front > 0f) 0f else bw * 0.5f), hy(-72f)), Size(bw * 0.5f, 10f * k), CornerRadius(bw / 4f))
        }
        if (hc > 0.03f) {
            // ---- face screen, projected onto the front of the head
            val sc = hc.pow(0.5f)
            val scx = hx(0f, HEAD_DEPTH / 2f)
            val sw = 52f * k * sc; val sh = 37f * k; val sl = scx - sw / 2f; val st = hy(-83f)
            val rad = CornerRadius(min(14f * k, sw / 2f))
            drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(sl - 1f * k, st - 1f * k), Size(sw + 2f * k, sh + 2f * k), CornerRadius(rad.x + k))
            drawRoundRect(C.screen, Offset(sl, st), Size(sw, sh), rad)
            val eyeCol = C.eye.copy(alpha = (0.55f + 0.45f * rig.eyeGlow + 0.15f * rig.speakGlow).coerceIn(0f, 1f))
            val clip = Path().apply { addRoundRect(RoundRect(sl, st, sl + sw, st + sh, rad)) }
            clipPath(clip) {
                for (side in intArrayOf(-1, 1)) {
                    val sideScale = if (side > 0) 1f + f.asym else 1f - f.asym * 0.5f
                    val near = 1f - side * hs * 0.14f
                    val ex = scx + (side * 12f * sc + rig.lookX * 4.5f * sc) * k
                    val ey = hy(-66f) + rig.lookY * 3f * k
                    drawEye(ex, ey, 5.2f * k * f.scale * sideScale * near * sc.pow(0.4f), 7f * k * f.scale * sideScale * near, f, rig, side, eyeCol)
                    if (f.blush > 0.01f) drawOval(C.blush.copy(alpha = 0.6f * f.blush), Offset(scx + side * 19f * k * sc - 4.5f * k * sc, hy(-57.5f)), Size(9f * k * sc, 3.6f * k))
                }
                drawMouth(rig, k, scx, sc, hy(-53.5f), eyeCol)
                // eye glow spilling on the screen, over eyes AND lids so lid shapes never show seams
                drawRect(Brush.radialGradient(listOf(eyeCol.copy(alpha = 0.12f), Color.Transparent), center = Offset(scx, hy(-65f)), radius = 30f * k),
                    Offset(sl, st), Size(sw, sh))
                // glass: a diagonal reflection band + a hint of the room light
                val g0 = Offset(sl + sw * (if (lightSide < 0) 0.05f else 0.95f), st)
                val g1 = Offset(sl + sw * (if (lightSide < 0) 0.45f else 0.55f), st + sh)
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.03f), Color.Transparent), start = g0, end = g1), Offset(sl, st), Size(sw, sh))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, L.key.copy(alpha = 0.05f * L.keyStrength)), startY = st + sh * 0.7f, endY = st + sh), Offset(sl, st), Size(sw, sh))
            }
            drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(sl + 3f * k, st + 0.6f * k), Size(max(0f, sw - 6f * k), 1f * k), CornerRadius(0.5f * k))
        } else if (hc < -0.03f) {
            // back of his head: vents
            val bx = hx(0f, -HEAD_DEPTH / 2f); val sc = (-hc).pow(0.5f)
            for (i in 0..3) drawRoundRect(darkColor(C.shellShade, 0.7f), Offset(bx - 12f * k * sc, hy(-78f + i * 6f)), Size(24f * k * sc, 2f * k), CornerRadius(1f * k))
            drawCircle(darkColor(C.shellShade, 0.5f), 2.5f * k * sc, Offset(bx, hy(-50f)))
        }
        ear(ears[1])
        rig.hat?.let { drawHat(it, Offset(hx(0f, 0f), hy(-89f)), hw, rig.time) }
        // the helmet: red shell over his head, a gold faceplate with glowing slits (Mk III's flips up when he talks)
        if (rig.suit > 0) {
            val red = Color(0xFFB8262E); val gold = Color(0xFFE2B24A)
            drawRoundRect(Brush.verticalGradient(listOf(lerp(red, Color.White, 0.2f), red), startY = hy(-91f), endY = hy(-40f)), Offset(-hw / 2f - 1f * k, hy(-91f)), Size(hw + 2f * k, 9f * k), CornerRadius(8f * k))
            if (hc > 0.03f) {
                val sc = hc.pow(0.5f); val scx = hx(0f, HEAD_DEPTH / 2f)
                val sw = 52f * k * sc; val st = hy(-83f)
                val up = rig.suit >= 3 && (rig.visorUp || rig.talking)
                if (up) drawRoundRect(gold, Offset(scx - sw / 2f, st - 6f * k), Size(sw, 5f * k), CornerRadius(2.5f * k)) // flipped up
                else {
                    drawRoundRect(Brush.verticalGradient(listOf(lerp(gold, Color.White, 0.25f), gold, lerp(gold, Color.Black, 0.25f)), startY = st, endY = st + 37f * k),
                        Offset(scx - sw / 2f, st), Size(sw, 37f * k), CornerRadius(12f * k * sc))
                    for (side in listOf(-1f, 1f)) {
                        val ec = Offset(scx + side * 11f * k * sc, st + 15f * k)
                        drawCircle(Brush.radialGradient(listOf(Color(0xFF9FF3FF).copy(alpha = 0.6f), Color.Transparent), ec, 7f * k), 7f * k, ec)
                        drawRoundRect(Color(0xFFDFFBFF), Offset(ec.x - 6f * k * sc, ec.y - 1.4f * k), Size(12f * k * sc, 2.8f * k), CornerRadius(1.4f * k))
                    }
                    drawLine(lerp(gold, Color.Black, 0.35f), Offset(scx - 8f * k * sc, st + 28f * k), Offset(scx + 8f * k * sc, st + 28f * k), 1.2f * k)
                }
            }
        }
    }

    // ---- held item / book (between the hands)
    if (p.hold > 0.5f) {
        val hc = Offset(px(0f, 15f), -42f * k + drop)
        val shape = rig.holdItem
        if (shape != null) drawItem(shape, hc, 13f * k)
        else if (rig.anim == AnimState.PHONE || rig.anim == AnimState.PHOTO) drawTinyPhone(hc, k, rig.time, if (rig.anim == AnimState.PHOTO) com.pipo.robot.engine.PhoneApp.CAMERA else rig.phoneApp)
        else if (rig.anim == AnimState.GAMING) drawController(hc, k, rig.time)
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

/** Colours of the "reels" on Pipo's phone; each swipe shows the next one. */
private val reelColors = listOf(Color(0xFFFF8A7A), Color(0xFF7FD6FF), Color(0xFFFFD36B), Color(0xFFB690FF), Color(0xFF8BE8B0))

/** Pipo's tiny phone, held in both hands; the screen light spills onto his face. */
/** His phone, showing the app he's using. The screen light spills onto his face and hands. */
private fun DrawScope.drawTinyPhone(c: Offset, k: Float, time: Float, app: com.pipo.robot.engine.PhoneApp? = null) {
    if (app != null && app != com.pipo.robot.engine.PhoneApp.FEED) { drawPhoneApp(c, k, time, app); return }
    val swipe = time / 1.7f
    val idx = swipe.toInt()
    val ph = swipe - idx
    val col = reelColors[idx % reelColors.size]
    val w = 7.5f * k; val h = 12f * k
    val l = c.x - w / 2f; val t = c.y - h * 0.62f
    // screen glow on his face and hands
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.22f), Color.Transparent), center = Offset(c.x, t), radius = 16f * k), 16f * k, Offset(c.x, t))
    drawRoundRect(Color(0xFF1B1F2B), Offset(l, t), Size(w, h), CornerRadius(1.6f * k))
    val sl = l + 0.7f * k; val st = t + 0.9f * k; val sw = w - 1.4f * k; val sh = h - 1.8f * k
    // the reel: a swipe slides the next one up
    val cover = if (ph < 0.12f) ph / 0.12f else 1f   // how far the new reel has slid up
    val prev = reelColors[(idx + reelColors.size - 1) % reelColors.size]
    val base = if (cover < 1f) prev else col
    drawRoundRect(Brush.verticalGradient(listOf(base, lerp(base, Color.Black, 0.35f)), startY = st, endY = st + sh), Offset(sl, st), Size(sw, sh), CornerRadius(0.9f * k))
    // the swipe: the next reel slides up over the old one
    if (cover < 1f) drawRoundRect(col, Offset(sl, st + sh * (1f - cover)), Size(sw, sh * cover), CornerRadius(0.9f * k))
    // a little blob "subject" bouncing in the video, a heart, and the progress bar
    drawCircle(Color.White.copy(alpha = 0.85f), 1.3f * k, Offset(sl + sw * 0.5f, st + sh * (0.5f - 0.12f * abs(sin(time * 6f)))))
    drawCircle(Color(0xFFFF5A7A), 0.55f * k, Offset(sl + sw - 1.1f * k, st + sh * 0.62f))
    drawRect(Color.White.copy(alpha = 0.35f), Offset(sl + 0.4f * k, st + sh - 0.8f * k), Size((sw - 0.8f * k), 0.3f * k))
    drawRect(Color.White, Offset(sl + 0.4f * k, st + sh - 0.8f * k), Size((sw - 0.8f * k) * ph, 0.3f * k))
}

private fun DrawScope.drawPhoneApp(c: Offset, k: Float, time: Float, app: com.pipo.robot.engine.PhoneApp) {
    val w = 7.5f * k; val h = 12f * k
    val l = c.x - w / 2f; val t = c.y - h * 0.62f
    val sl = l + 0.7f * k; val st = t + 0.9f * k; val sw = w - 1.4f * k; val sh = h - 1.8f * k
    val bg = when (app) {
        com.pipo.robot.engine.PhoneApp.CAMERA -> Color(0xFF2B3342); com.pipo.robot.engine.PhoneApp.WEATHER -> Color(0xFF6FA8E8)
        com.pipo.robot.engine.PhoneApp.NOTES -> Color(0xFFF3E6B8); com.pipo.robot.engine.PhoneApp.RECORDER -> Color(0xFF1B2230)
        com.pipo.robot.engine.PhoneApp.CALCULATOR -> Color(0xFF20242F); com.pipo.robot.engine.PhoneApp.MAP -> Color(0xFFE8DCC2)
        else -> Color(0xFF8FA3A6)
    }
    drawCircle(Brush.radialGradient(listOf(bg.copy(alpha = 0.2f), Color.Transparent), center = Offset(c.x, t), radius = 15f * k), 15f * k, Offset(c.x, t))
    drawRoundRect(Color(0xFF1B1F2B), Offset(l, t), Size(w, h), CornerRadius(1.6f * k))
    drawRoundRect(bg, Offset(sl, st), Size(sw, sh), CornerRadius(0.9f * k))
    val ink = Color(0xFF2B3342)
    when (app) {
        com.pipo.robot.engine.PhoneApp.CAMERA -> {
            // viewfinder corners and a focus square that settles
            val f = 1f + 0.15f * abs(sin(time * 4f))
            drawRect(Color.White.copy(alpha = 0.8f), Offset(c.x - 1.4f * k * f, c.y - 2.5f * k - 1.4f * k * f), Size(2.8f * k * f, 2.8f * k * f), style = Stroke(0.25f * k))
            drawCircle(Color.White, 0.8f * k, Offset(c.x, st + sh - 1.1f * k))
        }
        com.pipo.robot.engine.PhoneApp.GALLERY -> for (i in 0 until 4) drawRect(listOf(Color(0xFF7FB77E), Color(0xFFE88B7A), Color(0xFF9CCBEA), Color(0xFFF3C35A))[i],
            Offset(sl + 0.4f * k + (i % 2) * sw * 0.5f, st + 0.5f * k + (i / 2) * sh * 0.3f), Size(sw * 0.45f, sh * 0.26f))
        com.pipo.robot.engine.PhoneApp.WEATHER -> {
            drawCircle(Color(0xFFFFE08A), 1.4f * k, Offset(c.x - 0.6f * k, st + 2.5f * k))
            drawCircle(Color.White, 1.2f * k, Offset(c.x + 0.8f * k, st + 3.2f * k)); drawCircle(Color.White, 0.9f * k, Offset(c.x - 0.3f * k, st + 3.5f * k))
            for (i in 0..2) drawRect(Color.White.copy(alpha = 0.7f), Offset(sl + 0.6f * k, st + (5.8f + i * 1.2f) * k), Size(sw - 1.2f * k, 0.35f * k))
        }
        com.pipo.robot.engine.PhoneApp.NOTES -> {
            // he's typing: the last line grows
            for (i in 0..3) {
                val len = if (i == 3) ((time * 0.8f) % 1f) else 0.6f + 0.3f * ((i * 7) % 3) / 2f
                drawRect(ink.copy(alpha = 0.6f), Offset(sl + 0.5f * k, st + (1.2f + i * 1.6f) * k), Size((sw - 1f * k) * len, 0.3f * k))
            }
        }
        com.pipo.robot.engine.PhoneApp.RECORDER -> {
            drawCircle(Color(0xFFE0605A).copy(alpha = 0.6f + 0.4f * abs(sin(time * 3f))), 0.5f * k, Offset(sl + 1f * k, st + 1f * k))
            for (i in 0 until 9) {
                val a = (0.3f + 0.7f * abs(sin(time * 7f + i * 1.3f))) * 2.2f * k
                drawRect(Color(0xFF8FF5E2), Offset(sl + 0.5f * k + i * sw / 9.5f, c.y - 2.2f * k - a / 2f), Size(0.35f * k, a))
            }
        }
        com.pipo.robot.engine.PhoneApp.CALCULATOR -> {
            drawRect(Color(0xFF8FF5E2).copy(alpha = 0.8f), Offset(sl + 0.5f * k, st + 0.8f * k), Size(sw - 1f * k, 1.4f * k))
            for (r in 0..2) for (col in 0..2) drawRoundRect(Color(0xFF3B4658), Offset(sl + 0.5f * k + col * sw * 0.31f, st + (3f + r * 2f) * k), Size(sw * 0.25f, 1.5f * k), CornerRadius(0.3f * k))
        }
        com.pipo.robot.engine.PhoneApp.MAP -> {
            drawLine(Color(0xFF9CC5DA), Offset(sl, st + sh * 0.3f), Offset(sl + sw, st + sh * 0.6f), 0.5f * k)
            drawLine(ink.copy(alpha = 0.4f), Offset(sl + sw * 0.2f, st + sh * 0.8f), Offset(sl + sw * 0.7f, st + sh * 0.25f), 0.3f * k)
            drawCircle(Color(0xFFE0605A), 0.6f * k, Offset(sl + sw * 0.7f, st + sh * 0.25f + sin(time * 4f) * 0.2f * k))
        }
        else -> Unit
    }
}

/** A small game controller with a glowing light bar. */
private fun DrawScope.drawController(c: Offset, k0: Float, time: Float) {
    val k = k0 * 1.3f // a little chunky so it reads against his white body
    val body = Color(0xFF3B4254)
    drawCircle(body, 3.4f * k, Offset(c.x - 4.6f * k, c.y + 1.2f * k))
    drawCircle(body, 3.4f * k, Offset(c.x + 4.6f * k, c.y + 1.2f * k))
    drawRoundRect(body, Offset(c.x - 6.5f * k, c.y - 2.6f * k), Size(13f * k, 5.2f * k), CornerRadius(2.4f * k))
    drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(c.x - 6f * k, c.y - 2.4f * k), Size(12f * k, 1.2f * k), CornerRadius(0.6f * k))
    drawRoundRect(Color(0xFF5AB8FF).copy(alpha = 0.75f + 0.25f * sin(time * 3f)), Offset(c.x - 3f * k, c.y - 2.9f * k), Size(6f * k, 0.8f * k), CornerRadius(0.4f * k))
    // d-pad and buttons (one lights up now and then: he's pressing it)
    drawRect(Color(0xFF151822), Offset(c.x - 5.6f * k, c.y - 0.3f * k), Size(2.4f * k, 0.8f * k))
    drawRect(Color(0xFF151822), Offset(c.x - 4.8f * k, c.y - 1.1f * k), Size(0.8f * k, 2.4f * k))
    val press = ((time * 7f).toInt() % 4)
    listOf(Offset(4.4f, -0.9f), Offset(5.5f, 0.1f), Offset(4.4f, 1.1f), Offset(3.3f, 0.1f)).forEachIndexed { i, o ->
        drawCircle(if (i == press) Color(0xFFBFE8FF) else Color(0xFF151822), 0.5f * k, Offset(c.x + o.x * k, c.y + o.y * k))
    }
}
