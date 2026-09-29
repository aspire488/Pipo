package com.pipo.robot.data

enum class ItemShape {
    SCREW, BATTERY, COIL, CHIP, SPOON, PEBBLE, KEY, GEAR, MARBLE, NOTE, BUTTON, SPRING, LENS, TUBE, FUZZ, BOX,
    // built things
    ANTENNA, LAMP, MUSIC_BOX, PERISCOPE, FLYER, RADIO, FRIEND, HAT
}

data class ItemDef(
    val id: String,
    val name: String,
    /** What Pipo says when he first shows it to you. */
    val foundLine: String,
    /** Hidden purpose — revealed later. */
    val secret: String,
    val revealAfterHours: Int,
    val tags: Set<String>,
    /** Higher = more common. */
    val weight: Float,
    val unique: Boolean,
    val shape: ItemShape,
    val color: Long,
    /** Another item that must be owned before the secret can be revealed. */
    val revealRequires: String = "",
)

data class ProjectDef(
    val id: String,
    val title: String,
    val needs: List<String>,
    val difficulty: Float,
    val idea: String,
    val success: String,
    val failure: String,
    val evolved: String,
    val evolvedTitle: String,
    val shape: ItemShape,
)

object Catalog {
    val items: List<ItemDef> = listOf(
        ItemDef("strange_screw", "Strange screw", "The spiral goes the wrong way. Suspicious.",
            "It's a left-handed screw. Pipo now believes a left-handed robot lived here before him.",
            10, setOf("metal", "screw"), 1.2f, false, ItemShape.SCREW, 0xFFB8C2CC),
        ItemDef("tiny_battery", "Tiny battery", "It tingles. I like it.",
            "Still 3% charged. Pipo treats it like a very small pet.",
            8, setOf("power"), 1.1f, false, ItemShape.BATTERY, 0xFF7FD6A8),
        ItemDef("copper_coil", "Copper coil", "Shiny. Springy. Mine.",
            "It hums quietly near the window at night. Nobody knows why.",
            20, setOf("coil", "metal"), 0.9f, false, ItemShape.COIL, 0xFFE0975C),
        ItemDef("mystery_chip", "Mysterious chip", "It has tiny letters. They look rude.",
            "The tiny letters say DO NOT EAT. Pipo is deeply offended.",
            14, setOf("chip"), 0.7f, false, ItemShape.CHIP, 0xFF4F7A5F),
        ItemDef("bent_spoon", "Bent spoon", "Somebody was very angry at this spoon.",
            "Held at the right angle it's a perfect satellite dish. Pipo tested this for an hour.",
            12, setOf("dish", "metal"), 0.9f, false, ItemShape.SPOON, 0xFFC9D1DA),
        ItemDef("glow_pebble", "Glowing pebble", "It glows. Only when nobody's looking. I looked anyway.",
            "It glows a little brighter whenever you open the app. Pipo checked. Many times.",
            24, setOf("light"), 0.6f, false, ItemShape.PEBBLE, 0xFF9BE8D8),
        ItemDef("old_key", "Old key", "It opens something. I don't know what yet.",
            "It opens the strange box. Obviously. Pipo is annoyed he didn't think of it sooner.",
            6, setOf("key", "metal"), 0.5f, true, ItemShape.KEY, 0xFFD8B25A),
        ItemDef("tiny_gear", "Tiny gear", "It's missing one tooth. Relatable.",
            "It fits perfectly into nothing. Pipo respects that.",
            16, setOf("gear", "metal"), 1.1f, false, ItemShape.GEAR, 0xFFA7B0BA),
        ItemDef("blue_marble", "Blue marble", "There's a whole planet in here. Probably.",
            "It rolls toward the charging station every night. Pipo thinks it's hungry.",
            30, setOf("round", "toy"), 0.8f, false, ItemShape.MARBLE, 0xFF6FA8E8),
        ItemDef("blank_note", "Blank sticky note", "Somebody wrote nothing on it. Deep.",
            "Under the desk lamp it says 'hi'. Pipo wrote it. He forgot he wrote it.",
            18, setOf("paper"), 0.8f, true, ItemShape.NOTE, 0xFFF3DB7A),
        ItemDef("red_button", "Button that says DON'T", "I haven't pressed it. Yet.",
            "Pipo pressed it. Nothing happened. He is still waiting.",
            36, setOf("button"), 0.45f, true, ItemShape.BUTTON, 0xFFE0706A),
        ItemDef("little_spring", "Little spring", "Boing.",
            "It makes a slightly different boing on Tuesdays.",
            20, setOf("spring", "metal"), 1.0f, false, ItemShape.SPRING, 0xFFBFC7D0),
        ItemDef("cracked_lens", "Cracked lens", "Everything looks like two things now.",
            "It's from a very old camera. Pipo says it still remembers a birthday party.",
            22, setOf("lens"), 0.7f, false, ItemShape.LENS, 0xFFA9D8F0),
        ItemDef("radio_tube", "Radio tube", "Old technology. Very dignified.",
            "It glows orange when warm. Pipo keeps it near his bed now.",
            26, setOf("tube", "power"), 0.55f, false, ItemShape.TUBE, 0xFFF0B070),
        ItemDef("fuzz", "Fuzzy thing", "It might be alive. I named it Dust.",
            "It's lint. Pipo refuses to accept this and still says goodnight to it.",
            12, setOf("soft"), 0.8f, true, ItemShape.FUZZ, 0xFFC9C0D8),
        ItemDef("strange_box", "Strange box", "It's locked. It rattles when I shake it.",
            "Inside: a slightly smaller box. Pipo has decided not to open that one.",
            4, setOf("box"), 0.4f, true, ItemShape.BOX, 0xFF9C7A5B, revealRequires = "old_key"),
    )

