package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.JournalEntry
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PipoMemory
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Places
import com.pipo.robot.data.ProjectState

/**
 * What Pipo actually knows, in a few short true sentences: his recent days, his things, his
 * projects, Nib, and whatever in his memory matches what you just asked. Both his own words and
 * the optional AI voice read from this, so "what did we do yesterday?" gets the real answer
 * instead of "I don't remember". Nothing here is invented: every line comes from saved state.
 */
object Grounding {
    private val stop = setOf("remember", "when", "that", "what", "with", "your", "you", "the", "time", "about", "does", "did", "do", "we",
        "our", "there", "have", "this", "went", "was", "were", "where", "who", "why", "how", "is", "are", "it", "a", "an", "and", "or", "of",
        "to", "in", "on", "at", "me", "my", "i", "for", "can", "get", "got", "like", "yesterday", "today", "thing", "things", "that's", "whats",
        "pipo", "tell", "know", "any", "some", "something", "again", "last", "together", "us")

    /** "earlier today", "yesterday", "3 days ago" (by calendar day, the way a kid counts). */
    fun ago(t: Long, now: Long): String {
        val d = WeatherEngine.localDay(now) - WeatherEngine.localDay(t)
        val h = hourOf(t)
        val part = when (h) { in 5..11 -> "morning"; in 12..17 -> "afternoon"; in 18..21 -> "evening"; else -> "night" }
        return when {
            d <= 0L && now - t < 90 * MINUTE -> "just now"
            d <= 0L -> "this $part"
            d == 1L -> "yesterday $part"
            else -> "$d days ago"
        }
    }

