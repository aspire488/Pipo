package com.pipo.robot.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pipo.robot.data.ItemShape
import com.pipo.robot.data.Place
import com.pipo.robot.data.PlaceKind
import com.pipo.robot.data.TripPurpose
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.Weather
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pipo out in the world: a small painted place (sky, far hills, the place itself, ground,
 * foreground) and Pipo doing the thing he went there for, with real props — the ball is at his
 * feet and flies at the goal, the butterfly is what he's photographing, Nib trots behind him.
 *
 * Everything is in normalised scene units: x 0..1 across, ground line at [GROUND].
 */
class OutsideDirector(val placeId: String, val purpose: TripPurpose, val withPet: Boolean, seed: Int = 5) {
    val rig = PipoRig(seed)
    val pet = PetRig(seed + 9)
    var t = 0f
        private set
    var pipoX = 0.3f; var petX = 0.15f
    var ballX = 0.36f; var ballY = 0f   // ballY = height above ground (0..)
    var critterX = 0.7f; var critterY = 0.55f
    private var facing = 1f

    init { rig.energy = 0.9f }

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        t += dt
        nibDriven = false
        when (purpose) {
            TripPurpose.FOOTBALL -> football()
            TripPurpose.CRICKET -> cricket()
            TripPurpose.TABLE_TENNIS -> tableTennis()
            TripPurpose.BADMINTON -> badminton()
            TripPurpose.WILDLIFE -> wildlife()
            TripPurpose.SHOP, TripPurpose.FOOD -> shopping()
            TripPurpose.LIBRARY -> { pipoX = 0.42f; rig.anim = AnimState.READING; rig.expr = Expr.FOCUSED; rig.holdItem = null; rig.bodyVel = 0f }
            TripPurpose.ODD_JOB -> { pipoX = 0.42f; rig.anim = AnimState.BUILDING; rig.expr = Expr.FOCUSED; rig.holdItem = ItemShape.GEAR; rig.bodyVel = 0f }
            else -> stroll()
        }
        // Nib: stays near him, a step behind, and does its own small thing when he stops
        // (in a match, the sport moves Nib itself: it's the other player)
        if (withPet && !nibDriven) {
            val target = if (purpose == TripPurpose.FOOTBALL) ballX - 0.04f * facing else pipoX - 0.09f * facing
            val d = target - petX
            val moving = abs(d) > 0.01f
            petX += d.coerceIn(-0.35f * dt, 0.35f * dt) * (if (purpose == TripPurpose.FOOTBALL) 1.6f else 1f)
            pet.anim = if (moving) (if (abs(d) > 0.08f) PetAnim.RUN else PetAnim.WALK) else if ((t % 9f) < 3f) PetAnim.STARE else PetAnim.WAG
            pet.facing = if (moving) (if (d > 0) 1f else -1f) else pet.facing
            if (!moving) pet.look((pipoX - petX) * 4f, -0.4f)
        }
        rig.update(dt); pet.update(dt)
    }

    private var nibDriven = false

    /** Nib, playing: moves to [x] at its own little speed, facing [face], hopping when it hits. */
    private fun nibAt(x: Float, face: Float, hit: Boolean = false, speed: Float = 0.4f) {
        nibDriven = true
        val d = x - petX
        petX += d.coerceIn(-speed * 0.05f, speed * 0.05f)
        pet.facing = if (abs(d) > 0.01f) (if (d > 0) 1f else -1f) else face
        pet.anim = when { hit -> PetAnim.HOP; abs(d) > 0.06f -> PetAnim.RUN; abs(d) > 0.01f -> PetAnim.WALK; else -> PetAnim.WAG }
        pet.look((ballX - petX) * 5f, -0.3f - ballY * 4f)
    }

    /** Point-by-point: who won the last point (for faces), cycling so both win some. */
    private fun pointWinner(len: Float): Boolean = ((t / len).toInt() * 7 + 3) % 5 < 3 // Pipo wins 3 in 5

    private fun walkTo(x: Float, speed: Float = 0.12f): Boolean {
        val d = x - pipoX
        if (abs(d) < 0.004f) { rig.bodyVel = 0f; rig.moveDir = 0f; return true }
        val step = d.coerceIn(-speed * 0.05f, speed * 0.05f)
        pipoX += step
        facing = if (d > 0) 1f else -1f
        rig.moveDir = facing; rig.bodyVel = speed * 300f
        rig.anim = if (speed > 0.16f) AnimState.RUNNING else AnimState.WALKING
        return false
    }

    /** Football, two players: he dribbles and shoots at Nib in goal; Nib saves (or doesn't) and dribbles it back. */
    private fun football() {
        val c = t % 9f
        val goal = pointWinner(9f)
        rig.holdItem = null
        when {
            c < 2.5f -> { // dribble towards the goal: the ball stays at his feet
                walkTo(0.3f + c / 2.5f * 0.25f, 0.1f); rig.expr = Expr.FOCUSED
                ballX = pipoX + 0.035f; ballY = abs(sin(c * 9f)) * 0.008f
                if (withPet) nibAt(0.86f, -1f)
            }
            c < 3.0f -> { rig.anim = AnimState.KICKUPS; rig.expr = Expr.EXCITED; rig.bodyVel = 0f; ballX = pipoX + 0.035f; ballY = 0f; if (withPet) nibAt(0.86f, -1f) }
            c < 4.2f -> { // the shot; Nib dives
                val k = (c - 3.0f) / 1.2f
                ballX = 0.59f + k * (if (goal || !withPet) 0.31f else 0.24f); ballY = sin(k * Math.PI.toFloat()) * 0.12f
                rig.anim = AnimState.HOP; rig.expr = Expr.EXCITED; rig.bodyVel = 0f
                if (withPet) nibAt(if (goal) 0.84f else ballX + 0.01f, -1f, hit = k > 0.6f, speed = 0.9f)
            }
            c < 5.6f -> {
                rig.anim = if (goal) AnimState.CELEBRATE else AnimState.SIGH; rig.expr = if (goal) Expr.EXCITED else Expr.BORED; ballY = 0f
                if (withPet) { nibAt(petX, -1f, hit = !goal); if (c < 3.5f) pet.feel(if (goal) PetFace.SAD else PetFace.SMUG, 2f) }
            }
            else -> { // Nib dribbles it back to him (or he fetches it, if Nib stayed home)
                if (withPet) {
                    val k = ((c - 5.6f) / 3.4f).coerceIn(0f, 1f)
                    ballX = 0.83f - k * 0.47f; ballY = abs(sin(c * 10f)) * 0.006f
                    nibAt(ballX + 0.03f, -1f, speed = 0.6f)
                    walkTo(0.3f, 0.1f); if (abs(pipoX - 0.3f) < 0.01f) { rig.anim = AnimState.CHEERFUL; rig.lookAt(0.6f, 0.3f, 1f) }
                } else if (c < 7.4f) { walkTo(0.85f, 0.2f); ballY = 0f } else { walkTo(0.3f, 0.14f); ballX = pipoX + 0.035f * facing; ballY = 0f }
            }
        }
    }

    /** Cricket: Nib bowls (nudges the ball with its nose), Pipo bats; a big hit, a miss, Nib fetches. */
    private fun cricket() {
        val c = t % 7f
        val out = !pointWinner(7f)
        pipoX = 0.3f; rig.holdItem = ItemShape.BAT; rig.bodyVel = 0f; facing = 1f
        when {
            c < 1.2f -> { // the delivery: one bounce, towards the bat
                val k = c / 1.2f
                ballX = 0.74f - k * 0.4f; ballY = abs(sin(k * Math.PI.toFloat() * 1.5f)) * 0.05f * (1f - k * 0.5f)
                rig.anim = AnimState.PRESENTING; rig.expr = Expr.FOCUSED
                if (withPet) nibAt(0.78f, -1f, hit = k < 0.15f)
            }
            c < 2.8f -> if (out) { // bowled! the ball sneaks past into the stumps
                ballX = 0.34f - (c - 1.2f).coerceAtMost(0.3f) * 0.3f; ballY = 0f
                rig.anim = if (c < 1.6f) AnimState.HOP else AnimState.SIGH; rig.expr = Expr.SURPRISED
                if (withPet) { nibAt(0.78f, -1f, hit = true); pet.feel(PetFace.SMUG, 1f) }
            } else { // a big hit: high over Nib's head
                val k = (c - 1.2f) / 1.6f
                ballX = 0.34f + k * 0.6f; ballY = sin(k * Math.PI.toFloat()) * 0.32f
                rig.anim = if (c < 1.6f) AnimState.HOP else AnimState.CELEBRATE; rig.expr = Expr.EXCITED
                if (withPet) { nibAt(0.78f, -1f); pet.feel(PetFace.SURPRISED, 1f) }
            }
            else -> { // Nib fetches it and trundles back to bowl again
                ballY = 0f
                if (!out) {
                    val k = ((c - 2.8f) / 4.2f)
                    if (withPet) { if (k < 0.5f) { nibAt(0.94f, 1f, speed = 0.6f); ballX = 0.94f } else { ballX = petX - 0.02f; nibAt(0.78f, -1f, speed = 0.5f) } }
                    else ballX = 0.94f - k * 0.2f
                } else { ballX = 0.27f; if (withPet) nibAt(0.78f, -1f) }
                rig.anim = AnimState.PRESENTING; rig.expr = Expr.CONTENT
            }
        }
    }

    /** Table tennis at the park table: a real rally over the net, Nib headbutting it back, a point every few hits. */
    private fun tableTennis() {
        val hitLen = 0.75f
        val n = (t / hitLen).toInt(); val k = (t % hitLen) / hitLen
        val toNib = n % 2 == 0
        val missed = n % 9 == 8
        pipoX = 0.27f; rig.holdItem = ItemShape.PADDLE; rig.bodyVel = 0f; facing = 1f
        val a = if (toNib) 0.31f else 0.69f; val b = if (toNib) 0.69f else 0.31f
        ballX = a + (b - a) * k
        // over the net, bouncing once on the far side
        ballY = 0.1f + (if (k < 0.6f) sin(k / 0.6f * Math.PI.toFloat()) * 0.07f else abs(sin((k - 0.6f) / 0.4f * Math.PI.toFloat())) * 0.035f)
        if (missed) { ballX = b + (if (toNib) 0.15f else -0.15f) * k; ballY = (0.1f - k * 0.1f).coerceAtLeast(0f) }
        rig.anim = if (!toNib && k > 0.85f) AnimState.HOP else AnimState.PRESENTING
        rig.expr = if (missed && !toNib) Expr.SURPRISED else if (missed) Expr.EXCITED else Expr.FOCUSED
        rig.lookAt(((ballX - pipoX) * 3f).coerceIn(-1f, 1f), -0.3f, 0.3f)
        if (withPet) {
            nibAt(0.74f, -1f, hit = toNib && k > 0.85f)
            if (missed) pet.feel(if (toNib) PetFace.SAD else PetFace.SMUG, 1f)
        }
    }

    /** Badminton over the garden net: long high shuttles, Nib leaping for every one. */
    private fun badminton() {
        val hitLen = 1.3f
        val n = (t / hitLen).toInt(); val k = (t % hitLen) / hitLen
        val toNib = n % 2 == 0
        pipoX = 0.3f; rig.holdItem = ItemShape.RACKET; rig.bodyVel = 0f; facing = 1f
        val a = if (toNib) 0.33f else 0.69f; val b = if (toNib) 0.69f else 0.33f
        ballX = a + (b - a) * k; ballY = 0.12f + sin(k * Math.PI.toFloat()) * 0.25f
        rig.anim = if (!toNib && k > 0.8f) AnimState.HOP else AnimState.PRESENTING; rig.expr = Expr.FOCUSED
        rig.lookAt(((ballX - pipoX) * 3f).coerceIn(-1f, 1f), -0.8f, 0.3f)
        if (withPet) nibAt(0.72f + sin(t) * 0.02f, -1f, hit = toNib && k > 0.75f)
    }

    /** A butterfly (or frog at the lake) he's trying to photograph: creep, crouch, snap. */
    private fun wildlife() {
        val c = t % 10f
        critterX = 0.66f + sin(t * 0.7f) * 0.06f
        critterY = if (placeId == "lake") 0f else 0.25f + sin(t * 2.3f) * 0.04f
        when {
            c < 3f -> { walkTo(0.48f, 0.05f); rig.expr = Expr.CURIOUS; rig.holdItem = null }
            c < 7.5f -> { rig.anim = AnimState.PHOTO; rig.expr = Expr.FOCUSED; rig.bodyVel = 0f; rig.lookAt(0.9f, if (placeId == "lake") 0.3f else -0.4f, 1f) }
            c < 8.6f -> { rig.anim = AnimState.HAPPY; rig.expr = Expr.EXCITED; rig.bodyVel = 0f }
            else -> { walkTo(0.4f, 0.06f) }
        }
    }

    /** Browsing the crates: walk, look, pick something up, into the bag. */
    private fun shopping() {
        val c = t % 11f
        when {
            c < 2f -> { walkTo(0.3f, 0.08f); rig.holdItem = ItemShape.BAG; rig.expr = Expr.CURIOUS }
            c < 4.5f -> { rig.anim = AnimState.CURIOUS; rig.bodyVel = 0f; rig.lookAt(-0.2f, 0.4f, 1f) }
            c < 6.5f -> { walkTo(0.58f, 0.08f) }
            c < 9f -> { rig.anim = AnimState.PRESENTING; rig.holdItem = if (placeId == "bakery") ItemShape.BREAD else if (placeId == "market") ItemShape.APPLE else ItemShape.SCREW; rig.expr = Expr.HAPPY; rig.bodyVel = 0f }
            else -> { rig.holdItem = ItemShape.BAG; walkTo(0.3f, 0.08f) }
        }
    }

    /** A walk: along the path, stop, look at the trees / the view, carry on. */
    private fun stroll() {
        val c = t % 12f
        rig.holdItem = null
        when {
            c < 4f -> walkTo(0.62f, 0.07f)
            c < 6.5f -> { rig.anim = AnimState.LOOK_AROUND; rig.expr = Expr.CURIOUS; rig.bodyVel = 0f }
            c < 10f -> walkTo(0.3f, 0.07f)
            else -> { rig.anim = AnimState.IDLE; rig.expr = Expr.CONTENT; rig.bodyVel = 0f; rig.lookAt(0f, -0.6f, 1f) }
        }
    }

    companion object { const val GROUND = 0.8f }
}

