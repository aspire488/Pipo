package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.EventType
import com.pipo.robot.data.Foods
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.NpcMemory
import com.pipo.robot.data.Npcs
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.data.Place
import com.pipo.robot.data.PlaceKind
import com.pipo.robot.data.PlaceMemory
import com.pipo.robot.data.Places
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.TripPurpose
import com.pipo.robot.data.TripReport
import com.pipo.robot.data.TripState
import kotlin.math.min
import kotlin.random.Random

/** A reason to go somewhere, before he's decided to. */
data class TripIdea(val placeId: String, val purpose: TripPurpose, val list: List<String>, val reason: String, val score: Float)

/**
 * Going out. Every trip has a cause (a project needs a motor, he wants noodles, it's a perfect
 * day for football, the drawing he made keeps bothering him) and consequences he brings home:
 * things in a bag, a photo, a name for a sparrow, a story. Deterministic given the RNG.
 */
object Trips {
    const val DAILY_MAX = 4

    /** Bought materials that would give the active project a tag it still needs. */
    fun materialsNeeded(s: PipoState): List<String> {
        val p = s.activeProject() ?: return emptyList()
        if (p.state != ProjectState.GATHERING) return emptyList()
        val free = Inventory.freeTags(s)
        val missing = p.components.filter { it !in p.collected && it !in free }
        return missing.mapNotNull { tag ->
            Catalog.items.filter { it.shopOnly && tag in it.tags && Places.sellersOf(it.id).isNotEmpty() }.minByOrNull { it.price }?.id
        }.distinct()
    }

    /** Food he'd go and get: a craving he can't satisfy, or an empty kitchen. */
    fun foodNeeded(s: PipoState): List<String> {
        val out = mutableListOf<String>()
        if (s.craving.isNotEmpty()) out += FoodLife.missingFor(s, s.craving)
        // finish the recipe that's closest (noodles at home: buy an egg, and it's noodle soup)
        if (FoodLife.feasible(s).isEmpty()) com.pipo.robot.data.Foods.recipes
            .map { r -> r.needs.filter { it !in s.pantry } }.filter { it.isNotEmpty() }.minByOrNull { it.size }?.let { out += it }
        if (FoodLife.edible(s).size + FoodLife.feasible(s).size <= 1) out += listOf("noodles", "egg", "apple", "bread").filter { it !in s.pantry }.take(2)
        return out.distinct().take(3)
    }

    /** Found things he has more than one of, that aren't in a project: Old Rook buys those. */
    fun spares(s: PipoState): List<com.pipo.robot.data.OwnedItem> =
        s.world.items.filter { it.usedInProjectId == 0L && it.id != s.pet.stolenItemId && Catalog.item(it.catalogId)?.let { d -> !d.unique && !d.shopOnly } == true }
            .groupBy { it.catalogId }.values.filter { it.size >= 2 }.map { it.first() }

    private fun tripsToday(s: PipoState, now: Long): Int =
        if (s.cooldowns["trips:day"] == WeatherEngine.localDay(now)) (s.cooldowns["trips:n"] ?: 0L).toInt() else 0

    fun allowedNow(s: PipoState, env: Env, now: Long): Boolean =
        s.trip == null && env.weather.kind != Weather.STORM && env.hour in 6..21 && s.mood.energy > 0.3f &&
            tripsToday(s, now) < DAILY_MAX && now >= (s.cooldowns["act:GO_OUT"] ?: 0L)

    /**
     * Why he can't go out right now even though you asked (null = he can). His own cooldowns and
     * daily limit don't apply to you asking: only real reasons do.
     */
    fun whyNot(s: PipoState, env: Env, now: Long): String? = when {
        s.trip != null -> "I'm already out."
        env.weather.kind == Weather.STORM -> "In THIS? The sky is shouting. Let's stay in. I'll make it cozy."
        env.hour !in 6..21 -> "It's night. Everything's closed. Even the frogs are asleep. Tomorrow, first thing?"
        s.mood.energy < 0.12f -> "My battery is basically a raisin. Let me charge and then we go. Promise."
        else -> null
    }

    private val wishes = listOf(
        "football" to "field", "field" to "field", "cricket" to "cricket", "sports hall" to "sports_hall", "table tennis" to "sports_hall", "court" to "court", "badminton" to "court", "park" to "park", "walk" to "park", "garden" to "garden", "bugs" to "garden",
        "lake" to "lake", "frogs" to "lake", "hills" to "hills", "library" to "library", "book" to "library",
        "market" to "market", "groceries" to "market", "food" to "market", "bakery" to "bakery", "bread" to "bakery", "cafe" to "cafe",
        "café" to "cafe", "hardware" to "hardware", "electronics" to "electronics", "rook" to "secondhand", "fennel" to "repair",
    )

