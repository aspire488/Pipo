package com.pipo.robot.data

import kotlinx.serialization.json.Json

/** One JSON configuration for the save file, shared by the repository and the tests. */
val PipoJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Save-file upgrades. New fields arrive with defaults through kotlinx-serialization; this is for
 * the parts where an old save needs *interpreting*, so an existing Pipo keeps his history.
 */
object Migrations {

    /** @return true if anything changed. */
    fun migrate(s: PipoState, now: Long): Boolean {
        var changed = false
        if (s.schema < 2) {
            // v1 kept "how many drawings of you" as a counter. They become real drawings, still on the wall.
            val n = s.world.objectStates["drawings"]?.toIntOrNull() ?: 0
            if (s.drawings.none { it.subject == DrawSubject.USER }) {
                repeat(n.coerceAtMost(4)) { i ->
                    s.drawings.add(Drawing(s.nextId(), DrawSubject.USER, "", "You. The ears are a choice.", s.profile.createdAt + i, seed = i * 31 + 7))
                }
            }
            // A Pipo from before Nib existed meets Nib as part of his story, not as a patch note.
            s.pet.adopted = false
            s.pet.adoptedAt = 0L
            s.pet.activity = PetActivity.AWAY
            s.migratedAt = now
            s.schema = 2
            changed = true
        }
        return sanitize(s) || changed
    }

    /** Repairs values a crash or a hand-edited file could leave behind. Never deletes history. */
    fun sanitize(s: PipoState): Boolean {
        var changed = false
        if (s.coins < 0) { s.coins = 0; changed = true }
        val t = s.trip
        if (t != null && (Places.byId(t.placeId) == null || t.endsAt < t.startedAt)) { s.trip = null; changed = true }
        if (s.pet.x.isNaN() || s.pet.x !in 0f..400f) { s.pet.x = 96f; changed = true }
        val unknownFood = s.pantry.filter { Foods.byId(it) == null }
        if (unknownFood.isNotEmpty()) { s.pantry.removeAll(unknownFood.toSet()); changed = true }
        if (s.pantry.size > 24) { s.pantry.subList(0, s.pantry.size - 24).clear(); changed = true }
        if (s.drawings.size > 60) { s.drawings.subList(0, s.drawings.size - 60).clear(); changed = true }
        if (s.photos.size > 80) { s.photos.subList(0, s.photos.size - 80).clear(); changed = true }
        if (s.mystery.stage !in 0..5) { s.mystery.stage = s.mystery.stage.coerceIn(0, 5); changed = true }
        // references to things that no longer exist (sold to Old Rook, pruned) are dropped, never invented
        val ids = s.world.items.map { it.id }.toSet()
        if (s.pet.stolenItemId != 0L && s.pet.stolenItemId !in ids) { s.pet.stolenItemId = 0L; changed = true }
        s.world.objectStates["floor"]?.toLongOrNull()?.let { if (it !in ids) { s.world.objectStates.remove("floor"); changed = true } }
        // a trip can't last longer than a long day out (a hand-edited or clock-jumped save)
        s.trip?.let { if (it.endsAt - it.startedAt > 6 * 3_600_000L) { it.endsAt = it.startedAt + 6 * 3_600_000L; changed = true } }
        return changed
    }
}