private fun shade(c: Color, night: Float) = lerp(c, Color(0xFF141C2E), night * 0.62f)

fun DrawScope.drawOutside(p: Place, d: OutsideDirector, hour: Float, weather: Weather, festival: com.pipo.robot.engine.Festival? = null) {
    val w = size.width; val h = size.height
    val day = dayFactor(hour)
    val night = 1f - day
    val dusk = (1f - abs(hour - 19f) / 2.2f).coerceIn(0f, 1f) + (1f - abs(hour - 6.5f) / 1.8f).coerceIn(0f, 1f)
    val grey = when (weather) { Weather.RAIN, Weather.STORM -> 0.5f; Weather.CLOUDY, Weather.FOG -> 0.28f; else -> 0f }
    val gy = h * OutsideDirector.GROUND
    val t = d.t

    // ---------------- sky
    val skyTop = lerp(lerp(Color(0xFF0B1430), Color(0xFF6FB2E3), day), Color(0xFF7D8A99), grey)
    val skyBot = lerp(lerp(lerp(Color(0xFF223463), Color(0xFFD6ECF5), day), Color(0xFFF2B58C), dusk * 0.7f), Color(0xFFAEB8C2), grey)
    drawRect(Brush.verticalGradient(listOf(skyTop, skyBot), 0f, gy))
    if (night > 0.5f && grey < 0.3f) for (i in 0 until 40) {
        val tw = 0.5f + 0.5f * sin(t * 1.7f + i * 2.1f)
        drawCircle(Color.White.copy(alpha = (night - 0.5f) * 1.6f * tw * 0.8f), 1.4f + (i % 3), Offset(((i * 83) % 100) / 100f * w, ((i * 47) % 60) / 100f * gy))
    }
    if (grey < 0.4f) {
        if (day > 0.3f) {
            val sx = w * (0.15f + ((hour - 6f) / 14f).coerceIn(0f, 1f) * 0.7f); val sy = h * 0.14f + abs(hour - 13f) * h * 0.025f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1C4).copy(alpha = 0.6f), Color.Transparent), Offset(sx, sy), w * 0.14f), w * 0.14f, Offset(sx, sy))
            drawCircle(Color(0xFFFFF4D6), w * 0.045f, Offset(sx, sy))
        } else {
            val mc = Offset(w * 0.78f, h * 0.15f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE6FF).copy(alpha = 0.35f), Color.Transparent), mc, w * 0.12f), w * 0.12f, mc)
            drawCircle(Color(0xFFF0F2FA), w * 0.035f, mc); drawCircle(skyTop, w * 0.03f, Offset(mc.x + w * 0.015f, mc.y - w * 0.008f))
        }
    }
    // clouds drift
    val cloud = lerp(lerp(Color.White, Color(0xFF3A4A6A), night * 0.8f), Color(0xFF9AA4AF), grey).copy(alpha = 0.85f)
    for (i in 0 until (if (grey > 0.2f) 6 else 3)) {
        val cx = (((i * 0.37f + t * 0.008f * (1 + i % 2)) % 1.3f) - 0.15f) * w; val cy = h * (0.1f + (i % 3) * 0.07f)
        val r = w * (0.05f + (i % 2) * 0.02f)
        drawCircle(cloud, r, Offset(cx, cy)); drawCircle(cloud, r * 0.8f, Offset(cx - r, cy + r * 0.2f)); drawCircle(cloud, r * 0.9f, Offset(cx + r * 0.9f, cy + r * 0.15f))
        drawRoundRect(cloud, Offset(cx - r * 1.6f, cy), Size(r * 3.3f, r * 0.75f), CornerRadius(r * 0.4f))
    }

    // ---------------- far hills (two hazy layers)
    val haze1 = shade(lerp(Color(0xFF9DB7B0), skyBot, 0.45f), night)
    val haze2 = shade(lerp(Color(0xFF7E9F86), skyBot, 0.2f), night)
    drawPath(Path().apply { moveTo(0f, gy * 0.72f); cubicTo(w * 0.2f, gy * 0.58f, w * 0.38f, gy * 0.7f, w * 0.55f, gy * 0.64f); cubicTo(w * 0.75f, gy * 0.56f, w * 0.9f, gy * 0.68f, w, gy * 0.62f); lineTo(w, gy); lineTo(0f, gy); close() }, haze1)
    drawPath(Path().apply { moveTo(0f, gy * 0.84f); cubicTo(w * 0.25f, gy * 0.76f, w * 0.5f, gy * 0.88f, w * 0.7f, gy * 0.8f); cubicTo(w * 0.85f, gy * 0.75f, w * 0.95f, gy * 0.82f, w, gy * 0.8f); lineTo(w, gy); lineTo(0f, gy); close() }, haze2)

    // ---------------- the place itself (mid layer)
    val grass = shade(when (p.id) { "field" -> Color(0xFF6FAE5C); "hills" -> Color(0xFF8DAE6A); "lake" -> Color(0xFF79A86A); else -> Color(0xFF79AE68) }, night)
    when {
        p.id == "sports_hall" || p.id == "cricket" || p.id == "court" -> Unit // painted with the ground, below
        p.kind == PlaceKind.SHOP || p.kind == PlaceKind.INDOOR || p.id == "repair" -> drawShopfront(p, gy, night, t)
        p.id == "field" && d.purpose == TripPurpose.CRICKET -> drawStumps(gy, night)
        p.id == "field" -> drawGoal(gy, night)
        p.id == "lake" -> {}
        p.id == "hills" || p.id == "observatory" -> {}
        else -> { // park & garden: proper trees, a bench / a fence and flowers
            drawTree(w * 0.08f, gy, h * 0.42f, night, t, 0f)
            drawTree(w * 0.9f, gy, h * 0.5f, night, t, 1.3f)
            if (p.id == "park") { drawBench(w * 0.72f, gy, h, night); drawLampPost(w * 0.2f, gy, h, night) }
            else drawFence(gy, night)
        }
    }

    // ---------------- ground
    if (p.id == "hills" || p.id == "observatory") {
        drawPath(Path().apply { moveTo(0f, gy * 0.92f); quadraticBezierTo(w * 0.5f, gy * 0.82f, w, gy * 0.95f); lineTo(w, h); lineTo(0f, h); close() }, grass)
        if (p.id == "observatory") {
            val c = shade(Color(0xFFB0A3C9), night * 0.6f)
            drawArc(c, 180f, 180f, true, Offset(w * 0.6f, gy * 0.48f), Size(w * 0.3f, w * 0.3f))
            drawRect(c, Offset(w * 0.6f, gy * 0.48f + w * 0.15f), Size(w * 0.3f, gy * 0.32f))
            drawSymbol(Offset(w * 0.75f, gy * 0.48f + w * 0.24f), w * 0.03f, Color(0xFF2B3342))
        }
    } else if (p.id != "sports_hall") drawRect(Brush.verticalGradient(listOf(grass, shade(lerp(grass, Color(0xFF3E5E3A), 0.4f), night)), gy, h), Offset(0f, gy), Size(w, h - gy))
    when (p.id) {
        "sports_hall" -> drawHall(gy, night, t)
        "cricket" -> { drawCricketGround(gy, night); drawStumps(gy, night) }
        "court" -> drawCourtBack(gy, night)
    }
    when (p.id) {
        "lake" -> {
            val water = shade(Color(0xFF5E9CC4), night)
            drawOval(Brush.verticalGradient(listOf(lerp(water, skyBot, 0.35f), water), gy, h), Offset(w * 0.5f, gy + h * 0.02f), Size(w * 0.62f, h * 0.17f))
            for (i in 0 until 4) drawLine(Color.White.copy(alpha = 0.3f), Offset(w * (0.58f + i * 0.1f) + sin(t + i) * 6f, gy + h * (0.07f + (i % 2) * 0.04f)), Offset(w * (0.63f + i * 0.1f) + sin(t + i) * 6f, gy + h * (0.07f + (i % 2) * 0.04f)), 2f)
            for (i in 0 until 5) drawLine(shade(Color(0xFF5C7A48), night), Offset(w * (0.5f + i * 0.015f), gy + h * 0.06f), Offset(w * (0.49f + i * 0.02f), gy - h * 0.04f), 3f)
        }
        "park", "garden" -> { // a path he walks on
            drawPath(Path().apply { moveTo(0f, gy + h * 0.06f); quadraticBezierTo(w * 0.5f, gy + h * 0.02f, w, gy + h * 0.07f); lineTo(w, gy + h * 0.13f); quadraticBezierTo(w * 0.5f, gy + h * 0.09f, 0f, gy + h * 0.13f); close() }, shade(Color(0xFFD8C39A), night))
        }
        "field" -> drawLine(Color.White.copy(alpha = 0.55f - night * 0.3f), Offset(0f, gy + h * 0.09f), Offset(w, gy + h * 0.09f), 3f)
    }

    festival?.let { drawFestivalOutside(it, gy, night, t, mirror = p.id == "otherside") }

    // ---------------- creatures + props
    val footY = gy + h * 0.1f
    val ph = h * 0.36f
    if (d.purpose == TripPurpose.WILDLIFE) {
        val cx = d.critterX * w
        if (p.id == "lake") { // a frog on a lily pad
            val cy = gy + h * 0.08f
            drawOval(shade(Color(0xFF5E9E5A), night), Offset(cx - w * 0.04f, cy - h * 0.01f), Size(w * 0.08f, h * 0.025f))
            drawCircle(shade(Color(0xFF7CC26E), night), w * 0.018f, Offset(cx, cy - h * 0.012f))
            drawCircle(Color.Black, w * 0.004f, Offset(cx - w * 0.007f, cy - h * 0.025f)); drawCircle(Color.Black, w * 0.004f, Offset(cx + w * 0.007f, cy - h * 0.025f))
        } else { // a butterfly
            val cy = gy - d.critterY * h
            val flap = abs(sin(t * 14f))
            val wing = if (night > 0.5f) Color(0xFFFFF3A8) else Color(0xFFF2A65A)
            drawOval(wing, Offset(cx - w * 0.022f * flap, cy - h * 0.018f), Size(w * 0.022f * flap, h * 0.03f))
            drawOval(wing, Offset(cx, cy - h * 0.018f), Size(w * 0.022f * flap, h * 0.03f))
            drawLine(Color(0xFF3A2A20), Offset(cx, cy - h * 0.02f), Offset(cx, cy + h * 0.012f), 2f)
        }
    }
    // the neighbour who lives/works here
    if (p.npc.isNotBlank()) {
        val (nx, face) = when (p.id) {
            "field" -> 0.1f to 1f; "cricket" -> 0.9f to -1f; "sports_hall" -> 0.09f to 1f; "court" -> 0.08f to 1f
            else -> if (p.kind == PlaceKind.SHOP || p.kind == PlaceKind.INDOOR) 0.08f to 1f else 0.85f to -1f
        }
        val cycle = (t + nx * 7f) % 9f
        val wave = if (cycle < 1.4f) sin(cycle / 1.4f * Math.PI.toFloat()) else 0f
        val cheer = d.rig.anim == AnimState.CELEBRATE
        drawNpc(p.npc, nx * w, footY - (if (p.kind == PlaceKind.SHOP) h * 0.02f else 0f), h * 0.3f, t, face, wave, cheer)
    }
    if (d.purpose == TripPurpose.TABLE_TENNIS) drawPingPongTable(footY, night)
    if (d.purpose == TripPurpose.BADMINTON) drawBadmintonNet(footY, night)
    // Nib (behind him) and Pipo, each with a contact shadow
    if (d.withPet) {
        val px = d.petX * w
        drawOval(Color.Black.copy(alpha = 0.2f), Offset(px - h * 0.045f, footY - h * 0.008f), Size(h * 0.09f, h * 0.018f))
        drawPet(d.pet, px, footY, h * 0.11f)
    }
    val bx = d.ballX * w
    when (d.purpose) {
        TripPurpose.CRICKET -> {
            val r = h * 0.013f; val by = footY - r - d.ballY * h
            drawOval(Color.Black.copy(alpha = 0.15f), Offset(bx - r, footY - r * 0.35f), Size(r * 2f, r * 0.6f))
            drawCircle(Color(0xFFC0392B), r, Offset(bx, by)); drawLine(Color.White.copy(alpha = 0.7f), Offset(bx - r * 0.7f, by), Offset(bx + r * 0.7f, by), 1f)
        }
        TripPurpose.TABLE_TENNIS -> {
            val r = h * 0.008f
            drawCircle(Color(0xFFFFF4E0), r, Offset(bx, footY - d.ballY * h))
        }
        TripPurpose.BADMINTON -> {
            val c = Offset(bx, footY - d.ballY * h)
            drawCircle(Color.White, h * 0.008f, c)
            drawPath(Path().apply { moveTo(c.x - h * 0.006f, c.y - h * 0.004f); lineTo(c.x - h * 0.016f, c.y - h * 0.028f); lineTo(c.x + h * 0.016f, c.y - h * 0.028f); lineTo(c.x + h * 0.006f, c.y - h * 0.004f); close() }, Color.White.copy(alpha = 0.85f))
        }
        else -> Unit
    }
    if (d.purpose == TripPurpose.FOOTBALL) {
        val r = h * 0.024f; val by = footY - r - d.ballY * h
        drawOval(Color.Black.copy(alpha = 0.18f * (1f - d.ballY * 3f).coerceIn(0.2f, 1f)), Offset(bx - r, footY - r * 0.35f), Size(r * 2f, r * 0.6f))
        drawCircle(Color(0xFFF4F4F0), r, Offset(bx, by))
        drawCircle(Color(0xFF2B2B2B), r * 0.35f, Offset(bx + r * 0.2f * sin(t * 6f), by - r * 0.1f))
    }
    val pipoFoot = Offset(d.pipoX * w, footY)
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(pipoFoot.x - ph * 0.22f, pipoFoot.y - ph * 0.025f), Size(ph * 0.44f, ph * 0.06f))
    drawPipo(d.rig, pipoFoot.x, pipoFoot.y, ph, shadow = false,
        light = PipoLight(dir = if (day > 0.4f) -0.5f else 0.4f, keyStrength = 0.55f + day * 0.3f, rim = if (night > 0.5f) Color(0xFF9FB6FF) else Color(0xFFFFE6B8), rimStrength = 0.25f))

    // ---------------- foreground: grass tufts, flowers, fireflies at night, weather
    val tuft = shade(Color(0xFF4F8A45), night)
    for (i in 0 until 14) {
        val x = ((i * 0.071f + 0.03f) % 1f) * w; val y = h * (0.9f + (i % 3) * 0.035f)
        val sway = sin(t * 1.6f + i) * 3f
        for (j in -1..1) drawLine(tuft, Offset(x + j * 4f, y), Offset(x + j * 7f + sway, y - h * 0.03f), 3f, cap = StrokeCap.Round)
        if (i % 4 == 1 && p.id != "field") drawCircle(shade(listOf(Color(0xFFF4D35E), Color(0xFFF28B82), Color.White)[i % 3], night), 4f, Offset(x + sway, y - h * 0.033f))
    }
    if (night > 0.55f && (p.id == "garden" || p.id == "lake" || p.id == "park")) for (i in 0 until 9) {
        val fx = ((i * 0.13f + sin(t * 0.3f + i) * 0.05f) % 1f) * w; val fy = gy - h * (0.05f + (i % 4) * 0.06f) + sin(t * 0.8f + i * 1.7f) * h * 0.02f
        val a = (0.5f + 0.5f * sin(t * 2.2f + i * 1.3f)).coerceIn(0f, 1f)
        drawCircle(Color(0xFFFFF3A8).copy(alpha = a * 0.35f), 9f, Offset(fx, fy)); drawCircle(Color(0xFFFFF3A8).copy(alpha = a), 2.5f, Offset(fx, fy))
    }
    if (weather == Weather.RAIN || weather == Weather.STORM) for (i in 0 until 60) {
        val ph2 = (t * 1.4f + i * 0.137f) % 1f
        val x = ((i * 97) % 100) / 100f * w
        drawLine(Color.White.copy(alpha = 0.35f), Offset(x, ph2 * h), Offset(x - 4f, ph2 * h + 14f), 1.5f)
    }
    if (weather == Weather.FOG) drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.45f))))
    if (weather == Weather.STORM && (t % 7f) < 0.12f) drawRect(Color.White.copy(alpha = 0.35f))
}

