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
    var wkts by remember(sim) { mutableIntStateOf(0) }
    var lastResult by remember(sim) { mutableStateOf<Int?>(null) }
    var phase by remember(sim) { mutableStateOf(sim.phase) }
    LaunchedEffect(sim) {
        gp.line = "I'll bowl. You bat. Tap when the ball reaches you. Then I chase."
        var last = 0L
        var lastPhase = sim.phase
        var lastBall = 0
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val ended = sim.step((t - last) / 1e9f)
                if (sim.ball != lastBall) {
                    lastBall = sim.ball
                    when (sim.last) {
                        -1 -> gp.react(AnimState.CELEBRATE, Expr.EXCITED, listOf("BOWLED! Ha! The stumps fell over!", "Out! I got you!").random(gp.rng), Sfx.WIN, EmoteKind.SPARKLE)
                        6 -> gp.react(AnimState.SURPRISED, Expr.SURPRISED, listOf("SIX?! That went over the bushes!", "Six! Nib has to go and find it.").random(gp.rng), Sfx.SURPRISED)
                        4 -> gp.react(AnimState.ANNOYED, Expr.ANNOYED, "Four. Lucky. Very lucky.", Sfx.GRUMBLE)
                        0 -> gp.react(AnimState.HAPPY, Expr.HAPPY, listOf("Dot ball! Too fast for you.", "Swing and a miss!").random(gp.rng), Sfx.GIGGLE)
                        else -> gp.react(AnimState.LOOK_AROUND, Expr.CURIOUS, "${sim.last}. Run run run!", Sfx.BOOP)
                    }
                }
                if (sim.phase != lastPhase) {
                    lastPhase = sim.phase
                    if (sim.phase == CricketSim.Phase.PIPO_BATS) gp.react(AnimState.HOP, Expr.FOCUSED, "My turn. I need ${sim.target}. Easy. Probably.", Sfx.BEEP)
                }
                if (ended && sim.claimResult()) {
                    val won = sim.pipoWon == true
                    GameLog.record(gp.repo, "cricket", won, "You: ${sim.yourRuns}. Pipo: ${sim.pipoRuns}.")
                    if (won) gp.win("Got it! ${sim.pipoRuns}! I'm a cricket robot now.") else gp.lose("${sim.pipoRuns}... I needed ${sim.target}. You win. This time.")
                }
            }
            last = t; frame = t
            yours = sim.yourRuns; his = sim.pipoRuns; balls = sim.ball; phase = sim.phase; wkts = sim.wickets; lastResult = sim.last
        }
    }
    val hud = if (phase == CricketSim.Phase.PIPO_BATS || phase == CricketSim.Phase.OVER) "You $yours  ·  Pipo $his (needs ${yours + 1})" else "Runs $yours  ·  Wickets $wkts/3  ·  Ball ${(balls + 1).coerceAtMost(6)} of 6"
    GameScaffold("Cricket", gp, onExit, hud) {
        Box(
            Modifier.widthIn(max = 380.dp).aspectRatio(0.72f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(22.dp))
                .semantics { contentDescription = "Cricket. Tap to swing when the ball reaches the bat. Your runs $yours." }
                .pointerInput(sim) {
                    detectTapGestures {
                        when (sim.phase) {
                            CricketSim.Phase.READY -> sim.start()
                            CricketSim.Phase.BOWLING -> sim.swing()?.let { gp.synth.sfx(Sfx.KICK) }
                            CricketSim.Phase.OVER -> { gp.restart(); seed++ }
                            else -> Unit
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                drawPitch(sim)
            }
            when (phase) {
                CricketSim.Phase.READY -> Text("tap to face the first ball", color = Color.White, fontWeight = FontWeight.Bold)
                CricketSim.Phase.SHOT -> Text(when (lastResult) { -1 -> "BOWLED!"; 6 -> "SIX!"; 4 -> "FOUR!"; 0 -> "dot ball"; else -> "$lastResult run${if (lastResult == 1) "" else "s"}" },
                    color = if (lastResult == -1) Color(0xFFFFD0C8) else Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                CricketSim.Phase.PIPO_BATS -> Text("Pipo needs ${yours + 1}\nhe has $his", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
                CricketSim.Phase.OVER -> Text("You $yours · Pipo $his\n${if (his > yours) "Pipo wins" else "You win!"}\ntap to play again", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 18.sp)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawPitch(sim: CricketSim) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7FB77E), Color(0xFF5E9A5C))))
    // the pitch strip, in perspective (narrow far end = Pipo bowling)
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.42f, h * 0.12f); lineTo(w * 0.58f, h * 0.12f); lineTo(w * 0.7f, h * 0.95f); lineTo(w * 0.3f, h * 0.95f); close() }, Color(0xFFD9C79A))
    drawLine(Color.White, Offset(w * 0.32f, h * 0.8f), Offset(w * 0.68f, h * 0.8f), 3f) // your crease
    // stumps at both ends
    for (i in -1..1) drawRect(Color(0xFF8A5A3A), Offset(w * 0.5f + i * w * 0.03f - 3f, h * 0.84f), Size(6f, h * 0.1f))
    for (i in -1..1) drawRect(Color(0xFFE2C48E), Offset(w * 0.5f + i * w * 0.012f - 1.5f, h * 0.1f), Size(3f, h * 0.035f))
    // little Pipo at the far end, bowling
    drawRoundRect(PipoColors.shell, Offset(w * 0.47f, h * 0.05f), Size(w * 0.06f, h * 0.045f), CornerRadius(8f))
    drawRect(PipoColors.screen, Offset(w * 0.478f, h * 0.06f), Size(w * 0.044f, h * 0.025f))
    // the ball: comes down the pitch, bounces once, grows as it nears you
    if (sim.phase == CricketSim.Phase.BOWLING) {
        val p = sim.progress.coerceAtMost(1.05f)
        val x = w * (0.5f + sim.drift * p); val y = h * (0.12f + p * 0.72f)
        val bounce = if (p < 0.6f) kotlin.math.sin(p / 0.6f * Math.PI.toFloat()) * h * 0.05f else kotlin.math.sin((p - 0.6f) / 0.45f * Math.PI.toFloat()).coerceAtLeast(0f) * h * 0.02f
        val r = w * (0.012f + p * 0.022f)
        drawOval(Color.Black.copy(alpha = 0.2f), Offset(x - r, y - r * 0.3f), Size(r * 2f, r * 0.6f))
        drawCircle(Color(0xFFC0392B), r, Offset(x, y - bounce))
        // the sweet spot
        drawCircle(Color.White.copy(alpha = 0.18f), w * 0.05f, Offset(w * 0.5f, h * 0.77f))
    }
    // your bat
    drawItem(ItemShape.BAT, Offset(w * 0.58f, h * 0.8f), w * 0.16f)
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
