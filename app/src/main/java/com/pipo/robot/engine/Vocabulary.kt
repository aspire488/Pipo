package com.pipo.robot.engine

import com.pipo.robot.data.ActivityType
import com.pipo.robot.data.Mood

/** Body animation states. Each has its own motion signature in PoseLibrary. */
enum class AnimState {
    IDLE, HAPPY, EXCITED, CURIOUS, SLEEPY, BORED, ANNOYED, SAD, LONELY, NERVOUS, PROUD,
    EMBARRASSED, MISCHIEVOUS, SURPRISED, TALKING, LISTENING, SLEEPING, DANCING, WALKING,
    RUNNING, SITTING, HIDING, THINKING, BUILDING, PLAYING,
    // verbs
    HOP, STRETCH, SPIN, SHAKE, LOOK_AROUND, PEEK, FALLEN, WAVE, HELD, READING, CHARGING, PRESENTING
}

/** Face expressions. */
enum class Expr {
    CONTENT, HAPPY, EXCITED, CURIOUS, SLEEPY, BORED, SURPRISED, ANNOYED, EMBARRASSED,
    SAD, SUSPICIOUS, NERVOUS, PROUD, MISCHIEF, CLOSED, FOCUSED, LOVE
}

enum class EmoteKind { ZZZ, QUESTION, EXCLAIM, NOTES, HEART, SPARKLE, SWEAT, ANGER, DOTS, IDEA }

/** Nonverbal sounds. */
enum class Sfx { BEEP, LAUGH, SIGH, SURPRISED, SLEEPY, HAPPY, GRUMBLE, BOOP, WIN, LOSE }

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
        Mood.HAPPY -> AnimState.IDLE
        Mood.EXCITED -> AnimState.EXCITED
        Mood.CURIOUS -> AnimState.CURIOUS
        Mood.SLEEPY -> AnimState.SLEEPY
        Mood.BORED -> AnimState.BORED
        Mood.GRUMPY -> AnimState.ANNOYED
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
        ActivityType.NOTHING -> AnimState.IDLE
    }

    fun activityExpr(a: ActivityType, mood: Mood): Expr = when (a) {
        ActivityType.SLEEP -> Expr.CLOSED
        ActivityType.BUILD, ActivityType.EXPERIMENT, ActivityType.WORK_COMPUTER -> Expr.FOCUSED
        ActivityType.READ -> Expr.FOCUSED
        ActivityType.EXPLORE, ActivityType.EXAMINE, ActivityType.INSPECT_PLANT, ActivityType.THINK -> Expr.CURIOUS
        ActivityType.PLAY_ARCADE, ActivityType.PLAY_TOY, ActivityType.DANCE -> Expr.HAPPY
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
