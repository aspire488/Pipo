package com.pipo.robot.engine

import kotlin.random.Random

/**
 * Pipo's Pattern: his antenna lights four pads in a sequence, you repeat it, and it grows by one
 * each round. It's played together, not against him: once per run, when you slip, Pipo may
 * remember the next light for you (how likely depends on how sharp he is right now).
 */
class PatternRun(private val rng: Random, private val pipoRecall: Float, val pads: Int = 4) {
    enum class Step { HIT, ROUND_DONE, SAVED, OVER }

    val sequence = mutableListOf<Int>()
    var pos = 0; private set
    var over = false; private set
    var saveUsed = false; private set
    /** Rounds fully repeated: the score. */
    var rounds = 0; private set

    /** Start the next round: one more light. Returns the whole sequence to show. */
    fun next(): List<Int> {
        check(!over) { "run is over" }
        sequence += rng.nextInt(pads)
        pos = 0
        return sequence.toList()
    }

    /** How long each light stays on, in ms: faster as the pattern grows, never below 260. */
    fun flashMs(): Long = (620L - sequence.size * 30L).coerceAtLeast(260L)

    fun press(pad: Int): Step {
        if (over || sequence.isEmpty()) return Step.OVER
        require(pad in 0 until pads) { "no pad $pad" }
        if (pad == sequence[pos]) {
            pos++
            if (pos < sequence.size) return Step.HIT
            rounds++
            return Step.ROUND_DONE
        }
        // one save per run: Pipo remembers, and you carry on from the light you missed
        if (!saveUsed && rng.nextFloat() < pipoRecall) { saveUsed = true; return Step.SAVED }
        over = true
        return Step.OVER
    }

    /** The light Pipo "remembers" for you after a save. */
    fun expected(): Int = sequence[pos]
}
