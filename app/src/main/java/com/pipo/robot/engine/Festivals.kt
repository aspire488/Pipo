package com.pipo.robot.engine

import java.util.Calendar

/** The days the whole world (his and ours) dresses up for. */
enum class Festival(val title: String) {
    NEW_YEAR("New Year"), PONGAL("Pongal"), HOLI("Holi"), EID("Eid"), VISHU("Vishu"),
    ONAM("Onam"), HALLOWEEN("Halloween"), DIWALI("Diwali"), CHRISTMAS("Christmas"),
}

/**
 * Which festival it is (or is about to be), from the real calendar. Fixed-date ones are exact;
 * moon-calendar ones come from a table with a window of a couple of days either side, so a day's
 * difference in when they're observed doesn't matter.
 */
object Festivals {
    private data class Window(val f: Festival, val year: Int, val month: Int, val day: Int, val before: Int, val after: Int)

    // month is 1-based here
    private val moving = listOf(
        // Holi
        Window(Festival.HOLI, 2026, 3, 4, 1, 1), Window(Festival.HOLI, 2027, 3, 22, 1, 1), Window(Festival.HOLI, 2028, 3, 11, 1, 1),
        // Eid al-Fitr (moon sighting can shift it a day)
        Window(Festival.EID, 2026, 3, 20, 1, 2), Window(Festival.EID, 2027, 3, 10, 1, 2), Window(Festival.EID, 2028, 2, 27, 1, 2),
        // Onam (Thiruvonam), with the days of Onam before it
        Window(Festival.ONAM, 2026, 8, 26, 4, 1), Window(Festival.ONAM, 2027, 9, 12, 4, 1), Window(Festival.ONAM, 2028, 8, 31, 4, 1),
        // Diwali (Lakshmi Puja), with the evenings of lamps around it
        Window(Festival.DIWALI, 2026, 11, 8, 2, 2), Window(Festival.DIWALI, 2027, 10, 29, 2, 2), Window(Festival.DIWALI, 2028, 10, 17, 2, 2),
    )

    fun today(now: Long): Festival? {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
        // fixed dates first
        when {
            (m == 12 && d == 31) || (m == 1 && d == 1) -> return Festival.NEW_YEAR
            m == 12 && d in 18..26 -> return Festival.CHRISTMAS
            m == 10 && d in 24..31 -> return Festival.HALLOWEEN
            m == 1 && d in 13..15 -> return Festival.PONGAL
            m == 4 && d in 13..15 -> return Festival.VISHU
        }
        val day = WeatherEngine.localDay(now)
        for (w in moving) {
            if (w.year != y && w.year != y + 1 && w.year != y - 1) continue
            val target = WeatherEngine.localDay(Calendar.getInstance().apply { clear(); set(w.year, w.month - 1, w.day, 12, 0) }.timeInMillis)
            if (day in (target - w.before)..(target + w.after)) return w.f
        }
        return null
    }

    /** Is it the actual day (not just the run-up)? */
    fun isTheDay(f: Festival, now: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
        return when (f) {
            Festival.CHRISTMAS -> m == 12 && d == 25
            Festival.HALLOWEEN -> m == 10 && d == 31
            Festival.NEW_YEAR -> true
            else -> true
        }
    }

    /** What he says about it when you open the app (once a day). */
    fun greeting(f: Festival, now: Long, withNib: Boolean, rng: kotlin.random.Random): String {
        val day = isTheDay(f, now)
        val nib = if (!withNib) "" else when (f) {
            Festival.CHRISTMAS -> " Nib is wearing antlers. Nib chose that."
            Festival.HALLOWEEN -> " Nib is a pumpkin this year. A pumpkin with one eye."
            Festival.DIWALI -> " Nib keeps staring at the lamps. Not too close, Nib."
            Festival.HOLI -> " Nib is green. Nib started it."
            Festival.NEW_YEAR -> " Nib already fell asleep. Twice."
            else -> " Nib is helping. Nib is not helping."
        }
        return when (f) {
            Festival.CHRISTMAS -> if (day) "MERRY CHRISTMAS! I made the tree myself. It leans a bit. That's the style." else "Christmas is coming! I put up a tree. And lights. So many lights."
            Festival.HALLOWEEN -> if (day) "Happy Halloween! I'm a witch. A robot witch. Very scary. Boo." else "Halloween's coming. I carved a pumpkin. It looks like me, a bit. Spooky."
            Festival.NEW_YEAR -> "Happy New Year! I'm going to stay up until midnight."
            Festival.DIWALI -> "Happy Diwali! I lit all the little lamps. Carefully. There's rangoli by the door."
            Festival.ONAM -> "Happy Onam! I made a pookalam on the floor. With real flowers. Well. Drawn flowers."
            Festival.HOLI -> "Happy Holi! Everything is pink now. Me too."
            Festival.EID -> "Eid Mubarak! I hung up lanterns. And I'm saving the sweets for you. Mostly."
            Festival.VISHU -> "Happy Vishu! I set up a little Vishukkani. The first thing you see should be lucky. Hi. That's me."
            Festival.PONGAL -> "Happy Pongal! The pot is boiling over. That's GOOD this time. I checked."
        } + nib
    }