    fun words(text: String): List<String> = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length > 2 && it !in stop }
        .map { it.removeSuffix("s") }.distinct()

    private fun matches(text: String, words: List<String>) = words.count { w -> text.lowercase().contains(w) }

    /** Memories that match the question, best first; recency and importance break ties. */
    fun relevantMemories(s: PipoState, query: String, now: Long, n: Int = 4): List<PipoMemory> {
        val w = words(query)
        if (w.isEmpty()) return emptyList()
        return s.memories.filter { it.type != MemoryType.CONVERSATION }
            .map { it to matches(it.content + " " + it.place + " " + it.with.joinToString(" "), w) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second * 1f + Chronicle.relevance(it.first, now) * 0.5f }
            .map { it.first }.distinctBy { it.content }.take(n)
    }

    fun relevantJournal(s: PipoState, query: String, n: Int = 3): List<JournalEntry> {
        val w = words(query)
        if (w.isEmpty()) return emptyList()
        return s.journal.map { it to matches(it.title + " " + it.description, w) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<JournalEntry, Int>> { it.second }.thenByDescending { it.first.timestamp })
            .map { it.first }.distinctBy { it.title }.take(n)
    }

    /** The last couple of days, newest first, one line each, duplicates folded. */
    fun recentDays(s: PipoState, now: Long, n: Int = 6, days: Int = 2): List<String> =
        s.journal.filter { WeatherEngine.localDay(now) - WeatherEngine.localDay(it.timestamp) <= days }
            .sortedByDescending { it.timestamp }.distinctBy { it.title }.take(n)
            .map { "${ago(it.timestamp, now)}: ${it.title.removePrefix("Pipo ").trimEnd('.')}. ${it.description.substringBefore(". ").trimEnd('.')}" }

    /** What he did on a given day (0 = today, 1 = yesterday), as plain phrases. */
    fun dayStory(s: PipoState, now: Long, daysAgo: Int): List<String> =
        s.journal.filter { WeatherEngine.localDay(now) - WeatherEngine.localDay(it.timestamp) == daysAgo.toLong() }
            .sortedBy { it.timestamp }.distinctBy { it.title }.map { it.title.removePrefix("Pipo ").trimEnd('.') }

    fun possessions(s: PipoState): List<String> =
        s.world.items.filter { it.usedInProjectId == 0L && it.id != s.pet.stolenItemId }
            .groupBy { it.catalogId }.mapNotNull { (id, l) -> Catalog.item(id)?.name?.lowercase()?.let { if (l.size > 1) "${l.size} × $it" else it } }

    fun pantry(s: PipoState): List<String> = s.pantry.map { Economy.nameOf(it) }.distinct()

    fun lastBuy(s: PipoState): String? {
        val r = s.lastTrip ?: return null
        if (r.bought.isEmpty()) return null
        val where = Places.byId(r.placeId)?.name ?: "the shop"
        return "${r.bought.joinToString(", ") { Economy.nameOf(it) }} from $where"
    }

    /** Compact true facts for the voice. [query] pulls in the memories it's actually about. */
    fun facts(s: PipoState, query: String, now: Long): List<String> = buildList {
        add("it's ${ago(now, now).let { if (it == "just now") "now" else it }}, the time is ${hourOf(now)}:00")
        Festivals.today(now)?.let { add("it's ${it.title} time! The room is decorated for it and you're dressed up") }
        add("it's ${Seasons.describe(Seasons.at(now, WeatherEngine.liveLat))}")
        val inv = possessions(s)
        if (inv.isNotEmpty()) add("things on your shelf and around your room: ${inv.take(10).joinToString(", ")}")
        val food = pantry(s)
        add(if (food.isEmpty()) "your kitchen has no food in it" else "in your kitchen: ${food.joinToString(", ")}")
        FoodLife.favorite(s)?.let { add("your favourite food is ${it.name}") }
        Likes.favorite(s, "place:")?.let { add("your favourite place is ${Likes.describe("place:$it")}") }
        Likes.favorite(s, "act:")?.let { add("you love ${Likes.describe("act:$it")}") }
        lastBuy(s)?.let { add("last time you shopped you got $it") }
        Inventor.rank(s).let { r -> add("you're a level ${r.level} ${r.title.lowercase()} (an inventor in training)" + (Inventor.inventions(s).takeIf { it.isNotEmpty() }?.let { "; you've built: ${it.take(6).joinToString(", ")}" } ?: "")) }
        s.activeProject()?.let { p -> add("you're building a ${p.title.lowercase()} (${(p.progress * 100).toInt()}% done, unfinished)") }
        s.projects.lastOrNull { it.state == ProjectState.DONE || it.state == ProjectState.EVOLVED || it.state == ProjectState.FAILED }?.let { p ->
            add("your last finished project: the ${p.title.lowercase()}, it ${if (p.state == ProjectState.FAILED) "failed" else "worked"}")
        }
        if (s.pet.adopted) {
            add("Nib is your pet and best mate, a small round beeping robot who sometimes steals things. ${NibLife.describe(s)}")
            if (s.pet.stolenItemId != 0L) s.world.items.firstOrNull { it.id == s.pet.stolenItemId }?.let { add("Nib has stolen your ${Catalog.item(it.catalogId)?.name?.lowercase()} and hidden it") }
            s.memories.filter { it.type == MemoryType.PET }.sortedByDescending { it.timestamp }.distinctBy { it.content }.take(2).forEach { add("with Nib: ${it.content} (${ago(it.timestamp, now)})") }
        }
        s.places.entries.sortedByDescending { it.value.visits }.take(4).mapNotNull { (id, m) -> Places.byId(id)?.let { "${it.name} (${m.visits}x)" } }
            .takeIf { it.isNotEmpty() }?.let { add("places you've been: ${it.joinToString(", ")}") }
        val rec = listOfNotNull(s.records["kickups"]?.let { "$it kick-ups" }, s.records["pipo:flappy"]?.let { "$it at Flappy Pipo" })
        if (rec.isNotEmpty()) add("your records: ${rec.joinToString(", ")}")
        recentDays(s, now).forEach { add(it) }
        relevantJournal(s, query).forEach { add("${ago(it.timestamp, now)}: ${it.title}. ${it.description}".take(160)) }
        if (s.mystery.stage > 0 && Inventory.owns(s, "brass_token")) add("you have a strange brass token with a mark from your dream on it")
    }.distinct()

    /** Memories to hand the voice: the ones about the question first, then the most important recent ones. */
    fun memoriesFor(s: PipoState, query: String, now: Long): List<String> =
        (relevantMemories(s, query, now).map { "${it.content} (${ago(it.timestamp, now)})" } +
            s.memories.filter { it.type != MemoryType.CONVERSATION }.sortedByDescending { Chronicle.relevance(it, now) }.take(4).map { "${it.content} (${ago(it.timestamp, now)})" })
            .distinct().take(7)
}
