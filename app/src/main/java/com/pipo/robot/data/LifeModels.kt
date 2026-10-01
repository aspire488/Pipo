package com.pipo.robot.data

import kotlinx.serialization.Serializable

/*
 * Pipo's life outside the four walls, his pet, and the things he makes.
 * Everything here is owned by the deterministic engine. The AI never writes any of it.
 */

/* ------------------------------------------------------------------ */
/*  Nib, the pet                                                       */
/* ------------------------------------------------------------------ */

@Serializable
data class PetTraits(
    var curiosity: Float = 0.65f,
    var playfulness: Float = 0.7f,
    var mischief: Float = 0.5f,
    var bravery: Float = 0.4f,
    var cuddly: Float = 0.6f,
)

enum class PetMood { HAPPY, PLAYFUL, SLEEPY, CURIOUS, GRUMPY, SCARED }

enum class PetActivity {
    FOLLOW, WANDER, NAP, PLAY_BALL, STEAL, STARE, ZOOMIES, SIT_WITH_PIPO, HIDE, GREET,
    /** Out with Pipo. */
    AWAY,
}

@Serializable
data class PetState(
    var name: String = "Nib",
    /** Nib lives here. Pipos from before the pet existed meet Nib later, in the story. */
    var adopted: Boolean = true,
    var adoptedAt: Long = 0L,
    var traits: PetTraits = PetTraits(),
    var energy: Float = 0.8f,
    var boredom: Float = 0.2f,
    var fear: Float = 0f,
    var mood: PetMood = PetMood.HAPPY,
    var activity: PetActivity = PetActivity.NAP,
    /** World position (u) in the room, so Nib is where you left it. */
    var x: Float = 96f,
    var napSpot: String = "rug",
    /** An item Nib has taken and hidden (under the bed). 0 = nothing. */
    var stolenItemId: Long = 0L,
    var bond: Float = 0.5f,
    var lastEventAt: Long = 0L,
    /** Tiny memory: keys of things that happened to Nib ("lost:hardware", "chased:squirrel"). */
    var memories: MutableList<String> = mutableListOf(),
)

/* ------------------------------------------------------------------ */
/*  Trips                                                              */
/* ------------------------------------------------------------------ */

enum class TripPurpose { SHOP, FOOD, WALK, FOOTBALL, ODD_JOB, LIBRARY, WILDLIFE, EXPLORE, INVESTIGATE, CRICKET, TABLE_TENNIS, BADMINTON }

@Serializable
data class TripState(
    val id: Long,
    val placeId: String,
    val purpose: TripPurpose,
    val startedAt: Long,
    var endsAt: Long,
    val withPet: Boolean,
    /** Catalog ids he means to buy (materials or food). */
    val shoppingList: List<String> = emptyList(),
    /** Why he went, in his words ("the flying machine needs a motor"). */
    val reason: String = "",
)

@Serializable
data class TripReport(
    val tripId: Long,
    val placeId: String,
    val purpose: TripPurpose,
    val at: Long,
    val bought: List<String> = emptyList(),
    /** Bought by mistake / out of curiosity instead of what he meant to buy. */
    val wrongBuy: String = "",
    val foundItemIds: List<Long> = emptyList(),
    val spent: Int = 0,
    val earned: Int = 0,
    val creatureKey: String = "",
    val photoId: Long = 0L,
    val npcLine: String = "",
    /** Short story beats, oldest first. The first is the headline. */
    val story: List<String> = emptyList(),
    val withPet: Boolean = false,
    val clue: String = "",
    var told: Boolean = false,
)

@Serializable
data class PlaceMemory(
    var visits: Int = 0,
    var firstVisit: Long = 0L,
    var lastVisit: Long = 0L,
    /** His own annotation on the map: "good food", "don't go here", "???". */
    var note: String = "",
)

@Serializable
data class NpcMemory(
    var visits: Int = 0,
    var lastVisit: Long = 0L,
    var lastLine: String = "",
    var fondness: Float = 0.3f,
)

/* ------------------------------------------------------------------ */
/*  Things he makes: drawings, photos                                  */
/* ------------------------------------------------------------------ */

enum class DrawSubject { USER, SELF, PET, CREATURE, PLACE, INVENTION, DREAM, SYMBOL, BUILDING, FOOD }

@Serializable
data class Drawing(
    val id: Long,
    val subject: DrawSubject,
    /** What exactly: a creature key, a place id, a project template, a food id… */
    val ref: String = "",
    val caption: String,
    val createdAt: Long,
    val seed: Int = 0,
    var hung: Boolean = true,
)

enum class PhotoSubject { CREATURE, PLACE, PET, SELFIE, WEATHER, FOOD, INVENTION, SKY }

@Serializable
data class Photo(
    val id: Long,
    val subject: PhotoSubject,
    val ref: String = "",
    val placeId: String = "room",
    val weather: String = "CLEAR",
    val hour: Int = 12,
    val caption: String,
    val createdAt: Long,
    val seed: Int = 0,
    /** Something in it that shouldn't be there. Only visible once he notices. */
    var anomaly: Boolean = false,
    var anomalySeen: Boolean = false,
)

/** A recurring animal he recognises (and eventually names). */
@Serializable
data class Creature(
    val key: String,
    val species: String,
    val look: String,
    var name: String = "",
    var sightings: Int = 0,
    var firstSeen: Long = 0L,
    var lastSeen: Long = 0L,
    var lastPlace: String = "",
)

/* ------------------------------------------------------------------ */
/*  The thing behind the world                                         */
/* ------------------------------------------------------------------ */

@Serializable
data class MysteryState(
    /** 0 nothing yet · 1 signal · 2 symbol · 3 wrong photo · 4 the building · 5 the other side. */
    var stage: Int = 0,
    var clues: MutableList<String> = mutableListOf(),
    var lastClueAt: Long = 0L,
    var dreamDrawingId: Long = 0L,
    var tokenItemId: Long = 0L,
    var photoId: Long = 0L,
    var notesFromOtherSide: Int = 0,
)

/** A sound he recorded on his little phone. [kind] picks how it's played back (synthesized). */
@Serializable
data class Recording(val id: Long, val label: String, val kind: String, val at: Long, val place: String = "room")