private fun DrawScope.drawTree(x: Float, gy: Float, th: Float, night: Float, t: Float, ph: Float) {
    val trunk = shade(Color(0xFF7A5638), night)
    drawPath(Path().apply { moveTo(x - th * 0.04f, gy); lineTo(x - th * 0.025f, gy - th * 0.55f); lineTo(x + th * 0.025f, gy - th * 0.55f); lineTo(x + th * 0.045f, gy); close() }, trunk)
    val sway = sin(t * 0.9f + ph) * th * 0.01f
    val dark = shade(Color(0xFF4E8A55), night); val mid = shade(Color(0xFF65A267), night); val lite = shade(Color(0xFF8CC27F), night)
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(x - th * 0.25f, gy - th * 0.02f), Size(th * 0.5f, th * 0.05f))
    drawCircle(dark, th * 0.24f, Offset(x - th * 0.1f + sway, gy - th * 0.62f))
    drawCircle(dark, th * 0.22f, Offset(x + th * 0.13f + sway, gy - th * 0.6f))
    drawCircle(mid, th * 0.25f, Offset(x + sway, gy - th * 0.78f))
    drawCircle(lite, th * 0.12f, Offset(x - th * 0.08f + sway, gy - th * 0.86f))
}

private fun DrawScope.drawBench(x: Float, gy: Float, h: Float, night: Float) {
    val wood = shade(Color(0xFFA9774E), night); val iron = shade(Color(0xFF3B3F48), night)
    val bw = h * 0.22f
    drawRect(iron, Offset(x - bw * 0.45f, gy - h * 0.06f), Size(h * 0.012f, h * 0.06f)); drawRect(iron, Offset(x + bw * 0.42f, gy - h * 0.06f), Size(h * 0.012f, h * 0.06f))
    drawRoundRect(wood, Offset(x - bw / 2, gy - h * 0.07f), Size(bw, h * 0.018f), CornerRadius(3f))
    drawRoundRect(wood, Offset(x - bw / 2, gy - h * 0.12f), Size(bw, h * 0.016f), CornerRadius(3f))
    drawRoundRect(wood, Offset(x - bw / 2, gy - h * 0.095f), Size(bw, h * 0.016f), CornerRadius(3f))
}

