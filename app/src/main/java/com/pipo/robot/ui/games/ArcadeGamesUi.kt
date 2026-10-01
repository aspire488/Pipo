package com.pipo.robot.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.ArcadePhase
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.FlappySim
import com.pipo.robot.engine.Sfx
import com.pipo.robot.engine.ShooterSim
import com.pipo.robot.ui.render.PipoColors
import com.pipo.robot.ui.theme.PipoPalette

/** Records a finished arcade run against Pipo's own best, once. Returns (your best before, Pipo's best). */
private fun recordRun(gp: GamePipo, game: String, score: Int): Pair<Int, Int> {
    val (yourBest, pipoBest) = gp.repo.mutate { s ->
        val y = s.records["best:$game"] ?: 0
        if (score > y) s.records["best:$game"] = score
        y to (s.records["pipo:$game"] ?: 0)
    }
    // "winning" an arcade game = beating Pipo's own best on his cabinet
    val pipoWon: Boolean? = if (pipoBest == 0) null else score <= pipoBest
    GameLog.record(gp.repo, game, pipoWon, "Your score: $score. Pipo's best: $pipoBest.")
    return yourBest to pipoBest
}

/* ============================ Flappy Pipo ============================ */

@Composable
fun FlappyGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("flappy")
    var seed by remember { mutableIntStateOf(gp.rng.nextInt()) }
    val sim = remember(seed) { FlappySim(seed) }
    var frame by remember { mutableLongStateOf(0L) }
    val pipoBest = remember { gp.repo.read { it.records["pipo:flappy"] ?: 0 } }
    // the sim is plain Kotlin: these are what the text on screen reads, synced every frame
    var score by remember(sim) { mutableIntStateOf(0) }
    var phase by remember(sim) { mutableStateOf(sim.phase) }
    LaunchedEffect(sim) {
        gp.line = if (pipoBest > 0) "My best is $pipoBest. On my cabinet. Tap to hop." else "Tap to make me hop. Gently. I'm delicate."
        var last = 0L
        var lastScore = 0
        while (true) withFrameNanos { t ->
            if (last != 0L && sim.step((t - last) / 1e9f)) {
                if (sim.claimResult()) {
                    val (yours, pb) = recordRun(gp, "flappy", sim.score)
                    val line = when {
                        sim.score == 0 -> "I hit the first one. Don't look at me."
                        pb > 0 && sim.score > pb -> "${sim.score}?! That's more than MY best. Rematch. Now."
                        sim.score > yours -> "${sim.score}! Your best ever. I was steering. Mostly."
                        else -> "${sim.score}. The pipes came out of nowhere. They always do."
                    }
                    gp.react(if (sim.score > pb && pb > 0) AnimState.SULK else AnimState.DIZZY, if (sim.score > pb && pb > 0) Expr.ANNOYED else Expr.DIZZY, line, Sfx.LOSE, secs = 2.5f)
                }
            }
            if (sim.score != lastScore) {
                lastScore = sim.score
                if (sim.score % 5 == 0) gp.react(AnimState.HAPPY, Expr.EXCITED, listOf("${sim.score}! Keep going!", "Wheee!", "I'm flying! Sort of!").random(gp.rng), Sfx.HAPPY, EmoteKind.SPARKLE, 0.8f)
            }
            last = t; frame = t; score = sim.score; phase = sim.phase
        }
    }
    GameScaffold("Flappy Pipo", gp, onExit, "Score $score  ·  Pipo's best $pipoBest") {
        Box(
            Modifier.widthIn(max = 380.dp).aspectRatio(0.72f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(22.dp))
                .semantics { contentDescription = "Flappy Pipo. Tap to hop. Score $score." }
                .pointerInput(sim) { detectTapGestures { if (sim.phase == ArcadePhase.OVER) { gp.restart(); seed++ } else if (sim.flap()) { gp.synth.sfx(Sfx.BOOP); phase = sim.phase } } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                drawFlappy(sim)
            }
            if (phase != ArcadePhase.READY) {
                val pop by androidx.compose.animation.core.animateFloatAsState(if (score % 2 == 0) 1f else 1.001f, androidx.compose.animation.core.keyframes { durationMillis = 220; 1.35f at 80 }, label = "score")
                Text("$score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).graphicsLayer { scaleX = pop; scaleY = pop })
            }
            when (phase) {
                ArcadePhase.READY -> Text("tap to start", color = Color.White, fontWeight = FontWeight.Bold)
                ArcadePhase.OVER -> Text("Score $score\ntap to try again", color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawFlappy(sim: FlappySim) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8EC5E8), Color(0xFFCFE8F2))))
    drawRect(Color(0xFF7FB77E), Offset(0f, h * 0.94f), Size(w, h * 0.06f))
    for (p in sim.pipes) {
        val x = p.x * w; val pw = sim.pipeW * w
        val top = (p.gapY - p.gap / 2f) * h; val bot = (p.gapY + p.gap / 2f) * h
        drawRect(Color(0xFF8FA3A6), Offset(x, 0f), Size(pw, top))
        drawRect(Color(0xFF8FA3A6), Offset(x, bot), Size(pw, h - bot))
        drawRect(Color(0xFF6E8285), Offset(x - pw * 0.08f, top - h * 0.03f), Size(pw * 1.16f, h * 0.03f))
        drawRect(Color(0xFF6E8285), Offset(x - pw * 0.08f, bot), Size(pw * 1.16f, h * 0.03f))
    }
    // pixel Pipo: shell, screen, two eyes; tilts with his speed
    val c = Offset(sim.birdX * w, sim.y * h)
    val r = sim.radius * w
    drawCircle(PipoColors.shell, r * 1.2f, c)
    drawRect(PipoColors.screen, Offset(c.x - r * 0.8f, c.y - r * 0.6f), Size(r * 1.6f, r * 1.1f))
    val squint = if (sim.phase == ArcadePhase.OVER) 0.15f else 1f
    drawRect(PipoColors.eye, Offset(c.x - r * 0.5f, c.y - r * 0.3f), Size(r * 0.3f, r * 0.45f * squint))
    drawRect(PipoColors.eye, Offset(c.x + r * 0.2f, c.y - r * 0.3f), Size(r * 0.3f, r * 0.45f * squint))
    drawLine(PipoColors.joint, Offset(c.x, c.y - r * 1.2f), Offset(c.x + sim.vy * r * 0.4f, c.y - r * 1.9f), r * 0.15f)
    drawCircle(Color(0xFFFFC27A), r * 0.22f, Offset(c.x + sim.vy * r * 0.4f, c.y - r * 1.95f))
}