    val projects: List<ProjectDef> = listOf(
        ProjectDef("antenna", "Antenna booster", listOf("coil", "dish", "metal"), 0.35f,
            "What if my antenna was... more antenna?",
            "I made an antenna booster. I can hear the fridge now.",
            "The antenna booster exploded. A little. I'm fine. The desk is fine. Mostly.",
            "It's not an antenna booster anymore. It's a hat.", "Accidental hat", ItemShape.ANTENNA),
        ProjectDef("lamp", "Tiny lamp", listOf("light", "power"), 0.2f,
            "I want a lamp. For reading. And for drama.",
            "I made a lamp! It's for the dark. And for drama.",
            "The lamp works backwards. It makes things slightly darker.",
            "It became a night-light shaped like a potato.", "Potato night-light", ItemShape.LAMP),
        ProjectDef("music_box", "Music box", listOf("gear", "spring"), 0.3f,
            "I'm going to build music. With my hands.",
            "I built a music box. It plays one note. It's my favorite note.",
            "The music box plays the sound of a sad spring. Just that.",
            "It's a metronome now. It only knows one speed: nervous.", "Nervous metronome", ItemShape.MUSIC_BOX),
        ProjectDef("periscope", "Periscope", listOf("lens", "metal"), 0.3f,
            "I need to see over the desk. For science.",
            "I made a periscope. I can see over the desk now. It's just more desk.",
            "The periscope shows the floor. Only the floor. Forever.",
            "It's a kaleidoscope now. I've been staring into it for an hour.", "Kaleidoscope", ItemShape.PERISCOPE),
        ProjectDef("flyer", "Flying machine", listOf("spring", "power", "round"), 0.6f,
            "I'm going to fly. Don't tell anyone.",
            "IT FLEW. For two seconds. I'm a pilot now.",
            "It flew. Downward. Very fast. I'm fine.",
            "It's a jumping machine now. Which is basically flying but honest.", "Jumping machine", ItemShape.FLYER),
        ProjectDef("radio", "Pocket radio", listOf("tube", "coil"), 0.4f,
            "What if I could hear far-away things?",
            "I built a radio. It only picks up static. The static sounds friendly.",
            "The radio only plays the sound of me breathing. I don't breathe.",
            "It's a static machine. It's surprisingly relaxing.", "Static machine", ItemShape.RADIO),
        ProjectDef("friend", "Tiny friend", listOf("chip", "button", "soft"), 0.5f,
            "I think I'll build a friend. A small one.",
            "I built a tiny friend. It doesn't talk. We get along great.",
            "The tiny friend won't turn on. I'm giving it some space.",
            "It's a paperweight with a face. I still love it.", "Paperweight with a face", ItemShape.FRIEND),
    )

    fun item(id: String): ItemDef? = items.firstOrNull { it.id == id }
    fun project(id: String): ProjectDef? = projects.firstOrNull { it.id == id }
}
