package com.pipo.robot.ui.render

import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.Mood
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.SpeechStyle
import com.pipo.robot.engine.SpeechStyles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Body pose. Units are Pipo-local (Pipo is 100 units tall). Angles in degrees, except [yaw]
 * which is radians around the vertical axis (0 = facing you). [yaw] in a pose is a *magnitude*
 * of "turning away"; the rig decides which side he turns to.
 */
class Pose {
    var bob = 0f; var squash = 1f; var stretchX = 1f; var lean = 0f
    var headTilt = 0f; var headBob = 0f
    var armL = 8f; var armR = 8f
    var legL = 0f; var legR = 0f; var legSpread = 0f
    var sit = 0f; var lie = 0f; var jump = 0f; var spin = 0f; var shakeX = 0f
    var crouch = 0f; var antenna = 0f; var cover = 0f; var hold = 0f
    var yaw = 0f; var cross = 0f; var yawn = 0f

    fun reset() {
        bob = 0f; squash = 1f; stretchX = 1f; lean = 0f; headTilt = 0f; headBob = 0f
        armL = 8f; armR = 8f; legL = 0f; legR = 0f; legSpread = 0f; sit = 0f; lie = 0f
        jump = 0f; spin = 0f; shakeX = 0f; crouch = 0f; antenna = 0f; cover = 0f; hold = 0f
        yaw = 0f; cross = 0f; yawn = 0f
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
        yaw = l(yaw, o.yaw); cross = l(cross, o.cross); yawn = l(yawn, o.yawn)
    }
}

/** Face parameters, all smoothly interpolated so expressions morph instead of snapping. */
class Face {
    var open = 1f; var scale = 1f; var lidTop = 0f; var lidAngle = 0f; var arc = 0f; var closed = 0f
    var round = 0f; var asym = 0f; var blush = 0f; var sparkle = 0f; var squint = 0f
    var mouthCurve = 0.4f; var mouthOpen = 0f; var mouthWidth = 0.9f; var mouthSkew = 0f; var mouthWave = 0f
    /** >0 closes his right eye, <0 his left. */
    var wink = 0f
    var spiral = 0f
    var tear = 0f
    /** Size of the bright pupil core inside each eye (dilates when interested, shrinks when shocked). */
    var pupil = 1f

    fun set(open: Float = 1f, scale: Float = 1f, lidTop: Float = 0f, lidAngle: Float = 0f, arc: Float = 0f,
            closed: Float = 0f, round: Float = 0f, asym: Float = 0f, blush: Float = 0f, sparkle: Float = 0f,
            squint: Float = 0f, mouthCurve: Float = 0.4f, mouthOpen: Float = 0f, mouthWidth: Float = 0.9f,
            mouthSkew: Float = 0f, mouthWave: Float = 0f, wink: Float = 0f, spiral: Float = 0f, tear: Float = 0f,
            pupil: Float = 1f) {
        this.open = open; this.scale = scale; this.lidTop = lidTop; this.lidAngle = lidAngle; this.arc = arc
        this.closed = closed; this.round = round; this.asym = asym; this.blush = blush; this.sparkle = sparkle
        this.squint = squint; this.mouthCurve = mouthCurve; this.mouthOpen = mouthOpen; this.mouthWidth = mouthWidth
        this.mouthSkew = mouthSkew; this.mouthWave = mouthWave; this.wink = wink; this.spiral = spiral
        this.tear = tear; this.pupil = pupil
    }

    fun lerpTo(o: Face, k: Float) {
        fun l(a: Float, b: Float) = a + (b - a) * k
        open = l(open, o.open); scale = l(scale, o.scale); lidTop = l(lidTop, o.lidTop); lidAngle = l(lidAngle, o.lidAngle)
        arc = l(arc, o.arc); closed = l(closed, o.closed); round = l(round, o.round); asym = l(asym, o.asym)
        blush = l(blush, o.blush); sparkle = l(sparkle, o.sparkle); squint = l(squint, o.squint)
        mouthCurve = l(mouthCurve, o.mouthCurve); mouthOpen = l(mouthOpen, o.mouthOpen)
        mouthWidth = l(mouthWidth, o.mouthWidth); mouthSkew = l(mouthSkew, o.mouthSkew); mouthWave = l(mouthWave, o.mouthWave)
        wink = l(wink, o.wink); spiral = l(spiral, o.spiral); tear = l(tear, o.tear); pupil = l(pupil, o.pupil)
    }
}

