package com.pipo.robot.engine

import com.pipo.robot.data.Npcs
import com.pipo.robot.data.PipoState
import com.pipo.robot.data.Place
import com.pipo.robot.data.TripPurpose
import com.pipo.robot.data.TripState
import kotlin.random.Random

/**
 * Real conversations out in the world: Pipo and whoever is there (Grumble at the hardware shop,
 * Coach Raj at the cricket ground...), each in their own voice, about what's actually going on —
 * the part he came for, the project it's for, the match, the weather, how often he's been, and
 * (for the ones who know things) the mirror. No neighbour there? Then it's Pipo and Nib.
 *
 * The script is fixed per trip (seeded by its id), so the live peek and the story he tells when
 * he's home agree.
 */
object Talks {
    /** Who says it: the neighbour, Pipo, or Nib. */
    enum class Who { NPC, PIPO, NIB }
    data class Line(val who: Who, val text: String)

    private class Voice(val hi: List<String>, val again: List<String>, val bye: List<String>, val small: List<String>)

    private val voices = mapOf(
        "grumble" to Voice(listOf("Hmph. Screws are aisle two.", "Don't touch the springs."), listOf("You again. Hmph. ...good.", "Back already. What broke?"),
            listOf("Mind the step.", "Don't come back. ...Come back Tuesday."), listOf("Washers. Nobody respects washers.", "In my day robots built things with ONE screw.")),
        "juniper" to Voice(listOf("Ooh! A customer! *blink blink*", "Welcome to Juniper Electronics! Everything lights up!"), listOf("Pipo!! My favourite small customer!", "You're back! I saved you a blinky thing!"),
            listOf("Bye! Blink twice if it works!", "Come back when it explodes! Kidding! Mostly!"), listOf("I put lights on my lights.", "Everything's better with an LED. Everything.")),
        "pim" to Voice(listOf("Oh, look at you! So small!", "Hello, dear. Mind the plums."), listOf("Pipo, dear! Have you eaten? You don't eat. Have a plum anyway.", "My favourite little robot!"),
            listOf("Take an apple for Nib. I know. Take it anyway.", "Wrap up warm, dear."), listOf("The tomatoes are lovely today.", "My knees say rain.")),
        "mo" to Voice(listOf("Fresh out the oven! Careful! Hot hot hot!", "Welcome! You've got flour on you already."), listOf("Pipo! The usual? You don't have a usual. Croissant.", "My best customer! My smallest customer!"),
            listOf("Don't eat it all at once! You can't eat it at all! Still!", "Come back tomorrow, there's cinnamon."), listOf("I got up at four for this bread.", "The secret is butter. The other secret is more butter.")),
        "ada" to Voice(listOf("Hi! Sit anywhere. That mug's bigger than you. Perfect.", "Welcome to the Corner Café."), listOf("Pipo! Your seat's free. By the window.", "There he is! Hot chocolate? You can just hold it."),
            listOf("Same time tomorrow?", "Bye, Pipo! Say hi to Nib!"), listOf("Busy morning. Everyone wants foam hearts.", "I named the coffee machine. It's called Greg.")),
        "rook" to Voice(listOf("Ah. A new face. Everything here has a story.", "Look, don't touch. Touch a little."), listOf("Ah, the young one. Back for more stories?", "I kept something for you. Somewhere."),
            listOf("Mind how you go. And mind the mirrors.", "Off you go. Things find their way back."), listOf("This lamp was in a lighthouse. Or a lamp shop.", "Old things remember. That's the trouble.")),
        "fennel" to Voice(listOf("What'd you break? Show me.", "Welcome. Wrench is mine. Don't touch the wrench."), listOf("Pipo! What'd you break THIS time?", "My best customer. Broken things love you."),
            listOf("Tighten it righty-tighty.", "Come back when it falls apart."), listOf("Everything breaks. That's job security.", "I fixed a toaster today. It thanked me. I think.")),
        "tess" to Voice(listOf("Shh. ...Hi. Shh.", "*whispers* Welcome to the library."), listOf("*whispers* Pipo! I saved you a book.", "*whispers* Back already? Good."),
            listOf("*whispers* Bring it back on Friday.", "*whispers* Bye. Shh."), listOf("*whispers* Someone dog-eared a page. I'm devastated.", "*whispers* This one's about space. You'll like it.")),
        "kip" to Voice(listOf("Hey! You play? Pass it here!", "New guy! You're on my team!"), listOf("PIPO! Two-touch, no hands!", "You came! We need a keeper. You're short. Perfect."),
            listOf("Same time tomorrow!", "Good game! You missed everything! Good game though!"), listOf("I scored with my head once. It hurt.", "Nib keeps stealing the ball. Nib's a natural.")),
        "raj" to Voice(listOf("Grip's wrong. Here. Like this.", "Welcome to the ground. Watch the ball."), listOf("Pipo! Pads on. Eyes on the ball.", "Ah, my cover-drive student!"),
            listOf("Well played. Mostly well left.", "Practise in your room. Shadow bat."), listOf("Patience. Cricket is patience.", "Well left! ...you swung at that. Never mind.")),
        "lin" to Voice(listOf("Ready? I don't miss. Fair warning.", "New player! Paddle's over there."), listOf("Pipo! Rematch? You'll lose. Nicely.", "Back for more spin?"),
            listOf("Good rally. You almost had one.", "Thursday. Bring Nib, Nib's scary."), listOf("Topspin. It's all topspin.", "I returned forty in a row once. It was a Tuesday.")),
        "bea" to Voice(listOf("Hi! I keep score. Smileys for points.", "Want to play? The net sags. It's fine."), listOf("Pipo! You've got eleven smileys so far this month.", "My favourite opponent! Shady side's yours."),
            listOf("Bye! I'll draw you a smiley for coming.", "Low and short on the serve. Like you!"), listOf("The shuttle is NOT a bird. Tell Nib.", "I drew a smiley on the net. Don't tell anyone.")),
    )