    /** Where to go when you ask him to: the place you named, else the best reason he has (shops if you said shopping). */
    fun forUser(s: PipoState, env: Env, now: Long, text: String): TripIdea? {
        if (whyNot(s, env, now) != null) return null
        val t = text.lowercase()
        Sports.fromText(t)?.let { sp ->
            val place = Sports.placeOf(sp)
            return TripIdea(place, Sports.purposeOf(sp), emptyList(), "${sp.title.replaceFirstChar { it.uppercase() }} with Nib! Nib's going to lose. Probably.", 1f)
        }
        val ideas = ideas(s, env, now, asked = true)
        val named = wishes.firstOrNull { (w, _) -> Regex("\\b${Regex.escape(w)}").containsMatchIn(t) }?.second
        if (named != null) {
            ideas.firstOrNull { it.placeId == named }?.let { return it }
            // a shop that's closed is closed: for food, another one that's open; otherwise no trip (never a random one)
            val place = Places.byId(named)?.takeIf { it.openAt(env.hour) }
                ?: if (named in setOf("market", "bakery", "cafe")) return openFoodShop(s, env) else return null
            val purpose = when {
                named == "field" -> TripPurpose.FOOTBALL
                named == "library" -> TripPurpose.LIBRARY
                place.kind == PlaceKind.SHOP && (named == "market" || named == "bakery" || named == "cafe") -> TripPurpose.FOOD
                place.kind == PlaceKind.SHOP -> TripPurpose.SHOP
                else -> TripPurpose.WALK
            }
            val list = if (purpose == TripPurpose.FOOD) foodNeeded(s).ifEmpty { listOf("apple") } else emptyList()
            return TripIdea(named, purpose, list, "${place.name}? Good idea.", 1f)
        }
        if (Regex("\\b(shop|shopping|buy|store|groceries)").containsMatchIn(t))
            return ideas.firstOrNull { it.purpose == TripPurpose.FOOD || it.purpose == TripPurpose.SHOP } ?: openFoodShop(s, env)
        return ideas.firstOrNull() ?: Places.byId("garden")?.let { TripIdea("garden", TripPurpose.WALK, emptyList(), "Just a little walk. Fresh air.", 0.5f) }
    }

    /** A food shop that's open right now (null if they're all shut). */
    fun openFoodShop(s: PipoState, env: Env): TripIdea? {
        val want = foodNeeded(s).ifEmpty { listOf("apple") }
        val shop = listOf("market", "bakery", "cafe").mapNotNull { Places.byId(it) }.filter { it.openAt(env.hour) }
            .maxByOrNull { p -> want.count { it in p.sells } } ?: return null
        val list = want.filter { it in shop.sells }.ifEmpty { shop.sells.take(1) }
        return TripIdea(shop.id, TripPurpose.FOOD, list, "Shopping! ${shop.name} is still open.", 1f)
    }

    /** Why a named shop can't be visited now ("Pim's Market is closed. It opens at 7."), or null. */
    fun closedLine(text: String, env: Env): String? {
        val t = text.lowercase()
        val named = wishes.firstOrNull { (w, _) -> Regex("\\b${Regex.escape(w)}").containsMatchIn(t) }?.second
        val shops = if (named != null) listOfNotNull(Places.byId(named)) else if (Regex("\\b(shop|shopping|buy|store|groceries|food)").containsMatchIn(t)) listOf("market", "bakery", "cafe").mapNotNull { Places.byId(it) } else emptyList()
        if (shops.isEmpty() || shops.any { it.openAt(env.hour) }) return null
        val first = shops.minByOrNull { it.opens }!!
        return if (shops.size == 1) "${first.name} is closed now. It opens at ${first.opens}. I'll go first thing." else "The shops are all closed now. ${first.name} opens at ${first.opens}. First thing tomorrow!"
    }

    private fun hasUmbrella(s: PipoState) = Inventory.owns(s, "umbrella")

    private fun weatherFactor(s: PipoState, p: Place, env: Env): Float = when {
        env.weather.wet -> if (p.outdoor) (if (hasUmbrella(s) && p.id == "lake") 0.3f else 0f) else if (hasUmbrella(s)) 0.8f else 0.45f
        env.weather.kind == Weather.FOG -> if (p.outdoor) 0.6f else 1f
        env.weather.kind == Weather.WIND -> if (p.id == "hills") 1.3f else if (p.outdoor) 0.8f else 1f
        env.weather.niceOut && p.outdoor -> 1.25f
        else -> 1f
    }

