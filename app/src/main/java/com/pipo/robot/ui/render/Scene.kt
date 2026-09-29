package com.pipo.robot.ui.render

import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.Station
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
        const val WORLD_W = 242f
        const val BED_PIVOT = 34f
        const val MATTRESS_TOP = 14f
        /** The TV + console stand under the window (world u). */
        const val TV_L = 107f
        const val TV_R = 121f

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
            else -> 104f
        }

        data class Obj(val id: String, val l: Float, val r: Float, val bottom: Float, val top: Float)

        /** Tappable objects in world u (x range, height above floor range). */
        val objects = listOf(
            Obj("bed", 3f, 50f, -6f, 30f), Obj("plant", 55f, 67f, -4f, 28f), Obj("charger", 74f, 97f, -6f, 22f),
            Obj("window", 86f, 122f, 38f, 76f), Obj("desk", 123f, 157f, -4f, 36f), Obj("drawings", 8f, 46f, 48f, 70f),
            Obj("shelf", 160f, 202f, 52f, 64f), Obj("workbench", 160f, 202f, -4f, 46f), Obj("arcade", 205f, 225f, -4f, 48f),
            Obj("toys", 226f, 242f, -8f, 20f), Obj("clock", 56f, 68f, 54f, 66f),
            Obj("console", 106f, 122f, -4f, 16f),
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

data class RoomState(
    val hour: Float,
    val pranks: Set<String> = emptySet(),
    val drawings: Int = 0,
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

    /** A bird crossing the window every ~47 s in daytime. Returns progress 0..1 or null. */
    fun bird(t: Float): Float? {
        val ph = (t % 47f) / 3.2f
        return if (ph < 1f) ph else null
    }

    fun birdX(progress: Float) = 90f + progress * 28f
}
