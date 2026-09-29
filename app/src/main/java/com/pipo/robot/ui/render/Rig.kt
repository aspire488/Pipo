package com.pipo.robot.ui.render

import com.pipo.robot.data.ItemShape
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Body pose. Units are Pipo-local (Pipo is 100 units tall). Angles in degrees. */
class Pose {
    var bob = 0f; var squash = 1f; var stretchX = 1f; var lean = 0f
    var headTilt = 0f; var headBob = 0f
    var armL = 8f; var armR = 8f
    var legL = 0f; var legR = 0f; var legSpread = 0f
    var sit = 0f; var lie = 0f; var jump = 0f; var spin = 0f; var shakeX = 0f
    var crouch = 0f; var antenna = 0f; var cover = 0f; var hold = 0f

    fun reset() {
        bob = 0f; squash = 1f; stretchX = 1f; lean = 0f; headTilt = 0f; headBob = 0f
        armL = 8f; armR = 8f; legL = 0f; legR = 0f; legSpread = 0f; sit = 0f; lie = 0f
        jump = 0f; spin = 0f; shakeX = 0f; crouch = 0f; antenna = 0f; cover = 0f; hold = 0f
    }

    fun lerpTo(o: Pose, k: Float, fast: Float) {
        fun l(a: Float, b: Float, kk: Float = k) = a + (b - a) * kk
        bob = l(bob, o.bob, fast); squash = l(squash, o.squash, fast); stretchX = l(stretchX, o.stretchX, fast)
        lean = l(lean, o.lean); headTilt = l(headTilt, o.headTilt); headBob = l(headBob, o.headBob, fast)
        armL = l(armL, o.armL, fast); armR = l(armR, o.armR, fast)
        legL = l(legL, o.legL, fast); legR = l(legR, o.legR, fast); legSpread = l(legSpread, o.legSpread)
        sit = l(sit, o.sit); lie = l(lie, o.lie, k * 0.6f); jump = l(jump, o.jump, fast)
        spin = l(spin, o.spin, fast); shakeX = l(shakeX, o.shakeX, fast); crouch = l(crouch, o.crouch)
        antenna = l(antenna, o.antenna, fast); cover = l(cover, o.cover); hold = l(hold, o.hold)
    }
}

/** Face parameters, all smoothly interpolated so expressions morph instead of snapping. */
class Face {
    var open = 1f; var scale = 1f; var lidTop = 0f; var lidAngle = 0f; var arc = 0f; var closed = 0f
    var round = 0f; var asym = 0f; var blush = 0f; var sparkle = 0f; var squint = 0f
    var mouthCurve = 0.4f; var mouthOpen = 0f; var mouthWidth = 0.9f; var mouthSkew = 0f; var mouthWave = 0f

    fun set(open: Float = 1f, scale: Float = 1f, lidTop: Float = 0f, lidAngle: Float = 0f, arc: Float = 0f,
            closed: Float = 0f, round: Float = 0f, asym: Float = 0f, blush: Float = 0f, sparkle: Float = 0f,
            squint: Float = 0f, mouthCurve: Float = 0.4f, mouthOpen: Float = 0f, mouthWidth: Float = 0.9f,
            mouthSkew: Float = 0f, mouthWave: Float = 0f) {
        this.open = open; this.scale = scale; this.lidTop = lidTop; this.lidAngle = lidAngle; this.arc = arc
        this.closed = closed; this.round = round; this.asym = asym; this.blush = blush; this.sparkle = sparkle
        this.squint = squint; this.mouthCurve = mouthCurve; this.mouthOpen = mouthOpen; this.mouthWidth = mouthWidth
        this.mouthSkew = mouthSkew; this.mouthWave = mouthWave
    }

    fun lerpTo(o: Face, k: Float) {
        fun l(a: Float, b: Float) = a + (b - a) * k
        open = l(open, o.open); scale = l(scale, o.scale); lidTop = l(lidTop, o.lidTop); lidAngle = l(lidAngle, o.lidAngle)
        arc = l(arc, o.arc); closed = l(closed, o.closed); round = l(round, o.round); asym = l(asym, o.asym)
        blush = l(blush, o.blush); sparkle = l(sparkle, o.sparkle); squint = l(squint, o.squint)
        mouthCurve = l(mouthCurve, o.mouthCurve); mouthOpen = l(mouthOpen, o.mouthOpen)
        mouthWidth = l(mouthWidth, o.mouthWidth); mouthSkew = l(mouthSkew, o.mouthSkew); mouthWave = l(mouthWave, o.mouthWave)
    }
}

