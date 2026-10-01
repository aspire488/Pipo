package com.pipo.robot.ui.render

import com.pipo.robot.data.ItemShape
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr

/**
 * The boundary between Pipo's brain and whatever draws him.
 *
 * Everything a renderer needs for one frame, as plain values. The deterministic engine and the
 * HomeViewModel stay the only source of truth; a renderer (today the procedural 2.5D painters,
 * later the Blender-built 3D assets — see docs/3D_PIPELINE.md) only *reads* this. It never decides
 * where Pipo is, what he's doing or what's in the room.
 *
 * Units: world x in "u" along the room (0 … SceneGeo.WORLD_W), heights in u above the floor,
 * yaw in radians (0 = facing the camera, +turns to his left), look in −1..1.
 */
data class VisualFrame(
    val pipo: PipoVisual?,
    val nib: NibVisual?,
    val room: RoomState,
    /** Camera: left edge of the view (u) and zoom (1 = no dolly). */
    val cameraU: Float,
    val cameraZoom: Float,
)

data class PipoVisual(
    val x: Float,
    val lift: Float,
    val yaw: Float,
    val headYaw: Float,
    val anim: AnimState,
    val expr: Expr,
    val lookX: Float,
    val lookY: Float,
    val blink: Float,
    val talking: Boolean,
    /** Mouth opening from the speech system, 0..1. */
    val talkLevel: Float,
    val holdItem: ItemShape?,
    val emote: EmoteKind?,
    val glow: Long,
    val inBed: Boolean,
    val hideSpot: HideSpot?,
)

data class NibVisual(val x: Float, val facing: Float, val anim: PetAnim, val lookX: Float, val lookY: Float, val blink: Float)

/**
 * Stable names shared with the Blender asset pipeline: animation clips and face shape keys are
 * named after these enums, so a 3D renderer can map state → clip without a lookup table drifting.
 */
object AssetNames {
    fun clip(a: AnimState) = "PIPO_" + a.name
    fun face(e: Expr) = "FACE_" + e.name
    fun nibClip(a: PetAnim) = "NIB_" + a.name
    fun prop(s: ItemShape) = "PROP_" + s.name
}
