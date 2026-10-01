package com.pipo.robot.engine

import kotlin.random.Random

enum class ArcadePhase { READY, PLAYING, OVER }

/**
 * Flappy Pipo: tap to hop Pipo through gaps in the pipes. Normalised coordinates (x, y in 0..1,
 * y down). Pure and deterministic for a seed; the phase guards mean a crash ends the game exactly
 * once and the result is claimed exactly once, whatever the UI does.
 */
class FlappySim(seed: Int) {
    data class Pipe(var x: Float, val gapY: Float, val gap: Float, var passed: Boolean = false)

    private val rng = Random(seed)
    var phase = ArcadePhase.READY
        private set
    var y = 0.45f
        private set
    var vy = 0f
        private set
    var score = 0
        private set
    val pipes = mutableListOf<Pipe>()
    private var spawnIn = 0.6f
    private var claimed = false

    val birdX = 0.28f
    val radius = 0.035f
    val pipeW = 0.14f

    fun flap(): Boolean {
        if (phase == ArcadePhase.OVER) return false
        if (phase == ArcadePhase.READY) phase = ArcadePhase.PLAYING
        // one hop rises ~13% of the screen: a gap is at least 22%, so a well-timed hop always fits
        vy = -0.8f
        return true
    }

    /** Advances the game. Returns true on the frame he crashes (once). */
    fun step(dtIn: Float): Boolean {
        if (phase != ArcadePhase.PLAYING) return false
        val dt = dtIn.coerceIn(0f, 0.05f)
        vy = (vy + 2.4f * dt).coerceAtMost(1.4f)
        y += vy * dt
        val speed = 0.3f + minOf(score, 30) * 0.004f
        for (p in pipes) p.x -= speed * dt
        pipes.removeAll { it.x < -pipeW }
        spawnIn -= dt
        if (spawnIn <= 0f) {
            spawnIn = 1.55f
            val gap = (0.3f - minOf(score, 25) * 0.003f).coerceAtLeast(0.22f)
            pipes += Pipe(1.05f, 0.25f + rng.nextFloat() * 0.5f, gap)
        }
        for (p in pipes) if (!p.passed && p.x + pipeW < birdX - radius) { p.passed = true; score++ }
        val hitPipe = pipes.any { p -> birdX + radius > p.x && birdX - radius < p.x + pipeW && (y - radius < p.gapY - p.gap / 2f || y + radius > p.gapY + p.gap / 2f) }
        if (y > 1f - radius || y < radius || hitPipe) { phase = ArcadePhase.OVER; return true }
        return false
    }

    fun claimResult(): Boolean { if (phase != ArcadePhase.OVER || claimed) return false; claimed = true; return true }
}

/**
 * Pixel Shooter: his arcade's space game. Your little ship (a pixel Pipo head) slides along the
 * bottom and fires on its own; rows of pixel moths march down. Three lives. Waves speed up.
 */
class ShooterSim(seed: Int) {
    data class Invader(var x: Float, var y: Float, var alive: Boolean = true)
    data class Shot(var x: Float, var y: Float, val up: Boolean)

    private val rng = Random(seed)
    var phase = ArcadePhase.READY
        private set
    var shipX = 0.5f
        private set
    var score = 0
        private set
    var lives = 3
        private set
    var wave = 1
        private set
    val invaders = mutableListOf<Invader>()
    val shots = mutableListOf<Shot>()
    private var dir = 1f
    private var fireIn = 0f
    private var enemyFireIn = 1.2f
    private var claimed = false
    /** Set when something happened this step (for Pipo's commentary): "hit", "wave", "lost". */
    var event: String? = null
        private set

    val shipY = 0.9f

    init { spawnWave() }

    private fun spawnWave() {
        invaders.clear()
        for (r in 0 until 3) for (c in 0 until 6) invaders += Invader(0.15f + c * 0.13f, 0.1f + r * 0.08f)
    }