    /** All the reasons he has right now to go out, best first. Pure. */
    fun ideas(s: PipoState, env: Env, now: Long, asked: Boolean = false): List<TripIdea> {
        if (if (asked) whyNot(s, env, now) != null else !allowedNow(s, env, now)) return emptyList()
        val t = s.profile.traits
        val m = s.mood
        val out = mutableListOf<TripIdea>()
        fun open(id: String) = Places.byId(id)?.takeIf { it.openAt(env.hour) && it.openAt(min(23, env.hour + 1)) }
        fun add(placeId: String, purpose: TripPurpose, list: List<String>, reason: String, score: Float) {
            val p = open(placeId) ?: return
            out += TripIdea(placeId, purpose, list, reason, score * weatherFactor(s, p, env) * (1f + Likes.bonus(s, "place:$placeId")))
        }

        // ---- a project is waiting on something only a shop has
        val mats = materialsNeeded(s)
        if (mats.isNotEmpty()) {
            val byStore = mats.groupBy { id -> Places.sellersOf(id).firstOrNull { it.openAt(env.hour) }?.id }.filterKeys { it != null }
            val best = byStore.maxByOrNull { it.value.size }
            if (best != null) {
                val list = best.value
                val cost = list.sumOf { Economy.priceOf(it) }
                val what = Economy.nameOf(list.first())
                val proj = s.activeProject()?.title?.lowercase() ?: "project"
                if (s.coins >= Economy.priceOf(list.first())) {
                    add(best.key!!, TripPurpose.SHOP, list, "The $proj needs ${article(what)} $what.", 1.25f + t.confidence * 0.2f + (if (cost <= s.coins) 0.2f else 0f))
                } else {
                    // can't afford it: earn it, or put it off (a real choice, made by personality)
                    add("repair", TripPurpose.ODD_JOB, emptyList(), "I need coins for ${article(what)} $what. Fennel pays in coins.", 0.8f + t.patience * 0.4f + t.stubbornness * 0.2f)
                }
            }
        }
        // ---- food
        val food = foodNeeded(s)
        if (food.isNotEmpty() && s.coins >= 2) {
            val stores = food.flatMap { f -> Places.sellersOf(f).map { it.id to f } }.groupBy({ it.first }, { it.second })
            val best = stores.filterKeys { open(it) != null }.maxByOrNull { it.value.size }
            if (best != null) {
                val list = best.value.distinct()
                val hungry = m.appetite > 0.55f && FoodLife.edible(s).isEmpty()
                val why = if (s.craving.isNotEmpty() && FoodLife.missingFor(s, s.craving).isNotEmpty()) s.cravingReason.ifEmpty { "I want ${Economy.nameOf(s.craving)}." }
                    else "The kitchen is empty. Like, echo empty."
                add(best.key, TripPurpose.FOOD, list, why, (if (hungry) 1.6f else 0.7f) + m.appetite * 0.6f)
            }
        }
        // ---- money: work for Fennel, or sell spares to Old Rook
        if (s.coins < 10) add("repair", TripPurpose.ODD_JOB, emptyList(), "My coin jar is looking sad. Fennel needs a helper.", 0.45f + t.patience * 0.3f + (10 - s.coins) * 0.04f)
        if (s.coins < 15 && spares(s).isNotEmpty()) add("secondhand", TripPurpose.SHOP, emptyList(), "I have too many of some things. Old Rook buys things.", 0.4f + (15 - s.coins) * 0.03f)
        // ---- just because (only in nice-ish weather, weighted by who he is)
        val restless = m.boredom * 0.4f + t.adventurousness * 0.35f
        add("garden", TripPurpose.WILDLIFE, emptyList(), "I'm going to look at bugs. Professionally.", 0.2f + t.curiosity * 0.25f + restless * 0.5f + (if (env.hour in 20..21) 0.25f else 0f))
        add("park", TripPurpose.WALK, emptyList(), "I need a walk. My legs said so.", 0.15f + restless + (s.feed["wildlife"] ?: 0f) * 0.2f)
        if (s.pet.adopted && env.hour in 7..18) {
            add("cricket", TripPurpose.CRICKET, emptyList(), "Cricket with Nib. Nib bowls. Nib doesn't know the rules. Neither do I. Perfect.", 0.08f + t.playfulness * 0.35f + s.pet.bond * 0.2f + m.energy * 0.15f)
            add("sports_hall", TripPurpose.TABLE_TENNIS, emptyList(), "Table tennis at the sports hall. Nib and I have a rivalry. Lin keeps score.", 0.06f + t.playfulness * 0.3f + s.pet.bond * 0.2f)
            if (env.weather.kind != Weather.WIND) add("court", TripPurpose.BADMINTON, emptyList(), "Badminton at the court. Nib versus me. Winner gets... winning.", 0.05f + t.playfulness * 0.3f + s.pet.bond * 0.15f)
        }
        if (env.hour in 8..19) add("field", TripPurpose.FOOTBALL, emptyList(), "Football. I've been practising in my head.", 0.1f + t.playfulness * 0.4f + (s.feed["football"] ?: 0f) * 0.6f + m.energy * 0.2f)
        add("library", TripPurpose.LIBRARY, emptyList(), "I need a book. About something. I'll know when I see it.", 0.1f + t.curiosity * 0.25f + (1f - t.adventurousness) * 0.15f + (if (env.weather.wet) 0.3f else 0f))
        if (env.hour in 7..18 && m.energy > 0.5f) add("lake", TripPurpose.WILDLIFE, emptyList(), "The frogs at the lake have opinions. I want to hear them.", 0.05f + t.adventurousness * 0.3f + (s.feed["wildlife"] ?: 0f) * 0.4f)
        if (env.hour in 8..17 && m.energy > 0.55f) add("hills", TripPurpose.EXPLORE, emptyList(), "The hills. You can see everything from up there. Everything is small.", 0.03f + t.adventurousness * 0.3f)
        // ---- the thing he can't stop thinking about
        Mystery.tripIdea(s, env, now)?.let { add(it.placeId, it.purpose, it.list, it.reason, it.score) }

        // Mood colours it: sleepy Pipos stay in, curious ones wander, grumpy ones go shopping alone.
        val moodMult = when (m.current) {
            Mood.SLEEPY -> 0.3f; Mood.EXCITED, Mood.CURIOUS, Mood.PLAYFUL -> 1.3f; Mood.BORED -> 1.4f; Mood.NERVOUS, Mood.WORRIED -> 0.6f
            else -> 1f
        }
        return out.map { it.copy(score = it.score * moodMult) }.filter { it.score > 0.05f }.sortedByDescending { it.score }
    }

