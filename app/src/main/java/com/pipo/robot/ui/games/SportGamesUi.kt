package com.pipo.robot.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pipo.robot.ui.render.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.data.ItemShape
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.ArcadePhase
import com.pipo.robot.engine.CricketSim
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.PongSim
import com.pipo.robot.engine.Sfx
import com.pipo.robot.ui.render.PipoColors
import com.pipo.robot.ui.render.drawItem

/* ============================ Cricket ============================ */

@Composable
fun CricketGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("cricket")
    var seed by remember { mutableIntStateOf(gp.rng.nextInt()) }
    val sim = remember(seed) { CricketSim(seed, gp.energy * 0.4f + gp.traits.confidence * 0.4f) }
    var frame by remember { mutableLongStateOf(0L) }
    // HUD mirrors (the sim is plain Kotlin; these recompose the text)
    var yours by remember(sim) { mutableIntStateOf(0) }
    var his by remember(sim) { mutableIntStateOf(0) }
    var balls by remember(sim) { mutableIntStateOf(0) }
    var hisBalls by remember(sim) { mutableIntStateOf(0) }
    var wkts by remember(sim) { mutableIntStateOf(0) }
    var hisWkts by remember(sim) { mutableIntStateOf(0) }
    var phase by remember(sim) { mutableStateOf(sim.phase) }
    LaunchedEffect(sim) {
        gp.line = "You bat first: tap when the ball reaches you. Then you bowl to ME."
        var last = 0L
        var lastPhase = sim.phase
        var lastBall = 0; var lastHisBall = 0
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val ended = sim.step((t - last) / 1e9f)
                if (sim.ball != lastBall) {
                    lastBall = sim.ball
                    when (sim.last) {
                        -1 -> gp.react(AnimState.CELEBRATE, Expr.EXCITED, listOf("BOWLED! The stumps went flying!", "Out! Got you!").random(gp.rng), Sfx.WIN, EmoteKind.SPARKLE)
                        6 -> gp.react(AnimState.SURPRISED, Expr.SURPRISED, listOf("SIX?! Into the bushes!", "Six! Nib has to go find it.").random(gp.rng), Sfx.SURPRISED)
                        4 -> gp.react(AnimState.ANNOYED, Expr.ANNOYED, "Four. Lucky. Very lucky.", Sfx.GRUMBLE)
                        0 -> gp.react(AnimState.HAPPY, Expr.HAPPY, if (sim.timing == "early") "Too early! Hehe." else if (sim.timing == "late") "Too late!" else "Dot ball! Too fast for you.", Sfx.GIGGLE)
                        else -> gp.react(AnimState.LOOK_AROUND, Expr.CURIOUS, "${sim.last}. Run run run!", Sfx.BOOP)
                    }
                }
                if (sim.phase != lastPhase) {
                    if (sim.phase == CricketSim.Phase.PIPO_AIM && lastPhase == CricketSim.Phase.SHOT)
                        gp.react(AnimState.HOP, Expr.FOCUSED, "My turn to bat! I need ${sim.target}. Bowl when the arrow's on the stumps.", Sfx.BEEP)
                    lastPhase = sim.phase
                }
                if (sim.pipoBall != lastHisBall && sim.phase == CricketSim.Phase.PIPO_BALL && sim.shotT > 0.9f) {
                    lastHisBall = sim.pipoBall
                    when (sim.pipoLast) {
                        -1 -> gp.react(AnimState.SULK, Expr.SAD, listOf("Bowled?! That one was sneaky.", "OUT. I wasn't ready. I was a bit ready.").random(gp.rng), Sfx.LOSE)
                        6 -> gp.react(AnimState.CELEBRATE, Expr.EXCITED, "SIX! Did you SEE that?!", Sfx.WIN, EmoteKind.SPARKLE)
                        4 -> gp.react(AnimState.HAPPY, Expr.PROUD, "FOUR! Cover drive. Coach Raj would be proud.", Sfx.HAPPY)
                        0 -> gp.react(AnimState.ANNOYED, Expr.ANNOYED, "Missed it. Good ball. Grr.", Sfx.GRUMBLE)
                        else -> gp.react(AnimState.HAPPY, Expr.HAPPY, "${sim.pipoLast}! Running!", Sfx.BOOP)
                    }
                }
                if (ended && sim.claimResult()) {
                    val won = sim.pipoWon == true
                    GameLog.record(gp.repo, "cricket", won, "You: ${sim.yourRuns}. Pipo: ${sim.pipoRuns}.")
                    if (won) gp.win("Got it! ${sim.pipoRuns}! I'm a cricket robot now.") else gp.lose("${sim.pipoRuns}... I needed ${sim.target}. You win. This time.")
                }
            }
            last = t; frame = t
            yours = sim.yourRuns; his = sim.pipoRuns; balls = sim.ball; hisBalls = sim.pipoBall; phase = sim.phase; wkts = sim.wickets; hisWkts = sim.pipoWickets
        }
    }
    val pipoInnings = phase == CricketSim.Phase.PIPO_AIM || phase == CricketSim.Phase.PIPO_BALL || phase == CricketSim.Phase.OVER
    val hud = if (pipoInnings) "You $yours  ·  Pipo $his/${hisWkts} (needs ${yours + 1})  ·  Ball ${hisBalls.coerceAtMost(6)}/6"
        else "Runs $yours  ·  Wickets $wkts/3  ·  Ball ${(balls + 1).coerceAtMost(6)}/6"
    GameScaffold("Cricket", gp, onExit, hud) {
        Box(
            Modifier.widthIn(max = 380.dp).aspectRatio(0.72f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(22.dp))
                .semantics { contentDescription = if (pipoInnings) "Cricket. You're bowling. Tap when the arrow is on the stumps." else "Cricket. You're batting. Tap to swing when the ball reaches the bat." }
                .pointerInput(sim) {
                    detectTapGestures {
                        when (sim.phase) {
                            CricketSim.Phase.READY -> sim.start()
                            CricketSim.Phase.BOWLING -> if (sim.swing()) gp.synth.sfx(Sfx.KICK)
                            CricketSim.Phase.PIPO_AIM -> if (sim.bowl()) gp.synth.sfx(Sfx.BOOP)
                            CricketSim.Phase.OVER -> { gp.restart(); seed++ }
                            else -> Unit
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                if (sim.phase == CricketSim.Phase.PIPO_AIM || sim.phase == CricketSim.Phase.PIPO_BALL) drawPipoBatting(sim) else drawPitch(sim)
            }
            val lbl = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
            when (phase) {
                CricketSim.Phase.READY -> Text("You bat first.\ntap to face the first ball", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                CricketSim.Phase.SHOT -> Text(when (sim.last) { -1 -> "BOWLED!"; 6 -> "SIX!"; 4 -> "FOUR!"; 0 -> if (sim.timing == "early") "too early" else if (sim.timing == "late") "too late" else "dot ball"
                    else -> "${sim.last} run${if (sim.last == 1) "" else "s"}" }, color = if (sim.last == -1) Color(0xFFFFD0C8) else Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = lbl)
                CricketSim.Phase.PIPO_AIM -> Text("You bowl! Tap when the arrow\nis on the stumps", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = lbl)
                CricketSim.Phase.PIPO_BALL -> if (sim.shotT > 0.9f) Text(when (sim.pipoLast) { -1 -> "BOWLED HIM!"; 6 -> "SIX!"; 4 -> "FOUR!"; 0 -> "dot ball"; else -> "${sim.pipoLast} run${if (sim.pipoLast == 1) "" else "s"}" },
                    color = if (sim.pipoLast == -1) Color(0xFFB8F5C8) else Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = lbl)
                CricketSim.Phase.OVER -> Text("You $yours · Pipo $his\n${if (his > yours) "Pipo wins" else "You win!"}\ntap to play again", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 18.sp)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawField() {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7FB77E), Color(0xFF5E9A5C))))
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.42f, h * 0.12f); lineTo(w * 0.58f, h * 0.12f); lineTo(w * 0.7f, h * 0.95f); lineTo(w * 0.3f, h * 0.95f); close() }, Color(0xFFD9C79A))
    drawLine(Color.White, Offset(w * 0.32f, h * 0.8f), Offset(w * 0.68f, h * 0.8f), 3f)
    for (i in -1..1) drawRect(Color(0xFF8A5A3A), Offset(w * 0.5f + i * w * 0.012f - 1.5f, h * 0.1f), Size(3f, h * 0.035f))
}

