package com.pipo.robot.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.data.Mood
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.data.Traits
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.Dialogue
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.Sfx
import com.pipo.robot.ui.common.PipoTopBar
import com.pipo.robot.ui.home.moodGlow
import com.pipo.robot.ui.render.PipoRig
import com.pipo.robot.ui.render.drawEmote
import com.pipo.robot.ui.render.drawPipo
import com.pipo.robot.ui.theme.PipoPalette
import com.pipo.robot.voice.SoundSynth
import kotlin.random.Random

/** Pipo as a game opponent: his own rig, a bubble, sounds, and his real personality. */
class GamePipo(val repo: PipoRepository) {
    val rng = Random(System.nanoTime())
    val rig = PipoRig(rng.nextInt())
    val synth = SoundSynth()
    val traits: Traits = repo.read { it.profile.traits.copy() }
    val mood: Mood = repo.read { it.mood.current }
    val energy: Float = repo.read { it.mood.energy }
    var line by mutableStateOf("")
    var frame by mutableLongStateOf(0L)
    private var holdUntil = 0f

    init {
        synth.enabled = repo.read { it.settings.sounds }
        rig.glow = moodGlow(mood)
        rig.expr = if (traits.confidence > 0.5f) Expr.MISCHIEF else Expr.CURIOUS
        line = Dialogue.pick(if (traits.confidence > 0.5f) Dialogue.gameStartConfident else Dialogue.gameStartNervous, rng)
    }

    fun react(anim: AnimState, expr: Expr, text: String? = null, sfx: Sfx? = null, emote: EmoteKind? = null, secs: Float = 1.4f) {
        rig.anim = anim; rig.expr = expr
        holdUntil = rig.time + secs
        text?.let { line = it }
        sfx?.let { synth.sfx(it) }
        emote?.let { rig.showEmote(it) }
    }

    fun win(t: String? = null) = react(AnimState.HAPPY, Expr.PROUD, t ?: Dialogue.pick(Dialogue.pipoWinsRound, rng), Sfx.HAPPY, EmoteKind.SPARKLE)
    fun lose(t: String? = null) = react(AnimState.ANNOYED, Expr.ANNOYED, t ?: Dialogue.pick(Dialogue.pipoLosesRound, rng), Sfx.GRUMBLE)
    fun draw(t: String? = null) = react(AnimState.LOOK_AROUND, Expr.SUSPICIOUS, t ?: Dialogue.pick(Dialogue.drawRound, rng), Sfx.BOOP)
    fun think(t: String? = null) = react(AnimState.THINKING, Expr.FOCUSED, t, null, EmoteKind.DOTS, 30f)

    fun update(dt: Float) {
        if (rig.time > holdUntil && rig.anim != AnimState.IDLE && rig.anim != AnimState.THINKING) {
            rig.anim = AnimState.IDLE
            rig.expr = if (mood == Mood.SLEEPY) Expr.SLEEPY else Expr.CONTENT
        }
        rig.update(dt)
        frame++
    }

    fun finalWords(pipoWon: Boolean?): String = when (pipoWon) {
        true -> Dialogue.pick(Dialogue.pipoWinsGame, rng)
        false -> Dialogue.pick(if (traits.confidence > 0.6f) Dialogue.overconfidentLoss else Dialogue.pipoLosesGame, rng)
        null -> "A tie. Which means I won. Emotionally."
    }
}

@Composable
fun rememberGamePipo(): GamePipo {
    val ctx = LocalContext.current
    val gp = remember { GamePipo(PipoRepository.get(ctx)) }
    LaunchedEffect(gp) {
        var last = 0L
        while (true) withFrameNanos { t -> if (last != 0L) gp.update((t - last) / 1e9f); last = t }
    }
    return gp
}

@Composable
fun MiniPipo(gp: GamePipo, height: Dp = 150.dp) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp)).background(PipoPalette.paper).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(gp.line, color = PipoPalette.ink, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            @Suppress("UNUSED_VARIABLE") val tick = gp.frame
            val h = size.height * 0.8f
            drawPipo(gp.rig, size.width / 2f, size.height * 0.95f, h)
            drawEmote(gp.rig, size.width / 2f, size.height * 0.95f - h, h / 100f)
        }
    }
}

@Composable
fun GameScaffold(title: String, gp: GamePipo, onExit: () -> Unit, score: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(PipoPalette.night)) {
        PipoTopBar(title, onExit)
        MiniPipo(gp)
        Text(score, Modifier.fillMaxWidth().padding(vertical = 6.dp), color = PipoPalette.muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        Column(
            Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, primary: Boolean = true) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (primary) PipoPalette.mint else PipoPalette.cardHi)
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp)
    ) { Text(text, color = if (primary) Color(0xFF0F2A2A) else PipoPalette.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@Composable
fun GameOver(gp: GamePipo, headline: String, onAgain: () -> Unit, onExit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(headline, color = PipoPalette.text, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Again", onAgain)
            PillButton("Back to room", onExit, primary = false)
        }
    }
}

@Composable
fun GameHost(id: String, onExit: () -> Unit) {
    when (id) {
        "rps" -> RpsGame(onExit)
        "memory" -> MemoryGame(onExit)
        "reaction" -> ReactionGame(onExit)
        else -> TicTacToeGame(onExit)
    }
}