    fun best(s: PipoState, env: Env, now: Long): TripIdea? = ideas(s, env, now).firstOrNull()

    /** Leaves. [inApp] trips are quick (you're watching); offline ones take real time. */
    fun begin(s: PipoState, idea: TripIdea, now: Long, rng: Random, inApp: Boolean): TripState {
        val place = Places.byId(idea.placeId)!!
        val stay = when (idea.purpose) {
            TripPurpose.SHOP, TripPurpose.FOOD -> 8 + rng.nextInt(12)
            TripPurpose.ODD_JOB -> 40 + rng.nextInt(30)
            TripPurpose.FOOTBALL, TripPurpose.CRICKET, TripPurpose.TABLE_TENNIS, TripPurpose.BADMINTON -> 25 + rng.nextInt(30)
            TripPurpose.LIBRARY -> 20 + rng.nextInt(25)
            else -> 15 + rng.nextInt(30)
        }
        val durMs = if (inApp) (50_000L + (place.travelMin + stay) * 1_200L).coerceAtMost(170_000L)
            else (place.travelMin * 2 + stay) * MINUTE
        val sport = idea.purpose in setOf(TripPurpose.FOOTBALL, TripPurpose.CRICKET, TripPurpose.TABLE_TENNIS, TripPurpose.BADMINTON)
        val withPet = s.pet.adopted && idea.purpose !in setOf(TripPurpose.LIBRARY, TripPurpose.ODD_JOB) &&
            (sport || rng.nextFloat() < 0.35f + s.pet.bond * 0.35f + (if (place.outdoor) 0.2f else 0f))
        val trip = TripState(s.nextId(), place.id, idea.purpose, now, now + durMs, withPet, idea.list, idea.reason)
        s.trip = trip
        val day = WeatherEngine.localDay(now)
        if (s.cooldowns["trips:day"] != day) { s.cooldowns["trips:day"] = day; s.cooldowns["trips:n"] = 0L }
        s.cooldowns["trips:n"] = (s.cooldowns["trips:n"] ?: 0L) + 1L
        s.cooldowns["act:GO_OUT"] = now + durMs + if (inApp) 12 * MINUTE else 2 * HOUR
        if (withPet) s.pet.activity = com.pipo.robot.data.PetActivity.AWAY
        s.world.objectStates.remove("note")
        return trip
    }

