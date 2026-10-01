package com.pipo.robot.engine

import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.TripPurpose
import kotlin.random.Random

/** The games he plays with a ball (or a shuttle): not just football. */
enum class Sport(val title: String) { FOOTBALL("football"), CRICKET("cricket"), TABLE_TENNIS("table tennis"), BADMINTON("badminton") }

/**
 * Outdoor sessions and indoor practice for every sport. Like football: practice makes him better,
 * records are real numbers he remembers, and Nib gets involved.
 */
object Sports {
    fun purposeOf(sp: Sport) = when (sp) {
        Sport.FOOTBALL -> TripPurpose.FOOTBALL; Sport.CRICKET -> TripPurpose.CRICKET
        Sport.TABLE_TENNIS -> TripPurpose.TABLE_TENNIS; Sport.BADMINTON -> TripPurpose.BADMINTON
    }

    /** Where each one is played. */
    fun placeOf(sp: Sport) = when (sp) { Sport.FOOTBALL, Sport.CRICKET -> "field"; Sport.TABLE_TENNIS -> "park"; Sport.BADMINTON -> "garden" }

    fun fromText(t: String): Sport? = when {
        Regex("\\b(cricket|batting|bowling|bat)\\b").containsMatchIn(t) -> Sport.CRICKET
        Regex("\\b(table tennis|ping ?pong|tt)\\b").containsMatchIn(t) -> Sport.TABLE_TENNIS
        Regex("\\b(badminton|shuttle|shuttlecock)\\b").containsMatchIn(t) -> Sport.BADMINTON
        Regex("\\b(football|soccer|kick|kickups|kick ups|keepy)\\b").containsMatchIn(t) -> Sport.FOOTBALL
        else -> null
    }

    private fun skill(s: PipoState, key: String) = (s.counters["sport:$key"] ?: 0).coerceAtMost(30) * 0.25f + s.mood.energy * 3f + s.profile.traits.confidence * 3f

    /** Nib's game, per sport: Nib is small, but Nib is fast and has no fear. */
    private fun nibSkill(s: PipoState, sp: Sport): Float = s.pet.traits.playfulness * 4f + s.pet.energy * 3f + (s.counters["sport:${sp.name}"] ?: 0).coerceAtMost(30) * 0.15f +
        when (sp) { Sport.TABLE_TENNIS -> 1f; Sport.BADMINTON -> -0.5f; Sport.CRICKET -> 0f; Sport.FOOTBALL -> 1.5f }

    /** First to [target] (win by two): returns (pipo, nib). */
    private fun rally(rng: Random, pipo: Float, nib: Float, target: Int): Pair<Int, Int> {
        var a = 0; var b = 0
        val p = (pipo / (pipo + nib)).coerceIn(0.2f, 0.8f)
        while (!((a >= target || b >= target) && kotlin.math.abs(a - b) >= 2)) { if (rng.nextFloat() < p) a++ else b++; if (a + b > 60) break }
        return a to b
    }

