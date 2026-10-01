package com.pipo.robot.ui.render

import com.pipo.robot.data.DrawSubject
import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.PhotoSubject
import com.pipo.robot.data.Station
import com.pipo.robot.engine.Weather
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Screen geometry. World positions are in "u" (1u = 1% of a phone's width). */
class SceneGeo(val w: Float, val h: Float) {
    // Tall phones (19.5:9 and up) show a slightly narrower slice of the room at a larger scale;
    // at 100u wide the room only filled the top half and left a band of empty floor.
    val u = min(w / 88f, h / 185f)
    val floorY = h * 0.64f
    /** Baseline for near-camera props: in the floor band between Pipo and the bottom controls. */
    val foregroundY = floorY + (h - floorY) * 0.5f
    val viewU = w / u
    val pipoH = 40f * u
    val pipoFootY = floorY + 5f * u
    fun sx(worldU: Float, camU: Float) = (worldU - camU) * u
    fun maxCam() = (WORLD_W - viewU).coerceAtLeast(0f)

    companion object {
        /** The room grew a kitchenette and a front door (schema 2); everything left of 242u is where it always was. */
        const val WORLD_W = 296f
        const val BED_PIVOT = 34f
        const val MATTRESS_TOP = 14f
        /** The TV + console stand under the window (world u). */
        const val TV_L = 107f
        const val TV_R = 121f
        const val FRIDGE_L = 245f
        const val COUNTER_L = 256f
        const val COUNTER_R = 271f
        const val DOOR_L = 279f
        const val DOOR_R = 293f
        const val DOOR_X = 286f
        const val BOX_L = 267f
        const val BOX_R = 277f
        const val BOX_H = 10f
        const val ARCADE_HIDE_X = 215f

        fun station(s: Station): Float = when (s) {
            Station.BED -> 56f
            Station.PLANT -> 71f
            Station.CHARGER -> 84f
            Station.WINDOW, Station.RUG -> 104f
            Station.CONSOLE -> 92f      // on the rug, beside and facing the TV so you can see the game
            Station.DESK -> 138f
            Station.SHELF, Station.WORKBENCH -> 181f
            Station.ARCADE -> 215f
            Station.TOYS -> 225f
            Station.KITCHEN -> 262f
            Station.DOOR -> DOOR_X
            else -> 104f
        }

        data class Obj(val id: String, val l: Float, val r: Float, val bottom: Float, val top: Float)

        /** Tappable objects in world u (x range, height above floor range). Earlier entries win overlaps. */
        val objects = listOf(
            Obj("bed", 3f, 50f, -6f, 30f), Obj("plant", 55f, 67f, -4f, 28f), Obj("charger", 74f, 97f, -6f, 22f),
            Obj("window", 86f, 122f, 38f, 76f), Obj("corkboard", 126f, 156f, 39f, 58f), Obj("desk", 123f, 157f, -4f, 36f), Obj("drawings", 8f, 46f, 48f, 70f),
            Obj("drawings", 70f, 84f, 36f, 58f),
            Obj("shelf", 160f, 202f, 52f, 64f), Obj("workbench", 160f, 202f, -4f, 46f), Obj("arcade", 205f, 225f, -4f, 48f),
            Obj("toys", 226f, 242f, -8f, 20f), Obj("clock", 56f, 68f, 54f, 66f),
            Obj("console", 106f, 122f, -4f, 16f),
            Obj("box", BOX_L, BOX_R, -4f, BOX_H + 1f),
            Obj("kitchen", 244f, 272f, -4f, 34f), Obj("door", 276f, 295f, -4f, 58f),
        )

        fun prankX(key: String) = when (key) {
            "plant_hat" -> 61f; "lamp_sock", "screen_note" -> 140f; "ball_on_bed" -> 22f
            "arcade_score" -> 215f; "screw_tower" -> 196f; "ball_behind_plant" -> 66f; "clock_sideways" -> 62f; else -> 120f
        }
        fun prankObject(key: String) = when (key) {
            "plant_hat" -> "plant"; "lamp_sock", "screen_note" -> "desk"; "ball_on_bed" -> "bed"
            "arcade_score" -> "arcade"; "screw_tower" -> "workbench"; "ball_behind_plant" -> "plant"; "clock_sideways" -> "clock"; else -> ""
        }
    }
}

/** A drawing on his wall: what it's of, and a seed so it always looks the same. */
data class WallDrawing(val subject: DrawSubject, val seed: Int)

/** A photo pinned to the corkboard. */
data class PinnedPhoto(val subject: PhotoSubject, val seed: Int, val placeColor: Long, val night: Boolean, val weather: Weather, val anomaly: Boolean, val ref: String)

/** Where he's hiding, if he is. The painter draws the thing he's hiding behind in front of him. */
enum class HideSpot { ARCADE, BOX, BLANKET }

data class RoomState(
    val hour: Float,
    val pranks: Set<String> = emptySet(),
    val drawings: List<WallDrawing> = emptyList(),
    val shelf: List<ItemShape> = emptyList(),
    val benchThing: ItemShape? = null,
    val projectActive: Boolean = false,
    val charging: Boolean = false,
    val battery: Int = 100,
    val arcadeActive: Boolean = false,
    /** Pipo is playing on his console: the TV shows the game. */
    val consoleActive: Boolean = false,
    val ballU: Float = 233f,
    val torch: Boolean = false,
    /** 0..1, leaves shake when Pipo brushes past the plant. */
    val plantRustle: Float = 0f,
    val computerActive: Boolean = false,
    val benchActive: Boolean = false,
    val music: Boolean = false,
    /** Phone tilt (-1..1), used for parallax depth. */
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    // ---- schema 2: weather, the kitchen, the door, and the evidence of a life
    val weather: Weather = Weather.CLEAR,
    val weatherAmt: Float = 0.5f,
    val photos: List<PinnedPhoto> = emptyList(),
    val pantry: List<ItemShape> = emptyList(),
    val built: List<ItemShape> = emptyList(),
    val box: Boolean = false,
    val bag: Boolean = false,
    val plate: Boolean = false,
    val smoke: Boolean = false,
    val cooking: Boolean = false,
    val floorItem: ItemShape? = null,
    val ballByDoor: Boolean = false,
    /** One pennant per place he's actually been (colour of the place), oldest first. */
    val pennants: List<Long> = emptyList(),
    /** Today on his wall calendar. */
    val calDay: Int = 1,
    val calMonth: String = "",
    /** The festival it is (the room dresses up). */
    val festival: com.pipo.robot.engine.Festival? = null,
    /** Where you are in the year (and whether winter there means bare trees). */
    val season: com.pipo.robot.engine.Season = com.pipo.robot.engine.Season.SUMMER,
    val coldWinter: Boolean = false,
    /** His builder level (1..5): the workshop slowly turns into a lab. */
    val builderLevel: Int = 1,
    /** B.O.L.T., the helper screen on his desk, if he built it. */
    val helper: Boolean = false,
    val petInBed: Boolean = false,
    val stolenGlint: Boolean = false,
    val umbrella: Boolean = false,
    /** 0 closed … 1 wide open. */
    val doorOpen: Float = 0f,
    /** He's out: there's a note on the door. */
    val doorNote: Boolean = false,
    val feeder: Boolean = false,
    val telescope: Boolean = false,
    val book: Boolean = false,
    val coins: Int = 0,
    /** Ball in the air (kick-ups), u above the floor. */
    val ballLift: Float = 0f,
    val reflectionUneasy: Boolean = false,
    /** 0..1 lightning brightness this frame. */
    val flash: Float = 0f,
    /** He owns the brass token: it sits at the end of the shelf. [tokenAwake] = it glows at night (the thread has started). */
    val token: Boolean = false,
    val tokenAwake: Boolean = false,
    /** 0 closed … 1 open: he's getting something out of the fridge. */
    val fridgeOpen: Float = 0f,
    /** The actual parts on the workbench for the project he's building (not generic props). */
    val benchParts: List<ItemShape> = emptyList(),
    /** Bits of a project that failed, on the floor by the bench. */
    val scraps: ItemShape? = null,
    /** The screw from the other side, on the shelf next to the token. */
    val mirrorScrew: Boolean = false,
)

fun dayFactor(hour: Float): Float = when {
    hour < 5f -> 0f
    hour < 8f -> (hour - 5f) / 3f
    hour < 17.5f -> 1f
    hour < 20.5f -> 1f - (hour - 17.5f) / 3f
    else -> 0f
}


/**
 * Tiny living things in the room. Deterministic functions of time so the painter draws them
 * and Pipo's attention system can look at exactly the same spot.
 */
object Critters {
    const val LAMP_X = 150.5f
    const val LAMP_H = 30f

    /** A moth circling the desk lamp at night. World (x, height above floor) in u. */
    fun moth(t: Float): Pair<Float, Float> {
        val x = LAMP_X + sin(t * 1.3f) * 7f + sin(t * 3.1f) * 2f
        val h = LAMP_H + cos(t * 1.7f) * 5f + sin(t * 4.3f) * 1.5f
        return x to h
    }

    /** A bird crossing the window every [period] s in daytime (more often with a feeder). Progress 0..1 or null. */
    fun bird(t: Float, period: Float = 47f): Float? {
        val ph = (t % period) / 3.2f
        return if (ph < 1f) ph else null
    }

    const val STORM_PERIOD = 19f

    /** Lightning during a storm: a flash and a flicker every ~19 s. Deterministic, so sound and light agree. */
    fun lightning(t: Float): Float {
        val ph = t % STORM_PERIOD
        return when { ph < 0.09f -> 1f; ph in 0.17f..0.27f -> 0.65f; else -> 0f }
    }

    fun birdX(progress: Float) = 90f + progress * 28f
}