    /**
     * He was on his way out but didn't make it through the door (you tapped him, the phone rang).
     * The trip never happened: nothing is bought or found, and he isn't "out" for anyone.
     */
    fun cancel(s: PipoState) {
        val t = s.trip ?: return
        s.trip = null
        if (t.withPet) s.pet.activity = com.pipo.robot.data.PetActivity.FOLLOW
        s.cooldowns["act:GO_OUT"] = (s.cooldowns["act:GO_OUT"] ?: 0L).coerceAtMost(t.startedAt + 15 * MINUTE)
    }

    /** He's asked to come home (you texted him). He hurries. No sulking. */
    fun hurryHome(s: PipoState, now: Long) {
        val t = s.trip ?: return
        // you called him home: he drops everything and comes straight back (a few seconds' walk)
        t.endsAt = min(t.endsAt, now + 4_000L)
    }

    /** He's back. What happened out there becomes things, memories and a story. */
    fun resolve(s: PipoState, env: Env, now: Long, rng: Random, offline: Boolean): TripReport {
        val trip = s.trip!!
        s.trip = null
        val place = Places.byId(trip.placeId)!!
        val t = s.profile.traits
        val story = mutableListOf<String>()
        val bought = mutableListOf<String>()
        val found = mutableListOf<Long>()
        var wrong = ""
        var spent = 0
        var earned = 0
        var creatureKey = ""
        var photoId = 0L
        var clue = ""

        // ---- the place remembers him, and he remembers the place
        val pm = s.places.getOrPut(place.id) { PlaceMemory() }
        val firstVisit = pm.visits == 0
        pm.visits++; pm.lastVisit = now
        if (firstVisit) pm.firstVisit = now

        // ---- someone there
        var npcLine = ""
        Npcs.byId(place.npc)?.let { npc ->
            val nm = s.npcs.getOrPut(npc.id) { NpcMemory() }
            val asked = nm.lastLine.startsWith("bought:")
            npcLine = when {
                nm.visits == 0 -> npc.hello
                asked && rng.nextFloat() < 0.6f -> "${npc.name} asked if the ${Economy.nameOf(nm.lastLine.removePrefix("bought:"))} worked out."
                nm.fondness > 0.55f && rng.nextFloat() < 0.5f -> npc.friendly.random(rng)
                else -> npc.lines.filter { it != nm.lastLine }.random(rng)
            }
            Talks.recap(s, trip, place)?.let { r -> if (rng.nextFloat() < 0.7f) npcLine = r }
            nm.visits++; nm.lastVisit = now; nm.lastLine = npcLine
            nm.fondness = (nm.fondness + 0.04f).coerceAtMost(1f)
        }

        when (trip.purpose) {
            TripPurpose.SHOP, TripPurpose.FOOD -> {
                val list = trip.shoppingList.toMutableList()
                // "I went for eggs and came back with chocolate": curiosity, impatience and Nib all help
                val wrongChance = (0.05f + (1f - t.patience) * 0.1f + t.curiosity * 0.05f + (if (trip.withPet) 0.06f else 0f)) * (if (trip.purpose == TripPurpose.FOOD) 0.5f else 1f) // dinner depends on it
                if (list.isNotEmpty() && rng.nextFloat() < wrongChance) {
                    val decoy = place.sells.filter { it !in list }.randomOrNull(rng)
                    if (decoy != null && Economy.priceOf(decoy) <= s.coins) {
                        val meant = list.removeAt(rng.nextInt(list.size))
                        wrong = decoy
                        story += "Went for ${article(Economy.nameOf(meant))} ${Economy.nameOf(meant)}. Came back with ${article(Economy.nameOf(decoy))} ${Economy.nameOf(decoy)}. Don't ask."
                        list.add(0, decoy)
                    }
                }
                for (id in list) {
                    val price = Economy.priceOf(id)
                    if (!Economy.spend(s, price)) { story += "Couldn't afford the ${Economy.nameOf(id)}. Saving up."; Chronicle.remember(s, MemoryType.PROJECT, "I'm saving up for ${article(Economy.nameOf(id))} ${Economy.nameOf(id)}", 0.4f, now, "saving:$id"); continue }
                    spent += price
                    bought += id
                    if (Foods.byId(id) != null) s.pantry.add(id) else Inventory.add(s, id, now).also { it.seenByUser = false }
                }
                if (bought.isNotEmpty() && wrong.isEmpty()) story.add(0, "Got ${listPhrase(bought.map { Economy.nameOf(it).let { n -> "${article(n)} $n" } })} at ${place.name.removePrefix("The ")}.")
                if (bought.any { Catalog.item(it) != null }) {
                    s.world.objectStates["box"] = "1" // he kept the box. obviously.
                    s.activeProject()?.let { Projects.gather(s, it) } // parts go straight onto the workbench
                }
                if (bought.isNotEmpty()) s.world.objectStates["bag"] = "1"
                if ("umbrella" in bought) s.cooldowns.remove("rain_caught")
            }
            TripPurpose.ODD_JOB -> {
                earned = 7 + rng.nextInt(6) + (if (t.patience > 0.6f) 1 else 0)
                Economy.earn(s, earned)
                Personality.nudge(s, Trait.CONFIDENCE, 0.006f)
                story += "Helped Fennel fix things. Earned $earned coins."
                if (rng.nextFloat() < 0.25f) {
                    val spare = listOf("radio_tube", "cracked_lens", "tiny_gear", "copper_coil").random(rng)
                    Inventory.add(s, spare, now).also { found += it.id }
                    story += "Fennel gave him ${article(Catalog.item(spare)!!.name)} ${Catalog.item(spare)!!.name.lowercase()}. \"Might be useful.\""
                }
                Chronicle.remember(s, MemoryType.PLACE, "I work at Fennel's sometimes", 0.4f, now, "job:fennel")
            }
            TripPurpose.LIBRARY -> {
                val topic = listOf("stars", "bridges", "birds", "cooking", "machines", "the sea").random(rng)
                s.world.objectStates["book"] = topic
                story += "Borrowed a book about $topic."
                if (topic == "cooking") FoodLife.maybeCrave(s, env, rng, "omelette" to "The book had an omelette in it. A beautiful one.")
                if (topic == "stars" || topic == "machines") Projects.maybeStart(s, rng, now, 0.5f, prefer = if (topic == "stars") "telescope" else "drone")
                    ?.let { story += "Now he wants to build ${article(it.title)} ${it.title.lowercase()}." }
            }
            TripPurpose.FOOTBALL -> story += Sports.session(s, Sport.FOOTBALL, rng, now, trip.withPet)
            TripPurpose.CRICKET -> story += Sports.session(s, Sport.CRICKET, rng, now, trip.withPet)
            TripPurpose.TABLE_TENNIS -> story += Sports.session(s, Sport.TABLE_TENNIS, rng, now, trip.withPet)
            TripPurpose.BADMINTON -> story += Sports.session(s, Sport.BADMINTON, rng, now, trip.withPet)
            else -> Unit
        }

        // ---- Old Rook buys spares (never the one-of-a-kind things, never what's in a project)
        if (place.id == "secondhand" && s.coins < 25) {
            for (item in spares(s).take(2)) {
                val d = Catalog.item(item.catalogId) ?: continue
                val price = 2 + rng.nextInt(3)
                s.world.items.remove(item)
                Economy.earn(s, price)
                earned += price
                story += "Sold a spare ${d.name.lowercase()} to Old Rook for $price coins."
            }
        }

        // ---- outside: animals, photos, things on the ground
        if (place.outdoor || place.kind == PlaceKind.HIDDEN) {
            val c = WildlifeLife.encounter(s, place.id, env, now, rng)
            if (c != null) {
                creatureKey = c.key
                story += WildlifeLife.storyLine(c)
            }
            if (rng.nextFloat() < 0.35f + t.curiosity * 0.25f + (if (c != null) 0.2f else 0f)) {
                val subject = if (c != null) PhotoSubject.CREATURE else if (trip.withPet && rng.nextBoolean()) PhotoSubject.PET else if (rng.nextFloat() < 0.25f) PhotoSubject.SELFIE else PhotoSubject.PLACE
                photoId = PhotoLife.take(s, subject, if (c != null) c.key else "", place.id, env, now, rng).id
            }
            if (rng.nextFloat() < 0.18f + t.curiosity * 0.12f + (if (trip.purpose == TripPurpose.EXPLORE) 0.2f else 0f)) {
                Discovery.roll(s, rng, now, placeTags = place.findTags)?.let { found += it.id; story += "Found ${article(Catalog.item(it.catalogId)!!.name)} ${Catalog.item(it.catalogId)!!.name.lowercase()} on the ground." }
            }
            if (env.weather.wet && !hasUmbrella(s)) {
                story += "Got rained on. Completely. He's damp."
                s.cooldowns["rain_caught"] = now
                Chronicle.remember(s, MemoryType.EVENT, "I got caught in the rain", 0.45f, now, "rain_caught")
                MoodEngine.bump(s, irritation = 0.15f)
            }
        }

        // ---- Nib
        if (trip.withPet) {
            PetEngine.tripEvent(s, place, rng, now)?.let { story += it }
            // sometimes Nib finds something before Pipo does
            PetEngine.nibFinds(s, place, rng, now)?.let { (line, id) -> story += line; found += id }
        }
        if (!s.pet.adopted) story += PetEngine.adopt(s, now, place)
        // the shopkeeper will remember what he bought (and ask about it next time)
        Npcs.byId(place.npc)?.let { npc -> bought.firstOrNull { Catalog.item(it) != null }?.let { b -> s.npcs[npc.id]?.lastLine = "bought:$b" } }

        // ---- the strange thread (rare; only when the story is ready for it)
        Mystery.onTrip(s, place, trip, now, rng, env)?.let { clue = it; story += it }

        // ---- cross-system consequences (Nib remembering a place, animals turning up somewhere new...)
        val partial = TripReport(trip.id, place.id, trip.purpose, now, bought, wrong, found, spent, earned, creatureKey, photoId, npcLine, story, trip.withPet, clue)
        story += Links.afterTrip(s, partial, now)
        photoId.takeIf { it != 0L }?.let { id -> s.photos.firstOrNull { it.id == id } }?.let { Links.afterPhoto(s, it, now) }?.let { story += it }
        found.mapNotNull { id -> s.world.items.firstOrNull { it.id == id } }.firstNotNullOfOrNull { Links.afterFind(s, it, now) }?.let { story += it }

        // ---- how it felt: places become favourites (or "don't go here") through what happens there
        val rained = story.any { it.contains("rained on") }
        val petLost = story.any { it.contains("lost") }
        val good = (if (creatureKey.isNotEmpty()) 0.08f else 0f) + (if (found.isNotEmpty()) 0.08f else 0f) + (if (bought.isNotEmpty() && wrong.isEmpty()) 0.04f else 0f) +
            (if (trip.purpose == TripPurpose.FOOTBALL) 0.06f else 0f) + 0.03f
        Likes.feel(s, "place:${place.id}", good - (if (rained) 0.15f else 0f) - (if (petLost) 0.05f else 0f))
        // bad experiences make him a little more careful; good ones a little braver
        if (rained || petLost) Personality.nudge(s, Trait.ADVENTUROUSNESS, -0.004f)

        // ---- he's home
        MoodEngine.bump(s, boredom = -0.35f, happiness = 0.08f, energy = -0.12f, loneliness = 0.05f)
        if (place.outdoor && !rained) Personality.nudge(s, Trait.ADVENTUROUSNESS, 0.004f)
        if (trip.withPet) { s.pet.activity = com.pipo.robot.data.PetActivity.FOLLOW; s.pet.bond = (s.pet.bond + 0.02f).coerceAtMost(1f) }
        Experience.remember(s, MemoryType.PLACE, "I went to ${place.name.lowercase().removePrefix("the ")}", 0.3f + if (firstVisit) 0.25f else 0f, now, "place:${place.id}",
            place = place.id, with = listOfNotNull(if (trip.withPet) "Nib" else null, Npcs.byId(place.npc)?.name), objects = bought + listOfNotNull(creatureKey.ifEmpty { null }),
            feeling = if (rained) -0.4f else 0.3f)
        if (firstVisit) {
            pm.note = firstNote(place, story, rng)
            Chronicle.journal(s, "Pipo went to ${placePhrase(place)}", (listOf(place.blurb) + story.take(2)).joinToString(" "), JournalCategory.PLACE, now)
        } else if (story.isNotEmpty() && (wrong.isNotEmpty() || found.isNotEmpty() || clue.isNotEmpty() || creatureKey.isNotEmpty() && rng.nextFloat() < 0.4f)) {
            Chronicle.journal(s, "A trip to ${placePhrase(place)}", story.take(3).joinToString(" "), JournalCategory.PLACE, now)
        }
        updateNote(pm, place, wrong, clue, trip.withPet && story.any { it.contains("lost") })
        val importance = when {
            clue.isNotEmpty() -> 0.0f // the clue raises its own STRANGE event
            wrong.isNotEmpty() -> 0.5f
            found.isNotEmpty() || firstVisit -> 0.46f
            creatureKey.isNotEmpty() && (s.creatures[creatureKey]?.sightings == 2) -> 0.45f
            else -> 0.3f
        }
        if (importance > 0f) Chronicle.event(s, EventType.TRIP, importance + t.sociability * 0.05f, now, trip.id.toString())
        s.count("trips")
        val report = TripReport(trip.id, place.id, trip.purpose, now, bought, wrong, found, spent, earned, creatureKey, photoId, npcLine, story, trip.withPet, clue)
        s.lastTrip = report
        return report
    }