private fun DrawScope.drawLampPost(x: Float, gy: Float, h: Float, night: Float) {
    drawRect(shade(Color(0xFF3B3F48), night * 0.5f), Offset(x - 3f, gy - h * 0.38f), Size(6f, h * 0.38f))
    val lc = Offset(x, gy - h * 0.4f)
    if (night > 0.4f) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A0).copy(alpha = 0.5f * night), Color.Transparent), lc, h * 0.25f), h * 0.25f, lc)
    drawCircle(if (night > 0.4f) Color(0xFFFFE9B0) else Color(0xFFE6E2D6), h * 0.022f, lc)
}

private fun DrawScope.drawFence(gy: Float, night: Float) {
    val c = shade(Color(0xFFE8DCC4), night)
    val w = size.width
    for (i in 0 until 14) drawRoundRect(c, Offset(i * w / 13f, gy - size.height * 0.11f), Size(w * 0.025f, size.height * 0.11f), CornerRadius(4f))
    drawRect(c, Offset(0f, gy - size.height * 0.085f), Size(w, size.height * 0.015f))
    drawRect(c, Offset(0f, gy - size.height * 0.04f), Size(w, size.height * 0.015f))
}

/** Inside the sports hall: wooden floor lines, high windows, a banner, bright lights. */
private fun DrawScope.drawHall(gy: Float, night: Float, t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFDCD2C0), Color(0xFFC9BCA4)), 0f, gy), Offset.Zero, Size(w, gy))
    for (i in 0 until 4) drawRoundRect(Color(0xFFA9D2EE).copy(alpha = 0.8f - night * 0.5f), Offset(w * (0.08f + i * 0.24f), h * 0.12f), Size(w * 0.16f, h * 0.12f), CornerRadius(4f))
    drawRect(Color(0xFFE0453A), Offset(w * 0.25f, h * 0.3f), Size(w * 0.5f, h * 0.06f))
    for (i in 0 until 8) drawRect(Color.White.copy(alpha = 0.85f), Offset(w * (0.28f + i * 0.055f), h * 0.32f), Size(w * 0.035f, h * 0.02f))
    for (i in 0 until 3) { val lc = Offset(w * (0.2f + i * 0.3f), h * 0.04f); drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6D8).copy(alpha = 0.6f), Color.Transparent), lc, w * 0.15f), w * 0.15f, lc) }
    drawRect(Brush.verticalGradient(listOf(Color(0xFFC98E55), Color(0xFFA06A3A)), gy, h), Offset(0f, gy), Size(w, h - gy))
    for (i in 0 until 9) drawLine(Color.Black.copy(alpha = 0.08f), Offset(i * w / 8f, gy), Offset(i * w / 8f + (i - 4) * w * 0.05f, h), 1.5f)
    drawLine(Color(0xFFFFD34A), Offset(0f, gy + h * 0.12f), Offset(w, gy + h * 0.12f), 3f)
}