object FaceLibrary {
    fun target(e: Expr, f: Face) = when (e) {
        Expr.CONTENT -> f.set(mouthCurve = 0.45f)
        Expr.HAPPY -> f.set(arc = 1f, mouthCurve = 1f, mouthOpen = 0.35f, mouthWidth = 1.1f, blush = 0.15f)
        Expr.EXCITED -> f.set(scale = 1.2f, sparkle = 1f, round = 0.3f, mouthCurve = 0.9f, mouthOpen = 0.6f, mouthWidth = 1.15f)
        Expr.CURIOUS -> f.set(asym = 0.35f, scale = 1.05f, mouthCurve = 0f, mouthOpen = 0.2f, mouthWidth = 0.55f)
        Expr.SLEEPY -> f.set(open = 0.4f, lidTop = 0.45f, scale = 0.95f, mouthCurve = 0f, mouthWidth = 0.6f)
        Expr.BORED -> f.set(open = 0.9f, lidTop = 0.5f, mouthCurve = -0.05f, mouthSkew = 0.3f)
        Expr.SURPRISED -> f.set(round = 1f, scale = 1.2f, mouthOpen = 0.8f, mouthCurve = 0f, mouthWidth = 0.6f)
        Expr.ANNOYED -> f.set(lidAngle = 0.9f, lidTop = 0.25f, mouthCurve = -0.5f, mouthWidth = 0.8f)
        Expr.EMBARRASSED -> f.set(arc = 0.75f, blush = 1f, mouthWave = 0.6f, mouthCurve = 0.2f, mouthWidth = 0.7f)
        Expr.SAD -> f.set(lidAngle = -0.8f, lidTop = 0.15f, scale = 0.95f, mouthCurve = -0.7f, mouthWidth = 0.7f)
        Expr.SUSPICIOUS -> f.set(open = 0.5f, lidTop = 0.3f, asym = -0.3f, mouthSkew = 0.6f, mouthCurve = 0f)
        Expr.NERVOUS -> f.set(scale = 0.85f, mouthWave = 1f, mouthCurve = 0f, mouthWidth = 0.9f)
        Expr.PROUD -> f.set(squint = 0.5f, lidTop = 0.2f, mouthCurve = 0.85f, mouthSkew = 0.4f, mouthWidth = 1f)
        Expr.MISCHIEF -> f.set(lidTop = 0.35f, lidAngle = 0.35f, squint = 0.3f, mouthCurve = 0.7f, mouthSkew = 0.8f, mouthWidth = 1f)
        Expr.CLOSED -> f.set(closed = 1f, mouthCurve = 0.1f, mouthWidth = 0.5f)
        Expr.FOCUSED -> f.set(open = 0.9f, lidTop = 0.3f, mouthCurve = 0f, mouthWidth = 0.5f, mouthSkew = 0.2f)
        Expr.LOVE -> f.set(arc = 1f, blush = 1f, mouthCurve = 1f, mouthWidth = 0.9f)
    }
}