/* ============================ Pixel Shooter ============================ */

@Composable
fun ShooterGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("shooter")
    var seed by remember { mutableIntStateOf(gp.rng.nextInt()) }
    val sim = remember(seed) { ShooterSim(seed) }
    var frame by remember { mutableLongStateOf(0L) }
    val pipoBest = remember { gp.repo.read { it.records["pipo:shooter"] ?: 0 } }
    val bursts = remember(sim) { mutableListOf<FloatArray>() } // x, y, age
    var hitFlash by remember(sim) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var score by remember(sim) { mutableIntStateOf(0) }
    var lives by remember(sim) { mutableIntStateOf(sim.lives) }
    var phase by remember(sim) { mutableStateOf(sim.phase) }
    LaunchedEffect(sim) {
        gp.line = if (pipoBest > 0) "My best is $pipoBest. Drag to move. The ship shoots by itself." else "Drag to move. It shoots by itself. I'll cheer."
        var aliveBefore = sim.invaders.filter { it.alive }.map { it.x to it.y }
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val dt = (t - last) / 1e9f
                val ended = sim.step(dt)
                val aliveNow = sim.invaders.filter { it.alive }.map { it.x to it.y }
                if (aliveNow.size < aliveBefore.size && sim.event != "wave") aliveBefore.filter { a -> aliveNow.none { kotlin.math.abs(it.first - a.first) < 0.02f && kotlin.math.abs(it.second - a.second) < 0.02f } }
                    .take(2).forEach { bursts += floatArrayOf(it.first, it.second, 0f) }
                aliveBefore = aliveNow
                bursts.forEach { it[2] += dt }; bursts.removeAll { it[2] > 0.4f }
                hitFlash = (hitFlash - dt * 3f).coerceAtLeast(0f)
                if (sim.event == "hit" && !gp.calm) hitFlash = 1f
                when (sim.event) {
                    "hit" -> gp.react(AnimState.SURPRISED, Expr.SURPRISED, listOf("Ow! I felt that.", "They hit us!", "Dodge! DODGE!").random(gp.rng), Sfx.SURPRISED, secs = 0.8f)
                    "wave" -> gp.react(AnimState.CELEBRATE, Expr.EXCITED, "Wave ${sim.wave}! They're angrier now.", Sfx.WIN, EmoteKind.SPARKLE, 1.2f)
                }
                if (ended && sim.claimResult()) {
                    val (_, pb) = recordRun(gp, "shooter", sim.score)
                    val beat = pb > 0 && sim.score > pb
                    gp.react(if (beat) AnimState.SULK else AnimState.SAD, if (beat) Expr.ANNOYED else Expr.SAD,
                        if (beat) "${sim.score}. That's more than me. I'm going to practise all night." else "${sim.score} points. The moths won this time.", Sfx.LOSE, secs = 2.5f)
                }
            }
            last = t; frame = t; score = sim.score; lives = sim.lives; phase = sim.phase
        }
    }
    GameScaffold("Pixel Shooter", gp, onExit, "Score $score  ·  Lives $lives  ·  Pipo's best $pipoBest") {
        Box(
            Modifier.widthIn(max = 380.dp).aspectRatio(0.8f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(22.dp)).background(Color(0xFF0E1320))
                .semantics { contentDescription = "Pixel Shooter. Drag to move. Score $score, lives $lives." }
                .pointerInput(sim) {
                    detectTapGestures {
                        if (sim.phase == ArcadePhase.OVER) { gp.restart(); seed++ } else { sim.start(); sim.moveTo(it.x / size.width) }
                    }
                }
                .pointerInput(sim) { detectDragGestures { ch, _ -> sim.start(); sim.moveTo(ch.position.x / size.width) } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                drawShooter(sim)
                for (b in bursts) for (i in 0 until 6) {
                    val a = i * 1.047f; val r = b[2] * size.width * 0.12f
                    drawRect(Color(0xFFE8DCC2).copy(alpha = 1f - b[2] / 0.4f), Offset(b[0] * size.width + kotlin.math.cos(a) * r, b[1] * size.height + kotlin.math.sin(a) * r), Size(size.width / 90f, size.width / 90f))
                }
                if (hitFlash > 0f) drawRect(Color(0xFFFF7A6B).copy(alpha = 0.25f * hitFlash))
            }
            when (phase) {
                ArcadePhase.READY -> Text("drag or tap to start", color = PipoColors.eye, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                ArcadePhase.OVER -> Text("Score $score\ntap to play again", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = PipoColors.eye, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawShooter(sim: ShooterSim) {
    val w = size.width; val h = size.height
    val px = w / 64f // chunky pixels
    fun pixel(cx: Float, cy: Float, dx: Int, dy: Int, c: Color) = drawRect(c, Offset(cx + dx * px, cy + dy * px), Size(px, px))
    for (i in 0 until 30) drawRect(Color.White.copy(alpha = 0.25f), Offset(((i * 37) % 64) * px, ((i * 53) % 80) * px * 0.8f), Size(px * 0.5f, px * 0.5f))
    // moths: two wing pixels flapping around a body
    for (m in sim.invaders.filter { it.alive }) {
        val cx = m.x * w; val cy = m.y * h
        val flap = if ((m.x * 40).toInt() % 2 == 0) -1 else 0
        for (dx in -1..0) pixel(cx, cy, dx, 0, Color(0xFFE8DCC2))
        pixel(cx, cy, -3, flap, Color(0xFFC9B79C)); pixel(cx, cy, -2, flap, Color(0xFFC9B79C))
        pixel(cx, cy, 1, flap, Color(0xFFC9B79C)); pixel(cx, cy, 2, flap, Color(0xFFC9B79C))
    }
    for (s in sim.shots) drawRect(if (s.up) PipoColors.eye else Color(0xFFFF7A6B), Offset(s.x * w - px * 0.4f, s.y * h), Size(px * 0.8f, px * 2f))
    // the ship: a pixel Pipo head
    val cx = sim.shipX * w; val cy = sim.shipY * h
    for (dx in -3..2) for (dy in -2..1) pixel(cx, cy, dx, dy, PipoColors.shell)
    for (dx in -2..1) for (dy in -1..0) pixel(cx, cy, dx, dy, PipoColors.screen)
    pixel(cx, cy, -2, -1, PipoColors.eye); pixel(cx, cy, 1, -1, PipoColors.eye)
    pixel(cx, cy, 0, -3, Color(0xFFFFC27A))
    for (i in 0 until sim.lives) drawCircle(PipoPalette.pink, px * 0.8f, Offset(px * (2 + i * 3), px * 2))
}
