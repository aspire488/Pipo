package com.pipo.robot.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class PetAnim { IDLE, WALK, RUN, SLEEP, SIT, STARE, HOP, WAG, HIDE }

/** What Nib's one eye says. */
enum class PetFace { NEUTRAL, HAPPY, GRUMPY, CURIOUS, SAD, SMUG, LOVE, SURPRISED }

/** What Nib is thinking about, shown as a little picture above it (Nib doesn't do words). */
enum class PetThought { BALL, PIPO, BATTERY, FOOD, EXCLAIM, QUESTION, HEART, MUSIC }

/** Nib's body, for one frame. Small springs and a single eye that goes where Nib's attention goes. */
class PetRig(seed: Int = 7) {
    var anim = PetAnim.SLEEP
    /** -1 facing left, +1 right. Smoothed so turning isn't a snap. */
    var facing = 1f
    private var facingNow = 1f
    var time = 0f
        private set
    private var animT = 0f
    private var last = anim
    var lookX = 0f
    var lookY = 0f
    private var lookTX = 0f
    private var lookTY = 0f
    private var nextLook = 1f
    var blink = 1f
        private set
    private var blinkT = -1f
    private var nextBlink = 3f
    var tail = 0f
        private set
    private var tailVel = 0f
    var hop = 0f
        private set
    private val rng = Random(seed)
    /** Something Nib has in its mouth (on the way to hide it under the bed). */
    var carry: com.pipo.robot.data.ItemShape? = null
    /** Antlers at Christmas, a pumpkin at Halloween… */
    var hat: Hat? = null
    /** Pipo built Nib a light-up tail. */
    var tailGlow = false
    private var settleT = -1f
    /** Nib arrives somewhere: a little squash as it stops. */
    fun settle() { settleT = 0f }
    /** 0..1 squash from stopping, for the painter. */
    val settle get() = if (settleT < 0f) 0f else sin(PI.toFloat() * (settleT / 0.25f).coerceAtMost(1f))

    fun look(x: Float, y: Float) { lookTX = x.coerceIn(-1f, 1f); lookTY = y.coerceIn(-1f, 1f); nextLook = 1.5f }

    var face = PetFace.NEUTRAL
        private set
    private var faceUntil = 0f
    var thought: PetThought? = null
        private set
    var thoughtAge = 0f
        private set
    private var thoughtUntil = 0f

    /** Nib feels something for a moment (the eye shows it). */
    fun feel(f: PetFace, secs: Float = 2.5f) { face = f; faceUntil = time + secs }
    /** Nib thinks about something (a picture bubble). */
    fun think(t: PetThought, secs: Float = 2.8f) { thought = t; thoughtAge = 0f; thoughtUntil = time + secs }

    val facingSmooth get() = facingNow

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        time += dt
        if (anim != last) { animT = 0f; last = anim }
        animT += dt
        if (face != PetFace.NEUTRAL && time > faceUntil) face = PetFace.NEUTRAL
        if (thought != null) { thoughtAge += dt; if (time > thoughtUntil) thought = null }
        facingNow += (facing - facingNow) * (1f - exp(-dt * 10f))
        nextLook -= dt
        if (nextLook <= 0f && anim != PetAnim.STARE) {
            lookTX = rng.nextFloat() * 2f - 1f; lookTY = rng.nextFloat() - 0.5f
            nextLook = 0.8f + rng.nextFloat() * 2.2f
        }
        val lk = 1f - exp(-dt * 12f)
        lookX += (lookTX - lookX) * lk; lookY += (lookTY - lookY) * lk
        nextBlink -= dt
        if (blinkT < 0f && nextBlink <= 0f) blinkT = 0f
        if (blinkT >= 0f) {
            blinkT += dt
            blink = 1f - sin(PI.toFloat() * (blinkT / 0.14f).coerceAtMost(1f))
            if (blinkT >= 0.14f) { blinkT = -1f; blink = 1f; nextBlink = 1.5f + rng.nextFloat() * 3.5f }
        }
        // tail: a spring that wags when happy and whips when Nib moves
        val target = when (anim) {
            PetAnim.WAG -> sin(time * 18f) * 40f
            PetAnim.WALK -> sin(time * 9f) * 18f
            PetAnim.RUN -> -25f + sin(time * 14f) * 12f
            PetAnim.STARE -> 35f
            PetAnim.SLEEP, PetAnim.HIDE -> -40f
            else -> sin(time * 1.6f) * 10f
        }
        tailVel += ((target - tail) * 90f - tailVel * 7f) * dt
        tail += tailVel * dt
        if (settleT >= 0f) { settleT += dt; if (settleT > 0.25f) settleT = -1f }
        hop = when (anim) {
            PetAnim.HOP -> abs(sin(animT * 7f)) * 1f
            PetAnim.WAG -> abs(sin(time * 9f)) * 0.12f // too excited to stand still
            PetAnim.RUN -> abs(sin(time * 16f)) * 0.25f
            PetAnim.WALK -> abs(sin(time * 10f)) * 0.12f
            else -> 0f
        }
    }
}