object FaceLibrary {
    fun target(e: Expr, f: Face) = when (e) {
        Expr.CONTENT -> f.set(mouthCurve = 0.45f)
        Expr.HAPPY -> f.set(arc = 1f, mouthCurve = 1f, mouthOpen = 0.35f, mouthWidth = 1.1f, blush = 0.15f)
        Expr.EXCITED -> f.set(scale = 1.2f, sparkle = 1f, round = 0.3f, mouthCurve = 0.9f, mouthOpen = 0.6f, mouthWidth = 1.15f, pupil = 1.35f)
        Expr.CURIOUS -> f.set(asym = 0.35f, scale = 1.05f, mouthCurve = 0f, mouthOpen = 0.2f, mouthWidth = 0.55f, pupil = 1.3f)
        Expr.SLEEPY -> f.set(open = 0.4f, lidTop = 0.45f, scale = 0.95f, mouthCurve = 0f, mouthWidth = 0.6f, pupil = 0.9f)
        Expr.BORED -> f.set(open = 0.9f, lidTop = 0.5f, mouthCurve = -0.05f, mouthSkew = 0.3f)
        Expr.SURPRISED -> f.set(round = 1f, scale = 1.2f, mouthOpen = 0.8f, mouthCurve = 0f, mouthWidth = 0.6f, pupil = 0.55f)
        Expr.ANNOYED -> f.set(lidAngle = 0.9f, lidTop = 0.25f, mouthCurve = -0.5f, mouthWidth = 0.8f, pupil = 0.8f)
        Expr.EMBARRASSED -> f.set(arc = 0.75f, blush = 1f, mouthWave = 0.6f, mouthCurve = 0.2f, mouthWidth = 0.7f)
        Expr.SAD -> f.set(lidAngle = -0.8f, lidTop = 0.15f, scale = 0.95f, mouthCurve = -0.7f, mouthWidth = 0.7f, tear = 0.6f)
        Expr.SUSPICIOUS -> f.set(open = 0.5f, lidTop = 0.3f, asym = -0.3f, mouthSkew = 0.6f, mouthCurve = 0f, pupil = 0.85f)
        Expr.NERVOUS -> f.set(scale = 0.85f, mouthWave = 1f, mouthCurve = 0f, mouthWidth = 0.9f, pupil = 0.7f)
        Expr.PROUD -> f.set(squint = 0.5f, lidTop = 0.2f, mouthCurve = 0.85f, mouthSkew = 0.4f, mouthWidth = 1f)
        Expr.MISCHIEF -> f.set(lidTop = 0.35f, lidAngle = 0.35f, squint = 0.3f, mouthCurve = 0.7f, mouthSkew = 0.8f, mouthWidth = 1f)
        Expr.CLOSED -> f.set(closed = 1f, mouthCurve = 0.1f, mouthWidth = 0.5f)
        Expr.FOCUSED -> f.set(open = 0.9f, lidTop = 0.3f, mouthCurve = 0f, mouthWidth = 0.5f, mouthSkew = 0.2f, pupil = 0.9f)
        Expr.LOVE -> f.set(arc = 1f, blush = 1f, mouthCurve = 1f, mouthWidth = 0.9f)
        Expr.WINK -> f.set(wink = 1f, mouthCurve = 0.8f, mouthSkew = 0.6f, mouthWidth = 0.95f, blush = 0.2f)
        Expr.DIZZY -> f.set(spiral = 1f, mouthWave = 0.8f, mouthCurve = 0f, mouthWidth = 0.8f)
        Expr.LAUGH -> f.set(arc = 1f, squint = 0.2f, mouthCurve = 1f, mouthOpen = 0.75f, mouthWidth = 1.15f, blush = 0.25f)
        Expr.WORRIED -> f.set(lidAngle = -0.6f, scale = 0.92f, mouthWave = 0.5f, mouthCurve = -0.2f, mouthWidth = 0.75f, pupil = 0.8f)
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
            AnimState.CHEERFUL -> {
                p.bob = abs(s(3.2f)) * 1.2f; p.lean = s(1.6f) * 3f; p.headTilt = s(1.6f, 0.5f) * 5f
                p.armL = 14f + s(3.2f) * 5f; p.armR = 14f + s(3.2f, 1f) * 5f; p.antenna = s(3.2f) * 9f
            }
            AnimState.TALKING -> { p.headBob = s(8f) * 0.8f; p.armL = 14f + s(3f) * 10f; p.armR = 20f + s(2.3f, 1f) * 18f; p.headTilt = s(1.1f) * 4f }
            AnimState.HAPPY -> { p.bob = abs(s(5f)) * 2.5f; p.armL = 35f + s(10f) * 15f; p.armR = 35f + s(10f, 1f) * 15f; p.antenna = s(6f) * 14f; p.headTilt = s(2.5f) * 6f }
            AnimState.EXCITED -> { p.jump = max(0f, s(7f)) * 7f; p.armL = 125f + s(14f) * 25f; p.armR = 125f + s(14f, 1.5f) * 25f; p.squash = 1f + s(14f) * 0.04f; p.antenna = s(12f) * 20f }
            AnimState.CURIOUS -> { p.lean = 6f; p.headTilt = 12f + s(0.8f) * 4f; p.armR = 70f; p.armL = 10f; p.antenna = 12f + s(2f) * 6f; p.yaw = 0.15f }
            AnimState.SLEEPY -> { p.squash = 0.96f; p.lean = s(0.6f) * 4f; p.headTilt = 8f + s(0.5f) * 3f; p.headBob = max(0f, s(0.7f)) * 2.5f; p.armL = 2f; p.armR = 2f; p.antenna = -25f + s(0.6f) * 4f }
            AnimState.BORED -> { p.lean = -3f + s(0.4f) * 3f; p.headTilt = -8f; p.armL = 0f; p.armR = 0f; p.squash = 0.98f; p.antenna = -10f + s(0.5f) * 5f; p.yaw = 0.2f }
            AnimState.ANNOYED -> { p.lean = -4f; p.armL = 35f; p.armR = 35f; p.cross = 0.6f; p.yaw = 0.35f; p.shakeX = if (s(1.3f) > 0.95f) s(40f) * 1f else 0f; p.antenna = if (s(2f) > 0.9f) 15f else 0f; p.headTilt = -3f }
            AnimState.SAD -> { p.squash = 0.94f; p.headTilt = 10f; p.headBob = 2f; p.armL = 0f; p.armR = 0f; p.antenna = -35f; p.bob = s(0.8f) * 0.5f }
            AnimState.LONELY -> { p.squash = 0.96f; p.headTilt = 6f + s(0.3f) * 6f; p.armL = 4f; p.armR = 4f; p.antenna = -20f + s(0.7f) * 6f; p.lean = s(0.3f) * 3f; p.yaw = 0.25f + s(0.2f) * 0.15f }
            AnimState.NERVOUS -> { p.shakeX = s(40f) * 0.5f; p.armL = 20f + s(9f) * 4f; p.armR = 20f + s(9f, 1f) * 4f; p.antenna = s(25f) * 6f; p.squash = 0.97f; p.cross = 0.3f }
            AnimState.PROUD -> { p.lean = -6f; p.stretchX = 1.03f; p.squash = 1.03f; p.armL = 35f; p.armR = 35f; p.headTilt = -5f; p.antenna = 10f + s(1f) * 4f; p.bob = s(1.2f) * 1f }
            AnimState.EMBARRASSED -> { p.headTilt = 10f + s(0.8f) * 3f; p.cover = 0.35f; p.lean = s(1.2f) * 4f; p.antenna = -8f + s(3f) * 6f; p.armL = 60f; p.armR = 60f; p.yaw = 0.55f }
            AnimState.MISCHIEVOUS -> { p.lean = 5f; p.armL = 50f + s(12f) * 8f; p.armR = 50f + s(12f, 3f) * 8f; p.headTilt = -8f; p.bob = s(3f) * 1f; p.antenna = 8f + s(4f) * 8f; p.yaw = 0.25f; p.crouch = 0.15f }
            AnimState.SURPRISED -> { p.jump = max(0f, 1f - t * 3f) * 8f; p.armL = 100f; p.armR = 100f; p.squash = 1.06f; p.antenna = 25f; p.stretchX = 0.96f }
            AnimState.LISTENING -> { p.lean = 4f; p.headTilt = 10f; p.antenna = 14f + s(3f) * 3f; p.armL = 10f; p.armR = 10f }
            AnimState.SLEEPING -> { p.lie = 1f; p.squash = 1f + s(1.1f) * 0.02f; p.armL = 5f; p.armR = 5f; p.antenna = -30f; p.bob = 0f }
            AnimState.DANCING -> {
                p.bob = abs(s(6f)) * 3f; p.lean = s(3f) * 12f; p.armL = 60f + s(6f) * 60f; p.armR = 60f - s(6f) * 60f
                p.legL = max(0f, s(6f)) * 3f; p.legR = max(0f, -s(6f)) * 3f; p.antenna = s(6f) * 20f; p.headTilt = s(3f, 1f) * 10f
                p.yaw = s(1.5f) * 0.5f
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
            AnimState.SNEAK -> {
                // tiptoe: slow, exaggerated steps, crouched, hands up like a cartoon burglar
                p.crouch = 0.45f; p.lean = 9f; p.legL = max(0f, s(4f)) * 4f; p.legR = max(0f, -s(4f)) * 4f
                p.bob = abs(s(4f)) * 1.8f; p.armL = 105f + s(4f) * 8f; p.armR = 105f - s(4f) * 8f; p.antenna = 6f + s(4f) * 4f
            }
            AnimState.SITTING -> { p.sit = 1f; p.armL = 20f; p.armR = 20f; p.headTilt = s(0.4f) * 5f; p.legSpread = 3f; p.legL = 1.5f + s(1.1f) * 0.8f; p.legR = 1.5f - s(1.1f) * 0.8f }
            AnimState.HIDING -> { p.crouch = 1f; p.cover = 1f; p.shakeX = s(20f) * 0.3f; p.antenna = -30f; p.squash = 0.92f }
            AnimState.THINKING -> { p.armR = 150f; p.armL = 20f; p.headTilt = s(0.9f) * 8f; p.antenna = s(0.8f) * 15f; p.lean = 2f; p.yaw = 0.2f }
            AnimState.BUILDING -> { p.lean = 6f; p.armL = 60f; p.armR = if (s(9f) > 0.2f) 110f else 55f; p.headTilt = 6f; p.antenna = s(9f) * 4f; p.bob = if (s(9f) > 0.2f) 0.6f else 0f }
            AnimState.PLAYING -> { p.lean = 4f; p.armL = 70f + s(18f) * 10f; p.armR = 70f + s(21f) * 12f; p.bob = s(9f) * 1f; p.antenna = s(7f) * 12f; p.headTilt = s(2f) * 5f }
            AnimState.HOP -> { p.jump = abs(s(6.5f)) * 8f; p.armL = 60f; p.armR = 60f; p.squash = 1f + (if (abs(s(6.5f)) < 0.2f) -0.06f else 0.03f) }
            AnimState.STRETCH -> { p.armL = 165f; p.armR = 165f; p.squash = 1.08f; p.stretchX = 0.96f; p.antenna = 20f; p.yawn = 0.6f * min(1f, t * 2f) }
            AnimState.SPIN -> { p.spin = t * 9f; p.armL = 90f; p.armR = 90f; p.bob = 1f }
            AnimState.SHAKE -> { p.shakeX = s(50f) * 2f; p.antenna = s(40f) * 20f }
            AnimState.LOOK_AROUND -> { p.headTilt = s(1.1f) * 10f; p.lean = s(0.9f) * 5f; p.armL = 12f; p.armR = 12f; p.antenna = 10f + s(2f) * 8f; p.crouch = max(0f, s(0.7f)) * 0.3f; p.yaw = s(0.9f) * 0.6f }
            AnimState.PEEK -> { p.crouch = 0.6f; p.lean = 14f; p.headTilt = 12f; p.antenna = 20f }
            AnimState.FALLEN -> { p.lie = 1f; p.armL = 130f; p.armR = 40f; p.antenna = s(8f) * 25f }
            AnimState.WAVE -> { p.armR = 150f + s(11f) * 25f; p.armL = 10f; p.headTilt = 6f; p.bob = s(3f) * 0.8f; p.antenna = s(5f) * 10f }
            AnimState.HELD -> { p.legL = 1f + s(4f) * 1.5f; p.legR = 1f - s(4f) * 1.5f; p.armL = 50f + s(5f) * 10f; p.armR = 50f - s(5f) * 10f; p.lean = s(2f) * 6f; p.antenna = s(6f) * 15f; p.squash = 1.03f }
            AnimState.PHONE -> {
                // sat down, phone in both hands, head bowed over it; a little nod on every swipe
                val swipe = (t / 1.7f) % 1f
                p.sit = 1f; p.hold = 1f; p.headTilt = 13f + s(0.6f) * 2f
                p.headBob = if (swipe < 0.12f) 1.1f else 0f
                p.legSpread = 2f; p.legL = 1.2f + s(1.3f) * 0.9f; p.legR = 1.2f - s(1.3f) * 0.9f
                p.antenna = 6f + s(0.8f) * 3f + (if (swipe < 0.12f) 6f else 0f)
            }
            AnimState.GAMING -> {
                // sat on the rug turned to the TV, controller up, leaning into it; mashing makes the antenna buzz
                p.sit = 1f; p.hold = 1f; p.yaw = 0.55f; p.lean = 4f + s(0.5f) * 2f
                p.headBob = s(11f) * 0.4f; p.bob = abs(s(9f)) * 0.5f
                p.antenna = s(9f) * 6f + (if (s(0.9f) > 0.93f) 12f else 0f)
            }
            AnimState.READING -> { p.sit = 1f; p.hold = 1f; p.headTilt = 8f; p.headBob = 1f; p.antenna = s(0.5f) * 4f }
            AnimState.CHARGING -> { p.armL = 20f; p.armR = 20f; p.lean = 0f; p.squash = 1f + s(2.5f) * 0.02f; p.antenna = 15f + s(3f) * 3f; p.headTilt = s(0.4f) * 3f }
            AnimState.PRESENTING -> { p.hold = 1f; p.lean = -2f; p.bob = abs(s(4f)) * 1.2f; p.antenna = 15f + s(5f) * 8f }
            AnimState.YAWN -> {
                val e = sin(PI.toFloat() * min(1f, t / 2.2f))
                p.yawn = e; p.armL = 8f + e * 120f; p.armR = 8f + e * 120f; p.squash = 1f + e * 0.05f; p.headTilt = -8f * e; p.antenna = -10f + e * 25f
            }
            AnimState.ARMS_CROSSED -> {
                p.cross = 1f; p.lean = -3f; p.headTilt = -6f + s(0.4f) * 2f; p.yaw = 0.5f
                p.legR = max(0f, s(7f)) * 1.1f // impatient foot tap
                p.antenna = -5f + (if (s(1.7f) > 0.93f) 14f else 0f)
            }
            AnimState.TURN_AWAY -> { p.yaw = 2.6f; p.headTilt = -5f; p.armL = 4f; p.armR = 4f; p.cross = 0.7f; p.antenna = -8f + s(0.6f) * 4f }
            AnimState.LIE_DOWN -> {
                // lying on the floor, staring at the ceiling, thinking about nothing
                p.lie = 1f; p.armL = 165f; p.armR = 165f; p.legL = 1.5f + s(0.9f) * 1.5f; p.antenna = s(0.5f) * 10f; p.squash = 1f + s(0.9f) * 0.015f
            }
            AnimState.GET_UP -> {
                val u = min(1f, t / 1.1f)
                p.lie = max(0f, 1f - u * 1.6f); p.crouch = sin(PI.toFloat() * u) * 0.8f; p.armL = 150f * (1f - u); p.armR = 150f * (1f - u)
                p.squash = 1f - 0.05f * sin(PI.toFloat() * u); p.antenna = s(9f) * 12f
            }
            AnimState.DIZZY -> { p.headTilt = s(5f) * 11f; p.lean = s(2.5f) * 7f; p.shakeX = s(2.5f, 1f) * 1.2f; p.armL = 40f + s(5f) * 20f; p.armR = 40f - s(5f) * 20f; p.antenna = s(10f) * 25f; p.legSpread = 3f }
            AnimState.LAUGH -> { p.bob = abs(s(14f)) * 1.6f; p.headTilt = -7f + s(7f) * 3f; p.cross = 0.45f; p.squash = 1f + s(14f) * 0.025f; p.antenna = s(14f) * 12f; p.lean = -3f }
            AnimState.SIGH -> {
                val e = sin(PI.toFloat() * min(1f, t / 1.4f))
                p.squash = 1f - 0.06f * e; p.headBob = 3f * e; p.armL = 2f; p.armR = 2f; p.antenna = -20f * e; p.headTilt = 6f * e
            }
            AnimState.CELEBRATE -> {
                p.jump = abs(s(7.5f)) * 9f; p.armL = 160f + s(15f) * 15f; p.armR = 160f + s(15f, 1f) * 15f
                p.antenna = s(15f) * 25f; p.squash = 1f + (if (abs(s(7.5f)) < 0.2f) -0.07f else 0.03f); p.headTilt = s(3.7f) * 8f
            }
            AnimState.SULK -> { p.sit = 1f; p.cross = 1f; p.yaw = 2.3f; p.headTilt = 12f; p.antenna = -25f; p.squash = 0.97f }
            AnimState.FINGER_UP -> { p.armR = 165f; p.armL = 60f; p.lean = 5f; p.headTilt = 6f; p.yaw = 0.35f; p.antenna = 4f }
        }
    }
}

