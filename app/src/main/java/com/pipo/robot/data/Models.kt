package com.pipo.robot.data

import kotlinx.serialization.Serializable

/* ------------------------------------------------------------------ */
/*  Personality                                                        */
/* ------------------------------------------------------------------ */

@Serializable
data class Traits(
    var curiosity: Float = 0.6f,
    var playfulness: Float = 0.6f,
    var mischief: Float = 0.4f,
    var affection: Float = 0.6f,
    var confidence: Float = 0.5f,
    var sociability: Float = 0.55f,
    var patience: Float = 0.5f,
    var laziness: Float = 0.4f,
    var adventurousness: Float = 0.5f,
    var stubbornness: Float = 0.4f,
)

@Serializable
data class PipoProfile(
    var traits: Traits = Traits(),
    var createdAt: Long = 0L,
    /** 0..1 — grows slowly with shared time. Never decreases because of absence. */
    var relationship: Float = 0.1f,
    var userName: String = "",
    var firstRunDone: Boolean = false,
    var interactions: Int = 0,
    var favoriteGame: String = "",
)

/* ------------------------------------------------------------------ */
/*  Mood                                                               */
/* ------------------------------------------------------------------ */

enum class Mood {
    HAPPY, EXCITED, CURIOUS, SLEEPY, BORED, GRUMPY, LONELY,
    NERVOUS, PROUD, EMBARRASSED, MISCHIEVOUS, RELAXED
}

@Serializable
data class PipoMood(
    var happiness: Float = 0.65f,
    var energy: Float = 0.8f,
    var curiosity: Float = 0.6f,
    var boredom: Float = 0.2f,
    var affection: Float = 0.5f,
    var irritation: Float = 0f,
    var loneliness: Float = 0.15f,
    var excitement: Float = 0.3f,
    /** Short-lived moods (proud, embarrassed, nervous...) override the derived one. */
    var transient: Mood? = null,
    var transientUntil: Long = 0L,
    var current: Mood = Mood.RELAXED,
)

/* ------------------------------------------------------------------ */
/*  Memory                                                             */
/* ------------------------------------------------------------------ */

enum class MemoryType { USER_FACT, CONVERSATION, JOKE, DISCOVERY, GAME, EVENT, PROJECT, MOMENT, SELF }

@Serializable
data class PipoMemory(
    val id: Long,
    val type: MemoryType,
    var content: String,
    var importance: Float,
    var timestamp: Long,
    var lastReferenced: Long = 0L,
    /** Dedupe key: repeated experiences strengthen one memory instead of adding many. */
    val key: String = "",
    var count: Int = 1,
)

/* ------------------------------------------------------------------ */
/*  World                                                              */
/* ------------------------------------------------------------------ */

@Serializable
data class OwnedItem(
    val id: Long,
    val catalogId: String,
    val foundAt: Long,
    var revealed: Boolean = false,
    var usedInProjectId: Long = 0L,
    var seenByUser: Boolean = false,
)

@Serializable
data class PipoWorld(
    var currentRoom: String = "workshop",
    var unlockedRooms: MutableList<String> = mutableListOf("workshop"),
    /** e.g. "prank:plant_hat" -> "1", "drawings" -> "2" */
    var objectStates: MutableMap<String, String> = mutableMapOf(),
    var items: MutableList<OwnedItem> = mutableListOf(),
)

/* ------------------------------------------------------------------ */
/*  Activity                                                           */
/* ------------------------------------------------------------------ */

enum class Station { BED, PLANT, CHARGER, WINDOW, DESK, SHELF, WORKBENCH, ARCADE, TOYS, RUG, WANDER, FRONT, STAY }

enum class ActivityType(val station: Station) {
    SLEEP(Station.BED),
    REST(Station.STAY),
    CHARGE(Station.CHARGER),
    EXPLORE(Station.WANDER),
    PLAY_TOY(Station.TOYS),
    PLAY_ARCADE(Station.ARCADE),
    EXPERIMENT(Station.WORKBENCH),
    BUILD(Station.WORKBENCH),
    EXAMINE(Station.SHELF),
    REARRANGE(Station.WANDER),
    READ(Station.DESK),
    THINK(Station.WINDOW),
    WORK_COMPUTER(Station.DESK),
    INSPECT_PLANT(Station.PLANT),
    DANCE(Station.RUG),
    PREPARE_SURPRISE(Station.WORKBENCH),
    SEEK_USER(Station.FRONT),
    NOTHING(Station.STAY),
}

@Serializable
data class PipoActivity(
    var type: ActivityType = ActivityType.SLEEP,
    var startedAt: Long = 0L,
    var durationMs: Long = 0L,
    var result: String = "",
)

/* ------------------------------------------------------------------ */
/*  Projects                                                           */
/* ------------------------------------------------------------------ */

enum class ProjectState { GATHERING, BUILDING, DONE, FAILED, EVOLVED }

@Serializable
data class PipoProject(
    val id: Long,
    val templateId: String,
    val title: String,
    var state: ProjectState = ProjectState.GATHERING,
    var progress: Float = 0f,
    val components: MutableList<String> = mutableListOf(),
    val collected: MutableList<String> = mutableListOf(),
    var result: String = "",
    val startedAt: Long = 0L,
    var finishedAt: Long = 0L,
    var attempts: Int = 0,
) {
    val active: Boolean get() = state == ProjectState.GATHERING || state == ProjectState.BUILDING
}