    private fun firstNote(p: Place, story: List<String>, rng: Random): String = when {
        p.id == "bakery" || p.id == "cafe" -> "good food"
        p.id == "hardware" -> "Grumble. Screws."
        p.id == "lake" -> "frogs!!"
        p.id == "hills" -> "windy. big view"
        p.id == "field" -> "football!"
        p.id == "library" -> "whisper here"
        p.id == "secondhand" -> "weird stuff. good weird"
        story.any { it.contains("rained") } -> "wet. bring umbrella"
        else -> listOf("nice place", "ok", "been here").random(rng)
    }

    private fun updateNote(pm: PlaceMemory, p: Place, wrong: String, clue: String, petLost: Boolean) {
        when {
            clue.isNotEmpty() -> pm.note = "???"
            petLost -> pm.note = "Nib got lost here"
            wrong.isNotEmpty() && pm.visits >= 2 -> pm.note = "don't get distracted"
        }
        if (p.id == "observatory") pm.note = "the building from my dream"
    }

    /** "the park", "Mo's Bakery" — for use mid-sentence. */
    fun placePhrase(p: Place): String = if (p.name.startsWith("The ")) "the " + p.name.removePrefix("The ").let { it.replaceFirstChar { c -> c.lowercase() } } else p.name

    fun listPhrase(xs: List<String>): String = when (xs.size) {
        0 -> "nothing"; 1 -> xs[0]; 2 -> "${xs[0]} and ${xs[1]}"
        else -> xs.dropLast(1).joinToString(", ") + " and " + xs.last()
    }