private val petShell = Color(0xFFFFB86B)
private val petShade = Color(0xFFD9874A)
private val petBand = Color(0xFF3B4658)

/** Nib with feet at ([fx], [fy]), [h] px tall. */
fun DrawScope.drawPet(rig: PetRig, fx: Float, fy: Float, h: Float, light: PipoLight = PipoLight()) {
    val k = h / 12f
    val a = rig.anim
    val t = rig.time
    val sleeping = a == PetAnim.SLEEP
    val squash = when (a) {
        PetAnim.SLEEP -> 0.78f + sin(t * 1.4f) * 0.025f // breathing, slow
        PetAnim.HIDE -> 0.7f + sin(t * 30f) * 0.02f // trembling
        PetAnim.SIT -> 0.92f
        else -> 1f + sin(t * 2.2f) * 0.015f
    } * (1f - 0.12f * rig.settle)
    val lift = rig.hop * 5f * k
    val lean = when (a) { PetAnim.STARE -> 1.2f * k; PetAnim.RUN -> 1f * k; else -> 0f } * rig.facingSmooth
    val shake = if (a == PetAnim.HIDE) sin(t * 40f) * 0.25f * k else 0f
    // contact shadow
    drawOval(Color.Black.copy(alpha = 0.25f - rig.hop * 0.12f), Offset(fx - 6.5f * k, fy - 1f * k), Size(13f * k, 2.4f * k))
    val baseY = fy - lift - 2.2f * k * (if (sleeping) 0.2f else 1f)
    val cx = fx + lean + shake
    // legs
    if (!sleeping) {
        val step = when (a) { PetAnim.WALK -> sin(t * 10f); PetAnim.RUN -> sin(t * 16f); else -> 0f }
        for (side in listOf(-1f, 1f)) {
            val ph = if (side < 0) step else -step
            val lx = cx + side * 2.6f * k + ph * 0.8f * k * rig.facingSmooth
            drawRoundRect(petBand, Offset(lx - 0.9f * k, baseY - 0.6f * k), Size(1.8f * k, 2.8f * k + lift - max(0f, ph) * 0.8f * k), CornerRadius(0.8f * k))
        }
    }
    // tail with a bobble, behind the body
    run {
        val tailBase = Offset(cx - rig.facingSmooth * 5f * k, baseY - 3f * k * squash)
        val ang = (rig.tail + 110f) * PI.toFloat() / 180f
        val dir = -rig.facingSmooth
        val tip = Offset(tailBase.x + dir * kotlin.math.cos(ang).let { abs(it) } * 3.5f * k + dir * 1.2f * k, tailBase.y - sin(ang) * 4.2f * k)
        drawLine(petBand, tailBase, tip, 0.6f * k, StrokeCap.Round)
        if (rig.tailGlow) {
            val col = listOf(Color(0xFF8FF5E2), Color(0xFFFF8FD0), Color(0xFFFFE08A))[((t * 0.8f).toInt()) % 3]
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.6f), Color.Transparent), tip, 4f * k), 4f * k, tip)
            drawCircle(col, 1.3f * k, tip)
        } else drawCircle(Color(0xFF8FF5E2).copy(alpha = if (sleeping) 0.4f else 0.95f), 1f * k, tip)
    }
    // body: a dome with a dark band at the bottom
    val bw = 13f * k
    val bh = 9.5f * k * squash
    val top = baseY - bh
    drawRoundRect(petBand, Offset(cx - bw / 2f, baseY - 2.2f * k), Size(bw, 2.4f * k), CornerRadius(1.1f * k))
    drawArc(
        Brush.horizontalGradient(listOf(lerp(petShell, light.key, 0.25f), petShell, petShade), startX = cx - bw / 2f + light.dir * 2f * k, endX = cx + bw / 2f),
        180f, 180f, true, Offset(cx - bw / 2f, top), Size(bw, (bh - 1.8f * k) * 2f),
    )
    drawArc(Color.White.copy(alpha = 0.28f), 200f, 60f, false, Offset(cx - bw / 2f + 1.4f * k, top + 1f * k), Size(bw - 2.8f * k, (bh - 2.6f * k) * 2f), style = Stroke(0.8f * k, cap = StrokeCap.Round))
    // little ear fins
    val earUp = when (rig.face) { PetFace.SURPRISED, PetFace.CURIOUS -> 1.2f; PetFace.SAD -> -1f; PetFace.GRUMPY -> -0.4f; else -> if (a == PetAnim.STARE) 1f else 0f }
    for (side in listOf(-1f, 1f)) rotate(if (rig.face == PetFace.SAD) side * 35f else 0f, Offset(cx + side * 4.4f * k, top + 2.6f * k)) {
        drawRoundRect(petShade, Offset(cx + side * 4.4f * k - 0.7f * k, top + 1.4f * k - earUp * k), Size(1.4f * k, 2.6f * k), CornerRadius(0.7f * k))
    }
    // the eye: one round screen, and where it's looking is where Nib's mind is
    val eyeC = Offset(cx + rig.facingSmooth * 1.6f * k, top + bh * 0.5f)
    val er = 2.7f * k
    drawCircle(Color(0xFF141B27), er, eyeC)
    if (sleeping) {
        drawArc(Color(0xFF8FF5E2).copy(alpha = 0.7f), 20f, 140f, false, Offset(eyeC.x - er * 0.55f, eyeC.y - er * 0.4f), Size(er * 1.1f, er * 0.8f), style = Stroke(0.5f * k, cap = StrokeCap.Round))
        // z
        val zp = (t * 0.5f) % 1f
        drawLine(Color.White.copy(alpha = 0.6f * (1f - zp)), Offset(cx + 3f * k, top - 1f * k - zp * 4f * k), Offset(cx + 4.2f * k, top - 1f * k - zp * 4f * k), 0.35f * k)
    } else {
        val f = rig.face
        val wide = if (a == PetAnim.STARE || f == PetFace.SURPRISED) 1.3f else if (f == PetFace.CURIOUS) 1.12f else 1f
        val pr = er * 0.5f * wide
        val p = Offset(eyeC.x + rig.lookX * er * 0.35f + rig.facingSmooth * 0.3f * k, eyeC.y + rig.lookY * er * 0.3f)
        val glowC = if (f == PetFace.LOVE) Color(0xFFFF8FA8) else if (f == PetFace.GRUMPY) Color(0xFFFFB06B) else Color(0xFF8FF5E2)
        drawCircle(Brush.radialGradient(listOf(glowC.copy(alpha = 0.5f), Color.Transparent), center = p, radius = pr * 2f), pr * 2f, p)
        when (f) {
            PetFace.HAPPY -> drawArc(glowC, 200f, 140f, false, Offset(p.x - pr, p.y - pr * 0.6f), Size(pr * 2f, pr * 1.6f), style = Stroke(0.7f * k, cap = StrokeCap.Round))
            PetFace.LOVE -> {
                val hp = Path().apply {
                    moveTo(p.x, p.y + pr * 0.9f)
                    cubicTo(p.x - pr * 1.6f, p.y - pr * 0.1f, p.x - pr * 0.7f, p.y - pr * 1.3f, p.x, p.y - pr * 0.4f)
                    cubicTo(p.x + pr * 0.7f, p.y - pr * 1.3f, p.x + pr * 1.6f, p.y - pr * 0.1f, p.x, p.y + pr * 0.9f)
                }
                drawPath(hp, glowC)
            }
            else -> {
                drawOval(glowC, Offset(p.x - pr, p.y - pr * rig.blink), Size(pr * 2f, pr * 2f * rig.blink.coerceAtLeast(0.08f)))
                drawCircle(Color.White.copy(alpha = 0.8f), pr * 0.3f, Offset(p.x - pr * 0.35f, p.y - pr * 0.35f * rig.blink))
                // eyelid: a slab of shell over the top of the eye, angled by the feeling
                val lid = when (f) { PetFace.GRUMPY -> 0.55f; PetFace.SMUG -> 0.5f; PetFace.SAD -> 0.45f; else -> 0f }
                if (lid > 0f) {
                    val tilt = when (f) { PetFace.GRUMPY -> 0.6f * rig.facingSmooth; PetFace.SAD -> -0.6f * rig.facingSmooth; else -> 0f } * er
                    drawPath(Path().apply {
                        moveTo(eyeC.x - er * 1.05f, eyeC.y - er * 1.05f); lineTo(eyeC.x + er * 1.05f, eyeC.y - er * 1.05f)
                        lineTo(eyeC.x + er * 1.05f, eyeC.y - er + lid * 2f * er + tilt); lineTo(eyeC.x - er * 1.05f, eyeC.y - er + lid * 2f * er - tilt); close()
                    }, petShade)
                }
            }
        }
    }
    drawCircle(Color.White.copy(alpha = 0.12f), er, eyeC, style = Stroke(0.4f * k))
    rig.hat?.let { drawHat(it, Offset(cx, top + 1f * k), bw * 1.8f, t) }
    // a thought, as a picture: it pops in, floats, and fades
    rig.thought?.let { th ->
        val pop = (rig.thoughtAge / 0.18f).coerceIn(0f, 1f)
        val bc = Offset(cx + rig.facingSmooth * 4f * k, top - 6f * k - rig.thoughtAge * 0.4f * k)
        val br = 4.2f * k * (0.6f + 0.4f * pop)
        drawCircle(Color.White.copy(alpha = 0.6f * pop), 0.7f * k, Offset(cx + rig.facingSmooth * 1.5f * k, top - 1.2f * k))
        drawCircle(Color.White.copy(alpha = 0.6f * pop), 1.1f * k, Offset(cx + rig.facingSmooth * 2.6f * k, top - 2.8f * k))
        drawCircle(Color(0xFFF4EFE6).copy(alpha = 0.95f * pop), br, bc)
        val ink = Color(0xFF3B4658)
        when (th) {
            PetThought.BALL -> { drawCircle(Color(0xFFE0605A), br * 0.55f, bc); drawLine(Color.White, Offset(bc.x - br * 0.55f, bc.y), Offset(bc.x + br * 0.55f, bc.y), 0.5f * k) }
            PetThought.PIPO -> {
                drawRoundRect(Color(0xFFE8ECF1), Offset(bc.x - br * 0.65f, bc.y - br * 0.45f), Size(br * 1.3f, br * 0.9f), CornerRadius(br * 0.3f))
                drawRoundRect(Color(0xFF141B27), Offset(bc.x - br * 0.48f, bc.y - br * 0.3f), Size(br * 0.96f, br * 0.6f), CornerRadius(br * 0.2f))
                drawCircle(Color(0xFF8FF5E2), br * 0.1f, Offset(bc.x - br * 0.2f, bc.y)); drawCircle(Color(0xFF8FF5E2), br * 0.1f, Offset(bc.x + br * 0.2f, bc.y))
            }
            PetThought.BATTERY -> {
                drawRoundRect(ink, Offset(bc.x - br * 0.55f, bc.y - br * 0.3f), Size(br * 1.0f, br * 0.6f), CornerRadius(br * 0.1f), style = Stroke(0.45f * k))
                drawRect(ink, Offset(bc.x + br * 0.45f, bc.y - br * 0.12f), Size(br * 0.15f, br * 0.24f))
                drawRect(Color(0xFFE0605A), Offset(bc.x - br * 0.45f, bc.y - br * 0.2f), Size(br * 0.2f, br * 0.4f))
            }
            PetThought.FOOD -> drawItem(com.pipo.robot.data.ItemShape.NOODLES, bc, br * 0.9f)
            PetThought.EXCLAIM -> { drawLine(Color(0xFFE0605A), Offset(bc.x, bc.y - br * 0.5f), Offset(bc.x, bc.y + br * 0.15f), 0.8f * k, StrokeCap.Round); drawCircle(Color(0xFFE0605A), 0.45f * k, Offset(bc.x, bc.y + br * 0.48f)) }
            PetThought.QUESTION -> {
                drawArc(ink, 180f, 250f, false, Offset(bc.x - br * 0.3f, bc.y - br * 0.55f), Size(br * 0.6f, br * 0.55f), style = Stroke(0.6f * k, cap = StrokeCap.Round))
                drawLine(ink, Offset(bc.x, bc.y), Offset(bc.x, bc.y + br * 0.18f), 0.6f * k, StrokeCap.Round); drawCircle(ink, 0.35f * k, Offset(bc.x, bc.y + br * 0.45f))
            }
            PetThought.HEART -> drawPath(Path().apply {
                moveTo(bc.x, bc.y + br * 0.45f)
                cubicTo(bc.x - br * 0.9f, bc.y - br * 0.05f, bc.x - br * 0.4f, bc.y - br * 0.7f, bc.x, bc.y - br * 0.2f)
                cubicTo(bc.x + br * 0.4f, bc.y - br * 0.7f, bc.x + br * 0.9f, bc.y - br * 0.05f, bc.x, bc.y + br * 0.45f)
            }, Color(0xFFFF7A90))
            PetThought.MUSIC -> { drawCircle(ink, br * 0.18f, Offset(bc.x - br * 0.15f, bc.y + br * 0.3f)); drawLine(ink, Offset(bc.x, bc.y + br * 0.3f), Offset(bc.x, bc.y - br * 0.45f), 0.45f * k); drawLine(ink, Offset(bc.x, bc.y - br * 0.45f), Offset(bc.x + br * 0.35f, bc.y - br * 0.25f), 0.45f * k) }
        }
    }
    // the loot: whatever Nib is running off with, held at the front
    rig.carry?.let { drawItem(it, Offset(cx + rig.facingSmooth * 6.5f * k, baseY - 2.5f * k), 4.5f * k) }
}
