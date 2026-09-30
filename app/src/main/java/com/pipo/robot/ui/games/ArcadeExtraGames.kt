package com.pipo.robot.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.Sfx
import com.pipo.robot.ui.theme.PipoPalette
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private fun DrawScope.pixelRect(x: Float, y: Float, w: Float, h: Float, c: Color, unit: Float) {
    drawRect(c, Offset((x / unit).toInt() * unit, (y / unit).toInt() * unit), androidx.compose.ui.geometry.Size((w / unit).toInt().coerceAtLeast(1) * unit, (h / unit).toInt().coerceAtLeast(1) * unit))
}

/* ============================== Flappy Pipo ============================== */

private data class Pipe(val x: Float, val gapY: Float, val gap: Float, val passed: Boolean = false)

@Composable
fun FlappyPipoGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("flappy")
    val rng = remember { Random(System.nanoTime()) }
    var birdY by remember { mutableFloatStateOf(0f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var resetToken by remember { mutableIntStateOf(0) }
    val pipes = remember { mutableStateListOf<Pipe>() }

    fun reset() {
        birdY = 0f
        velocity = 0f
        score = 0
        started = false
        over = false
        pipes.clear()
        pipes += Pipe(1.15f, 0.45f + rng.nextFloat() * 0.18f, 0.28f)
        pipes += Pipe(1.75f, 0.38f + rng.nextFloat() * 0.25f, 0.27f)
    }

    LaunchedEffect(resetToken) {
        reset()
        gp.restart()
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && started && !over) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.04f)
                    velocity += 1450f * dt
                    birdY += velocity * dt
                    val moved = pipes.map { it.copy(x = it.x - 0.48f * dt) }
                    pipes.clear()
                    pipes.addAll(moved)
                    if (pipes.lastOrNull()?.x ?: 0f < 1.1f) {
                        pipes += Pipe(1.35f, 0.28f + rng.nextFloat() * 0.42f, 0.27f)
                    }
                    val updated = pipes.map {
                        if (!it.passed && it.x < 0.23f) {
                            score += 1
                            it.copy(passed = true)
                        } else it
                    }
                    pipes.clear(); pipes.addAll(updated.filter { it.x > -0.2f })
                    val hitPipe = pipes.any { p ->
                        val dx = abs(p.x - 0.23f)
                        dx < 0.055f && (birdY < p.gapY - p.gap / 2f || birdY > p.gapY + p.gap / 2f)
                    }
                    if (birdY < 0.04f || birdY > 0.94f || hitPipe) {
                        over = true
                        gp.react(AnimState.FALLEN, Expr.SURPRISED, "I hit the pipes. The pipes started it.", Sfx.SURPRISED, secs = 2.2f)
                        GameLog.record(gp.repo, "flappy", false, "Score $score")
                    }
                }
                last = now
            }
        }
    }

    GameScaffold("Flappy Pipo", gp, onExit, "Score $score  ·  best effort: don't hit anything") {
        if (over) {
            GameOver(gp, "You got $score. The pipes remain undefeated.", onAgain = { resetToken++ }, onExit = onExit)
        } else {
            Box(
                Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF182B38))
                    .pointerInput(started, over) {
                        detectTapGestures {
                            if (!started) {
                                started = true
                                gp.react(AnimState.EXCITED, Expr.HAPPY, "FLAP FLAP! I have wings now!", Sfx.HAPPY)
                            }
                            if (!over) velocity = -520f
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(360.dp)) {
                    val unit = 8f
                    val birdX = size.width * 0.23f
                    val y = size.height * birdY
                    drawRect(Color(0xFF203C4C))
                    drawRect(Color(0xFF2F5867), Offset(0f, size.height * 0.86f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.14f))
                    pipes.forEach { p ->
                        val x = size.width * p.x
                        val gapY = size.height * p.gapY
                        val gapH = size.height * p.gap
                        drawRect(Color(0xFF75B86A), Offset(x - 22f, 0f), androidx.compose.ui.geometry.Size(44f, gapY - gapH / 2f))
                        drawRect(Color(0xFF75B86A), Offset(x - 22f, gapY + gapH / 2f), androidx.compose.ui.geometry.Size(44f, size.height - (gapY + gapH / 2f)))
                        drawRect(Color(0xFF9BD886), Offset(x - 28f, gapY - gapH / 2f - 12f), androidx.compose.ui.geometry.Size(56f, 12f))
                        drawRect(Color(0xFF9BD886), Offset(x - 28f, gapY + gapH / 2f), androidx.compose.ui.geometry.Size(56f, 12f))
                    }
                    pixelRect(birdX - 18f, y - 14f, 30f, 28f, PipoPalette.amber, unit)
                    pixelRect(birdX + 10f, y - 6f, 12f, 10f, Color.White, unit)
                    pixelRect(birdX + 15f, y - 3f, 5f, 5f, Color.Black, unit)
                    pixelRect(birdX - 8f, y + 8f, 18f, 7f, Color(0xFFE08D4A), unit)
                }
                if (!started) TextOverlay("TAP TO FLAP", "Pipo has no idea what he's doing.", PipoPalette.mint)
            }
        }
    }
}

/* ============================== Pixel Shooter/ ============================== */

private data class Enemy(var x: Float, var y: Float, var speed: Float, val kind: Int, var hp: Int)
private data class Bullet(var x: Float, var y: Float)

