package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Mood

/** Body animation states. Each has its own motion signature in PoseLibrary. */
enum class AnimState {
    IDLE, HAPPY, EXCITED, CURIOUS, SLEEPY, BORED, ANNOYED, SAD, LONELY, NERVOUS, PROUD,
    EMBARRASSED, MISCHIEVOUS, SURPRISED, TALKING, LISTENING, SLEEPING, DANCING, WALKING,
    RUNNING, SITTING, HIDING, THINKING, BUILDING, PLAYING,
    // verbs
    HOP, STRETCH, SPIN, SHAKE, LOOK_AROUND, PEEK, FALLEN, WAVE, HELD, READING, CHARGING, PRESENTING, PHONE, GAMING,
    // evolution pass: fuller body language
    CHEERFUL, YAWN, SNEAK, ARMS_CROSSED, TURN_AWAY, LIE_DOWN, GET_UP, DIZZY, LAUGH, SIGH, CELEBRATE, SULK, FINGER_UP,
    // a life beyond the room
    EATING, COOKING, DRAWING, KICKUPS, PETTING, CARRYING, PHOTO
}

/** Face expressions. */
enum class Expr {
    CONTENT, HAPPY, EXCITED, CURIOUS, SLEEPY, BORED, SURPRISED, ANNOYED, EMBARRASSED,
    SAD, SUSPICIOUS, NERVOUS, PROUD, MISCHIEF, CLOSED, FOCUSED, LOVE,
    WINK, DIZZY, LAUGH, WORRIED
}

enum class EmoteKind { ZZZ, QUESTION, EXCLAIM, NOTES, HEART, SPARKLE, SWEAT, ANGER, DOTS, IDEA }

/** Nonverbal sounds. */
enum class Sfx { BEEP, LAUGH, SIGH, SURPRISED, SLEEPY, HAPPY, GRUMBLE, BOOP, WIN, LOSE, YAWN, SERVO, GIGGLE, HMM,
    /** A real closed-mouth hum: a little original tune, legato, with vibrato. Not the letters "hmm". */
    HUM,
    CHEW, KICK, DOOR, PET_CHIRP,
    /** Nib's feelings, in Nib: a happy trill, a grumpy buzz, a curious rising boop, a sad slide, a smug double-chirp. */
    PET_HAPPY, PET_GRUMP, PET_CURIOUS, PET_SAD, PET_SMUG,
    SHUTTER, SIZZLE, EFFORT, THUNDER, SCRIBBLE }

object Vocab {
    fun moodExpr(m: Mood): Expr = when (m) {
        Mood.HAPPY -> Expr.CONTENT
        Mood.EXCITED -> Expr.EXCITED
        Mood.CURIOUS -> Expr.CURIOUS
        Mood.SLEEPY -> Expr.SLEEPY
        Mood.BORED -> Expr.BORED
        Mood.GRUMPY -> Expr.ANNOYED
        Mood.LONELY -> Expr.SAD
        Mood.NERVOUS -> Expr.NERVOUS
        Mood.PROUD -> Expr.PROUD
        Mood.EMBARRASSED -> Expr.EMBARRASSED
        Mood.MISCHIEVOUS -> Expr.MISCHIEF
        Mood.RELAXED -> Expr.CONTENT
        Mood.WORRIED -> Expr.WORRIED
        Mood.THOUGHTFUL -> Expr.FOCUSED
        Mood.PLAYFUL -> Expr.HAPPY
    }

    /** Idle body language for a mood (used when Pipo is not doing a specific action). */
    fun moodIdle(m: Mood): AnimState = when (m) {
        Mood.HAPPY -> AnimState.CHEERFUL
        Mood.EXCITED -> AnimState.EXCITED
        Mood.CURIOUS -> AnimState.CURIOUS
        Mood.SLEEPY -> AnimState.SLEEPY
        Mood.BORED -> AnimState.BORED
        Mood.GRUMPY -> AnimState.ARMS_CROSSED
        Mood.LONELY -> AnimState.LONELY
        Mood.NERVOUS -> AnimState.NERVOUS
        Mood.PROUD -> AnimState.PROUD
        Mood.EMBARRASSED -> AnimState.EMBARRASSED
        Mood.MISCHIEVOUS -> AnimState.MISCHIEVOUS
        Mood.RELAXED -> AnimState.IDLE
        Mood.WORRIED -> AnimState.NERVOUS
        Mood.THOUGHTFUL -> AnimState.THINKING
        Mood.PLAYFUL -> AnimState.CHEERFUL
    }

