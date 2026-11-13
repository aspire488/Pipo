package com.pipo.robot.engine

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pipo Football: a real-time 1-on-1 match on a little top-down pitch. Coordinates are 0..1 across
 * and 0..1 down; you attack the top goal, Pipo attacks the bottom one. Drag to run (the ball sticks
 * to your feet when you reach it), tap to kick. First to 3 goals, or whoever leads at full time.
 * Pure and deterministic for a seed.
 */
class FootballSim(seed: Int, private val pipoSkill: Float) {
    enum class Phase { READY, PLAYING, GOAL, OVER }
    class Body(var x: Float, var y: Float) { var vx = 0f; var vy = 0f }

    private val rng = Random(seed)
    var phase = Phase.READY; private set
    val you = Body(0.5f, 0.72f)
    val pipo = Body(0.5f, 0.28f)
    val ball = Body(0.5f, 0.5f)
    var yourGoals = 0; private set
    var pipoGoals = 0; private set
    /** Who has the ball at their feet: 1 you, 2 Pipo, 0 loose. */
    var owner = 0; private set
    var clock = 0f; private set
    var lastGoalPipo: Boolean? = null; private set
    /** A tackle just happened (for reactions): 1 you won the ball, 2 Pipo did. Cleared by [consumeEvent]. */
    var event = 0; private set
    private var goalT = 0f
    private var target: Pair<Float, Float>? = null
    private var kickCooldown = 0f
    private var pipoThink = 0f
    private var pipoGoal = Pair(0.5f, 0.5f)
    private var claimed = false

    companion object {
        const val MATCH_SECONDS = 120f
        const val WIN_GOALS = 3
        const val GOAL_HALF = 0.16f        // goal mouth half-width
        const val FEET = 0.045f            // how close you must be to control the ball
        const val YOU_SPEED = 0.42f
        const val FRICTION = 1.6f          // ball slows by this factor per second (exp)
    }

    val goalMouth get() = (0.5f - GOAL_HALF)..(0.5f + GOAL_HALF)
    val timeLeft get() = (MATCH_SECONDS - clock).coerceAtLeast(0f)

    fun start() { if (phase == Phase.READY) { phase = Phase.PLAYING; kickOff(towardsYou = rng.nextBoolean()) } }

    private fun kickOff(towardsYou: Boolean) {
        you.x = 0.5f; you.y = 0.72f; pipo.x = 0.5f; pipo.y = 0.28f
        ball.x = 0.5f; ball.y = 0.5f; ball.vx = 0f; ball.vy = if (towardsYou) 0.12f else -0.12f
        owner = 0; target = null; kickCooldown = 0.4f
    }

    /** Your finger: you run towards it (null = stop). */
    fun runTo(x: Float?, y: Float?) { target = if (x == null || y == null) null else Pair(x.coerceIn(0.03f, 0.97f), y.coerceIn(0.03f, 0.97f)) }

    /** Tap: kick towards the point. Harder the farther it is. Only with the ball at your feet (or touching it). */
    fun kick(tx: Float, ty: Float): Boolean {
        if (phase != Phase.PLAYING || kickCooldown > 0f) return false
        val near = hypot(ball.x - you.x, ball.y - you.y) < FEET * 1.6f
        if (owner != 1 && !near) return false
        val dx = tx - ball.x; val dy = ty - ball.y
        val d = hypot(dx, dy).coerceAtLeast(0.001f)
        val power = (0.55f + d * 1.1f).coerceAtMost(1.35f)
        ball.vx = dx / d * power; ball.vy = dy / d * power
        owner = 0; kickCooldown = 0.35f
        return true
    }

    fun consumeEvent(): Int = event.also { event = 0 }