    /**
     * A match with Nib at the field / park / garden: two players, a real score, a winner (sometimes
     * Nib). Returns story lines; the result is remembered by both of them.
     */
    fun session(s: PipoState, sp: Sport, rng: Random, now: Long, withNib: Boolean): List<String> {
        val story = mutableListOf<String>()
        s.count("sport:${sp.name}")
        val me = skill(s, sp.name); val nib = nibSkill(s, sp)
        var pipoWon = true
        var score = ""
        if (!withNib) {
            // Nib stayed home: Kip at the field makes up the numbers
            story += "Nib stayed home, so he played with Kip. It wasn't the same. Kip doesn't beep."
        }
        val opp = if (withNib) "Nib" else "Kip"
        when (sp) {
            Sport.CRICKET -> {
                // he bats while Nib bowls (nudging the ball with its nose), then they swap
                val mine = (me * (0.6f + rng.nextFloat() * 1.4f)).toInt() + rng.nextInt(6)
                val theirs = (nib * (0.5f + rng.nextFloat() * 1.5f)).toInt() + rng.nextInt(6)
                pipoWon = mine >= theirs; score = "$mine to $theirs"
                story += "Cricket with $opp. He batted first: $mine runs" + (if (mine > 14 && rng.nextBoolean()) ", including a six into the bushes." else ".")
                story += if (withNib) "Then Nib batted (it holds the bat with its whole body) and got $theirs." else "Then Kip batted and got $theirs."
                story += if (pipoWon) "He won by ${mine - theirs} run${if (mine - theirs == 1) "" else "s"}." + (if (withNib) " Nib demanded a rematch. In beeps." else "")
                    else "$opp won by ${theirs - mine}. " + (if (withNib) "Nib did a lap of honour. Pipo has questions about the umpiring." else "Kip was nice about it. Too nice.")
                record(s, "cricket", mine, now, "my best is $mine runs at cricket", "Top score: $mine runs", "He took his bat home like a trophy.")
            }
            Sport.TABLE_TENNIS -> {
                val (a, b) = rally(rng, me, nib, 11)
                pipoWon = a > b; score = "$a-$b"
                story += "Table tennis with $opp at the park table. " + (if (withNib) "Nib stood on the other end and headbutted every ball back. Every single one." else "")
                story += if (pipoWon) "He won $a-$b. Close. Very close. He'd like that written down." else "$opp won $b-$a. " + (if (withNib) "Nib is unbeatable at the net. Nib IS the net, basically." else "")
            }
            Sport.BADMINTON -> {
                val (a, b) = rally(rng, me, nib, 11)
                pipoWon = a > b; score = "$a-$b"
                story += "Badminton in the garden with $opp. " + (if (withNib) "Nib thinks the shuttle is a bird. Nib jumps for it every time." else "")
                story += if (pipoWon) "He won $a-$b. The wind was on his side." else "$opp won $b-$a. \"The wind cheated.\""
            }
            Sport.FOOTBALL -> {
                // shots at Nib in goal, then Nib's turn
                val mine = (me / 3f * (0.3f + rng.nextFloat())).toInt().coerceIn(0, 6)
                val theirs = (nib / 3f * (0.3f + rng.nextFloat())).toInt().coerceIn(0, 6)
                pipoWon = mine >= theirs; score = "$mine-$theirs"
                story += "Football with $opp: " + (if (withNib) "Nib in goal, then Pipo in goal. " else "") + "Final score $mine-$theirs."
                story += if (pipoWon) (if (withNib) "Nib saved one with its face. On purpose, it says." else "He won. Kip says he's improving.") else "$opp won. " + (if (withNib) "Nib dribbles like a little round tornado." else "")
                story += FootballLife.fieldSession(s, rng, now).story.take(1)
            }
        }
        // the head-to-head is remembered by both of them
        val key = "vs:${sp.name}"
        s.records["$key:pipo"] = (s.records["$key:pipo"] ?: 0) + (if (pipoWon) 1 else 0)
        s.records["$key:opp"] = (s.records["$key:opp"] ?: 0) + (if (pipoWon) 0 else 1)
        if (withNib) {
            PetEngine.together(s, sp.name.lowercase(), if (pipoWon) "I beat Nib at ${sp.title}, $score. Nib wants a rematch" else "Nib beat me at ${sp.title}, $score. I'm getting better. Nib isn't slowing down",
                now, 0.45f, place = placeOf(sp))
            s.pet.boredom = (s.pet.boredom - 0.4f).coerceAtLeast(0f)
            s.pet.bond = (s.pet.bond + 0.02f).coerceAtMost(1f)
        }
        return story
    }

    private fun record(s: PipoState, key: String, n: Int, now: Long, memory: String, title: String, body: String) {
        val best = s.records[key] ?: 0
        if (n <= best) return
        s.records[key] = n
        Chronicle.remember(s, MemoryType.SELF, memory, 0.5f, now, "record:$key")
        if (best == 0 || n >= best + 3) Chronicle.journal(s, "New record! $title", body, JournalCategory.GAME, now)
    }

    /** Indoor practice (the ball/bat/paddle at the toy corner). Returns his line. */
    fun practice(s: PipoState, sp: Sport, rng: Random, now: Long): String {
        s.count("sport:${sp.name}")
        return when (sp) {
            Sport.CRICKET -> {
                val n = 3 + rng.nextInt(12)
                record(s, "wall_cricket", n, now, "I hit the ball off the wall $n times in a row", "Wall cricket: $n", "Inside. With a real bat. Nothing broke. This time.")
                listOf("Wall cricket! $n in a row. The wall is a tough bowler.", "$n shots. Still not out. The wall's tired.", "Out! The ball hit the lamp. The lamp is the umpire.").random(rng)
            }
            Sport.TABLE_TENNIS -> {
                val n = 5 + rng.nextInt(25)
                record(s, "paddle_bounce", n, now, "I bounced the ball on my paddle $n times", "Paddle bounces: $n", "Up, down, up, down. Nib watched every single one.")
                listOf("$n bounces on the paddle! Don't talk to me, I'm concentrating.", "$n! The ball is my friend now.").random(rng)
            }
            Sport.BADMINTON -> "Indoors? The shuttle went on the shelf. It lives there now."
            Sport.FOOTBALL -> ""
        }
    }

    /** What's in the toy corner to pick from. */
    fun indoorPick(s: PipoState, rng: Random): Sport = listOf(Sport.FOOTBALL, Sport.FOOTBALL, Sport.CRICKET, Sport.TABLE_TENNIS).random(rng)
}
