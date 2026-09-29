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
    CHEERFUL, YAWN, SNEAK, ARMS_CROSSED, TURN_AWAY, LIE_DOWN, GET_UP, DIZZY, LAUGH, SIGH, CELEBRATE, SULK, FINGER_UP
}

/** Face expressions. */
enum class Expr {
    CONTENT, HAPPY, EXCITED, CURIOUS, SLEEPY, BORED, SURPRISED, ANNOYED, EMBARRASSED,
    SAD, SUSPICIOUS, NERVOUS, PROUD, MISCHIEF, CLOSED, FOCUSED, LOVE,
    WINK, DIZZY, LAUGH, WORRIED
}

enum class EmoteKind { ZZZ, QUESTION, EXCLAIM, NOTES, HEART, SPARKLE, SWEAT, ANGER, DOTS, IDEA }

/** Nonverbal sounds. */
enum class Sfx { BEEP, LAUGH, SIGH, SURPRISED, SLEEPY, HAPPY, GRUMBLE, BOOP, WIN, LOSE, YAWN, SERVO, GIGGLE, HMM }

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
    }

    fun activityExpr(a: ActivityType, mood: Mood): Expr = when (a) {
        ActivityType.SLEEP -> Expr.CLOSED
        ActivityType.BUILD, ActivityType.EXPERIMENT, ActivityType.WORK_COMPUTER -> Expr.FOCUSED
        ActivityType.READ -> Expr.FOCUSED
        ActivityType.EXPLORE, ActivityType.EXAMINE, ActivityType.INSPECT_PLANT, ActivityType.THINK -> Expr.CURIOUS
        ActivityType.PLAY_ARCADE, ActivityType.PLAY_TOY, ActivityType.DANCE, ActivityType.SCROLL_PHONE, ActivityType.PLAY_CONSOLE -> Expr.HAPPY
        ActivityType.PREPARE_SURPRISE, ActivityType.REARRANGE -> Expr.MISCHIEF
        ActivityType.CHARGE -> Expr.CONTENT
        else -> moodExpr(mood)
    }

    fun moodSfx(m: Mood): Sfx = when (m) {
        Mood.SLEEPY -> Sfx.SLEEPY
        Mood.GRUMPY -> Sfx.GRUMBLE
        Mood.BORED, Mood.LONELY -> Sfx.SIGH
        Mood.EXCITED, Mood.HAPPY, Mood.PROUD -> Sfx.HAPPY
        Mood.MISCHIEVOUS -> Sfx.LAUGH
        Mood.NERVOUS, Mood.EMBARRASSED -> Sfx.SURPRISED
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
