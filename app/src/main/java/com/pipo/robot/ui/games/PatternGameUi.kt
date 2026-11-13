package com.pipo.robot.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.PatternRun
import com.pipo.robot.engine.Sfx
import com.pipo.robot.ui.theme.PipoPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================ Pipo's Pattern (co-op) ============================ */

private val padColors = listOf(Color(0xFF8FF5E2), Color(0xFFFFC27A), Color(0xFFC99BFF), Color(0xFFFF8A80))
private val padNames = listOf("mint", "amber", "lilac", "coral")
private val padTones = listOf(392f, 494f, 587f, 698f)   // G, B, D, F: a pleasant chord, each pad its own note

@Composable
fun PatternGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("pattern")
    val scope = rememberCoroutineScope()
    // how sharp he is right now decides whether he can catch your slip
    val recall = (0.35f + gp.traits.curiosity * 0.3f + gp.energy * 0.25f).coerceIn(0.2f, 0.9f)
    var run by remember { mutableStateOf(PatternRun(gp.rng, recall)) }
    var lit by remember { mutableIntStateOf(-1) }
    var showing by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var rounds by remember { mutableIntStateOf(0) }
    val best = remember { mutableIntStateOf(gp.repo.read { it.records["best:pattern"] ?: 0 }) }
    var newBest by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun flash(pad: Int, ms: Long) = scope.launch {
        lit = pad; gp.rig.glow = padColors[pad].toArgb().toLong() and 0xFFFFFFFFL
        gp.synth.tone(padTones[pad], ms.toInt().coerceAtMost(320))
        delay(ms); lit = -1; delay(ms / 3)
    }

    fun showRound() {
        val seq = run.next()
        showing = true
        job?.cancel()
        job = scope.launch {
            gp.react(AnimState.THINKING, Expr.FOCUSED, if (seq.size == 1) "Watch my antenna." else listOf("Again, plus one.", "Longer now.", "Watch closely.").random(gp.rng), secs = 30f)
            delay(650)
            for (p in seq) flash(p, run.flashMs()).join()
            showing = false
            gp.react(AnimState.LISTENING, Expr.CURIOUS, "Your turn.", null, secs = 30f)
        }
    }

    fun finish() {
        over = true
        val score = run.rounds
        rounds = score
        val isBest = score > best.intValue
        newBest = isBest
        if (isBest) { best.intValue = score; gp.repo.mutate { it.records["best:pattern"] = score } }
        gp.react(if (isBest) AnimState.CELEBRATE else AnimState.HAPPY, if (isBest) Expr.EXCITED else Expr.CONTENT,
            when { isBest && score > 0 -> "Round $score! Our best ever. Team Pipo."; score >= 5 -> "$score rounds. We're good at this."; else -> "We'll get it next time. I believe in us." },
            if (isBest) Sfx.WIN else Sfx.BEEP, if (isBest) EmoteKind.SPARKLE else null, 3f)
        gp.finalWords(null)
        GameLog.record(gp.repo, "pattern", null, "Reached round $score.", coop = true, coopBest = if (isBest && score > 0) score else null)
    }

    fun press(pad: Int) {
        if (showing || over) return
        gp.synth.tone(padTones[pad], 160)
        lit = pad
        scope.launch { delay(140); if (lit == pad) lit = -1 }
        when (run.press(pad)) {
            PatternRun.Step.HIT -> Unit
            PatternRun.Step.ROUND_DONE -> {
                rounds = run.rounds
                gp.react(AnimState.HAPPY, Expr.HAPPY, listOf("Yes!", "Perfect.", "We did it.", "Nice.").random(gp.rng), Sfx.HAPPY, secs = 0.9f)
                scope.launch { delay(900); showRound() }
            }
            PatternRun.Step.SAVED -> {
                val hint = run.expected()
                gp.react(AnimState.SURPRISED, Expr.SURPRISED, "Wait! I remember. It was ${padNames[hint]}. Keep going!", Sfx.SURPRISED, EmoteKind.IDEA, 2f)
                scope.launch { delay(350); flash(hint, 420) }
            }
            PatternRun.Step.OVER -> finish()
        }
    }

    fun restart() { run = PatternRun(gp.rng, recall); over = false; rounds = 0; newBest = false; gp.restart(); showRound() }

    GameScaffold("Pipo's Pattern", gp, onExit, "Round ${rounds + if (over) 0 else 1}  ·  Our best ${best.intValue}  ·  ${if (run.saveUsed) "Pipo's save used" else "Pipo has one save"}") {
        if (over) {
            GameOver(gp, if (newBest && rounds > 0) "New best: round $rounds!" else "You reached round $rounds", onAgain = { restart() }, onExit = onExit)
        } else {
            Column(Modifier.widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (c in 0 until 2) {
                        val pad = r * 2 + c
                        val col by animateColorAsState(if (lit == pad) padColors[pad] else padColors[pad].copy(alpha = 0.28f), tween(90), label = "pad")
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(col)
                                .clickable(enabled = !showing) { press(pad) }
                                .semantics { contentDescription = "${padNames[pad]} pad" },
                            contentAlignment = Alignment.Center,
                        ) { if (lit == pad) Text("●", color = Color(0xFF0F2A2A)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(if (showing) "Watch…" else if (run.sequence.isEmpty()) "Tap Start, then repeat what Pipo shows." else "Repeat the pattern.",
                color = PipoPalette.muted, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (run.sequence.isEmpty()) { Spacer(Modifier.height(12.dp)); PillButton("Start", { showRound() }) }
        }
    }
}
