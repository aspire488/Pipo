package com.pipo.robot.ui.games

import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.engine.Chronicle
import com.pipo.robot.engine.DAY
import com.pipo.robot.engine.Dialogue
import com.pipo.robot.engine.MoodEngine
import com.pipo.robot.engine.Personality
import com.pipo.robot.engine.Trait

/** Persists game outcomes as part of Pipo's life, and lets the home screen react afterwards. */
object GameLog {
    data class Result(val game: String, val pipoWon: Boolean?, val detail: String)

    @Volatile private var last: Result? = null
    fun consume(): Result? = last.also { last = null }

    /** [pipoWon]: true = Pipo won, false = you won, null = draw. */
    fun record(repo: PipoRepository, game: String, pipoWon: Boolean?, detail: String) {
        val now = System.currentTimeMillis()
        val gname = Dialogue.gameName(game)
        repo.mutate { s ->
            val r = s.game(game)
            val first = r.plays == 0
            r.plays++; r.lastPlayed = now
            when (pipoWon) {
                true -> { r.pipoWins++; r.pipoStreak++; r.userStreak = 0 }
                false -> { r.userWins++; r.userStreak++; r.pipoStreak = 0 }
                null -> r.draws++
            }
            s.profile.favoriteGame = s.games.maxByOrNull { it.value.plays }?.key ?: game
            s.profile.relationship = (s.profile.relationship + 0.01f).coerceAtMost(1f)
            s.lastUserInteractionAt = now
            com.pipo.robot.engine.Experience.remember(s, MemoryType.GAME, "playing $gname with you", 0.4f + minOf(r.plays, 10) * 0.03f, now, "game:$game", with = listOf("you"), feeling = if (pipoWon == false) -0.1f else 0.4f)
            com.pipo.robot.engine.Rituals.shared(s, "games", now)
            com.pipo.robot.engine.Likes.feel(s, "game:$game", if (pipoWon == true) 0.08f else if (pipoWon == false) 0.02f else 0.04f)
            Personality.nudge(s, Trait.PLAYFULNESS, 0.01f)
            Personality.nudge(s, Trait.SOCIABILITY, 0.006f)
            MoodEngine.bump(s, boredom = -0.45f, loneliness = -0.3f, happiness = 0.08f)
            val key = "journal:game:$game"
            val lastJ = s.cooldowns[key] ?: 0L
            when (pipoWon) {
                true -> {
                    Personality.nudge(s, Trait.CONFIDENCE, 0.012f)
                    MoodEngine.setTransient(s, Mood.PROUD, now, 60_000)
                    if (first || now - lastJ > DAY / 2 || r.pipoStreak == 3) {
                        s.cooldowns[key] = now
                        Chronicle.journal(s, "Pipo beat you at $gname", if (r.pipoStreak >= 3) "That's ${r.pipoStreak} in a row. He's unbearable now. $detail" else detail, JournalCategory.GAME, now)
                    }
                }
                false -> {
                    val overconfident = s.profile.traits.confidence > 0.6f
                    Personality.nudge(s, Trait.CONFIDENCE, -0.008f)
                    if (overconfident) MoodEngine.setTransient(s, Mood.EMBARRASSED, now, 30_000)
                    if (first || now - lastJ > DAY / 2 || r.userStreak == 3) {
                        s.cooldowns[key] = now
                        Chronicle.journal(s, "You beat Pipo at $gname", if (r.userStreak >= 3) "Three in a row. Pipo has requested a rematch in writing. $detail" else detail, JournalCategory.GAME, now)
                    }
                    if (r.userStreak >= 3) Chronicle.remember(s, MemoryType.GAME, "you beat me at $gname three times in a row", 0.75f, now, "streak:$game")
                }
                null -> if (first) Chronicle.journal(s, "A tie at $gname", "Nobody won. Pipo says that means he won.", JournalCategory.GAME, now)
            }
        }
        last = Result(game, pipoWon, detail)
    }
}