@Composable
fun PixelShooterGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("shooter")
    val rng = remember { Random(System.nanoTime()) }
    var playerX by remember { mutableFloatStateOf(0.5f) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var over by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var spawnClock by remember { mutableFloatStateOf(0f) }
    var fireClock by remember { mutableFloatStateOf(0f) }
    var resetToken by remember { mutableIntStateOf(0) }
    val enemies = remember { mutableStateListOf<Enemy>() }
    val bullets = remember { mutableStateListOf<Bullet>() }

    fun reset() {
        playerX = 0.5f
        score = 0
        lives = 3
        over = false
        started = false
        spawnClock = 0f
        fireClock = 0f
        enemies.clear(); bullets.clear()
    }

    LaunchedEffect(resetToken) { reset(); gp.restart() }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && started && !over) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.04f)
                    spawnClock += dt
                    fireClock += dt
                    if (fireClock > 0.24f) {
                        fireClock = 0f
                        bullets += Bullet(playerX, 0.86f)
                    }
                    if (spawnClock > (0.65f - (score.coerceAtMost(30) * 0.008f))) {
                        spawnClock = 0f
                        val kind = if (rng.nextFloat() < 0.18f) 1 else 0
                        enemies += Enemy(0.08f + rng.nextFloat() * 0.84f, -0.05f, 0.18f + rng.nextFloat() * 0.12f, kind, if (kind == 1) 2 else 1)
                    }
                    bullets.forEach { it.y -= 0.95f * dt }
                    enemies.forEach { it.y += it.speed * dt }

                    val deadBullets = mutableListOf<Bullet>()
                    val deadEnemies = mutableListOf<Enemy>()
                    for (b in bullets) {
                        val hit = enemies.firstOrNull { e -> abs(e.x - b.x) < 0.045f && abs(e.y - b.y) < 0.055f }
                        if (hit != null) {
                            deadBullets += b
                            hit.hp -= 1
                            if (hit.hp <= 0) { deadEnemies += hit; score += if (hit.kind == 1) 3 else 1; gp.win() }
                        }
                    }
                    bullets.removeAll(deadBullets)
                    enemies.removeAll(deadEnemies)

                    val reached = enemies.filter { it.y > 1.02f }.toList()
                    if (reached.isNotEmpty()) {
                        enemies.removeAll(reached)
                        lives -= reached.size.coerceAtMost(2)
                        gp.lose("One got past me. I need better aim!")
                    }
                    if (lives <= 0) {
                        over = true
                        gp.react(AnimState.SULK, Expr.ANNOYED, "They got through. Rematch. Obviously.", Sfx.GRUMBLE, secs = 2.4f)
                        GameLog.record(gp.repo, "shooter", false, "Score $score")
                    }
                }
                last = now
            }
        }
    }

    GameScaffold("Pixel Shooter", gp, onExit, "Score $score  ·  ♥ $lives  ·  auto-fire") {
        if (over) {
            GameOver(gp, "You scored $score.", onAgain = { resetToken++ }, onExit = onExit)
        } else {
            Box(
                Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF090D1A))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                playerX = (playerX + amount.x / 320f).coerceIn(0.06f, 0.94f)
                                started = true
                            },
                            onDragEnd = { started = true },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { started = true; playerX = (it.x / 320f).coerceIn(0.06f, 0.94f) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(360.dp)) {
                    drawRect(Color(0xFF090D1A))
                    val grid = 32f
                    for (x in 0..(size.width / grid).toInt()) drawRect(Color(0xFF15223A), Offset(x * grid, 0f), androidx.compose.ui.geometry.Size(1f, size.height))
                    for (y in 0..(size.height / grid).toInt()) drawRect(Color(0xFF15223A), Offset(0f, y * grid), androidx.compose.ui.geometry.Size(size.width, 1f))
                    bullets.forEach { b ->
                        drawRect(PipoPalette.mint, Offset(b.x * size.width - 3f, b.y * size.height), androidx.compose.ui.geometry.Size(6f, 18f))
                    }
                    enemies.forEach { e ->
                        val x = e.x * size.width
                        val y = e.y * size.height
                        val c = if (e.kind == 1) PipoPalette.pink else PipoPalette.lilac
                        drawRect(c, Offset(x - 14f, y - 14f), androidx.compose.ui.geometry.Size(28f, 28f))
                        drawRect(Color.Black.copy(alpha = 0.28f), Offset(x - 6f, y - 5f), androidx.compose.ui.geometry.Size(5f, 5f))
                        drawRect(Color.Black.copy(alpha = 0.28f), Offset(x + 4f, y - 5f), androidx.compose.ui.geometry.Size(5f, 5f))
                        if (e.kind == 1) drawRect(PipoPalette.amber, Offset(x - 19f, y - 4f), androidx.compose.ui.geometry.Size(5f, 9f))
                    }
                    val px = playerX * size.width
                    drawRect(PipoPalette.mint, Offset(px - 18f, size.height - 42f), androidx.compose.ui.geometry.Size(36f, 24f))
                    drawRect(PipoPalette.amber, Offset(px - 6f, size.height - 58f), androidx.compose.ui.geometry.Size(12f, 18f))
                    drawRect(PipoPalette.text, Offset(px - 4f, size.height - 70f), androidx.compose.ui.geometry.Size(8f, 12f))
                }
                if (!started) TextOverlay("DRAG / TAP TO MOVE", "Pipo auto-fires. Keep the sky clear.", PipoPalette.mint)
            }
        }
    }
}

@Composable
private fun TextOverlay(title: String, subtitle: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = accent, fontSize = 24.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = PipoPalette.muted, fontSize = 13.sp)
    }
}