/** The cricket ground: a big oval with a boundary rope, a sight-screen, the pitch strip. */
private fun DrawScope.drawCricketGround(gy: Float, night: Float) {
    val w = size.width; val h = size.height
    drawRect(shade(Color(0xFFF2F2EE), night), Offset(w * 0.02f, gy - h * 0.22f), Size(w * 0.14f, h * 0.22f)) // sight-screen
    drawOval(Color.White.copy(alpha = 0.7f - night * 0.3f), Offset(-w * 0.2f, gy + h * 0.02f), Size(w * 1.4f, h * 0.3f), style = Stroke(3f)) // boundary rope
    drawRect(shade(Color(0xFFD9C79A), night), Offset(w * 0.2f, gy + h * 0.085f), Size(w * 0.64f, h * 0.03f))
}

/** The badminton court: green court with white lines (the net is drawn with the players). */
private fun DrawScope.drawCourtBack(gy: Float, night: Float) {
    val w = size.width; val h = size.height
    drawRect(shade(Color(0xFF4F8A5A), night), Offset(w * 0.12f, gy + h * 0.04f), Size(w * 0.76f, h * 0.1f))
    drawRect(Color.White.copy(alpha = 0.8f), Offset(w * 0.12f, gy + h * 0.04f), Size(w * 0.76f, h * 0.1f), style = Stroke(2f))
    drawLine(Color.White.copy(alpha = 0.8f), Offset(w * 0.5f, gy + h * 0.04f), Offset(w * 0.5f, gy + h * 0.14f), 2f)
    // a bench, with Bea's chalkboard
    drawRect(shade(Color(0xFFA9774E), night), Offset(w * 0.01f, gy + h * 0.02f), Size(w * 0.14f, h * 0.015f))
}

