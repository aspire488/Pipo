package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.PipoProject
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectRetryTest {
    private val now = 20_000L * DAY
    private val def = Catalog.projects.first()

    private fun attempt(s: PipoState, state: ProjectState, n: Int, started: Long, finished: Long) =
        PipoProject(s.nextId(), def.id, def.title, state = state, attempts = n, startedAt = started, finishedAt = finished).also { s.projects.add(it) }

    @Test
    fun anEvolvedRetrySettlesTheOldFailure() {
        // regression (seen on device): failed → retried → evolved → "trying again" from scratch
        val s = PipoState()
        attempt(s, ProjectState.FAILED, 1, now - 2 * DAY, now - 2 * DAY + HOUR)
        attempt(s, ProjectState.EVOLVED, 2, now - DAY, now - DAY + HOUR)
        assertNull(Projects.retryCandidate(s, now))
    }

    @Test
    fun aSecondFailureIsRetriedFromTheLatestAttempt() {
        val s = PipoState()
        attempt(s, ProjectState.FAILED, 1, now - 2 * DAY, now - 2 * DAY + HOUR)
        attempt(s, ProjectState.FAILED, 2, now - DAY, now - DAY + HOUR)
        assertEquals(2, Projects.retryCandidate(s, now)!!.attempts)
    }

    @Test
    fun noRetryWhileAnAttemptIsInProgress() {
        val s = PipoState()
        attempt(s, ProjectState.FAILED, 1, now - 2 * DAY, now - 2 * DAY + HOUR)
        attempt(s, ProjectState.BUILDING, 1, now - HOUR, 0L)
        assertNull(Projects.retryCandidate(s, now))
    }
}