    /** Advance time. Returns true on the frame the match ends. */
    fun step(dtIn: Float): Boolean {
        val dt = dtIn.coerceIn(0f, 0.05f)
        when (phase) {
            Phase.GOAL -> { goalT += dt; if (goalT > 1.6f) { phase = Phase.PLAYING; kickOff(towardsYou = lastGoalPipo == false) } ; return false }
            Phase.PLAYING -> Unit
            else -> return false
        }
        clock += dt
        kickCooldown = (kickCooldown - dt).coerceAtLeast(0f)

        // you: run towards your finger (a bit slower with the ball)
        target?.let { (tx, ty) ->
            val dx = tx - you.x; val dy = ty - you.y; val d = hypot(dx, dy)
            val sp = YOU_SPEED * (if (owner == 1) 0.85f else 1f)
            if (d > 0.01f) { val m = (sp * dt).coerceAtMost(d); you.x += dx / d * m; you.y += dy / d * m }
        }
        pipoBrain(dt)
        you.x = you.x.coerceIn(0.03f, 0.97f); you.y = you.y.coerceIn(0.03f, 0.97f)
        pipo.x = pipo.x.coerceIn(0.03f, 0.97f); pipo.y = pipo.y.coerceIn(0.03f, 0.97f)

        // possession: whoever's feet are on a slow ball takes it; contact while dribbling is a tackle
        val dYou = hypot(ball.x - you.x, ball.y - you.y); val dPipo = hypot(ball.x - pipo.x, ball.y - pipo.y)
        val slow = hypot(ball.vx, ball.vy) < 0.5f
        if (kickCooldown == 0f) when {
            owner == 2 && dYou < FEET && dYou < dPipo -> { owner = 1; event = 1; kickCooldown = 0.25f }
            // he wins it back at ~0.6..1.5 tackles a second of contact: you can still dribble past him
            owner == 1 && dPipo < FEET && rng.nextFloat() < (0.6f + pipoSkill * 0.9f) * dt -> { owner = 2; event = 2; kickCooldown = 0.25f }
            owner == 0 && slow && dYou < FEET && dYou <= dPipo -> owner = 1
            owner == 0 && slow && dPipo < FEET -> owner = 2
        }
        // dribble: the ball rolls just ahead of whoever has it
        when (owner) {
            1 -> carry(you, -1f)
            2 -> carry(pipo, 1f)
            else -> {
                ball.x += ball.vx * dt; ball.y += ball.vy * dt
                val f = kotlin.math.exp(-FRICTION * dt); ball.vx *= f; ball.vy *= f
            }
        }
        // walls (not across the goal mouths)
        if (ball.x < 0.02f || ball.x > 0.98f) { ball.vx = -ball.vx * 0.8f; ball.x = ball.x.coerceIn(0.02f, 0.98f) }
        val inMouth = ball.x in goalMouth
        if (ball.y < 0.0f) { if (inMouth) return goal(byPipo = false) else { ball.vy = abs(ball.vy) * 0.8f; ball.y = 0.0f } }
        if (ball.y > 1.0f) { if (inMouth) return goal(byPipo = true) else { ball.vy = -abs(ball.vy) * 0.8f; ball.y = 1.0f } }

        if (clock >= MATCH_SECONDS) { phase = Phase.OVER; return true }
        return false
    }

    private fun carry(p: Body, dir: Float) {
        ball.x += (p.x - ball.x) * 0.35f
        ball.y += (p.y + dir * FEET * 0.8f - ball.y) * 0.35f
        ball.vx = 0f; ball.vy = 0f
    }

    private fun goal(byPipo: Boolean): Boolean {
        if (byPipo) pipoGoals++ else yourGoals++
        lastGoalPipo = byPipo
        owner = 0; ball.vx = 0f; ball.vy = 0f
        if (pipoGoals >= WIN_GOALS || yourGoals >= WIN_GOALS) { phase = Phase.OVER; return true }
        phase = Phase.GOAL; goalT = 0f
        return false
    }

    /** Pipo: chase the ball, dribble at your goal, shoot when he sees it, get back when you have it. */
    private fun pipoBrain(dt: Float) {
        val speed = 0.3f + pipoSkill * 0.14f
        pipoThink -= dt
        if (pipoThink <= 0f) {
            pipoThink = 0.25f + (1f - pipoSkill) * 0.25f   // he reacts a beat late, like a small robot would
            pipoGoal = when (owner) {
                2 -> Pair(0.5f + (rng.nextFloat() - 0.5f) * 0.3f, 0.95f)               // run at your goal
                1 -> Pair(you.x * 0.7f + 0.15f, (you.y - 0.08f).coerceAtLeast(0.15f))  // pressure you, goal-side
                else -> Pair(ball.x + ball.vx * 0.25f, ball.y + ball.vy * 0.25f)        // go to the ball
            }
        }
        val dx = pipoGoal.first - pipo.x; val dy = pipoGoal.second - pipo.y; val d = hypot(dx, dy)
        val sp = speed * (if (owner == 2) 0.85f else 1f)
        if (d > 0.01f) { val m = (sp * dt).coerceAtMost(d); pipo.x += dx / d * m; pipo.y += dy / d * m }
        // shoot when close enough to your goal (aim is a little off sometimes)
        if (owner == 2 && pipo.y > 0.68f && kickCooldown == 0f) {
            val aimX = 0.5f + (rng.nextFloat() - 0.5f) * GOAL_HALF * (2.6f - pipoSkill)
            val tx = aimX - ball.x; val ty = 1.05f - ball.y; val n = sqrt(tx * tx + ty * ty)
            val power = 0.95f + pipoSkill * 0.3f
            ball.vx = tx / n * power; ball.vy = ty / n * power
            owner = 0; kickCooldown = 0.45f
        }
    }

    /** Who won (null while playing, or a draw at full time). */
    val pipoWon: Boolean? get() = if (phase != Phase.OVER || pipoGoals == yourGoals) null else pipoGoals > yourGoals

    fun claimResult(): Boolean { if (phase != Phase.OVER || claimed) return false; claimed = true; return true }
}