private fun DrawScope.drawStumps(gy: Float, night: Float) {
    val w = size.width; val h = size.height
    val wood = shade(Color(0xFFE2C48E), night)
    for (i in 0..2) drawRoundRect(wood, Offset(w * 0.24f + i * w * 0.012f, gy + h * 0.1f - h * 0.09f), Size(w * 0.006f, h * 0.09f), CornerRadius(2f))
    drawLine(wood, Offset(w * 0.238f, gy + h * 0.01f), Offset(w * 0.272f, gy + h * 0.01f), 3f)
    // the crease, and Nib's end of the pitch
    drawRect(shade(Color(0xFFD9C79A), night).copy(alpha = 0.6f), Offset(w * 0.2f, gy + h * 0.085f), Size(w * 0.64f, h * 0.03f))
}

private fun DrawScope.drawPingPongTable(footY: Float, night: Float) {
    val w = size.width; val h = size.height
    val top = footY - h * 0.1f
    for (x in listOf(0.34f, 0.66f)) drawRect(shade(Color(0xFF3B3F48), night), Offset(w * x - 2f, top), Size(4f, h * 0.1f))
    drawRect(shade(Color(0xFF2F6E8E), night), Offset(w * 0.3f, top - h * 0.012f), Size(w * 0.4f, h * 0.014f))
    drawLine(Color.White.copy(alpha = 0.8f), Offset(w * 0.3f, top - h * 0.012f), Offset(w * 0.7f, top - h * 0.012f), 1.5f)
    drawRect(Color.White.copy(alpha = 0.7f), Offset(w * 0.498f, top - h * 0.035f), Size(w * 0.004f, h * 0.024f)) // the net
}

