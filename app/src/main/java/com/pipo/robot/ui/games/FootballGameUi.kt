package com.pipo.robot.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.FootballSim
import com.pipo.robot.engine.Sfx
import com.pipo.robot.ui.render.PipoLight
import com.pipo.robot.ui.render.drawPipo

/* ============================ Pipo Football ============================ */

@Composable
fun FootballGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("football")
    var seed by remember { mutableIntStateOf(gp.rng.nextInt()) }
    val sim = remember(seed) { FootballSim(seed, gp.energy * 0.4f + gp.traits.confidence * 0.4f) }
    var frame by remember { mutableLongStateOf(0L) }
    var you by remember(sim) { mutableIntStateOf(0) }
    var him by remember(sim) { mutableIntStateOf(0) }
    var secs by remember(sim) { mutableIntStateOf(FootballSim.MATCH_SECONDS.toInt()) }
    var phase by remember(sim) { mutableStateOf(sim.phase) }

    LaunchedEffect(sim) {
        gp.line = "Drag to run. Tap to kick. You shoot UP, I shoot DOWN. First to 3!"
        var last = 0L
        var goals = 0
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val ended = sim.step((t - last) / 1e9f)
                when (sim.consumeEvent()) {
                    1 -> gp.react(AnimState.ANNOYED, Expr.ANNOYED, listOf("Hey! That was my ball.", "Sneaky tackle.", "I was dribbling!").random(gp.rng), Sfx.GRUMBLE, secs = 1f)
                    2 -> gp.react(AnimState.HAPPY, Expr.MISCHIEF, listOf("Mine now!", "Tackled! Hehe.", "Too slow!").random(gp.rng), Sfx.GIGGLE, secs = 1f)
                }
                if (sim.yourGoals + sim.pipoGoals != goals) {
                    goals = sim.yourGoals + sim.pipoGoals
                    gp.synth.sfx(Sfx.WIN)
                    if (sim.lastGoalPipo == true) gp.win(listOf("GOOOAL! Did you see that?!", "Goal! Nib, write that down.", "Top corner. Obviously.").random(gp.rng))
                    else gp.lose(listOf("No! The goal moved. I'm sure it moved.", "Lucky shot. Very lucky.", "Okay. Okay. That one was good.").random(gp.rng))
                }
                if (ended && sim.claimResult()) {
                    val won = sim.pipoWon
                    GameLog.record(gp.repo, "football", won, "Final score: you ${sim.yourGoals}, Pipo ${sim.pipoGoals}.")
                    gp.react(
                        if (won == true) AnimState.CELEBRATE else if (won == false) AnimState.SAD else AnimState.LOOK_AROUND,
                        if (won == true) Expr.PROUD else if (won == false) Expr.SAD else Expr.SUSPICIOUS, gp.finalWords(won),
                        if (won == true) Sfx.WIN else if (won == false) Sfx.LOSE else Sfx.BOOP, if (won == true) EmoteKind.SPARKLE else null, 3f)
                }
            }
            last = t; frame = t
            you = sim.yourGoals; him = sim.pipoGoals; secs = sim.timeLeft.toInt(); phase = sim.phase
        }
    }

    GameScaffold("Pipo Football", gp, onExit, "You $you  ·  Pipo $him  ·  ${secs / 60}:${"%02d".format(secs % 60)}") {
        Box(
            Modifier.widthIn(max = 380.dp).aspectRatio(0.66f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(22.dp))
                .semantics { contentDescription = "Football pitch. Drag to run, tap to kick. You attack the top goal." }
                .pointerInput(sim) {
                    detectDragGestures(
                        onDragStart = { o -> sim.runTo(o.x / size.width, o.y / size.height) },
                        onDrag = { ch, _ -> ch.consume(); sim.runTo(ch.position.x / size.width, ch.position.y / size.height) },
                        onDragEnd = { sim.runTo(null, null) },
                        onDragCancel = { sim.runTo(null, null) },
                    )
                }
                .pointerInput(sim) {
                    detectTapGestures { o ->
                        when (sim.phase) {
                            FootballSim.Phase.READY -> sim.start()
                            FootballSim.Phase.PLAYING -> if (sim.kick(o.x / size.width, o.y / size.height)) gp.synth.sfx(Sfx.KICK) else sim.runTo(o.x / size.width, o.y / size.height)
                            FootballSim.Phase.OVER -> { gp.restart(); seed++ }
                            else -> Unit
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                drawPitch(sim)
                drawPlayers(sim, gp)
            }
            when (phase) {
                FootballSim.Phase.READY -> Text("Kick-off!\ntap to start", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                FootballSim.Phase.GOAL -> Text(if (sim.lastGoalPipo == true) "PIPO SCORES!" else "GOAL!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp)
                FootballSim.Phase.OVER -> Text(when (sim.pipoWon) { true -> "Pipo wins $him–$you"; false -> "You win $you–$him!"; null -> "Draw $you–$him" } + "\ntap to play again",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                else -> Unit
            }
        }
    }
}

private val grassA = Color(0xFF4E9A52)
private val grassB = Color(0xFF468C4A)
private val line = Color.White.copy(alpha = 0.75f)

private fun DrawScope.drawPitch(sim: FootballSim) {
    val w = size.width; val h = size.height
    for (i in 0 until 8) drawRect(if (i % 2 == 0) grassA else grassB, Offset(0f, h * i / 8f), Size(w, h / 8f + 1f))
    val st = Stroke(3f)
    drawRect(line, Offset(6f, 6f), Size(w - 12f, h - 12f), style = st)
    drawLine(line, Offset(6f, h / 2f), Offset(w - 6f, h / 2f), 3f)
    drawCircle(line, w * 0.14f, Offset(w / 2f, h / 2f), style = st)
    drawCircle(line, 5f, Offset(w / 2f, h / 2f))
    // penalty boxes
    drawRect(line, Offset(w * 0.25f, 6f), Size(w * 0.5f, h * 0.14f), style = st)
    drawRect(line, Offset(w * 0.25f, h - 6f - h * 0.14f), Size(w * 0.5f, h * 0.14f), style = st)
    // goals: Pipo defends the top, you the bottom
    val gl = (0.5f - FootballSim.GOAL_HALF) * w; val gw = FootballSim.GOAL_HALF * 2 * w
    drawRect(Color(0xFF8FF5E2).copy(alpha = 0.35f), Offset(gl, 0f), Size(gw, 10f))
    drawRect(Color.White, Offset(gl, 0f), Size(gw, 10f), style = Stroke(4f))
    drawRect(Color(0xFFFFC27A).copy(alpha = 0.35f), Offset(gl, h - 10f), Size(gw, 10f))
    drawRect(Color.White, Offset(gl, h - 10f), Size(gw, 10f), style = Stroke(4f))
}

private fun DrawScope.drawPlayers(sim: FootballSim, gp: GamePipo) {
    val w = size.width; val h = size.height
    val r = w * 0.045f
    // ball: a soft shadow, a lit sphere
    val b = Offset(sim.ball.x * w, sim.ball.y * h)
    drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.55f, b + Offset(3f, 5f))
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFD9DEE6)), b - Offset(r * 0.2f, r * 0.2f), r * 0.6f), r * 0.5f, b)
    drawCircle(Color(0xFF2B3342), r * 0.15f, b)
    // you: an amber player with a ring
    val y = Offset(sim.you.x * w, sim.you.y * h)
    drawCircle(Color.Black.copy(alpha = 0.22f), r, y + Offset(2f, 5f))
    drawCircle(Color(0xFFFFC27A), r, y)
    drawCircle(Color.White, r, y, style = Stroke(3f))
    if (sim.owner == 1) drawCircle(Color.White.copy(alpha = 0.5f), r * 1.5f, y, style = Stroke(2f))
    // Pipo: himself, small, the same rig that's commentating above
    val p = Offset(sim.pipo.x * w, sim.pipo.y * h)
    drawCircle(Color.Black.copy(alpha = 0.22f), r, p + Offset(2f, 5f))
    drawPipo(gp.rig, p.x, p.y + r * 0.9f, r * 3.2f, shadow = false, light = PipoLight(dir = -0.4f, keyStrength = 0.7f))
    if (sim.owner == 2) drawCircle(Color(0xFF8FF5E2).copy(alpha = 0.6f), r * 1.5f, p, style = Stroke(2f))
}