    /** Finishes "While you were gone I …" for a trip. Keeps the strange part strange. */
    fun digest(r: TripReport, s: PipoState): String {
        val place = Places.byId(r.placeId)?.let { placePhrase(it) } ?: "somewhere"
        val creature = s.creatures[r.creatureKey]
        return when {
            r.clue.isNotEmpty() -> "went to $place. Something there doesn't add up"
            r.wrongBuy.isNotEmpty() -> "went to $place for one thing and came back with ${article(Economy.nameOf(r.wrongBuy))} ${Economy.nameOf(r.wrongBuy)}"
            r.bought.isNotEmpty() -> "went to $place and got ${listPhrase(r.bought.take(3).map { Economy.nameOf(it) })}"
            r.purpose == TripPurpose.ODD_JOB -> "worked at Fennel's and earned ${r.earned} coins"
            r.earned > 0 -> "sold some spares to Old Rook for ${r.earned} coins"
            creature != null && creature.sightings == 2 -> "went to $place and named ${creature.look} ${creature.name}"
            creature != null -> "went to $place and saw ${WildlifeLife.label(creature)}"
            r.foundItemIds.isNotEmpty() -> "went to $place and found something"
            r.purpose == TripPurpose.FOOTBALL -> "played football with Nib at the field"
            r.purpose == TripPurpose.CRICKET -> "played cricket with Nib at the field"
            r.purpose == TripPurpose.TABLE_TENNIS -> "played table tennis with Nib at the park"
            r.purpose == TripPurpose.BADMINTON -> "played badminton with Nib in the garden"
            r.purpose == TripPurpose.LIBRARY -> "went to the library and got a book"
            else -> "went to $place"
        }
    }

    /** What he says as he leaves. */
    fun leavingLine(trip: TripState, rng: Random): String {
        val w = Places.byId(trip.placeId)?.let { placePhrase(it) } ?: "out"
        return when (trip.purpose) {
            TripPurpose.SHOP -> "${trip.reason} I'm going to $w. Back soon!"
            TripPurpose.FOOD -> "${trip.reason} I'm going to $w."
            TripPurpose.ODD_JOB -> trip.reason + " Back later!"
            else -> Dialogue.pick(listOf("I'm going to $w. Don't touch anything.", "Going to $w. Be right back. Probably.", trip.reason), rng)
        }
    }

    /** The note on the door while he's out. */
    fun doorNote(trip: TripState, mischievous: Boolean): String {
        if (mischievous && trip.purpose !in setOf(TripPurpose.SHOP, TripPurpose.FOOD)) return "Went somewhere. Not telling. Back soon.\n— P"
        val pet = if (trip.withPet) "\n(Nib came too)" else ""
        return "Went to ${Places.byId(trip.placeId)?.let { placePhrase(it) } ?: "out"}. Back soon.$pet\n— P"
    }
}