/** Stumps at the near end; knocked flying when [down] > 0 (0..1 = how far through the tumble). */
private fun DrawScope.nearStumps(down: Float) {
    val w = size.width; val h = size.height
    for (i in -1..1) {
        val base = Offset(w * 0.5f + i * w * 0.03f, h * 0.94f)
        rotate(i * 35f * down + (if (i == 0) 15f * down else 0f), base) { drawRect(Color(0xFF8A5A3A), Offset(base.x - 3f, base.y - h * 0.1f - down * h * 0.04f), Size(6f, h * 0.1f)) }
    }
}

/** You batting: Pipo bowls from the far end; the ball comes down the pitch; your bat at the crease. */
private fun DrawScope.drawPitch(sim: CricketSim) {
    val w = size.width; val h = size.height
    drawField()
    // little Pipo at the far end, bowling
    drawRoundRect(PipoColors.shell, Offset(w * 0.47f, h * 0.05f), Size(w * 0.06f, h * 0.045f), CornerRadius(8f))
    drawRect(PipoColors.screen, Offset(w * 0.478f, h * 0.06f), Size(w * 0.044f, h * 0.025f))
    val bowled = sim.phase == CricketSim.Phase.SHOT && sim.last == -1
    nearStumps(if (bowled) (sim.shotT / 0.4f).coerceAtMost(1f) else 0f)
    if (sim.phase == CricketSim.Phase.BOWLING) {
        val p = sim.progress.coerceAtMost(1.08f)
        val x = w * (0.5f + sim.drift * p); val y = h * (0.12f + p * 0.75f)
        val bounce = if (p < 0.6f) kotlin.math.sin(p / 0.6f * Math.PI.toFloat()) * h * 0.05f else kotlin.math.sin((p - 0.6f) / 0.48f * Math.PI.toFloat()).coerceAtLeast(0f) * h * 0.02f
        val r = w * (0.012f + p * 0.022f)
        drawOval(Color.Black.copy(alpha = 0.2f), Offset(x - r, y - r * 0.3f), Size(r * 2f, r * 0.6f))
        drawCircle(Color(0xFFC0392B), r, Offset(x, y - bounce))
        drawCircle(Color.White.copy(alpha = 0.18f), w * 0.05f, Offset(w * 0.5f, h * 0.77f)) // the sweet spot
    } else if (sim.phase == CricketSim.Phase.SHOT && (sim.last ?: 0) > 0) {
        // the shot: off the bat and away, higher and further for bigger hits
        val k = (sim.shotT / 1f).coerceAtMost(1f); val pow = (sim.last ?: 1) / 6f
        val dir = if ((sim.ball % 2) == 0) -1f else 1f
        val x = w * (0.55f + dir * k * (0.2f + pow * 0.35f)); val y = h * (0.78f - k * (0.25f + pow * 0.5f)) - kotlin.math.sin(k * Math.PI.toFloat()) * h * 0.1f * pow
        drawCircle(Color(0xFFC0392B), w * 0.025f * (1f - k * 0.5f), Offset(x, y))
    }
    // your bat: swings when you tap
    val swingAng = if (sim.phase == CricketSim.Phase.SHOT && sim.last != -1) (-80f * (1f - (sim.shotT / 0.3f).coerceAtMost(1f))) else 0f
    rotate(swingAng, Offset(w * 0.6f, h * 0.84f)) { drawItem(ItemShape.BAT, Offset(w * 0.58f, h * 0.8f), w * 0.16f) }
}

