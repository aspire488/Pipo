package com.pipo.robot.engine

import kotlin.random.Random

/**
 * Pipo noticing your chat/social notifications ("ooh, someone sent you a reel!").
 *
 * Privacy: this is opt-in (Android's Notification access, which only you can switch on). The
 * listener classifies a notification into an [App] and a coarse [Kind] ON THE DEVICE and passes
 * on nothing else. Pipo never sees, says, stores or logs who it's from or what it says, and he
 * never opens, replies to or dismisses anything.
 */
object PhoneNotifs {

    enum class App(val label: String, val packages: Set<String>) {
        WHATSAPP("WhatsApp", setOf("com.whatsapp", "com.whatsapp.w4b")),
        INSTAGRAM("Instagram", setOf("com.instagram.android")),
        TELEGRAM("Telegram", setOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
        MESSAGES("Messages", setOf("com.google.android.apps.messaging", "com.samsung.android.messaging")),
        SNAPCHAT("Snapchat", setOf("com.snapchat.android")),
        MESSENGER("Messenger", setOf("com.facebook.orca")),
        DISCORD("Discord", setOf("com.discord")),
        SIGNAL("Signal", setOf("org.thoughtcrime.securesms")),
    }

    enum class Kind { MESSAGE, REEL, POST, PHOTO, VIDEO, VOICE, LIKE, COMMENT, STORY, FOLLOW, CALL }

    data class Event(val app: App, val kind: Kind)

    fun appFor(pkg: String): App? = App.entries.firstOrNull { pkg in it.packages }

    /**
     * Coarse kind from the notification's own words. [text] is used here and thrown away.
     * [isCall] = the notification's category is CATEGORY_CALL.
     */
    fun classify(app: App, text: String, isCall: Boolean = false): Kind {
        val t = text.lowercase().trim()
        fun has(vararg w: String) = w.any { it in t }
        fun starts(vararg w: String) = w.any { t.startsWith(it) }
        if (isCall || has("incoming call", "incoming video call", "incoming voice call", "is calling you", "calling you…", "calling you...")) return Kind.CALL
        val social = app == App.INSTAGRAM || app == App.SNAPCHAT || app == App.MESSENGER
        if (social) when {
            // app-generated phrasing ("x sent you a reel", "x liked your photo")
            has("sent you a reel", "sent a reel", "shared a reel") -> return Kind.REEL
            has("liked your", "reacted to your") -> return Kind.LIKE
            has("commented", "replied to your comment", "mentioned you in a comment") -> return Kind.COMMENT
            has("started following", "followed you", "follow request") -> return Kind.FOLLOW
            has("your story", "their story", "added to their story", "mentioned you in") -> return Kind.STORY
            has("sent you a post", "shared a post", "sent a post") -> return Kind.POST
            has("sent you a snap", "sent a snap", "sent you a photo", "sent a photo") -> return Kind.PHOTO
            has("sent you a video", "sent a video") -> return Kind.VIDEO
            has("sent you a voice message", "sent a voice message", "sent an audio") -> return Kind.VOICE
        }
        // Chat apps: only the attachment marker the app puts at the START of the preview
        // ("📷 Photo", "🎤 Voice message (0:04)"), never words inside someone's actual message.
        return when {
            starts("🎤", "voice message", "audio") -> Kind.VOICE                   // 🎤
            starts("🎥", "📹", "video") -> Kind.VIDEO                     // 🎥 📹
            starts("📷", "🖼", "photo", "image") -> Kind.PHOTO            // 📷 🖼
            else -> Kind.MESSAGE
        }
    }

    /** What Pipo says about a small burst of notifications (never who, never what). */
    fun line(events: List<Event>, rng: Random): String {
        val apps = events.map { it.app }.distinct()
        if (apps.size > 1) return pick(rng,
            "Ding! Ding! Your phone is very popular right now.",
            "Ooh, lots of buzzes! ${apps[0].label} AND ${apps[1].label}!",
            "So many dings! Everybody wants you.")
        val app = apps[0].label
        if (events.any { it.kind == Kind.CALL }) return pick(rng,
            "Someone's calling you on $app! Go go go!",
            "Ring ring! It's a $app call!")
        if (events.size >= 3) return pick(rng,
            "Whoa, lots of $app messages! Somebody's chatty.",
            "Ding ding ding! $app won't stop!",
            "$app keeps buzzing! Is it a party?")
        return when (events.last().kind) {
            Kind.MESSAGE -> pick(rng,
                "Ooh! Someone texted you on $app!",
                "Bzz! A $app message! Who is it? ...I won't peek.",
                "Your phone went ding! $app! Someone's thinking about you.")
            Kind.REEL -> pick(rng,
                "Ooh, someone sent you a reel! Is it funny? It's probably funny.",
                "A reel! On $app! Can I watch too?",
                "Someone sent a reel! I love reels. I think.")
            Kind.POST -> pick(rng, "Someone shared a post with you on $app!", "Ooh, a $app post! For you!")
            Kind.PHOTO -> pick(rng, "Someone sent you a picture on $app! I love pictures.", "Ooh, a photo! On $app!")
            Kind.VIDEO -> pick(rng, "A video! On $app! Ooh.", "Someone sent you a video! Is there a cat in it?")
            Kind.VOICE -> pick(rng, "Someone sent a voice message! A tiny voice in your phone.", "A $app voice note! Somebody's talking to you!")
            Kind.LIKE -> pick(rng, "Someone liked your thing on $app! You're famous!", "Ooh, a like! On $app! Yay!")
            Kind.COMMENT -> pick(rng, "Ooh, someone commented on $app!", "A comment! On $app! People are talking!")
            Kind.STORY -> pick(rng, "$app story stuff! Ooh!", "Something about a story on $app!")
            Kind.FOLLOW -> pick(rng, "A new friend on $app! Someone followed you!", "Someone followed you! Are they nice?")
            Kind.CALL -> "Someone's calling you on $app!"
        }
    }

    private fun pick(rng: Random, vararg lines: String) = lines[rng.nextInt(lines.size)]
}