    fun activityAnim(a: ActivityType): AnimState = when (a) {
        ActivityType.SLEEP -> AnimState.SLEEPING
        ActivityType.REST -> AnimState.SITTING
        ActivityType.CHARGE -> AnimState.CHARGING
        ActivityType.EXPLORE -> AnimState.LOOK_AROUND
        ActivityType.PLAY_TOY -> AnimState.PLAYING
        ActivityType.PLAY_ARCADE -> AnimState.PLAYING
        ActivityType.EXPERIMENT -> AnimState.BUILDING
        ActivityType.BUILD -> AnimState.BUILDING
        ActivityType.EXAMINE -> AnimState.CURIOUS
        ActivityType.REARRANGE -> AnimState.MISCHIEVOUS
        ActivityType.READ -> AnimState.READING
        ActivityType.THINK -> AnimState.THINKING
        ActivityType.WORK_COMPUTER -> AnimState.BUILDING
        ActivityType.INSPECT_PLANT -> AnimState.CURIOUS
        ActivityType.DANCE -> AnimState.DANCING
        ActivityType.PREPARE_SURPRISE -> AnimState.HIDING
        ActivityType.SEEK_USER -> AnimState.WAVE
        ActivityType.SCROLL_PHONE -> AnimState.PHONE
        ActivityType.PLAY_CONSOLE -> AnimState.GAMING
        ActivityType.NOTHING -> AnimState.IDLE
        ActivityType.GO_OUT -> AnimState.WALKING
        ActivityType.EAT -> AnimState.EATING
        ActivityType.COOK -> AnimState.COOKING
        ActivityType.DRAW -> AnimState.DRAWING
        ActivityType.CLEAN -> AnimState.BUILDING
        ActivityType.HIDE -> AnimState.HIDING
        ActivityType.PLAY_PET -> AnimState.PETTING
    }

    fun activityExpr(a: ActivityType, mood: Mood): Expr = when (a) {
        ActivityType.SLEEP -> Expr.CLOSED
        ActivityType.BUILD, ActivityType.EXPERIMENT, ActivityType.WORK_COMPUTER -> Expr.FOCUSED
        ActivityType.READ -> Expr.FOCUSED
        ActivityType.EXPLORE, ActivityType.EXAMINE, ActivityType.INSPECT_PLANT, ActivityType.THINK -> Expr.CURIOUS
        ActivityType.PLAY_ARCADE, ActivityType.PLAY_TOY, ActivityType.DANCE, ActivityType.SCROLL_PHONE, ActivityType.PLAY_CONSOLE, ActivityType.PLAY_PET -> Expr.HAPPY
        ActivityType.PREPARE_SURPRISE, ActivityType.REARRANGE, ActivityType.HIDE -> Expr.MISCHIEF
        ActivityType.CHARGE, ActivityType.EAT -> Expr.CONTENT
        ActivityType.COOK, ActivityType.DRAW, ActivityType.CLEAN -> Expr.FOCUSED
        else -> moodExpr(mood)
    }

    fun moodSfx(m: Mood): Sfx = when (m) {
        Mood.SLEEPY -> Sfx.SLEEPY
        Mood.GRUMPY -> Sfx.GRUMBLE
        Mood.BORED, Mood.LONELY -> Sfx.SIGH
        Mood.EXCITED, Mood.HAPPY, Mood.PROUD -> Sfx.HAPPY
        Mood.MISCHIEVOUS -> Sfx.LAUGH
        Mood.NERVOUS, Mood.EMBARRASSED -> Sfx.SURPRISED
        Mood.WORRIED, Mood.THOUGHTFUL -> Sfx.HMM
        Mood.PLAYFUL -> Sfx.GIGGLE
        else -> Sfx.BEEP
    }
}

/** How a line should be delivered. Drives TTS volume/pitch, extra chirps and body language. */
enum class SpeechStyle { NORMAL, WHISPER, EXCITED, LAUGH, SIGH, QUESTION }

object SpeechStyles {
    private val laugh = Regex("\\b(ha(ha)+|he(he)+|heh|hah)\\b",RegexOption.IGNORE_CASE)

    fun style(text: String): SpeechStyle {
        val t = text.trim()
        if (t.isEmpty()) return SpeechStyle.NORMAL
        val letters = t.filter { it.isLetter() }
        return when {
            (t.startsWith("(") && t.endsWith(")")) || t.startsWith("psst", ignoreCase = true) || t.startsWith("*whisper") -> SpeechStyle.WHISPER
            laugh.containsMatchIn(t) -> SpeechStyle.LAUGH
            t.startsWith("*sigh", ignoreCase = true) || t.startsWith("ugh", ignoreCase = true) || t.startsWith("sigh", ignoreCase = true) -> SpeechStyle.SIGH
            letters.length >= 4 && letters.count { it.isUpperCase() } >= letters.length * 0.7f -> SpeechStyle.EXCITED
            t.count { it == '!' } >= 2 -> SpeechStyle.EXCITED
            t.endsWith("?") -> SpeechStyle.QUESTION
            else -> SpeechStyle.NORMAL
        }
    }
}