    fun script(s: PipoState, trip: TripState, place: Place): List<Line> {
        val rng = Random(trip.id)
        val npc = Npcs.byId(place.npc)
        val out = mutableListOf<Line>()
        fun n(t: String) { out += Line(Who.NPC, t) }
        fun p(t: String) { out += Line(Who.PIPO, t) }
        fun b(t: String) { if (trip.withPet) out += Line(Who.NIB, t) }
        val proj = s.activeProject()
        val item = trip.shoppingList.firstOrNull()?.let { Economy.nameOf(it) }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (npc == null) {
            // nobody here: Pipo and Nib, out together
            p(listOf("Look, Nib! ${place.name}!", "Nice out here. Right, Nib?", "Nib. Stay close.").random(rng))
            b(listOf("Ooh! Big!", "Nib likes here!", "Smells green!").random(rng))
            p(when (trip.purpose) {
                TripPurpose.WILDLIFE -> "Shh. If we're quiet, something might come out."
                TripPurpose.WALK, TripPurpose.EXPLORE -> "Which way? You pick. ...not into the pond."
                TripPurpose.INVESTIGATE -> "This is where the blinking came from. I think."
                else -> "Okay. Let's do this."
            })
            b(listOf("Nib leads!", "That way! No, THAT way!", "Nib not scared. Little scared.").random(rng))
            return out
        }
        val v = voices[npc.id]
        val nm = s.npcs[npc.id]
        val visits = nm?.visits ?: 0
        val fond = (nm?.fondness ?: 0.3f) > 0.55f
        // hello
        n(if (v == null) "Hello, Pipo." else if (visits == 0) v.hi.random(rng) else v.again.random(rng))
        p(if (visits == 0) listOf("Hi! I'm Pipo. This is Nib. Nib bites. Not hard.", "Hello! I'm Pipo! I'm new. To here. To most things.").random(rng).let { if (trip.withPet) it else it.replace(" This is Nib. Nib bites. Not hard.", "") }
            else listOf("Hi, ${npc.name}!", "Hello again, ${npc.name}!", "${npc.name}! It's me! Pipo!").random(rng))
        // what he came for
        when (trip.purpose) {
            TripPurpose.SHOP -> {
                if (item != null) {
                    n(when (npc.id) { "grumble" -> "What's the $item for. Not another flying thing."; "juniper" -> "A $item! For what? Tell me tell me!"; "rook" -> "A $item. Hm. I might have one. In a box. In a box."; else -> "A $item? What's it for?" })
                    if (proj != null) {
                        p("It's for my ${proj.title.lowercase()}! It's going to be amazing. Or explode.")
                        if (proj.templateId == "helper") { p("It's called Bolt. It's going to be my J.A.R.V.I.S."); n(when (npc.id) { "grumble" -> "Your what. ...Hmph. Fine. Take the good wire."; "juniper" -> "A robot that helps a robot! I LOVE it!"; else -> "Well! Good luck with Bolt." }) }
                        else n(when (npc.id) { "grumble" -> "Hmph. Bring it in when it breaks."; "juniper" -> "Add lights. Trust me. Lights."; else -> "Ooh. Show me when it's done." })
                    } else { p("Just in case. Inventors keep spares."); n("Smart. Very smart.") }
                } else { n("Looking for anything special?"); p("Just looking. Looking is free. Right?") }
            }
            TripPurpose.FOOD -> {
                n(when (npc.id) { "pim" -> "The ${item ?: "apples"} are lovely today."; "mo" -> "${(item ?: "bread").replaceFirstChar { it.uppercase() }}? Still warm."; "ada" -> "What can I get you?"; else -> "What'll it be?" })
                p(if (item != null) "${item.replaceFirstChar { it.uppercase() }}, please! I'm going to cook. Maybe. Probably." else "Something nice! For the fridge!")
                b(listOf("And Nib! Nib wants!", "Snack for Nib?", "Nib smells it!").random(rng))
                n(when (npc.id) { "pim" -> "Here's one for Nib. I know. Take it anyway."; "mo" -> "One for the little one too."; else -> "There you go. Enjoy!" })
            }
            TripPurpose.CRICKET -> { n("Pads on. Watch the ball, not Nib."); p("I'm watching! I'm watching the ball!"); b("Nib bowls! Nib bowls FAST!"); n("Nib's action is... unique. Well left!") }
            TripPurpose.FOOTBALL -> { n("Pipo in goal, Nib up front!"); p("Why am I in goal? ...because I'm short. Okay."); b("GOAL! Nib goal!"); n("That was a handball. Nib doesn't have hands. Allowed.") }
            TripPurpose.TABLE_TENNIS -> { n("First to eleven. I'll go easy. I won't."); p("I've been practising! On the wall! The wall won."); b("Nib ball boy!"); n("Nice serve! It went in the bin, but nice serve.") }
            TripPurpose.BADMINTON -> { n("Low serve. Low and short."); p("Like me! Low and short!"); b("Bird! BIRD!"); n("It's not a bird, Nib. It's a shuttle. ...fine, it's a bird.") }
            TripPurpose.LIBRARY -> { n("*whispers* Looking for anything?"); p("*whispers* Something about space. Or robots. Or robots in space."); n("*whispers* Aisle three. Bring it back Friday.") }
            TripPurpose.ODD_JOB -> { n("Hold this. Don't let go. Unless it's hot."); p("Is it hot? It's hot. It's FINE."); n("Good work. Here's your coins.") }
            else -> { n(v?.small?.random(rng) ?: "Lovely day."); p(listOf("Really?", "Wow.", "I didn't know that.").random(rng)) }
        }
        // weather and small talk
        val rain = WeatherEngine.at(s.seed, System.currentTimeMillis()).kind.name.contains("RAIN")
        if (rain) { n("Wet one out there."); p("I'm waterproof! Mostly. Partly.") }
        else if (v != null && rng.nextFloat() < 0.6f) { n(v.small.random(rng)); p(listOf("Ha! Really?", "I'll remember that.", "That's the best thing I've heard today.").random(rng)) }
        // the ones who know about the other world
        if (s.mystery.stage >= 1 && npc.id in setOf("rook", "tess", "juniper") && rng.nextFloat() < 0.8f) {
            p("${npc.name}... can a mirror be a beat behind you?")
            n(when (npc.id) {
                "rook" -> "Hm. Some mirrors go both ways, young one. Mind which side you're on."
                "tess" -> "*whispers* There's a book about that. Someone always has it out. Always."
                else -> "Ooh. Maybe it's lagging! Mirrors don't lag. ...do they?"
            })
            b("Nib TOLD Pipo!")
        }
        if (hour >= 18 && rng.nextFloat() < 0.5f) n("Getting late. Home before dark, little one.")
        // bye
        n(v?.bye?.random(rng) ?: "Bye, Pipo!")
        p(if (fond) "Bye, ${npc.name}! See you soon! Very soon!" else "Bye, ${npc.name}!")
        return out
    }

    /** The bit he'll mention when he's home ("Grumble asked what the wire was for."). */
    fun recap(s: PipoState, trip: TripState, place: Place): String? {
        val npc = Npcs.byId(place.npc) ?: return null
        val lines = script(s, trip, place)
        val said = lines.drop(2).firstOrNull { it.who == Talks.Who.NPC }?.text ?: return null
        return "${npc.name} said: \"$said\""
    }
}