    /** A line he might say while just living through the day. */
    fun idleLine(f: Festival, rng: kotlin.random.Random): String = when (f) {
        Festival.CHRISTMAS -> listOf("Do robots get presents? Asking for a robot.", "I wrapped a screw for Nib. Nib unwrapped it in a second.", "Jingle bells. That's all the words I know.")
        Festival.HALLOWEEN -> listOf("If the pumpkin moves, it wasn't me.", "Nib is dressed as a pumpkin. A pumpkin with an eye.", "Boo. Did that work?")
        Festival.NEW_YEAR -> listOf("My resolution is to be even more me.", "New year, same screws.")
        Festival.DIWALI -> listOf("The little lamps make the room feel warm. Like a hug made of light.", "I'm not touching the sparklers. I'm watching them. From far.", "Sweets! Ladoo. One more ladoo. Okay, last one.")
        Festival.ONAM -> listOf("I want a sadya. A tiny robot sadya. On a tiny leaf.", "The pookalam is my best drawing. It's made of flowers.")
        Festival.HOLI -> listOf("I'm still pink. I think it's permanent. I like it.", "Nib got me again. Nib has good aim.")
        Festival.EID -> listOf("I saved you some sweets. I ate the rest. Sorry.", "The lanterns look nice at night.")
        Festival.VISHU -> listOf("Vishu means new beginnings. I'm beginning a snack.")
        Festival.PONGAL -> listOf("Sweet pongal is the best pongal.")
    }.let { it[rng.nextInt(it.size)] }

    /** On the other side they celebrate it too. Wrongly. */
    fun mirrorLine(f: Festival): String = when (f) {
        Festival.CHRISTMAS -> "It's Christmas over there too, but the presents go back UP the chimney."
        Festival.HALLOWEEN -> "Their Halloween is the opposite. The pumpkins dress up as people."
        Festival.DIWALI -> "Their Diwali lamps glow blue. They say ours look 'strange'."
        Festival.ONAM -> "Their pookalam is on the ceiling. Nobody knows how."
        Festival.HOLI -> "Their Holi colours go on and come off as the wrong colours."
        Festival.NEW_YEAR -> "Over there they count UP to midnight. It takes forever."
        Festival.EID -> "Their lanterns float a little. Just a little."
        Festival.VISHU -> "Their Vishukkani is the last thing you see before sleep."
        Festival.PONGAL -> "Their pot boils over upwards."
    }
}

/** The season where you are (from latitude when we know it, else from your timezone). */
enum class Season { SPRING, SUMMER, MONSOON, AUTUMN, WINTER }

object Seasons {
    fun at(now: Long, lat: Double?): Season {
        val m = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.MONTH) + 1
        val la = lat ?: if (java.util.TimeZone.getDefault().id.startsWith("Asia/Kolkata") || java.util.TimeZone.getDefault().id.startsWith("Asia/Calcutta")) 15.0 else 45.0
        return when {
            la in -23.5..23.5 && la >= 5.0 -> when (m) { in 3..5 -> Season.SUMMER; in 6..9 -> Season.MONSOON; 10, 11 -> Season.AUTUMN; else -> Season.WINTER } // the subcontinent's year
            la in -23.5..23.5 -> if (m in 5..10) Season.MONSOON else Season.SUMMER
            la > 0 -> when (m) { in 3..5 -> Season.SPRING; in 6..8 -> Season.SUMMER; in 9..11 -> Season.AUTUMN; else -> Season.WINTER }
            else -> when (m) { in 3..5 -> Season.AUTUMN; in 6..8 -> Season.WINTER; in 9..11 -> Season.SPRING; else -> Season.SUMMER }
        }
    }

    fun describe(s: Season) = when (s) { Season.SPRING -> "spring"; Season.SUMMER -> "summer"; Season.MONSOON -> "monsoon season"; Season.AUTUMN -> "autumn"; Season.WINTER -> "winter" }
}
