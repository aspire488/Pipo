package com.pipo.robot.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.Sfx
import com.pipo.robot.ui.theme.PipoPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================ Rock Paper Scissors ============================ */

private enum class Hand { ROCK, PAPER, SCISSORS }
private fun beats(a: Hand, b: Hand) = (a == Hand.ROCK && b == Hand.SCISSORS) || (a == Hand.PAPER && b == Hand.ROCK) || (a == Hand.SCISSORS && b == Hand.PAPER)

private fun DrawScope.drawHand(h: Hand, c: Color) {
    val w = size.width
    when (h) {
        Hand.ROCK -> { drawCircle(c, w * 0.32f); drawCircle(Color.Black.copy(alpha = 0.12f), w * 0.07f, Offset(w * 0.42f, w * 0.42f)) }
        Hand.PAPER -> {
            drawRoundRect(c, Offset(w * 0.2f, w * 0.14f), Size(w * 0.6f, w * 0.72f), CornerRadius(w * 0.05f))
            for (i in 0..3) drawLine(Color.Black.copy(alpha = 0.15f), Offset(w * 0.3f, w * (0.3f + i * 0.13f)), Offset(w * 0.7f, w * (0.3f + i * 0.13f)), w * 0.03f)
        }
        Hand.SCISSORS -> {
            drawLine(c, Offset(w * 0.5f, w * 0.55f), Offset(w * 0.2f, w * 0.12f), w * 0.09f, StrokeCap.Round)
            drawLine(c, Offset(w * 0.5f, w * 0.55f), Offset(w * 0.8f, w * 0.12f), w * 0.09f, StrokeCap.Round)
            drawCircle(c, w * 0.13f, Offset(w * 0.33f, w * 0.72f), style = Stroke(w * 0.07f))
            drawCircle(c, w * 0.13f, Offset(w * 0.67f, w * 0.72f), style = Stroke(w * 0.07f))
        }
    }
}

@Composable
fun RpsGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("rps")
    val scope = rememberCoroutineScope()
    var you by remember { mutableIntStateOf(0) }
    var pipo by remember { mutableIntStateOf(0) }
    var yourHand by remember { mutableStateOf<Hand?>(null) }
    var pipoHand by remember { mutableStateOf<Hand?>(null) }
    var busy by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    val history = remember { mutableListOf<Hand>() }
    // Every Pipo has a favourite throw, tied to his personality.
    val favourite = remember { when { gp.traits.mischief > 0.55f -> Hand.SCISSORS; gp.traits.stubbornness > 0.55f -> Hand.ROCK; else -> Hand.PAPER } }

    fun pipoPick(): Hand {
        val r = gp.rng.nextFloat()
        val last = history.lastOrNull()
        return when {
            last != null && r < 0.3f + gp.traits.curiosity * 0.15f -> Hand.values().first { beats(it, last) } // "you always repeat"
            r < 0.55f -> favourite
            else -> Hand.values()[gp.rng.nextInt(3)]
        }
    }

    fun play(h: Hand) {
        if (busy || over) return
        busy = true
        scope.launch {
            yourHand = h; pipoHand = null
            for (w in listOf("Rock…", "Paper…", "Scissors!")) {
                gp.react(AnimState.SHAKE, Expr.FOCUSED, w, Sfx.BEEP, secs = 0.4f); delay(420)
            }
            val p = pipoPick()
            pipoHand = p
            history += h
            when {
                beats(p, h) -> { pipo++; gp.win() }
                beats(h, p) -> { you++; gp.lose() }
                else -> gp.draw()
            }
            delay(900)
            if (you == 3 || pipo == 3) {
                over = true
                val pw = pipo == 3
                gp.react(if (pw) AnimState.DANCING else AnimState.SAD, if (pw) Expr.PROUD else Expr.SAD, gp.finalWords(pw), if (pw) Sfx.WIN else Sfx.LOSE, secs = 3f)
                GameLog.record(gp.repo, "rps", pw, "Final score $pipo–$you (Pipo–you).")
            }
            busy = false
        }
    }

    GameScaffold("Rock Paper Scissors", gp, onExit, "Pipo $pipo  ·  You $you  ·  first to 3") {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
            HandSlot("Pipo", pipoHand, PipoPalette.mint)
            HandSlot("You", yourHand, PipoPalette.amber)
        }
        Spacer(Modifier.height(28.dp))
        if (over) GameOver(gp, if (pipo > you) "Pipo wins." else "You win!", onAgain = { you = 0; pipo = 0; yourHand = null; pipoHand = null; over = false; history.clear() }, onExit = onExit)
        else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Hand.values().forEach { h ->
                Box(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).background(PipoPalette.card).clickable(enabled = !busy) { play(h) }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(64.dp)) { drawHand(h, PipoPalette.amber) }
                }
            }
        }
    }
}