private fun DrawScope.drawBadmintonNet(footY: Float, night: Float) {
    val w = size.width; val h = size.height
    val post = shade(Color(0xFF8A8F99), night)
    drawLine(post, Offset(w * 0.5f, footY), Offset(w * 0.5f, footY - h * 0.2f), 3f)
    for (i in 0..4) drawLine(Color.White.copy(alpha = 0.35f), Offset(w * 0.5f - 6f, footY - h * (0.12f + i * 0.016f)), Offset(w * 0.5f + 6f, footY - h * (0.12f + i * 0.016f)), 1f)
    drawLine(Color.White.copy(alpha = 0.9f), Offset(w * 0.5f - 7f, footY - h * 0.2f), Offset(w * 0.5f + 7f, footY - h * 0.2f), 2f)
}

private fun DrawScope.drawGoal(gy: Float, night: Float) {
    val w = size.width; val h = size.height
    val post = Color.White.copy(alpha = 0.95f - night * 0.3f)
    val left = w * 0.86f; val top = gy - h * 0.2f
    // the net, then the frame
    for (i in 0..6) drawLine(post.copy(alpha = 0.3f), Offset(left + i * w * 0.02f, top), Offset(left + i * w * 0.02f + w * 0.02f, gy + h * 0.05f), 1.5f)
    for (i in 0..5) drawLine(post.copy(alpha = 0.3f), Offset(left, top + i * h * 0.04f), Offset(w, top + i * h * 0.04f + h * 0.01f), 1.5f)
    drawLine(post, Offset(left, gy + h * 0.06f), Offset(left, top), 6f); drawLine(post, Offset(left, top), Offset(w, top), 6f)
}

