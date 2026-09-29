package com.pipo.robot.engine

/**
 * Media apps Pipo knows how to talk to. Only apps actually installed on the phone count (checked
 * by the Android layer); anything else in your launcher can still be opened by name.
 */
object MediaApps {
    enum class Kind { MUSIC, VIDEO, PODCAST, BOOKS, SOCIAL }

    /** [search] is a deep link with `{q}`; [home] a link that opens a useful screen (e.g. Reels). */
    data class App(
        val id: String, val label: String, val kind: Kind, val packages: List<String>,
        val aliases: List<String>, val search: String? = null, val home: String? = null,
    )

    val all = listOf(
        App("youtube", "YouTube", Kind.VIDEO, listOf("com.google.android.youtube"), listOf("youtube", "yt"), "https://www.youtube.com/results?search_query={q}"),
        App("youtube_music", "YouTube Music", Kind.MUSIC, listOf("com.google.android.apps.youtube.music"), listOf("youtube music", "yt music"), "https://music.youtube.com/search?q={q}"),
        App("spotify", "Spotify", Kind.MUSIC, listOf("com.spotify.music"), listOf("spotify"), "spotify:search:{q}"),
        App("jiosaavn", "JioSaavn", Kind.MUSIC, listOf("com.jio.media.jiobeats"), listOf("jiosaavn", "jio saavn", "saavn"), "https://www.jiosaavn.com/search/{q}"),
        App("gaana", "Gaana", Kind.MUSIC, listOf("com.gaana"), listOf("gaana")),
        App("wynk", "Wynk Music", Kind.MUSIC, listOf("com.bsbportal.music"), listOf("wynk music", "wynk")),
        App("apple_music", "Apple Music", Kind.MUSIC, listOf("com.apple.android.music"), listOf("apple music"), "https://music.apple.com/search?term={q}"),
        App("amazon_music", "Amazon Music", Kind.MUSIC, listOf("com.amazon.mp3"), listOf("amazon music")),
        App("soundcloud", "SoundCloud", Kind.MUSIC, listOf("com.soundcloud.android"), listOf("soundcloud", "sound cloud"), "https://soundcloud.com/search?q={q}"),
        App("netflix", "Netflix", Kind.VIDEO, listOf("com.netflix.mediaclient"), listOf("netflix"), "https://www.netflix.com/search?q={q}"),
        App("hotstar", "JioHotstar", Kind.VIDEO, listOf("in.startv.hotstar"), listOf("jiohotstar", "jio hotstar", "hotstar", "disney plus", "disney+")),
        App("prime_video", "Prime Video", Kind.VIDEO, listOf("com.amazon.avod.thirdpartyclient"), listOf("prime video", "amazon prime", "prime")),
        App("minitv", "Amazon miniTV", Kind.VIDEO, listOf("com.amazon.minitv.android.app"), listOf("minitv", "mini tv")),
        App("xstream", "Airtel Xstream", Kind.VIDEO, listOf("tv.accedo.airtel.wynk"), listOf("airtel xstream", "xstream")),
        App("mx_player", "MX Player", Kind.VIDEO, listOf("com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro"), listOf("mx player", "mxplayer")),
        App("vlc", "VLC", Kind.VIDEO, listOf("org.videolan.vlc"), listOf("vlc")),
        App("google_tv", "Google TV", Kind.VIDEO, listOf("com.google.android.videos"), listOf("google tv", "play movies")),
        App("samsung_video", "Samsung Video", Kind.VIDEO, listOf("com.samsung.android.video"), listOf("samsung video")),
        App("podcasts", "Podcasts", Kind.PODCAST, listOf("com.google.android.apps.podcasts", "au.com.shiftyjelly.pocketcasts"), listOf("podcasts", "podcast app", "pocket casts")),
        App("audible", "Audible", Kind.BOOKS, listOf("com.audible.application"), listOf("audible", "audiobooks")),
        App("instagram", "Instagram", Kind.SOCIAL, listOf("com.instagram.android"), listOf("instagram reels", "insta reels", "reels", "instagram", "insta"), home = "https://www.instagram.com/reels/"),
    )

    /** Longest alias first, so "youtube music" wins over "youtube". */
    private val byAlias = all.flatMap { a -> a.aliases.map { it to a } }.sortedByDescending { it.first.length }

    /** The media app a request mentions, and the alias used. */
    fun mentioned(s: String): Pair<App, String>? = byAlias.firstOrNull { (alias, _) -> Regex("\\b${Regex.escape(alias)}\\b").containsMatchIn(s) }?.let { it.second to it.first }

    /** "watch stranger things on netflix" → "stranger things". */
    fun query(s: String, alias: String): String = s.replace(alias, " ")
        .replace(Regex("\\b(play|put on|watch|listen to|stream|search for|search|find|open|launch|start|go to|on|in|some|me|a|the|app|please|pipo)\\b"), " ")
        .replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun link(template: String, q: String) = template.replace("{q}", java.net.URLEncoder.encode(q, "UTF-8"))

    /** "Spotify, YouTube and Netflix" / "Spotify, YouTube, Netflix and 4 more". */
    fun listLine(labels: List<String>): String = when {
        labels.isEmpty() -> "I can't find any music or video apps. Just me, then. I can hum."
        labels.size == 1 -> "You have ${labels[0]}. Just the one. Classic."
        labels.size <= 4 -> "You have ${labels.dropLast(1).joinToString(", ")} and ${labels.last()}. Fancy."
        else -> "You have ${labels.take(3).joinToString(", ")} and ${labels.size - 3} more. So many buttons."
    }
}
