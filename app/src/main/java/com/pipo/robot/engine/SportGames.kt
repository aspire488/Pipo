package com.pipo.robot.engine

import kotlin.math.abs
import kotlin.random.Random

/**
 * Cricket, you vs Pipo. He bowls you an over (6 balls): tap to swing, and your timing is your
 * score. Then he bats an over chasing it. Pure and deterministic for a seed.
 */
class CricketSim(seed: Int, private val pipoSkill: Float) {
    enum class Phase { READY, BOWLING, SHOT, PIPO_BATS, OVER }
    private val rng = Random(seed)
    var phase = Phase.READY
        private set
    var ball = 0               // balls bowled to you (0..6)
    var yourRuns = 0
    var pipoRuns = 0
    var pipoBall = 0
    /** 0 at Pipo's hand, 1 at your bat. */
    var progress = 0f
        private set
    /** This delivery's speed (per second) and sideways drift. */
    var speed = 0.8f
        private set
    var drift = 0f
        private set
    /** What happened on the last ball: runs (0..6), -1 = bowled. */
    var last: Int? = null
        private set
    private var shotT = 0f
    private var claimed = false
    /** Three wickets in an over: get bowled three times and your innings is done. */
    var wickets = 0
        private set
    val out get() = wickets >= 3
    val target get() = yourRuns + 1

    fun start() { if (phase == Phase.READY) next() }

    private fun next() {
        if (ball >= 6 || out) { phase = Phase.PIPO_BATS; shotT = 0f; return }
        phase = Phase.BOWLING; progress = 0f
        speed = 0.65f + rng.nextFloat() * 0.45f + ball * 0.03f
        drift = (rng.nextFloat() - 0.5f) * 0.2f
    }

    /** Tap: swing. Timing near progress 0.9 is perfect. Returns runs (or -1 if bowled). */
    fun swing(): Int? {
        if (phase != Phase.BOWLING) return null
        val off = abs(progress - 0.9f)
        val runs = when {
            off < 0.03f -> 6
            off < 0.06f -> 4
            off < 0.1f -> 2
            off < 0.16f -> 1
            abs(drift) < 0.06f && progress > 0.7f -> -1 // swung and missed a straight one: bowled
            else -> 0
        }
        land(runs)
        return runs
    }

    private fun land(r: Int) {
        last = r; ball++
        if (r > 0) yourRuns += r
        if (r == -1) wickets++
        phase = Phase.SHOT; shotT = 0f
    }

    /** Advance time. Returns true on the frame the match ends. */
    fun step(dtIn: Float): Boolean {
        val dt = dtIn.coerceIn(0f, 0.05f)
        when (phase) {
            Phase.BOWLING -> { progress += speed * dt; if (progress >= 1.05f) land(if (abs(drift) < 0.06f) -1 else 0) } // left it: straight ones hit the stumps
            Phase.SHOT -> { shotT += dt; if (shotT > 1.1f) next() }
            Phase.PIPO_BATS -> {
                shotT += dt
                if (shotT > 0.9f) {
                    shotT = 0f
                    if (pipoBall < 6 && pipoRuns < target) {
                        pipoBall++
                        val r = rng.nextFloat() * (0.6f + pipoSkill)
                        pipoRuns += when { r > 1.25f -> 6; r > 1.0f -> 4; r > 0.7f -> 2; r > 0.4f -> 1; else -> 0 }
                    } else { phase = Phase.OVER; return true }
                }
            }
            else -> Unit
        }
        return false
    }

    /** Did Pipo win the chase? (null while playing) */
    val pipoWon: Boolean? get() = if (phase != Phase.OVER) null else pipoRuns >= target

    fun claimResult(): Boolean { if (phase != Phase.OVER || claimed) return false; claimed = true; return true }
}

/** Table tennis, you vs Pipo, top-down: drag your paddle (bottom), he plays the top. First to 7. */
class PongSim(seed: Int, private val pipoSkill: Float) {
    private val rng = Random(seed)
    var phase = ArcadePhase.READY
        private set
    var you = 0; var pipo = 0
    var ballX = 0.5f; var ballY = 0.5f
    var vx = 0f; var vy = 0f
    var yourX = 0.5f
    var pipoX = 0.5f
    val paddleW = 0.22f
    /** Who scored last (for his face): true = Pipo. */
    var lastPointPipo: Boolean? = null
        private set
    private var claimed = false

    fun start() { if (phase == ArcadePhase.READY) { phase = ArcadePhase.PLAYING; serve(towardsYou = rng.nextBoolean()) } }

    private fun serve(towardsYou: Boolean) {
        ballX = 0.5f; ballY = 0.5f
        vx = (rng.nextFloat() - 0.5f) * 0.5f
        vy = if (towardsYou) 0.55f else -0.55f
    }

    fun moveTo(x: Float) { if (phase != ArcadePhase.OVER) yourX = x.coerceIn(paddleW / 2f, 1f - paddleW / 2f) }

    fun step(dtIn: Float): Boolean {
        if (phase != ArcadePhase.PLAYING) return false
        val dt = dtIn.coerceIn(0f, 0.05f)
        // Pipo tracks the ball, a little late and not quite fast enough (he's small)
        val aim = if (vy < 0) ballX + vx * 0.15f else 0.5f
        val maxV = 0.45f + pipoSkill * 0.35f
        pipoX += (aim - pipoX).coerceIn(-maxV * dt, maxV * dt)
        pipoX = pipoX.coerceIn(paddleW / 2f, 1f - paddleW / 2f)
        ballX += vx * dt; ballY += vy * dt
        if (ballX < 0.02f || ballX > 0.98f) { vx = -vx; ballX = ballX.coerceIn(0.02f, 0.98f) }
        // your paddle (bottom, y 0.92) and his (top, y 0.08)
        if (vy > 0 && ballY >= 0.9f && ballY < 0.95f && abs(ballX - yourX) < paddleW / 2f + 0.02f) {
            vy = -(abs(vy) * 1.06f).coerceAtMost(1.6f); vx += (ballX - yourX) * 2.2f; ballY = 0.9f
        }
        if (vy < 0 && ballY <= 0.1f && ballY > 0.05f && abs(ballX - pipoX) < paddleW / 2f + 0.02f) {
            vy = (abs(vy) * 1.05f).coerceAtMost(1.6f); vx += (ballX - pipoX) * 2.2f + (rng.nextFloat() - 0.5f) * 0.2f; ballY = 0.1f
        }
        vx = vx.coerceIn(-1.1f, 1.1f)
        if (ballY > 1.02f) { pipo++; lastPointPipo = true; if (pipo >= 7) { phase = ArcadePhase.OVER; return true }; serve(towardsYou = true) }
        if (ballY < -0.02f) { you++; lastPointPipo = false; if (you >= 7) { phase = ArcadePhase.OVER; return true }; serve(towardsYou = false) }
        return false
    }

    fun claimResult(): Boolean { if (phase != ArcadePhase.OVER || claimed) return false; claimed = true; return true }
}