/** A shop (or the library / Fennel's): a proper front with an awning, a sign, a lit window, crates outside. */
private fun DrawScope.drawShopfront(p: Place, gy: Float, night: Float, t: Float) {
    val w = size.width; val h = size.height
    val wall = shade(lerp(Color(0xFFE9DCC6), Color(p.color), 0.15f), night * 0.8f)
    val top = gy - h * 0.55f
    drawRect(wall, Offset(w * 0.04f, top), Size(w * 0.92f, gy - top))
    drawRect(shade(Color(0xFF8C6A5A), night), Offset(w * 0.02f, top - h * 0.04f), Size(w * 0.96f, h * 0.045f)) // roof edge
    // awning: scalloped stripes
    val aw = top + h * 0.13f
    for (i in 0 until 10) {
        val x = w * (0.06f + i * 0.088f)
        drawRect(if (i % 2 == 0) shade(Color(p.color), night) else shade(Color.White, night), Offset(x, aw - h * 0.07f), Size(w * 0.088f, h * 0.07f))
        drawArc(if (i % 2 == 0) shade(Color(p.color), night) else shade(Color.White, night), 0f, 180f, true, Offset(x, aw - h * 0.025f), Size(w * 0.088f, h * 0.05f))
    }
    // sign
    drawRoundRect(shade(Color(0xFF5E4A3A), night), Offset(w * 0.3f, top + h * 0.01f), Size(w * 0.4f, h * 0.05f), CornerRadius(8f))
    for (i in 0 until 5) drawRoundRect(Color(0xFFF1E8D6).copy(alpha = 0.8f), Offset(w * (0.34f + i * 0.065f), top + h * 0.03f), Size(w * 0.045f, h * 0.012f), CornerRadius(4f))
    // big lit window with the goods, and the door
    val win = Offset(w * 0.48f, aw + h * 0.04f); val ws = Size(w * 0.42f, h * 0.2f)
    drawRect(lerp(Color(0xFFFFE9B0), Color(0xFFFFD27A), night).copy(alpha = 0.55f + 0.45f * night), win, ws)
    shopGoods(p.id, win, ws, t)
    drawRect(shade(Color(0xFF5E4A3A), night), win, ws, style = Stroke(5f))
    drawLine(shade(Color(0xFF5E4A3A), night), Offset(win.x + ws.width / 2, win.y), Offset(win.x + ws.width / 2, win.y + ws.height), 4f)
    drawRect(shade(lerp(Color(p.color), Color.Black, 0.35f), night), Offset(w * 0.12f, aw + h * 0.04f), Size(w * 0.16f, gy - aw - h * 0.04f))
    drawCircle(Color(0xFFE8C26A), 5f, Offset(w * 0.25f, gy - h * 0.12f))
    if (night > 0.4f) drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE3A0).copy(alpha = 0.25f * night), Color.Transparent), win.y + ws.height, gy + h * 0.15f), Offset(win.x - w * 0.05f, win.y + ws.height), Size(ws.width + w * 0.1f, gy + h * 0.15f - win.y - ws.height))
    // crates out front (the market and bakery keep their best things outside)
    if (p.id == "market" || p.id == "bakery") for (i in 0 until 2) {
        val cx = w * (0.34f + i * 0.13f)
        drawRect(shade(Color(0xFFB98A5A), night), Offset(cx, gy - h * 0.07f), Size(w * 0.11f, h * 0.07f))
        val goods = if (p.id == "market") listOf(ItemShape.APPLE, ItemShape.TOMATO) else listOf(ItemShape.BREAD, ItemShape.CROISSANT)
        for (j in 0 until 3) drawItem(goods[i], Offset(cx + w * (0.02f + j * 0.035f), gy - h * 0.075f), h * 0.04f)
    }
}

private fun DrawScope.shopGoods(id: String, tl: Offset, sz: Size, t: Float) {
    val c = Offset(tl.x + sz.width / 2f, tl.y + sz.height * 0.62f)
    val s = sz.height * 0.38f
    fun row(vararg shapes: ItemShape) = shapes.forEachIndexed { i, sh -> drawItem(sh, Offset(tl.x + sz.width * ((i + 0.5f) / shapes.size), c.y), s) }
    when (id) {
        "bakery" -> row(ItemShape.BREAD, ItemShape.CROISSANT, ItemShape.CAKE, ItemShape.BREAD)
        "market" -> row(ItemShape.APPLE, ItemShape.BANANA, ItemShape.TOMATO, ItemShape.CHEESE, ItemShape.EGG)
        "hardware" -> row(ItemShape.SCREW, ItemShape.WIRE, ItemShape.MAGNET, ItemShape.GLUE)
        "electronics" -> { row(ItemShape.MOTOR, ItemShape.LED, ItemShape.CHIP); for (i in 0 until 6) drawCircle(listOf(Color(0xFF8FF5E2), Color(0xFFE0605A), Color(0xFFFFE08A))[i % 3].copy(alpha = if (sin(t * 3f + i * 1.7f) > 0f) 0.95f else 0.25f), sz.height * 0.04f, Offset(tl.x + sz.width * (0.1f + i * 0.16f), tl.y + sz.height * 0.15f)) }
        "cafe" -> row(ItemShape.MUG, ItemShape.CAKE, ItemShape.COOKIE)
        "secondhand" -> row(ItemShape.DUCK, ItemShape.LAMP, ItemShape.RADIO)
        "repair" -> row(ItemShape.GEAR, ItemShape.RADIO, ItemShape.SPRING)
        "library" -> for (i in 0 until 9) drawRect(listOf(Color(0xFF6F8FA6), Color(0xFFB97E5E), Color(0xFF7FB77E))[i % 3], Offset(tl.x + sz.width * (0.05f + i * 0.1f), c.y - s * 0.7f), Size(sz.width * 0.08f, s * 1.3f))
        else -> Unit
    }
}