object PoseLibrary {
    /** Each state has its own motion signature — nothing is animated identically. */
    fun target(a: AnimState, t: Float, p: Pose) {
        p.reset()
        fun s(f: Float, ph: Float = 0f) = sin(t * f + ph)
        // breathing baseline
        p.squash = 1f + s(1.7f) * 0.012f
        p.bob = s(1.7f) * 0.6f
        p.antenna = s(1.3f) * 5f
        when (a) {
            AnimState.IDLE -> { p.lean = s(0.5f) * 2f; p.armL = 8f + s(1.7f) * 3f; p.armR = 8f + s(1.7f, 0.4f) * 3f; p.headTilt = s(0.37f) * 4f }
            AnimState.TALKING -> { p.headBob = s(8f) * 0.8f; p.armL = 14f + s(3f) * 10f; p.armR = 20f + s(2.3f, 1f) * 18f; p.headTilt = s(1.1f) * 4f }
            AnimState.HAPPY -> { p.bob = abs(s(5f)) * 2.5f; p.armL = 35f + s(10f) * 15f; p.armR = 35f + s(10f, 1f) * 15f; p.antenna = s(6f) * 14f; p.headTilt = s(2.5f) * 6f }
            AnimState.EXCITED -> { p.jump = max(0f, s(7f)) * 7f; p.armL = 125f + s(14f) * 25f; p.armR = 125f + s(14f, 1.5f) * 25f; p.squash = 1f + s(14f) * 0.04f; p.antenna = s(12f) * 20f }
            AnimState.CURIOUS -> { p.lean = 6f; p.headTilt = 12f + s(0.8f) * 4f; p.armR = 70f; p.armL = 10f; p.antenna = 12f + s(2f) * 6f }
            AnimState.SLEEPY -> { p.squash = 0.96f; p.lean = s(0.6f) * 4f; p.headTilt = 8f + s(0.5f) * 3f; p.headBob = max(0f, s(0.7f)) * 2.5f; p.armL = 2f; p.armR = 2f; p.antenna = -25f + s(0.6f) * 4f }
            AnimState.BORED -> { p.lean = -3f + s(0.4f) * 3f; p.headTilt = -8f; p.armL = 0f; p.armR = 0f; p.squash = 0.98f; p.antenna = -10f + s(0.5f) * 5f }
            AnimState.ANNOYED -> { p.lean = -4f; p.armL = 35f; p.armR = 35f; p.shakeX = if (s(1.3f) > 0.95f) s(40f) * 1f else 0f; p.antenna = if (s(2f) > 0.9f) 15f else 0f; p.headTilt = -3f }
            AnimState.SAD -> { p.squash = 0.94f; p.headTilt = 10f; p.headBob = 2f; p.armL = 0f; p.armR = 0f; p.antenna = -35f; p.bob = s(0.8f) * 0.5f }
            AnimState.LONELY -> { p.squash = 0.96f; p.headTilt = 6f + s(0.3f) * 6f; p.armL = 4f; p.armR = 4f; p.antenna = -20f + s(0.7f) * 6f; p.lean = s(0.3f) * 3f }
            AnimState.NERVOUS -> { p.shakeX = s(40f) * 0.5f; p.armL = 20f + s(9f) * 4f; p.armR = 20f + s(9f, 1f) * 4f; p.antenna = s(25f) * 6f; p.squash = 0.97f }
            AnimState.PROUD -> { p.lean = -6f; p.stretchX = 1.03f; p.armL = 35f; p.armR = 35f; p.headTilt = -5f; p.antenna = 10f + s(1f) * 4f; p.bob = s(1.2f) * 1f }
            AnimState.EMBARRASSED -> { p.headTilt = 10f + s(0.8f) * 3f; p.cover = 0.35f; p.lean = s(1.2f) * 4f; p.antenna = -8f + s(3f) * 6f; p.armL = 60f; p.armR = 60f }
            AnimState.MISCHIEVOUS -> { p.lean = 5f; p.armL = 50f + s(12f) * 8f; p.armR = 50f + s(12f, 3f) * 8f; p.headTilt = -8f; p.bob = s(3f) * 1f; p.antenna = 8f + s(4f) * 8f }
            AnimState.SURPRISED -> { p.jump = max(0f, 1f - t * 3f) * 8f; p.armL = 100f; p.armR = 100f; p.squash = 1.06f; p.antenna = 25f; p.stretchX = 0.96f }
            AnimState.LISTENING -> { p.lean = 4f; p.headTilt = 10f; p.antenna = 14f + s(3f) * 3f; p.armL = 10f; p.armR = 10f }
            AnimState.SLEEPING -> { p.lie = 1f; p.squash = 1f + s(1.1f) * 0.02f; p.armL = 5f; p.armR = 5f; p.antenna = -30f; p.bob = 0f }
            AnimState.DANCING -> {
                p.bob = abs(s(6f)) * 3f; p.lean = s(3f) * 12f; p.armL = 60f + s(6f) * 60f; p.armR = 60f - s(6f) * 60f
                p.legL = max(0f, s(6f)) * 3f; p.legR = max(0f, -s(6f)) * 3f; p.antenna = s(6f) * 20f; p.headTilt = s(3f, 1f) * 10f
                val cyc = (t % 4f); if (cyc > 3.4f) p.spin = ((cyc - 3.4f) / 0.6f) * 2f * PI.toFloat()
            }
            AnimState.WALKING -> {
                p.legL = max(0f, s(10f)) * 3.5f; p.legR = max(0f, -s(10f)) * 3.5f; p.bob = abs(s(10f)) * 1.3f; p.lean = 3f
                p.armL = 18f + s(10f) * 14f; p.armR = 18f - s(10f) * 14f; p.antenna = s(10f) * 8f
            }
            AnimState.RUNNING -> {
                p.legL = max(0f, s(17f)) * 5f; p.legR = max(0f, -s(17f)) * 5f; p.bob = abs(s(17f)) * 2.5f; p.lean = 8f
                p.armL = 45f + s(17f) * 30f; p.armR = 45f - s(17f) * 30f; p.antenna = -20f + s(17f) * 8f
            }
            AnimState.SITTING -> { p.sit = 1f; p.armL = 20f; p.armR = 20f; p.headTilt = s(0.4f) * 5f; p.legSpread = 3f }
            AnimState.HIDING -> { p.crouch = 1f; p.cover = 1f; p.shakeX = s(20f) * 0.3f; p.antenna = -30f; p.squash = 0.92f }
            AnimState.THINKING -> { p.armR = 150f; p.armL = 20f; p.headTilt = s(0.9f) * 8f; p.antenna = s(0.8f) * 15f; p.lean = 2f }
            AnimState.BUILDING -> { p.lean = 6f; p.armL = 60f; p.armR = if (s(9f) > 0.2f) 110f else 55f; p.headTilt = 6f; p.antenna = s(9f) * 4f; p.bob = if (s(9f) > 0.2f) 0.6f else 0f }
            AnimState.PLAYING -> { p.lean = 4f; p.armL = 70f + s(18f) * 10f; p.armR = 70f + s(21f) * 12f; p.bob = s(9f) * 1f; p.antenna = s(7f) * 12f; p.headTilt = s(2f) * 5f }
            AnimState.HOP -> { p.jump = abs(s(6.5f)) * 8f; p.armL = 60f; p.armR = 60f; p.squash = 1f + (if (abs(s(6.5f)) < 0.2f) -0.06f else 0.03f) }
            AnimState.STRETCH -> { p.armL = 165f; p.armR = 165f; p.squash = 1.08f; p.stretchX = 0.96f; p.antenna = 20f }
            AnimState.SPIN -> { p.spin = t * 9f; p.armL = 90f; p.armR = 90f; p.bob = 1f }
            AnimState.SHAKE -> { p.shakeX = s(50f) * 2f; p.antenna = s(40f) * 20f }
            AnimState.LOOK_AROUND -> { p.headTilt = s(1.1f) * 10f; p.lean = s(0.9f) * 5f; p.armL = 12f; p.armR = 12f; p.antenna = 10f + s(2f) * 8f; p.crouch = max(0f, s(0.7f)) * 0.3f }
            AnimState.PEEK -> { p.crouch = 0.6f; p.lean = 14f; p.headTilt = 12f; p.antenna = 20f }
            AnimState.FALLEN -> { p.lie = 1f; p.armL = 130f; p.armR = 40f; p.antenna = s(8f) * 25f }
            AnimState.WAVE -> { p.armR = 150f + s(11f) * 25f; p.armL = 10f; p.headTilt = 6f; p.bob = s(3f) * 0.8f; p.antenna = s(5f) * 10f }
            AnimState.HELD -> { p.legL = 1f + s(4f) * 1.5f; p.legR = 1f - s(4f) * 1.5f; p.armL = 50f + s(5f) * 10f; p.armR = 50f - s(5f) * 10f; p.lean = s(2f) * 6f; p.antenna = s(6f) * 15f; p.squash = 1.03f }
            AnimState.READING -> { p.sit = 1f; p.hold = 1f; p.headTilt = 8f; p.headBob = 1f; p.antenna = s(0.5f) * 4f }
            AnimState.CHARGING -> { p.armL = 20f; p.armR = 20f; p.lean = 0f; p.squash = 1f + s(2.5f) * 0.02f; p.antenna = 15f + s(3f) * 3f; p.headTilt = s(0.4f) * 3f }
            AnimState.PRESENTING -> { p.hold = 1f; p.lean = -2f; p.bob = abs(s(4f)) * 1.2f; p.antenna = 15f + s(5f) * 8f }
        }
    }
}

