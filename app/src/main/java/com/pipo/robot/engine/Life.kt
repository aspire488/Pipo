package com.pipo.robot.engine

import com.pipo.robot.data.Catalog
import com.pipo.robot.data.EventType
import com.pipo.robot.data.Food
import com.pipo.robot.data.FoodKind
import com.pipo.robot.data.Foods
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.Mood
import com.pipo.robot.data.OwnedItem
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Recipe
import kotlin.random.Random

/**
 * Food is a thing in his world, not a meter. He eats because he wanted noodles, because it's
 * lunchtime, because it's raining and rain means soup. He learns what he likes by eating it.
 */
object FoodLife {
    fun mealTime(hour: Int) = hour in 7..9 || hour in 12..14 || hour in 18..20

    fun edible(s: PipoState): List<Food> = s.pantry.mapNotNull { Foods.byId(it) }.filter { it.edible }

    fun feasible(s: PipoState): List<Recipe> = Foods.recipes.filter { r -> hasAll(s.pantry, r.needs) }

    private fun hasAll(pantry: List<String>, needs: List<String>): Boolean {
        val left = pantry.toMutableList()
        return needs.all { left.remove(it) }
    }

    /** Innate taste (-0.3..0.6), unique to each Pipo, nudged by experience. */
    fun taste(s: PipoState, foodId: String): Float =
        s.tastes[foodId] ?: (unit(s.seed, foodId.hashCode().toLong()) * 0.9f - 0.3f)

    fun favorite(s: PipoState): Food? =
        Foods.all.filter { it.kind != FoodKind.INGREDIENT || it.edible }.maxByOrNull { taste(s, it.id) }?.takeIf { taste(s, it.id) > 0.35f }

    /** What he'd have to buy to satisfy a craving (empty = he can have it now). */
    fun missingFor(s: PipoState, foodId: String): List<String> {
        val f = Foods.byId(foodId) ?: return emptyList()
        if (f.kind != FoodKind.DISH) return if (foodId in s.pantry) emptyList() else listOf(foodId)
        val r = Foods.recipe(foodId) ?: return emptyList()
        val left = s.pantry.toMutableList()
        return r.needs.filter { !left.remove(it) }
    }

    fun eatScore(s: PipoState, env: Env): Float {
        val a = s.mood.appetite
        if (edible(s).isEmpty() && feasible(s).isEmpty()) return 0f
        val craving = s.craving.isNotEmpty() && missingFor(s, s.craving).isEmpty() && Foods.byId(s.craving)?.kind != FoodKind.DISH
        return a * 1.7f + (if (mealTime(env.hour) && a > 0.35f) 0.45f else 0f) + (if (craving) 0.5f else 0f) - (if (a < 0.3f) 0.6f else 0f)
    }

    fun cookScore(s: PipoState, env: Env): Float {
        val r = feasible(s)
        if (r.isEmpty()) return 0f
        val t = s.profile.traits
        val wantDish = s.craving.isNotEmpty() && r.any { it.id == s.craving }
        val interest = s.feed["cooking"] ?: 0f
        return s.mood.appetite * 1.1f + t.curiosity * 0.2f + (if (wantDish) 0.9f else 0f) + interest * 0.5f +
            (if (mealTime(env.hour) && s.mood.appetite > 0.4f) 0.3f else 0f) - (if (s.mood.appetite < 0.3f) 0.5f else 0f)
    }

    /**
     * Cravings come from somewhere: the weather, something he saw, a place he's been, an old favourite.
     * [cause] lets another system hand him one ("saw a noodle video").
     */
    fun maybeCrave(s: PipoState, env: Env, rng: Random, cause: Pair<String, String>? = null): Boolean {
        if (cause != null) { s.craving = cause.first; s.cravingReason = cause.second; return true }
        if (s.craving.isNotEmpty() || s.mood.appetite < 0.35f) return false
        val r = rng.nextFloat()
        val pick: Pair<String, String>? = when {
            env.weather.chilly && r < 0.5f -> if (rng.nextBoolean()) "noodle_soup" to "It's ${WeatherEngine.describe(env.weather.kind)}. That means soup. That's the rule."
                else "hot_chocolate" to "It's cold-ish. Hot chocolate is medicine."
            r < 0.35f -> favorite(s)?.let { it.id to "I've been thinking about ${it.name}. For an hour. Maybe two." }
            r < 0.45f && env.hour in 6..10 -> "croissant" to "Mornings need a croissant. I read that. I didn't read that."
            else -> null
        }
        if (pick == null) return false
        s.craving = pick.first; s.cravingReason = pick.second
        return true
    }

