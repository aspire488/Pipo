package com.pipo.robot.engine

/**
 * Memory Match levels: beat Pipo to move up, and both the board and his memory grow.
 * Level 0: 6 pairs (3×4), 1: 8 pairs (4×4), 2: 10 pairs (5×4). Losing never moves you down.
 */
object MemoryLevels {
    const val MAX = 2
    const val COLS = 4
    fun pairs(level: Int) = 6 + level.coerceIn(0, MAX) * 2
    fun rows(level: Int) = pairs(level) * 2 / COLS
    /** How much sharper Pipo's memory gets on bigger boards. */
    fun recallBonus(level: Int) = level.coerceIn(0, MAX) * 0.08f
    /** [youWon]: true you won, false Pipo won, null tie. */
    fun next(level: Int, youWon: Boolean?) = if (youWon == true) (level + 1).coerceAtMost(MAX) else level.coerceIn(0, MAX)
    fun name(level: Int) = listOf("Easy", "Tricky", "Brain-melter")[level.coerceIn(0, MAX)]
}
