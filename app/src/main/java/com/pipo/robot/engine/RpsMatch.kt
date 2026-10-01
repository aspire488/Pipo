package com.pipo.robot.engine

enum class RpsHand {
    ROCK, PAPER, SCISSORS;

    fun beats(o: RpsHand) = (this == ROCK && o == SCISSORS) || (this == PAPER && o == ROCK) || (this == SCISSORS && o == PAPER)
    fun beatenBy(): RpsHand = entries.first { it.beats(this) }
}

/**
 * WAITING_FOR_USER → USER_LOCKED → PIPO_THINKING → REVEAL → RESOLVE → (NEXT_ROUND →) WAITING_FOR_USER … MATCH_OVER
 */
enum class RpsPhase { WAITING_FOR_USER, USER_LOCKED, PIPO_THINKING, REVEAL, RESOLVE, MATCH_OVER }

/** [winner]: 1 = Pipo, -1 = you, 0 = draw. */
data class RpsRound(val index: Int, val user: RpsHand, val pipo: RpsHand, val winner: Int)

/**
 * One match of Rock Paper Scissors, first to [target]. Every transition is guarded by the phase it
 * comes from, so however many times the UI calls in (double taps, recomposition, a coroutine that
 * outlives a frame) there is exactly one user choice, one Pipo choice, one resolution and one score
 * update per round, and the match result is claimed exactly once.
 *
 * [picker] chooses Pipo's hand from the history of your previous throws (oldest first).
 */
class RpsMatch(val target: Int = 3, private val picker: (List<RpsHand>) -> RpsHand) {
    var phase = RpsPhase.WAITING_FOR_USER
        private set
    var round = 0
        private set
    var userScore = 0
        private set
    var pipoScore = 0
        private set
    var userHand: RpsHand? = null
        private set
    var pipoHand: RpsHand? = null
        private set
    private val history = mutableListOf<RpsHand>()
    val rounds = mutableListOf<RpsRound>()
    private var resultClaimed = false

    val over get() = userScore >= target || pipoScore >= target
    /** true = Pipo won the match, false = you did, null = not over yet. */
    val pipoWon: Boolean? get() = if (!over) null else pipoScore > userScore

    /** You throw. Ignored unless it's your turn. */
    fun lock(h: RpsHand): Boolean {
        if (phase != RpsPhase.WAITING_FOR_USER) return false
        userHand = h
        pipoHand = null
        phase = RpsPhase.USER_LOCKED
        return true
    }

    fun think(): Boolean {
        if (phase != RpsPhase.USER_LOCKED) return false
        phase = RpsPhase.PIPO_THINKING
        return true
    }

    /** Pipo decides and shows his hand. Decided once per round. */
    fun reveal(): RpsHand? {
        if (phase != RpsPhase.PIPO_THINKING) return null
        val p = picker(history.toList())
        pipoHand = p
        phase = RpsPhase.REVEAL
        return p
    }

    /** Scores the round. Returns null if it was already scored (or it's not time). */
    fun resolve(): RpsRound? {
        if (phase != RpsPhase.REVEAL) return null
        val u = userHand ?: return null
        val p = pipoHand ?: return null
        val w = when { p.beats(u) -> 1; u.beats(p) -> -1; else -> 0 }
        if (w == 1) pipoScore++ else if (w == -1) userScore++
        history += u
        val r = RpsRound(round, u, p, w)
        rounds += r
        phase = RpsPhase.RESOLVE
        return r
    }

    /** On to the next round, or the end of the match. */
    fun next(): Boolean {
        if (phase != RpsPhase.RESOLVE) return false
        if (over) phase = RpsPhase.MATCH_OVER
        else { round++; phase = RpsPhase.WAITING_FOR_USER }
        return true
    }

    /** True exactly once, when the match is over: the caller records the result then. */
    fun claimResult(): Boolean {
        if (phase != RpsPhase.MATCH_OVER || resultClaimed) return false
        resultClaimed = true
        return true
    }

    fun reset() {
        phase = RpsPhase.WAITING_FOR_USER
        round = 0; userScore = 0; pipoScore = 0
        userHand = null; pipoHand = null
        history.clear(); rounds.clear()
        resultClaimed = false
    }
}
