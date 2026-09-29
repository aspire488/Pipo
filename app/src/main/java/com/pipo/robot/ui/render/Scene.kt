package com.pipo.robot.ui.render

import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.Station
import kotlin.math.min

/** Screen geometry. World positions are in "u" (1u = 1% of a phone's width). */
class SceneGeo(val w: Float, val h: Float) {
    val u = min(w / 100f, h / 185f)
    val floorY = h * 0.62f
    val viewU = w / u
    val pipoH = 40f * u
    val pipoFootY = floorY + 5f * u
    fun sx(worldU: Float, camU: Float) = (worldU - camU) * u
    fun maxCam() = (WORLD_W - viewU).coerceAtLeast(0f)

    companion object {
        const val WORLD_W = 242f
        const val BED_PIVOT = 30f
        const val MATTRESS_TOP = 14f

        fun station(s: Station): Float = when (s) {
            Station.BED -> 56f
            Station.PLANT -> 71f
            Station.CHARGER -> 84f
            Station.WINDOW, Station.RUG -> 104f
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
        )

        fun prankX(key: String) = when (key) {
            "plant_hat" -> 61f; "lamp_sock", "screen_note" -> 140f; "ball_on_bed" -> 22f
            "arcade_score" -> 215f; "screw_tower" -> 196f; else -> 120f
        }
        fun prankObject(key: String) = when (key) {
            "plant_hat" -> "plant"; "lamp_sock", "screen_note" -> "desk"; "ball_on_bed" -> "bed"
            "arcade_score" -> "arcade"; "screw_tower" -> "workbench"; else -> ""
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
    val ballU: Float = 233f,
    val torch: Boolean = false,
)

fun dayFactor(hour: Float): Float = when {
    hour < 5f -> 0f
    hour < 8f -> (hour - 5f) / 3f
    hour < 17.5f -> 1f
    hour < 20.5f -> 1f - (hour - 17.5f) / 3f
    else -> 0f
}