/**
 * Everything needed to draw Pipo for one frame. The director (HomeViewModel / a game) sets
 * [anim], [expr], look targets and emotes; [update] turns that into smooth motion.
 */
class PipoRig(seed: Int = 1) {
    var anim: AnimState = AnimState.IDLE
    var expr: Expr = Expr.CONTENT
    var talking = false
    var speed = 1f
    var glow: Long = 0xFF8FF5E2
    var eyeGlow = 1f
    var holdItem: ItemShape? = null
    var torch = false

    val pose = Pose()
    val face = Face()
    private val tp = Pose()
    private val tf = Face()

    var lookX = 0f; var lookY = 0f
    private var lookTX = 0f; private var lookTY = 0f
    private var lookHold = 0f
    private var nextSaccade = 1f

    var blink = 1f
    private var blinkT = -1f
    private var nextBlink = 2f
    private var doubleBlink = false

    var talkLevel = 0f
    var emote: EmoteKind? = null
    var emoteT = 0f
    var time = 0f
    private var animT = 0f
    private var lastAnim = anim
    private val rng = Random(seed)

    fun showEmote(k: EmoteKind) { emote = k; emoteT = 0f }

    /** Look somewhere specific (-1..1 range) for [seconds]. */
    fun lookAt(x: Float, y: Float, seconds: Float = 1.2f) {
        lookTX = x.coerceIn(-1f, 1f); lookTY = y.coerceIn(-1f, 1f); lookHold = seconds
    }

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        time += dt
        if (anim != lastAnim) { animT = 0f; lastAnim = anim }
        animT += dt * speed