@Composable
private fun HandSlot(label: String, h: Hand?, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(PipoPalette.cardHi), contentAlignment = Alignment.Center) {
            if (h != null) Canvas(Modifier.size(70.dp)) { drawHand(h, c) } else Text("?", color = PipoPalette.muted, fontSize = 36.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = PipoPalette.muted)
    }
}

/* ============================ Reaction Race ============================ */

@Composable
fun ReactionGame(onExit: () -> Unit) {
    val gp = rememberGamePipo("reaction")
    val scope = rememberCoroutineScope()
    var round by remember { mutableIntStateOf(0) }
    var you by remember { mutableIntStateOf(0) }
    var pipo by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf("ready") } // ready, waiting, go, result, over
    var goAt by remember { mutableLongStateOf(0L) }
    var msg by remember { mutableStateOf("Tap when Pipo's antenna turns green.") }
    var best by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var roundId by remember { mutableIntStateOf(0) }
    // Pipo's reflexes depend on how awake he is.
    val pipoMs = { (230 + (1f - gp.energy) * 170 + gp.rng.nextInt(120)).toLong() }

    fun finishRound(youWon: Boolean?, text: String) {
        state = "result"; msg = text
        when (youWon) { true -> { you++; gp.lose() }; false -> { pipo++; gp.win() }; null -> gp.draw() }
        gp.rig.glow = 0xFF8FF5E2
        round++
        if (round >= 5) scope.launch {
            delay(900); state = "over"
            val pw = if (pipo == you) null else pipo > you
            gp.react(if (pw == true) AnimState.DANCING else AnimState.IDLE, if (pw == true) Expr.PROUD else Expr.CONTENT, gp.finalWords(pw), if (pw == true) Sfx.WIN else Sfx.BEEP, secs = 3f)
            GameLog.record(gp.repo, "reaction", pw, if (best < Long.MAX_VALUE) "Your best: ${best} ms." else "")
        }
    }

    fun start() {
        state = "waiting"; msg = "Wait for it…"
        val myId = ++roundId
        gp.react(AnimState.LISTENING, Expr.FOCUSED, "Ready…", null, secs = 10f)
        gp.rig.glow = 0xFFFF7A6B
        scope.launch {
            delay(1400L + gp.rng.nextInt(2600))
            if (state != "waiting" || myId != roundId) return@launch
            state = "go"; goAt = System.currentTimeMillis(); msg = "NOW!"
            gp.rig.glow = 0xFF6BFF8A
            gp.rig.showEmote(EmoteKind.EXCLAIM); gp.synth.sfx(Sfx.SURPRISED)
            val pm = pipoMs()
            delay(pm)
            if (state == "go" && myId == roundId) finishRound(false, "Pipo: $pm ms. Too slow!")
        }
    }

    fun tap() {
        if (round >= 5) return
        when (state) {
            "ready", "result" -> start()
            "waiting" -> { roundId++; finishRound(false, "Too early! Pipo laughs at you."); gp.react(AnimState.HAPPY, Expr.HAPPY, "Hehe. Too early.", Sfx.LAUGH) }
            "go" -> {
                val ms = System.currentTimeMillis() - goAt
                best = minOf(best, ms); roundId++
                finishRound(true, "You: $ms ms!")
            }
        }
    }

    GameScaffold("Reaction Race", gp, onExit, "Round ${minOf(round + 1, 5)}/5  ·  Pipo $pipo  ·  You $you") {
        if (state == "over") {
            GameOver(gp, if (pipo > you) "Pipo is faster. Today." else if (you > pipo) "Your fingers win!" else "Tie!",
                onAgain = { round = 0; you = 0; pipo = 0; state = "ready"; best = Long.MAX_VALUE; msg = "Tap when Pipo's antenna turns green." }, onExit = onExit)
        } else {
            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp))
                    .background(when (state) { "go" -> Color(0xFF2E7D5B); "waiting" -> Color(0xFF6B3A44); else -> PipoPalette.card })
                    .clickable { tap() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(msg, color = PipoPalette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        lineHeight = 30.sp, modifier = Modifier.padding(horizontal = 24.dp))
                    if (state == "ready" || state == "result") {
                        Spacer(Modifier.height(8.dp))
                        Text("tap to ${if (state == "ready") "start" else "go again"}", color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { gp.rig.glow = 0xFF8FF5E2 }
}