/* ------------------------------------------------------------------ */
/*  Events + notifications                                             */
/* ------------------------------------------------------------------ */

enum class NotifCategory(val label: String) {
    DISCOVERIES("Discoveries"),
    FUNNY("Funny moments"),
    CONVERSATIONS("Conversations"),
    GAMES("Games"),
    MILESTONES("Milestones"),
    THOUGHTS("Spontaneous thoughts"),
}

enum class EventType(val category: NotifCategory) {
    DISCOVERY(NotifCategory.DISCOVERIES),
    REVEAL(NotifCategory.DISCOVERIES),
    PROJECT_DONE(NotifCategory.MILESTONES),
    PROJECT_FAILED(NotifCategory.FUNNY),
    PROJECT_EVOLVED(NotifCategory.FUNNY),
    PRANK(NotifCategory.FUNNY),
    SURPRISE(NotifCategory.CONVERSATIONS),
    THOUGHT(NotifCategory.THOUGHTS),
    SILENT_DOTS(NotifCategory.THOUGHTS),
    WANT_PLAY(NotifCategory.GAMES),
    HI(NotifCategory.CONVERSATIONS),
    MILESTONE(NotifCategory.MILESTONES),
}

/** Something that actually happened to Pipo that he may want to share. */
@Serializable
data class PendingEvent(
    val id: Long,
    val type: EventType,
    val importance: Float,
    val createdAt: Long,
    val payload: String = "",
    val mood: Mood = Mood.RELAXED,
    var notified: Boolean = false,
    var shownInApp: Boolean = false,
)

enum class UserResponse { NONE, OPENED, PLAYED, NOT_NOW }

@Serializable
data class NotificationRecord(
    val id: Long,
    val eventId: Long,
    val eventType: EventType,
    val text: String,
    val timestamp: Long,
    val delivered: Boolean,
    var response: UserResponse = UserResponse.NONE,
)

/* ------------------------------------------------------------------ */
/*  Journal                                                            */
/* ------------------------------------------------------------------ */

enum class JournalCategory { DISCOVERY, PROJECT, GAME, MOMENT, CONVERSATION, MILESTONE, MISCHIEF }

@Serializable
data class JournalEntry(
    val id: Long,
    val timestamp: Long,
    val title: String,
    val description: String,
    val category: JournalCategory,
)

/* ------------------------------------------------------------------ */
/*  Settings + games                                                   */
/* ------------------------------------------------------------------ */

enum class Frequency(val label: String) { RARE("Rare"), NORMAL("Normal"), FREQUENT("Frequent") }
enum class VoiceMode(val label: String) { SPOKEN("Voice"), BEEPS("Beeps only"), SILENT("Silent") }

@Serializable
data class PipoSettings(
    var notificationsEnabled: Boolean = true,
    var frequency: Frequency = Frequency.NORMAL,
    var quietStartHour: Int = 22,
    var quietEndHour: Int = 8,
    var categories: MutableMap<String, Boolean> =
        NotifCategory.entries.associate { it.name to true }.toMutableMap(),
    var voiceMode: VoiceMode = VoiceMode.SPOKEN,
    var sounds: Boolean = true,
    var aiEnabled: Boolean = false,
    var aiApiKey: String = "",
    var askedNotificationPermission: Boolean = false,
) {
    fun categoryOn(c: NotifCategory) = categories[c.name] ?: true
}

@Serializable
data class GameRecord(
    var pipoWins: Int = 0,
    var userWins: Int = 0,
    var draws: Int = 0,
    var plays: Int = 0,
    var lastPlayed: Long = 0L,
    var pipoStreak: Int = 0,
    var userStreak: Int = 0,
)

/* ------------------------------------------------------------------ */
/*  Root                                                               */
/* ------------------------------------------------------------------ */

@Serializable
data class PipoState(
    var schema: Int = 1,
    var idCounter: Long = 0L,
    var seed: Long = 0L,
    var profile: PipoProfile = PipoProfile(),
    var mood: PipoMood = PipoMood(),
    var memories: MutableList<PipoMemory> = mutableListOf(),
    var world: PipoWorld = PipoWorld(),
    var activity: PipoActivity = PipoActivity(),
    var projects: MutableList<PipoProject> = mutableListOf(),
    var notifications: MutableList<NotificationRecord> = mutableListOf(),
    var journal: MutableList<JournalEntry> = mutableListOf(),
    var events: MutableList<PendingEvent> = mutableListOf(),
    var settings: PipoSettings = PipoSettings(),
    var games: MutableMap<String, GameRecord> = mutableMapOf(),
    var cooldowns: MutableMap<String, Long> = mutableMapOf(),
    var counters: MutableMap<String, Int> = mutableMapOf(),
    var lastSimulatedAt: Long = 0L,
    var lastSeenByUserAt: Long = 0L,
    var lastUserInteractionAt: Long = 0L,
) {
    fun nextId(): Long = ++idCounter
    fun count(key: String, by: Int = 1): Int {
        val v = (counters[key] ?: 0) + by
        counters[key] = v
        return v
    }
    fun activeProject(): PipoProject? = projects.lastOrNull { it.active }
    fun game(id: String): GameRecord = games.getOrPut(id) { GameRecord() }
}
