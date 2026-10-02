package com.pipo.robot.engine

import kotlin.math.abs
import kotlin.random.Random

/**
 * Cricket, you vs Pipo. He bowls you an over (6 balls): tap to swing, and your timing is your
 * score. Then he bats an over chasing it. Pure and deterministic for a seed.
 */
class CricketSim(seed: Int, private val pipoSkill: Float) {
    /** You bat (BOWLING/SHOT), then you bowl to him (PIPO_AIM → PIPO_BALL), then OVER. */
    enum class Phase { READY, BOWLING, SHOT, PIPO_AIM, PIPO_BALL, OVER }
    private val rng = Random(seed)
    var phase = Phase.READY
        private set
    var ball = 0               // balls bowled to you (0..6)
    var yourRuns = 0
    var pipoRuns = 0
    var pipoBall = 0
    /** 0 at the bowler's hand, 1 at your bat (it carries on to ~1.08 at the stumps). */
    var progress = 0f
        private set
    var speed = 0.8f
        private set
    var drift = 0f
        private set
    /** Runs off the last ball (0..6), -1 = bowled. */
    var last: Int? = null
        private set
    /** How your swing was timed on the last ball: "early", "late", "good", "perfect", or "" (left it). */
    var timing = ""
        private set
    /** Time since the ball was hit/finished (for the shot animation). */
    var shotT = 0f
        private set
    var wickets = 0
        private set
    val out get() = wickets >= 3
    val target get() = yourRuns + 1
    private var swung = false
    private var swingAt = -1f
    private var claimed = false

    /** Pipo's innings: your aim (-1 left … 0 on the stumps … 1 right), the ball's travel, what he did. */
    var aim = 0f
        private set
    private var aimT = 0f
    private var bowledAim = 0f
    var pipoProgress = 0f
        private set
    var pipoLast: Int? = null
        private set
    val pipoWickets get() = pipoOuts
    private var pipoOuts = 0

    fun start() { if (phase == Phase.READY) next() }

    private fun next() {
        if (ball >= 6 || out) { phase = Phase.PIPO_AIM; shotT = 0f; pipoProgress = 0f; aimT = 0f; return }
        phase = Phase.BOWLING; progress = 0f; swung = false; swingAt = -1f; timing = ""
        speed = 0.6f + rng.nextFloat() * 0.35f + ball * 0.03f
        drift = (rng.nextFloat() - 0.5f) * 0.2f
    }

    /**
     * Tap: one swing per ball. The sweet spot is when the ball reaches you (progress ~0.9). Swing
     * too early and the ball carries on past the bat — straight ones hit the stumps.
     */
    fun swing(): Boolean {
        if (phase != Phase.BOWLING || swung) return false
        swung = true; swingAt = progress
        val off = progress - 0.9f
        if (abs(off) < 0.16f) {
            val a = abs(off)
            val runs = when { a < 0.03f -> 6; a < 0.06f -> 4; a < 0.1f -> 2; else -> 1 }
            timing = if (a < 0.03f) "perfect" else if (a < 0.08f) "good" else if (off < 0f) "early" else "late"
            land(runs)
        } else timing = if (off < 0f) "early" else "late" // a miss: the ball keeps coming
        return true
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
            Phase.BOWLING -> {
                progress += speed * dt
                // past the bat: straight ones hit the stumps (whether you swung and missed or left it)
                if (progress >= 1.08f) land(if (abs(drift) < 0.06f) -1 else 0)
            }
            Phase.SHOT -> { shotT += dt; if (shotT > 1.2f) next() }
            Phase.PIPO_AIM -> { aimT += dt; aim = kotlin.math.sin(aimT * (2.2f + pipoBall * 0.25f)) } // the arrow sweeps faster each ball
            Phase.PIPO_BALL -> {
                shotT += dt
                pipoProgress = (shotT / 0.9f).coerceAtMost(1f)
                if (shotT > 2.0f) {
                    if (pipoBall >= 6 || pipoRuns >= target || pipoOuts >= 3) { phase = Phase.OVER; return true }
                    phase = Phase.PIPO_AIM; shotT = 0f; pipoProgress = 0f
                }
            }
            else -> Unit
        }
        return false
    }

    /**
     * You bowl to Pipo: release while the arrow is on the stumps. The straighter the ball, the
     * harder it is for him (and the likelier he's bowled); wide balls are easy runs.
     */
    fun bowl(): Boolean {
        if (phase != Phase.PIPO_AIM) return false
        bowledAim = aim
        val accuracy = (1f - abs(aim)).coerceIn(0f, 1f) // 1 = dead straight
        pipoBall++
        val r = rng.nextFloat()
        val outChance = accuracy * accuracy * 0.42f * (1.1f - pipoSkill * 0.5f)
        val runs = when {
            r < outChance -> -1
            else -> {
                val hit = rng.nextFloat() * (0.5f + pipoSkill) * (1.3f - accuracy * 0.7f)
                when { hit > 1.05f -> 6; hit > 0.8f -> 4; hit > 0.55f -> 2; hit > 0.3f -> 1; else -> 0 }
            }
        }
        pipoLast = runs
        if (runs > 0) pipoRuns += runs
        if (runs == -1) pipoOuts++
        phase = Phase.PIPO_BALL; shotT = 0f; pipoProgress = 0f
        return true
    }
    val lastAim get() = bowledAim

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