    fun eat(s: PipoState, now: Long, rng: Random, offline: Boolean): List<Outcome> {
        val options = edible(s)
        if (options.isEmpty()) return emptyList()
        val food = options.firstOrNull { it.id == s.craving }
            ?: options.maxByOrNull { taste(s, it.id) + rng.nextFloat() * 0.35f }!!
        s.pantry.remove(food.id)
        return consume(s, food, now, rng, offline)
    }

    /** Eating something (bought or just cooked). Shared by [eat] and [cook]. */
    private fun consume(s: PipoState, food: Food, now: Long, rng: Random, offline: Boolean, burnt: Boolean = false): List<Outcome> {
        val out = mutableListOf<Outcome>()
        val before = taste(s, food.id)
        val wasCraving = s.craving == food.id
        // he learns what he likes: a little drift towards "yes", less if it was burnt
        val after = (before + (if (burnt) -0.12f else 0.05f + rng.nextFloat() * 0.05f)).coerceIn(-0.6f, 0.95f)
        s.tastes[food.id] = after
        s.mood.appetite = (s.mood.appetite - if (food.kind == FoodKind.DISH || food.warm) 0.65f else 0.35f).coerceAtLeast(0f)
        MoodEngine.bump(s, happiness = if (after > 0.3f) 0.12f else 0.03f, energy = 0.08f, irritation = -0.15f)
        if (wasCraving) { s.craving = ""; s.cravingReason = "" }
        val first = s.count("ate:${food.id}") == 1
        if (food.kind == FoodKind.DISH || food.warm || food.id in setOf("pancakes", "pizza_slice")) s.world.objectStates["plate"] = "1"
        val line = when {
            burnt -> Dialogue.pick(listOf("It's crunchy. It wasn't supposed to be crunchy.", "I'm eating it anyway. Out of respect."), rng)
            wasCraving -> "I wanted ${food.name}. Now I have ${food.name}. Life is good."
            first && after > 0.4f -> "${food.name.replaceFirstChar { it.uppercase() }}! Where has this been all my life?"
            first && after < 0f -> "${food.name.replaceFirstChar { it.uppercase() }}. Hm. No. Not for me."
            after > 0.6f -> Dialogue.pick(listOf("Mmm. ${food.name}. My favorite.", "${food.name.replaceFirstChar { it.uppercase() }} again. No regrets."), rng)
            else -> Dialogue.pick(listOf("Nom.", "That was a good snack.", "Crumbs everywhere. Worth it."), rng)
        }
        out += Outcome.Ate(food.id, line)
        if (first) {
            Chronicle.remember(s, MemoryType.FOOD, if (after > 0.35f) "I love ${food.name}" else if (after < 0f) "I don't like ${food.name}" else "I tried ${food.name}", 0.4f + if (after > 0.5f || after < 0f) 0.15f else 0f, now, "food:${food.id}")
            if (after > 0.45f || after < -0.05f) Chronicle.journal(s, "Pipo tried ${food.name}",
                if (after > 0.45f) "Verdict: amazing. He's thinking about it again already." else "Verdict: no. He made a face. A big one.", JournalCategory.FOOD, now)
        }
        if (!offline && rng.nextFloat() < 0.35f) out += Outcome.Emote(if (after > 0.3f) EmoteKind.HEART else EmoteKind.SWEAT)
        return out
    }

