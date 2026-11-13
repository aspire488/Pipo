package com.pipo.robot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryLevelsTest {
    @Test
    fun boardsGrowAndAlwaysFillTheGrid() {
        assertEquals(listOf(6, 8, 10), (0..2).map { MemoryLevels.pairs(it) })
        for (l in 0..2) assertEquals(MemoryLevels.pairs(l) * 2, MemoryLevels.rows(l) * MemoryLevels.COLS)
        assertTrue(MemoryLevels.recallBonus(2) > MemoryLevels.recallBonus(0))
    }

    @Test
    fun winningMovesYouUpLosingNeverMovesYouDown() {
        assertEquals(1, MemoryLevels.next(0, youWon = true))
        assertEquals(2, MemoryLevels.next(2, youWon = true))
        assertEquals(1, MemoryLevels.next(1, youWon = false))
        assertEquals(1, MemoryLevels.next(1, youWon = null))
    }
}
