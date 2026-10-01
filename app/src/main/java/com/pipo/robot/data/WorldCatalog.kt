package com.pipo.robot.data

/*
 * Pipo's world outside the room: places, the people who run them, food and animals.
 * Static data only. What happens there is decided by the engine (engine/Trips.kt etc.).
 */

enum class PlaceKind { HOME, SHOP, OUTDOOR, INDOOR, HIDDEN }

data class Place(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    /** One-way travel time in minutes, as Pipo walks. */
    val travelMin: Int,
    val opens: Int = 0,
    val closes: Int = 24,
    val npc: String = "",
    /** Catalog ids of items / foods sold here. */
    val sells: List<String> = emptyList(),
    /** Species you can meet here. */
    val wildlife: List<String> = emptyList(),
    /** Discovery tags that turn up more here. */
    val findTags: Set<String> = emptySet(),
    /** Map position (0..1). */
    val mapX: Float,
    val mapY: Float,
    val color: Long,
    /** How he'd describe it before he has an opinion. */
    val blurb: String,
) {
    val outdoor get() = kind == PlaceKind.OUTDOOR
    fun openAt(hour: Int) = if (opens <= closes) hour in opens until closes else hour >= opens || hour < closes
}

object Places {
    val all = listOf(
        Place("garden", "The garden", PlaceKind.OUTDOOR, 3, wildlife = listOf("sparrow", "butterfly", "bee", "frog", "firefly", "cat"),
            findTags = setOf("soft", "round", "light"), mapX = 0.40f, mapY = 0.58f, color = 0xFF7FB77E, blurb = "Right outside. Grass, a hedge, a bee who thinks it owns the place."),
        Place("park", "The park", PlaceKind.OUTDOOR, 12, wildlife = listOf("squirrel", "sparrow", "cat", "butterfly"),
            findTags = setOf("key", "round", "metal"), mapX = 0.24f, mapY = 0.40f, color = 0xFF8CC08A, blurb = "Benches. Squirrels. A slide he's scared of."),
        Place("field", "The football field", PlaceKind.OUTDOOR, 10, npc = "kip", wildlife = listOf("sparrow"),
            findTags = setOf("round"), mapX = 0.62f, mapY = 0.36f, color = 0xFF9ACB7A, blurb = "Two goals. One of them leans."),
        Place("hardware", "Grumble's Hardware", PlaceKind.SHOP, 9, 8, 19, npc = "grumble",
            sells = listOf("screws", "wire", "sticks", "magnet", "springs"), mapX = 0.56f, mapY = 0.62f, color = 0xFFB8865A, blurb = "Every screw in the world. Grumble knows each one."),
        Place("electronics", "Juniper Electronics", PlaceKind.SHOP, 14, 9, 20, npc = "juniper",
            sells = listOf("motor", "battery_pack", "led", "propeller"), mapX = 0.70f, mapY = 0.56f, color = 0xFF6FA8C9, blurb = "Blinking lights. It hums. He loves it."),
        Place("market", "Pim's Market", PlaceKind.SHOP, 8, 7, 21, npc = "pim",
            sells = listOf("noodles", "egg", "tomato", "rice", "honey", "milk", "apple", "banana", "juice", "cheese", "chocolate", "umbrella"),
            mapX = 0.46f, mapY = 0.74f, color = 0xFFE0A86A, blurb = "Food. Also umbrellas, for some reason."),
        Place("bakery", "Mo's Bakery", PlaceKind.SHOP, 7, 6, 17, npc = "mo",
            sells = listOf("bread", "croissant", "cookie", "cake_slice"), mapX = 0.33f, mapY = 0.70f, color = 0xFFE8C07A, blurb = "It smells like a hug."),
        Place("cafe", "The Corner Café", PlaceKind.SHOP, 8, 8, 22, npc = "ada",
            sells = listOf("hot_chocolate", "pancakes", "pizza_slice"), mapX = 0.28f, mapY = 0.56f, color = 0xFFC99A7A, blurb = "Hot chocolate the size of his head."),
        Place("secondhand", "Old Rook's Things", PlaceKind.SHOP, 16, 10, 18, npc = "rook",
            sells = listOf("magnifier", "cardboard_tube", "felt", "rubber_duck"), findTags = setOf("tube", "lens", "chip", "box"),
            mapX = 0.80f, mapY = 0.72f, color = 0xFF9C8AB0, blurb = "Everything in here used to be something else."),
        Place("repair", "Fennel Fix-It", PlaceKind.SHOP, 11, 9, 18, npc = "fennel",
            mapX = 0.66f, mapY = 0.78f, color = 0xFF8FA3A6, blurb = "Fennel fixes things. Sometimes Pipo helps. For coins."),
        Place("library", "The Library", PlaceKind.INDOOR, 13, 10, 19, npc = "tess",
            mapX = 0.16f, mapY = 0.62f, color = 0xFFA69070, blurb = "Quiet. He whispers the whole time. Even outside."),
        Place("lake", "The lake", PlaceKind.OUTDOOR, 25, wildlife = listOf("frog", "duck", "firefly", "sparrow"),
            findTags = setOf("round", "lens", "light"), mapX = 0.14f, mapY = 0.28f, color = 0xFF6FA8C9, blurb = "Flat and shiny. The frogs have opinions."),
        Place("hills", "The hills", PlaceKind.OUTDOOR, 35, wildlife = listOf("sparrow", "butterfly"),
            findTags = setOf("metal", "coil", "gear"), mapX = 0.48f, mapY = 0.12f, color = 0xFF9DB08A, blurb = "Windy. You can see the whole town. It's small."),
        // ---- not on the map until he finds them
        Place("observatory", "The old dome", PlaceKind.HIDDEN, 45, mapX = 0.74f, mapY = 0.10f, color = 0xFFB0A3C9,
            findTags = setOf("lens", "coil"), blurb = "A round building with a hole in the roof. He dreamed it first."),
        Place("otherside", "???", PlaceKind.HIDDEN, 50, mapX = 0.90f, mapY = 0.20f, color = 0xFF9FF3E0,
            blurb = "The same street. Not the same street."),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun sellersOf(catalogId: String) = all.filter { catalogId in it.sells }
}

data class Npc(
    val id: String,
    val name: String,
    val placeId: String,
    /** First time they meet. */
    val hello: String,
    /** Regular visits, picked by what's going on. */
    val lines: List<String>,
    /** When Pipo keeps coming back. */
    val friendly: List<String>,
)

/** A handful of neighbours. They're small robots too — this is Pipo's scale of the world. */
object Npcs {
    val all = listOf(
        Npc("grumble", "Grumble", "hardware", "Grumble looked at him for a long time. Then said: \"Screws are aisle two.\"",
            listOf("Grumble said the screw was fine. The screw was not fine.", "Grumble sighed at him. It was a friendly sigh. Probably.", "Grumble told him about washers for eleven minutes."),
            listOf("Grumble saved him the good springs.", "Grumble almost smiled. Pipo is sure of it.")),
        Npc("juniper", "Juniper", "electronics", "Juniper blinked all her lights at him. That's hello, apparently.",
            listOf("Juniper showed him a motor that hums a song.", "Juniper said motors like to be talked to.", "Juniper let him press a button. Just one."),
            listOf("Juniper asked how the flying thing was going.", "Juniper gave him a free sticker. It says VOLTAGE.")),
        Npc("pim", "Mrs. Pim", "market", "Mrs. Pim said he was too small to carry a basket. He carried it anyway.",
            listOf("Mrs. Pim gave him the shiniest apple.", "Mrs. Pim asked if he was eating properly.", "Mrs. Pim said the noodles were on sale. She was right."),
            listOf("Mrs. Pim knows what he likes now.", "Mrs. Pim waved at him from across the market.")),
        Npc("mo", "Mo", "bakery", "Mo was covered in flour. So is Pipo now.",
            listOf("Mo let him smell the bread. For free.", "Mo said the croissants were shy today.", "Mo gave him a crumb. A big crumb."),
            listOf("Mo said he's the best customer. He's probably the smallest.", "Mo kept a cookie for him.")),
        Npc("ada", "Ada", "cafe", "Ada gave him a mug bigger than his head. He loved it.",
            listOf("Ada drew a heart in the foam.", "Ada asked him to taste a new thing. It was spicy.", "Ada hummed the whole time."),
            listOf("Ada has a stool just for him now.", "Ada remembered his order.")),
        Npc("rook", "Old Rook", "secondhand", "Old Rook said everything here used to be something else. Including Rook.",
            listOf("Old Rook told him a story about a lamp that was once a boat.", "Old Rook said: take your time. Things find you.", "Old Rook dusted him by accident."),
            listOf("Old Rook put something aside for him. He won't say what.", "Old Rook said he has a good eye.")),
        Npc("fennel", "Fennel", "repair", "Fennel handed him a screwdriver before saying hi.",
            listOf("Fennel let him fix a toaster. It toasts now. Mostly.", "Fennel said his wiring was neat. He's framing that sentence.", "Fennel paid him in coins and advice."),
            listOf("Fennel called him 'the apprentice'.", "Fennel trusted him with the tiny screws.")),
        Npc("tess", "Tess", "library", "Tess whispered hello. He whispered it back. Too loud.",
            listOf("Tess found him a book about bridges.", "Tess shushed him. Twice. Kindly.", "Tess said the book about stars was overdue since 1987."),
            listOf("Tess keeps a book aside for him every week.", "Tess let him stamp the date. He stamped it very well.")),
        Npc("kip", "Kip", "field", "Kip kicked the ball to him. He missed it. Kip was nice about it.",
            listOf("Kip showed him a trick. He almost did it.", "Kip said he runs like a toaster. He's taking it as a compliment.", "Kip was in goal. Pipo scored. Kip says it didn't count."),
            listOf("Kip waits for him on Saturdays.", "Kip picked him first. FIRST.")),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

enum class FoodKind { READY, INGREDIENT, DISH }

data class Food(
    val id: String,
    val name: String,
    val kind: FoodKind,
    val price: Int,
    val shape: ItemShape,
    val warm: Boolean = false,
    /** Ingredients that can also be eaten straight. */
    val edible: Boolean = kind != FoodKind.INGREDIENT,
)

data class Recipe(val id: String, val needs: List<String>, val tricky: Float)

object Foods {
    val all = listOf(
        Food("apple", "apple", FoodKind.READY, 1, ItemShape.APPLE),
        Food("banana", "banana", FoodKind.READY, 1, ItemShape.BANANA),
        Food("juice", "juice", FoodKind.READY, 2, ItemShape.JUICE),
        Food("chocolate", "chocolate", FoodKind.READY, 1, ItemShape.CHOCOLATE),
        Food("cookie", "cookie", FoodKind.READY, 1, ItemShape.COOKIE),
        Food("croissant", "croissant", FoodKind.READY, 2, ItemShape.CROISSANT),
        Food("cake_slice", "slice of cake", FoodKind.READY, 3, ItemShape.CAKE),
        Food("hot_chocolate", "hot chocolate", FoodKind.READY, 2, ItemShape.MUG, warm = true),
        Food("pancakes", "pancakes", FoodKind.READY, 3, ItemShape.PLATE, warm = true),
        Food("pizza_slice", "pizza", FoodKind.READY, 3, ItemShape.PIZZA, warm = true),
        Food("bread", "bread", FoodKind.INGREDIENT, 2, ItemShape.BREAD, edible = true),
        Food("cheese", "cheese", FoodKind.INGREDIENT, 2, ItemShape.CHEESE, edible = true),
        Food("noodles", "noodles", FoodKind.INGREDIENT, 2, ItemShape.NOODLES),
        Food("egg", "egg", FoodKind.INGREDIENT, 1, ItemShape.EGG),
        Food("tomato", "tomato", FoodKind.INGREDIENT, 1, ItemShape.TOMATO, edible = true),
        Food("rice", "rice", FoodKind.INGREDIENT, 2, ItemShape.RICE),
        Food("honey", "honey", FoodKind.INGREDIENT, 2, ItemShape.HONEY),
        Food("milk", "milk", FoodKind.INGREDIENT, 1, ItemShape.MILK, edible = true),
        // dishes: only exist by cooking them
        Food("noodle_soup", "noodle soup", FoodKind.DISH, 0, ItemShape.NOODLES, warm = true),
        Food("honey_toast", "honey toast", FoodKind.DISH, 0, ItemShape.BREAD, warm = true),
        Food("pizza_toast", "pizza toast", FoodKind.DISH, 0, ItemShape.PIZZA, warm = true),
        Food("tomato_rice", "tomato rice", FoodKind.DISH, 0, ItemShape.RICE, warm = true),
        Food("omelette", "omelette", FoodKind.DISH, 0, ItemShape.EGG, warm = true),
        Food("fruit_salad", "fruit salad", FoodKind.DISH, 0, ItemShape.BOWL),
    )

    val recipes = listOf(
        Recipe("noodle_soup", listOf("noodles", "egg"), 0.25f),
        Recipe("honey_toast", listOf("bread", "honey"), 0.1f),
        Recipe("pizza_toast", listOf("bread", "tomato", "cheese"), 0.3f),
        Recipe("tomato_rice", listOf("rice", "tomato"), 0.35f),
        Recipe("omelette", listOf("egg", "cheese"), 0.4f),
        Recipe("fruit_salad", listOf("apple", "banana"), 0.05f),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun recipe(id: String) = recipes.firstOrNull { it.id == id }
}

enum class Daytime { DAY, NIGHT, DUSK, ANY }

data class Species(
    val id: String,
    val name: String,
    val plural: String,
    val daytime: Daytime,
    /** null = any weather; otherwise the weather names it likes. */
    val weather: Set<String>? = null,
    /** Can be seen from Pipo's window. */
    val window: Boolean = false,
    val looks: List<String>,
)

object Wildlife {
    val species = listOf(
        Species("sparrow", "sparrow", "sparrows", Daytime.DAY, window = true,
            looks = listOf("a sparrow with a crooked tail", "a very round sparrow", "a sparrow that hops sideways")),
        Species("butterfly", "butterfly", "butterflies", Daytime.DAY, setOf("CLEAR", "CLOUDY", "WINDY"), window = true,
            looks = listOf("a yellow butterfly", "a butterfly with one torn wing")),
        Species("bee", "bee", "bees", Daytime.DAY, setOf("CLEAR", "CLOUDY"), looks = listOf("a fat bumblebee", "a bee that bumps into everything")),
        Species("frog", "frog", "frogs", Daytime.ANY, setOf("RAIN", "CLOUDY", "FOG", "CLEAR"), looks = listOf("a frog with a loud opinion", "a tiny green frog")),
        Species("squirrel", "squirrel", "squirrels", Daytime.DAY, looks = listOf("a squirrel with half a tail", "a squirrel who stares")),
        Species("cat", "cat", "cats", Daytime.ANY, window = true, looks = listOf("a grey cat with one white paw", "an orange cat who ignores everyone")),
        Species("firefly", "firefly", "fireflies", Daytime.NIGHT, setOf("CLEAR", "CLOUDY"), looks = listOf("a firefly that blinks in threes")),
        Species("duck", "duck", "ducks", Daytime.DAY, looks = listOf("a duck that follows other ducks", "a duck with a wonky quack")),
        Species("moth", "moth", "moths", Daytime.NIGHT, window = true, looks = listOf("the lamp moth")),
    )

    /** Names he gives the ones he keeps seeing. */
    val names = listOf("Pebble", "Sir Flap", "Toast", "Gus", "Button", "Mabel", "Noodle", "Crumb", "Captain", "Biscuit", "Dot", "Fern", "Ziggy", "Mr. Wobble")

    fun byId(id: String) = species.firstOrNull { it.id == id }
}