    fun cook(s: PipoState, now: Long, rng: Random, offline: Boolean): List<Outcome> {
        val options = feasible(s)
        if (options.isEmpty()) return emptyList()
        val recipe = options.firstOrNull { it.id == s.craving } ?: options.random(rng)
        recipe.needs.forEach { s.pantry.remove(it) }
        val dish = Foods.byId(recipe.id)!!
        val t = s.profile.traits
        val xp = (s.counters["cooked"] ?: 0).coerceAtMost(12)
        val chance = (0.5f + t.patience * 0.25f + xp * 0.03f - recipe.tricky * 0.5f + (if (s.mood.current == Mood.SLEEPY) -0.15f else 0f)).coerceIn(0.2f, 0.93f)
        val r = rng.nextFloat()
        s.count("cooked")
        val out = mutableListOf<Outcome>()
        when {
            r < chance -> {
                out += Outcome.Cooked(dish.id, true, Dialogue.pick(listOf("I made ${dish.name}. By myself. With my hands.", "${dish.name.replaceFirstChar { it.uppercase() }}. Chef Pipo.", "It smells right. That's a good sign. Right?"), rng))
                if (s.count("cooked:${dish.id}") == 1) {
                    Chronicle.journal(s, "Pipo cooked ${dish.name}", "First try. He wants you to know it was first try.", JournalCategory.FOOD, now)
                    Chronicle.remember(s, MemoryType.FOOD, "I cooked ${dish.name}", 0.5f, now, "cooked:${dish.id}")
                }
                Personality.nudge(s, Trait.CONFIDENCE, 0.004f)
                out += consume(s, dish, now, rng, offline)
            }
            r < chance + (1f - chance) * 0.5f -> {
                // burnt: still eaten, still a story
                s.world.objectStates["smoke"] = "1"
                out += Outcome.Cooked(dish.id, false, Dialogue.pick(listOf("It's... a little burnt. A lot burnt.", "The smoke alarm and I are not friends.", "I made charcoal. On purpose. For art."), rng))
                Chronicle.journal(s, "Pipo burnt the ${dish.name}", "He opened the window and pretended nothing happened. Something happened.", JournalCategory.FOOD, now)
                Chronicle.remember(s, MemoryType.FOOD, "I burnt the ${dish.name}", 0.45f, now, "burnt:${dish.id}")
                Chronicle.event(s, EventType.COOKED, 0.46f + t.mischief * 0.1f, now, dish.id)
                MoodEngine.setTransient(s, Mood.EMBARRASSED, now, 30_000)
                out += consume(s, dish, now, rng, offline, burnt = true)
            }
            else -> {
                // weird: not what he meant, not bad either
                out += Outcome.Cooked(dish.id, false, Dialogue.pick(listOf("It's purple now. I didn't add anything purple.", "It came out as soup. It was supposed to be not soup."), rng))
                Chronicle.remember(s, MemoryType.FOOD, "my ${dish.name} came out weird", 0.35f, now, "weird:${dish.id}")
                out += consume(s, dish, now, rng, offline)
            }
        }
        return out
    }
}

/** Money is small and believable: a coin jar, odd jobs, the odd lucky find. No grinding, no store. */
object Economy {
    fun canAfford(s: PipoState, price: Int) = s.coins >= price
    fun spend(s: PipoState, price: Int): Boolean { if (s.coins < price) return false; s.coins -= price; return true }
    fun earn(s: PipoState, amount: Int) { s.coins = (s.coins + amount).coerceAtMost(999) }
    fun priceOf(id: String): Int = Catalog.item(id)?.price ?: Foods.byId(id)?.price ?: 0
    fun nameOf(id: String): String = Catalog.item(id)?.name?.lowercase() ?: Foods.byId(id)?.name ?: id
    fun shapeOf(id: String) = Catalog.item(id)?.shape ?: Foods.byId(id)?.shape
}

/** Owned-item helpers shared by projects and trips. */
object Inventory {
    fun add(s: PipoState, catalogId: String, now: Long): OwnedItem {
        val item = OwnedItem(s.nextId(), catalogId, now, revealed = false, seenByUser = false)
        s.world.items.add(item)
        return item
    }

    /** Tags an unused owned item could provide. */
    fun freeTags(s: PipoState): Set<String> =
        s.world.items.filter { it.usedInProjectId == 0L && it.id != s.pet.stolenItemId }.flatMap { Catalog.item(it.catalogId)?.tags ?: emptySet() }.toSet() - "mirror"

    fun owns(s: PipoState, catalogId: String) = s.world.items.any { it.catalogId == catalogId }
}