/** You bowling: Pipo at the crease with his bat; an arrow sweeps across the stumps until you tap. */
private fun DrawScope.drawPipoBatting(sim: CricketSim) {
    val w = size.width; val h = size.height
    drawField()
    val out = sim.phase == CricketSim.Phase.PIPO_BALL && sim.pipoLast == -1 && sim.shotT > 0.85f
    nearStumps(if (out) ((sim.shotT - 0.85f) / 0.4f).coerceIn(0f, 1f) else 0f)
    // Pipo, batting, side-on beside his stumps (round head, dark face screen, cyan eyes, like him)
    val px = w * 0.37f; val py = h * 0.93f
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(px - w * 0.06f, py - h * 0.01f), Size(w * 0.12f, h * 0.02f))
    for (sd in listOf(-1f, 1f)) drawRoundRect(Color(0xFF4A5468), Offset(px + sd * w * 0.018f - w * 0.01f, py - h * 0.035f), Size(w * 0.02f, h * 0.035f), CornerRadius(6f))
    drawRoundRect(PipoColors.shell, Offset(px - w * 0.04f, py - h * 0.09f), Size(w * 0.08f, h * 0.06f), CornerRadius(14f))
    drawCircle(Color(0xFFFFB86B).copy(alpha = 0.8f), w * 0.01f, Offset(px, py - h * 0.06f))
    drawRoundRect(PipoColors.shell, Offset(px - w * 0.065f, py - h * 0.165f), Size(w * 0.13f, h * 0.08f), CornerRadius(26f))
    drawRoundRect(PipoColors.screen, Offset(px - w * 0.05f, py - h * 0.153f), Size(w * 0.1f, h * 0.056f), CornerRadius(18f))
    val blink = if ((sim.shotT * 3f + sim.pipoBall) % 4f < 0.1f) 0.2f else 1f
    for (sd in listOf(-1f, 1f)) drawOval(PipoColors.eye, Offset(px + w * 0.01f + sd * w * 0.02f - w * 0.009f, py - h * 0.14f), Size(w * 0.018f, h * 0.022f * blink))
    drawLine(PipoColors.shell, Offset(px, py - h * 0.165f), Offset(px, py - h * 0.185f), 3f)
    drawCircle(Color(0xFFFFB86B), w * 0.009f, Offset(px, py - h * 0.188f))
    val hit = sim.phase == CricketSim.Phase.PIPO_BALL && (sim.pipoLast ?: 0) > 0 && sim.shotT > 0.85f
    val batAng = if (sim.phase == CricketSim.Phase.PIPO_BALL && sim.shotT in 0.8f..1.1f) -70f else if (hit) -40f else 20f
    rotate(batAng, Offset(px + w * 0.045f, py - h * 0.07f)) { drawItem(ItemShape.BAT, Offset(px + w * 0.07f, py - h * 0.05f), w * 0.13f) }
    // your hand at the far end
    drawCircle(Color(0xFFF2C9A8), w * 0.025f, Offset(w * 0.5f, h * 0.08f))
    when (sim.phase) {
        CricketSim.Phase.PIPO_AIM -> {
            // the aiming arrow: green over the stumps, red when it's wide
            val ax = w * (0.5f + sim.aim * 0.2f)
            val good = 1f - kotlin.math.abs(sim.aim)
            val col = lerp(Color(0xFFE0605A), Color(0xFF4FD07A), good)
            drawLine(col.copy(alpha = 0.5f), Offset(w * 0.5f, h * 0.12f), Offset(ax, h * 0.7f), 2f)
            drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(ax, h * 0.73f); lineTo(ax - w * 0.035f, h * 0.66f); lineTo(ax + w * 0.035f, h * 0.66f); close() }, col)
            drawRect(Color.White.copy(alpha = 0.25f), Offset(w * 0.47f, h * 0.83f), Size(w * 0.06f, h * 0.11f)) // the stumps zone
            drawCircle(Color(0xFFC0392B), w * 0.014f, Offset(w * 0.5f, h * 0.1f))
        }
        CricketSim.Phase.PIPO_BALL -> {
            val p = sim.pipoProgress
            if (!hit) {
                val x = w * (0.5f + sim.lastAim * 0.2f * p); val y = h * (0.1f + p * 0.8f)
                val r = w * (0.012f + p * 0.02f)
                drawCircle(Color(0xFFC0392B), r, Offset(x, y - kotlin.math.sin(p * Math.PI.toFloat()) * h * 0.04f))
            } else {
                // his shot flies off into the field
                val k = ((sim.shotT - 0.85f) / 1f).coerceIn(0f, 1f); val pow = (sim.pipoLast ?: 1) / 6f
                val dir = if (sim.pipoBall % 2 == 0) 1f else -1f
                drawCircle(Color(0xFFC0392B), w * 0.025f * (1f - k * 0.5f), Offset(w * (0.5f + dir * k * (0.2f + pow * 0.3f)), h * (0.75f - k * (0.3f + pow * 0.45f))))
            }
        }
        else -> Unit
    }
}