    fun start(): Boolean { if (phase != ArcadePhase.READY) return false; phase = ArcadePhase.PLAYING; return true }

    fun moveTo(x: Float) { if (phase != ArcadePhase.OVER) shipX = x.coerceIn(0.06f, 0.94f) }

    /** Advances the game. Returns true on the frame it ends (once). */
    fun step(dtIn: Float): Boolean {
        event = null
        if (phase != ArcadePhase.PLAYING) return false
        val dt = dtIn.coerceIn(0f, 0.05f)
        // your ship fires on its own
        fireIn -= dt
        if (fireIn <= 0f) { fireIn = 0.38f; shots += Shot(shipX, shipY - 0.03f, true) }
        // the moths march, and drop a row at the edges
        val alive = invaders.filter { it.alive }
        val speed = (0.06f + wave * 0.02f) * (1f + (18 - alive.size) * 0.04f)
        val hitEdge = alive.any { (it.x > 0.94f && dir > 0) || (it.x < 0.06f && dir < 0) }
        if (hitEdge) { dir = -dir; alive.forEach { it.y += 0.04f } }
        alive.forEach { it.x += dir * speed * dt }
        // and sometimes they shoot back
        enemyFireIn -= dt
        if (enemyFireIn <= 0f && alive.isNotEmpty()) {
            enemyFireIn = (1.4f - wave * 0.12f).coerceAtLeast(0.5f) * (0.6f + rng.nextFloat() * 0.8f)
            val shooter = alive[rng.nextInt(alive.size)]
            shots += Shot(shooter.x, shooter.y + 0.03f, false)
        }
        for (sh in shots) sh.y += (if (sh.up) -1.1f else 0.55f) * dt
        // collisions
        for (sh in shots.filter { it.up }) {
            val hit = invaders.firstOrNull { it.alive && kotlin.math.abs(it.x - sh.x) < 0.045f && kotlin.math.abs(it.y - sh.y) < 0.035f }
            if (hit != null) { hit.alive = false; sh.y = -1f; score += 10 * wave }
        }
        for (sh in shots.filter { !it.up }) {
            if (kotlin.math.abs(sh.x - shipX) < 0.05f && kotlin.math.abs(sh.y - shipY) < 0.035f) { sh.y = 2f; lives--; event = "hit" }
        }
        shots.removeAll { it.y < 0f || it.y > 1f }
        if (invaders.none { it.alive }) { wave++; spawnWave(); event = "wave" }
        if (lives <= 0 || invaders.any { it.alive && it.y > shipY - 0.05f }) { phase = ArcadePhase.OVER; event = "lost"; return true }
        return false
    }

    fun claimResult(): Boolean { if (phase != ArcadePhase.OVER || claimed) return false; claimed = true; return true }
}

/** Pipo's own arcade practice: his records in these games come from actually playing on his cabinet. */
object ArcadePractice {
    /** A practice run. Better with practice, worse when sleepy. Returns (game, score, isNewBest). */
    fun play(s: com.pipo.robot.data.PipoState, rng: Random, now: Long): Triple<String, Int, Boolean> {
        val game = if (rng.nextBoolean()) "flappy" else "shooter"
        val practice = (s.counters["act:PLAY_ARCADE"] ?: 0).coerceAtMost(80)
        val skill = 3f + practice * 0.25f + s.mood.energy * 4f + s.profile.traits.patience * 3f
        val raw = (skill * (0.3f + rng.nextFloat())).toInt()
        val score = if (game == "shooter") raw * 20 else raw
        val key = "pipo:$game"
        val best = s.records[key] ?: 0
        val newBest = score > best
        if (newBest) {
            s.records[key] = score
            Chronicle.remember(s, com.pipo.robot.data.MemoryType.SELF, "my best at ${Dialogue.gameName(game)} is $score", 0.4f, now, "record:$game")
        }
        return Triple(game, score, newBest)
    }
}