        PoseLibrary.target(anim, animT, tp)
        val k = 1f - exp(-dt * 7f)
        val fast = 1f - exp(-dt * 16f)
        pose.lerpTo(tp, k, fast)
        // spin is cumulative; don't ease it back through a full turn
        if (tp.spin == 0f && pose.spin != 0f) pose.spin = if (abs(pose.spin % (2 * PI.toFloat())) < 0.3f) 0f else pose.spin

        FaceLibrary.target(expr, tf)
        face.lerpTo(tf, 1f - exp(-dt * 10f))

        // Eyes: autonomous saccades unless holding a target
        if (lookHold > 0f) lookHold -= dt
        else {
            nextSaccade -= dt
            if (nextSaccade <= 0f) {
                val range = when (expr) { Expr.CURIOUS -> 0.9f; Expr.SLEEPY, Expr.CLOSED -> 0.2f; Expr.NERVOUS -> 0.8f; else -> 0.55f }
                lookTX = (rng.nextFloat() * 2f - 1f) * range
                lookTY = (rng.nextFloat() * 2f - 1f) * range * 0.5f
                nextSaccade = when (expr) { Expr.NERVOUS -> 0.4f + rng.nextFloat() * 0.6f; Expr.SLEEPY -> 3f + rng.nextFloat() * 3f; else -> 1.2f + rng.nextFloat() * 2.5f }
            }
        }
        val lk = 1f - exp(-dt * 14f)
        lookX += (lookTX - lookX) * lk
        lookY += (lookTY - lookY) * lk

        // Blinking
        nextBlink -= dt
        if (blinkT < 0f && nextBlink <= 0f) blinkT = 0f
        if (blinkT >= 0f) {
            blinkT += dt
            val dur = if (expr == Expr.SLEEPY) 0.35f else 0.15f
            blink = 1f - sin(PI.toFloat() * min(1f, blinkT / dur))
            if (blinkT >= dur) {
                blinkT = -1f; blink = 1f
                if (!doubleBlink && rng.nextFloat() < 0.15f) { doubleBlink = true; nextBlink = 0.12f }
                else { doubleBlink = false; nextBlink = if (expr == Expr.SLEEPY) 1.2f + rng.nextFloat() * 1.5f else 1.8f + rng.nextFloat() * 3.5f }
            }
        }

        // Mouth while speaking: syllable-ish flapping
        val targetTalk = if (talking) (0.25f + 0.75f * abs(sin(time * 13f)) * (0.6f + 0.4f * sin(time * 3.1f))) else 0f
        talkLevel += (targetTalk - talkLevel) * (1f - exp(-dt * 25f))

        if (emote != null) {
            emoteT += dt
            if (emoteT > 2.6f) emote = null
        }
    }
}