/* ============================ Table tennis ============================ */

@Composable
fun PingPongGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("pingpong")
    var seed by remember { mutableIntStateOf(gp.rng.nextInt()) }
    val sim = remember(seed) { PongSim(seed, gp.energy * 0.5f + gp.traits.confidence * 0.3f) }
    var frame by remember { mutableLongStateOf(0L) }
    var you by remember(sim) { mutableIntStateOf(0) }
    var pipo by remember(sim) { mutableIntStateOf(0) }
    var phase by remember(sim) { mutableStateOf(sim.phase) }
    LaunchedEffect(sim) {
        gp.line = "Drag your paddle. First to 7. I'm at the top. I'm very fast. Kind of."
        var last = 0L
        var lastPts = 0
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val ended = sim.step((t - last) / 1e9f)
                if (sim.you + sim.pipo != lastPts) {
                    lastPts = sim.you + sim.pipo
                    if (sim.lastPointPipo == true) gp.react(AnimState.HAPPY, Expr.PROUD, listOf("Point to me!", "Too quick!", "${sim.pipo}-${sim.you}. Just saying.").random(gp.rng), Sfx.HAPPY)
                    else gp.react(AnimState.ANNOYED, Expr.ANNOYED, listOf("The table is tilted.", "I blinked. Robots blink.", "Lucky bounce.").random(gp.rng), Sfx.GRUMBLE)
                }
                if (ended && sim.claimResult()) {
                    val won = sim.pipo > sim.you
                    GameLog.record(gp.repo, "pingpong", won, "You ${sim.you}, Pipo ${sim.pipo}.")
                    if (won) gp.win("${sim.pipo}-${sim.you}! Table tennis champion of the room.") else gp.lose("${sim.you}-${sim.pipo} to you. I want a rematch. Now.")
                }
            }
            last = t; frame = t
            you = sim.you; pipo = sim.pipo; phase = sim.phase
        }
    }
    GameScaffold("Table Tennis", gp, onExit, "You $you  ·  Pipo $pipo  ·  first to 7") {
        Box(
            Modifier.widthIn(max = 380.dp).aspectRatio(0.62f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(22.dp))
                .semantics { contentDescription = "Table tennis against Pipo. Drag to move your paddle. You $you, Pipo $pipo." }
                .pointerInput(sim) { detectTapGestures { if (sim.phase == ArcadePhase.OVER) { gp.restart(); seed++ } else { sim.start(); sim.moveTo(it.x / size.width) } } }
                .pointerInput(sim) { detectDragGestures { ch, _ -> sim.start(); sim.moveTo(ch.position.x / size.width) } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                val w = size.width; val h = size.height
                drawRect(Color(0xFF2F6E8E))
                drawRect(Color.White, Offset(w * 0.02f, h * 0.02f), Size(w * 0.96f, h * 0.96f), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                drawLine(Color.White.copy(alpha = 0.5f), Offset(w / 2f, h * 0.02f), Offset(w / 2f, h * 0.98f), 2f)
                drawRect(Color.White.copy(alpha = 0.85f), Offset(0f, h / 2f - 3f), Size(w, 6f)) // the net
                // paddles
                drawRoundRect(Color(0xFFD9363E), Offset((sim.yourX - sim.paddleW / 2f) * w, h * 0.92f - 6f), Size(sim.paddleW * w, 14f), CornerRadius(7f))
                drawRoundRect(PipoColors.eye, Offset((sim.pipoX - sim.paddleW / 2f) * w, h * 0.08f - 8f), Size(sim.paddleW * w, 14f), CornerRadius(7f))
                // the ball, with a shadow
                drawCircle(Color.Black.copy(alpha = 0.25f), w * 0.022f, Offset(sim.ballX * w + 4f, sim.ballY * h + 5f))
                drawCircle(Color(0xFFFFF4E0), w * 0.022f, Offset(sim.ballX * w, sim.ballY * h))
            }
            when (phase) {
                ArcadePhase.READY -> Text("tap or drag to serve", color = Color.White, fontWeight = FontWeight.Bold)
                ArcadePhase.OVER -> Text("You $you · Pipo $pipo\ntap to play again", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                else -> Unit
            }
        }
    }
}