/** Little involuntary behaviours layered on top of calm poses so Pipo is never a statue. */
enum class Fidget { LOOK_SIDE, GLANCE_USER, SHIFT_WEIGHT, ANTENNA_TWITCH, HAND_LOOK, TAP_FOOT, HUM_SWAY, SIGH, YAWN, SCRATCH_HEAD, SMALL_HOP }

/**
 * Everything needed to draw Pipo for one frame. The director (HomeViewModel / a game) sets
 * [anim], [expr], look targets and emotes; [update] turns that into smooth, physical motion.
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

    /** Director hints. */
    var moveDir = 0f
    var bodyVel = 0f
    var energy = 0.8f
    var mood: Mood = Mood.RELAXED
    var fidgetsEnabled = true

    val pose = Pose()
    val face = Face()
    private val tp = Pose()
    private val tf = Face()

    var lookX = 0f; var lookY = 0f
    private var lookTX = 0f; private var lookTY = 0f
    private var lookHold = 0f
    private var nextSaccade = 1f
    private var microX = 0f; private var microY = 0f; private var nextMicro = 0.4f

    var blink = 1f
    private var blinkT = -1f
    private var nextBlink = 2f
    private var doubleBlink = false

    // 2.5D turning (radians). Spring-driven so turns overshoot and settle like a real body.
    var yaw = 0f
        internal set // settable by the on-device pose gallery test
    private var yawVel = 0f
    var headYaw = 0f
        internal set
    private var awaySide = 1f

    // Antenna as a damped spring: it lags behind the body and wobbles after stops and landings.
    var antenna = 0f
        private set
    private var antennaVel = 0f
    private var lastBodyVel = 0f

    private var impactAmt = 0f
    private var prevJump = 0f
    private var peakJump = 0f

    var fidget: Fidget? = null
        private set
    private var fidgetT = 0f
    private var fidgetDur = 1f
    private var fidgetSide = 1f
    private var nextFidget = 4f
    private var fidgetEvent: Fidget? = null

    // Speech: the mouth follows the actual text, not a sine wave.
    private var speechSlots = CharArray(0)
    private var speechEmph = BooleanArray(0)
    private var speechT = 0f
    private var speechCps = 14f
    var speechStyle = SpeechStyle.NORMAL
        private set
    var speakGlow = 0f
        private set

    var talkLevel = 0f
    var emote: EmoteKind? = null
    var emoteT = 0f
    var time = 0f
    private var animT = 0f
    private var lastAnim = anim
    private val rng = Random(seed)

    /** Squash/stretch including landing impacts. */
    val squashNow: Float get() = pose.squash * (1f - 0.16f * impactAmt)
    val stretchNow: Float get() = pose.stretchX * (1f + 0.12f * impactAmt)

    fun showEmote(k: EmoteKind) { emote = k; emoteT = 0f }

    /** Look somewhere specific (-1..1 range) for [seconds]. */
    fun lookAt(x: Float, y: Float, seconds: Float = 1.2f) {
        val nx = x.coerceIn(-1f, 1f); val ny = y.coerceIn(-1f, 1f)
        // Big gaze shifts come with a blink, like real eyes.
        if (abs(nx - lookTX) > 0.75f && blinkT < 0f && rng.nextFloat() < 0.45f) blinkT = 0f
        lookTX = nx; lookTY = ny; lookHold = seconds
    }

    fun isHoldingLook() = lookHold > 0f

    /** Body hit something (landing, bump). 0..1 */
    fun impact(strength: Float) {
        val a = strength.coerceIn(0f, 1f)
        impactAmt = max(impactAmt, a)
        antennaVel -= 320f * a
    }

    /** Called once for sound hooks (e.g. a yawn sound). */
    fun takeFidgetEvent(): Fidget? = fidgetEvent.also { fidgetEvent = null }

    fun speak(text: String, charsPerSec: Float) {
        val slots = StringBuilder(); val emph = ArrayList<Boolean>()
        val words = text.split(' ')
        for ((wi, w) in words.withIndex()) {
            val letters = w.filter { it.isLetter() }
            val loud = letters.length >= 2 && letters.all { it.isUpperCase() }
            for (c in w) {
                val n = when (c) { '.', '!', '?' -> 4; ',', ';', ':' -> 2; else -> 1 }
                repeat(n) { slots.append(c); emph.add(loud) }
            }
            if (wi < words.size - 1) { slots.append(' '); emph.add(false) }
        }
        speechSlots = slots.toString().toCharArray()
        speechEmph = emph.toBooleanArray()
        speechT = 0f
        speechCps = charsPerSec.coerceIn(6f, 24f)
        speechStyle = SpeechStyles.style(text)
    }

    private fun calmAnim(a: AnimState) = a == AnimState.IDLE || a == AnimState.CHEERFUL || a == AnimState.SITTING ||
        a == AnimState.BORED || a == AnimState.LONELY || a == AnimState.SLEEPY || a == AnimState.CHARGING ||
        a == AnimState.ARMS_CROSSED || a == AnimState.CURIOUS || a == AnimState.PROUD || a == AnimState.LIE_DOWN

    private fun startFidget() {
        val m = mood
        val w = linkedMapOf(
            Fidget.LOOK_SIDE to 1f,
            Fidget.GLANCE_USER to 0.6f + (if (m == Mood.LONELY || m == Mood.HAPPY) 0.6f else 0f),
            Fidget.SHIFT_WEIGHT to (if (anim == AnimState.SITTING || anim == AnimState.LIE_DOWN) 0.2f else 1f),
            Fidget.ANTENNA_TWITCH to 0.7f,
            Fidget.HAND_LOOK to 0.35f,
            Fidget.TAP_FOOT to if (m == Mood.BORED || m == Mood.GRUMPY) 1.2f else 0.15f,
            Fidget.HUM_SWAY to if (m == Mood.HAPPY || m == Mood.RELAXED || m == Mood.EXCITED) 1f else 0.1f,
            Fidget.SIGH to if (m == Mood.BORED || m == Mood.LONELY) 0.8f else 0.08f,
            Fidget.YAWN to if (energy < 0.55f) (0.6f - energy) * 3.5f else 0f,
            Fidget.SCRATCH_HEAD to if (m == Mood.CURIOUS) 0.8f else 0.2f,
            Fidget.SMALL_HOP to if ((m == Mood.EXCITED || m == Mood.HAPPY) && anim != AnimState.SITTING && anim != AnimState.LIE_DOWN) 0.5f else 0f,
        )
        var r = rng.nextFloat() * w.values.sum()
        var pick = Fidget.LOOK_SIDE
        for ((k, v) in w) { r -= v; if (r <= 0f) { pick = k; break } }
        fidget = pick
        fidgetT = 0f
        fidgetSide = if (rng.nextBoolean()) 1f else -1f
        fidgetDur = when (pick) { Fidget.YAWN -> 2.4f; Fidget.SIGH -> 1.6f; Fidget.HUM_SWAY -> 2.6f; Fidget.TAP_FOOT -> 1.8f; Fidget.SMALL_HOP -> 0.55f; else -> 1.2f + rng.nextFloat() * 0.8f }
        when (pick) {
            Fidget.LOOK_SIDE -> lookAt(fidgetSide * (0.7f + rng.nextFloat() * 0.3f), -0.15f + rng.nextFloat() * 0.3f, fidgetDur * 0.8f)
            Fidget.GLANCE_USER -> lookAt(0f, 0.35f, fidgetDur)
            Fidget.HAND_LOOK -> lookAt(0.45f, 0.7f, fidgetDur * 0.9f)
            Fidget.ANTENNA_TWITCH -> { antennaVel += 260f * fidgetSide; lookAt(0f, -0.9f, 0.6f) }
            Fidget.SMALL_HOP -> Unit
            else -> Unit
        }
        fidgetEvent = pick
    }

    private fun applyFidget(p: Pose, dt: Float) {
        val f = fidget
        if (!fidgetsEnabled || !calmAnim(anim) || talking) {
            fidget = null
            nextFidget = max(nextFidget, 1.5f)
            return
        }
        if (f == null) {
            nextFidget -= dt
            if (nextFidget <= 0f) {
                startFidget()
                nextFidget = (if (mood == Mood.NERVOUS || mood == Mood.EXCITED) 2.5f else 4f) + rng.nextFloat() * 5f
            }
            return
        }
        fidgetT += dt
        val u = fidgetT / fidgetDur
        if (u >= 1f) { fidget = null; return }
        val e = sin(PI.toFloat() * u)
        val sd = fidgetSide
        when (f) {
            Fidget.LOOK_SIDE -> { p.yaw += 0.45f * e * sd * awaySide; p.headTilt += 4f * e * sd }
            Fidget.GLANCE_USER -> { p.headTilt += 5f * e; p.antenna += 6f * e }
            Fidget.SHIFT_WEIGHT -> { p.shakeX += 1.3f * e * sd; p.lean -= 3.5f * e * sd; if (sd > 0) p.legL += 0.8f * e else p.legR += 0.8f * e }
            Fidget.ANTENNA_TWITCH -> p.headTilt -= 3f * e
            Fidget.HAND_LOOK -> { p.armR = p.armR + (125f - p.armR) * e; p.headTilt += 10f * e; p.lean += 3f * e }
            Fidget.TAP_FOOT -> p.legR += abs(sin(time * 13f)) * 1.4f * e
            Fidget.HUM_SWAY -> { p.lean += sin(time * 4f) * 5f * e; p.headTilt += sin(time * 4f + 0.7f) * 6f * e; p.antenna += sin(time * 8f) * 8f * e }
            Fidget.SIGH -> { p.squash -= 0.05f * e; p.headBob += 2.5f * e; p.antenna -= 18f * e }
            Fidget.YAWN -> { p.yawn = max(p.yawn, e); p.armL += 100f * e; p.armR += 100f * e; p.headTilt -= 7f * e; p.squash += 0.04f * e }
            Fidget.SCRATCH_HEAD -> { p.armL = p.armL + (170f - p.armL) * e; p.headTilt -= 8f * e * sd }
            Fidget.SMALL_HOP -> p.jump += sin(PI.toFloat() * u) * 4f
        }
    }

    private fun applySpeech(p: Pose) {
        if (!talking) return
        val prog = if (speechSlots.isEmpty()) 0f else (speechT * speechCps / speechSlots.size).coerceIn(0f, 1f)
        when (speechStyle) {
            SpeechStyle.QUESTION -> if (prog > 0.6f) { p.headTilt += 11f * ((prog - 0.6f) / 0.4f); p.antenna += 10f }
            SpeechStyle.EXCITED -> { p.bob += abs(sin(time * 9f)) * 1.6f; p.armL += 30f; p.armR += 30f; p.antenna += sin(time * 12f) * 10f }
            SpeechStyle.WHISPER -> { p.lean += 7f; p.headTilt += 7f; p.crouch = max(p.crouch, 0.25f); p.armR = max(p.armR, 120f) }
            SpeechStyle.LAUGH -> { p.bob += abs(sin(time * 14f)) * 1.6f; p.headTilt -= 6f; p.cross = max(p.cross, 0.35f) }
            SpeechStyle.SIGH -> if (prog < 0.3f) { p.squash -= 0.05f * sin(PI.toFloat() * prog / 0.3f); p.antenna -= 15f }
            SpeechStyle.NORMAL -> Unit
        }
        p.headBob += speakGlow * 1.8f
    }

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        if (dt <= 0f) return
        time += dt
        if (anim != lastAnim) {
            animT = 0f; lastAnim = anim
            awaySide = if (moveDir != 0f) sign(moveDir) else if (rng.nextBoolean()) 1f else -1f
        }
        animT += dt * speed
        if (talking) speechT += dt

        PoseLibrary.target(anim, animT, tp)
        applySpeech(tp)
        applyFidget(tp, dt)
        val k = 1f - exp(-dt * 7f)
        val fast = 1f - exp(-dt * 16f)
        pose.lerpTo(tp, k, fast)
        // spin is cumulative; don't ease it back through a full turn
        if (tp.spin == 0f && pose.spin != 0f) pose.spin = if (abs(pose.spin % (2 * PI.toFloat())) < 0.3f) 0f else pose.spin

        // ---- yaw spring (slightly under-damped → natural overshoot on turns and stops)
        val lying = pose.lie > 0.5f
        // Walking reads best three-quarter (face visible); only a deliberate pose turns him further away.
        // Most poses turn to whichever side feels natural; gaming faces the TV (to his right) every time.
        val side = if (anim == AnimState.GAMING) 1f else awaySide
        val yawTarget = if (lying) 0f else (tp.yaw * side + moveDir * 0.62f + lookX * 0.1f).coerceIn(-2.9f, 2.9f)
        yawVel += ((yawTarget - yaw) * 42f - yawVel * 8.5f) * dt
        yaw += yawVel * dt
        val headLimit = max(1.0f, abs(tp.yaw) + 0.3f)
        val hyT = (yaw + lookX * 0.28f).coerceIn(yaw - 0.45f, yaw + 0.45f).coerceIn(-headLimit, headLimit)
        headYaw += (hyT - headYaw) * (1f - exp(-dt * 12f))

        // ---- antenna spring, pushed by body acceleration
        val accel = ((bodyVel - lastBodyVel) / dt).coerceIn(-400f, 400f)
        lastBodyVel = bodyVel
        antennaVel += ((pose.antenna - antenna) * 140f - antennaVel * 7.5f - accel * 1.1f) * dt
        antenna += antennaVel * dt
        antenna = antenna.coerceIn(-70f, 70f)

        // ---- landing detection: coming down from a hop squashes him a little
        if (pose.jump > peakJump) peakJump = pose.jump
        if (prevJump >= 0.9f && pose.jump < 0.9f && pose.jump < prevJump) { impact(0.18f + min(0.35f, peakJump / 28f)); peakJump = 0f }
        prevJump = pose.jump
        impactAmt *= exp(-dt * 7f)

        // ---- face
        val laughing = talking && speechStyle == SpeechStyle.LAUGH
        FaceLibrary.target(if (laughing && expr != Expr.CLOSED) Expr.LAUGH else expr, tf)
        if (energy < 0.35f && tf.closed < 0.5f && tf.arc < 0.5f) tf.lidTop = max(tf.lidTop, (0.35f - energy) * 0.9f)
        if (pose.yawn > 0.3f) { tf.closed = max(tf.closed, pose.yawn * 0.9f); tf.mouthOpen = max(tf.mouthOpen, pose.yawn); tf.mouthCurve = 0f; tf.mouthWidth = 0.7f }
        face.lerpTo(tf, 1f - exp(-dt * 10f))

        // ---- eyes: autonomous saccades unless holding a target, plus tiny micro-saccades
        if (lookHold > 0f) lookHold -= dt
        else {
            nextSaccade -= dt
            if (nextSaccade <= 0f) {
                val range = when (expr) { Expr.CURIOUS -> 0.9f; Expr.SLEEPY, Expr.CLOSED -> 0.2f; Expr.NERVOUS, Expr.WORRIED -> 0.8f; else -> 0.55f }
                lookTX = (rng.nextFloat() * 2f - 1f) * range
                lookTY = (rng.nextFloat() * 2f - 1f) * range * 0.5f
                nextSaccade = when (expr) { Expr.NERVOUS, Expr.WORRIED -> 0.4f + rng.nextFloat() * 0.6f; Expr.SLEEPY -> 3f + rng.nextFloat() * 3f; else -> 1.2f + rng.nextFloat() * 2.5f }
            }
        }
        nextMicro -= dt
        if (nextMicro <= 0f) {
            microX = (rng.nextFloat() - 0.5f) * 0.07f; microY = (rng.nextFloat() - 0.5f) * 0.05f
            nextMicro = 0.25f + rng.nextFloat() * 0.6f
        }
        val lk = 1f - exp(-dt * 14f)
        lookX += (lookTX + microX - lookX) * lk
        lookY += (lookTY + microY - lookY) * lk

        // ---- blinking
        nextBlink -= dt
        if (blinkT < 0f && nextBlink <= 0f) blinkT = 0f
        if (blinkT >= 0f) {
            blinkT += dt
            val dur = if (expr == Expr.SLEEPY || energy < 0.3f) 0.35f else 0.15f
            blink = 1f - sin(PI.toFloat() * min(1f, blinkT / dur))
            if (blinkT >= dur) {
                blinkT = -1f; blink = 1f
                if (!doubleBlink && rng.nextFloat() < 0.15f) { doubleBlink = true; nextBlink = 0.12f }
                else { doubleBlink = false; nextBlink = if (expr == Expr.SLEEPY) 1.2f + rng.nextFloat() * 1.5f else 1.8f + rng.nextFloat() * 3.5f }
            }
        }

        // ---- mouth while speaking: driven by the letters being said
        var emphNow = false
        val targetTalk = if (!talking) 0f else {
            val i = (speechT * speechCps).toInt()
            val whisper = if (speechStyle == SpeechStyle.WHISPER) 0.45f else 1f
            if (i < speechSlots.size) {
                val c = speechSlots[i].lowercaseChar()
                emphNow = speechEmph[i]
                val base = when {
                    c in "aeiouy" -> 0.95f
                    c in "mbp" -> 0.08f
                    c.isLetter() -> 0.45f
                    c == ' ' -> 0.15f
                    else -> 0f
                }
                (base * (0.85f + 0.15f * sin(time * 31f)) * (if (emphNow) 1.15f else 1f) * whisper).coerceIn(0f, 1f)
            } else (0.25f + 0.75f * abs(sin(time * 13f)) * (0.6f + 0.4f * sin(time * 3.1f))) * whisper
        }
        talkLevel += (targetTalk - talkLevel) * (1f - exp(-dt * 28f))
        speakGlow += ((if (emphNow) 1f else 0f) - speakGlow) * (1f - exp(-dt * 10f))

        if (emote != null) {
            emoteT += dt
            if (emoteT > 2.6f) emote = null
        }
    }
}
