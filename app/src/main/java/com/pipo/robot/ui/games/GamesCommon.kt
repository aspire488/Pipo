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
import androidx.compose.runtime.DisposableEffect
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
import com.pipo.robot.ui.render.PipoLight
import com.pipo.robot.ui.render.PipoRig
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pipo.robot.ui.render.drawEmote
import com.pipo.robot.ui.render.drawPipo
import com.pipo.robot.ui.theme.PipoPalette
import com.pipo.robot.voice.PipoVoice
import com.pipo.robot.voice.SoundSynth
import com.pipo.robot.voice.charsPerSecond
import com.pipo.robot.engine.SpeechStyles
import kotlin.random.Random

/** Pipo as a game opponent: his own rig, a bubble, sounds, and his real personality. */
class GamePipo(val repo: PipoRepository, val gameId: String = "", private val voice: PipoVoice? = null) {
    val rng = Random(System.nanoTime())
    val rig = PipoRig(rng.nextInt())
    val synth = SoundSynth()
    val traits: Traits = repo.read { it.profile.traits.copy() }
    val mood: Mood = repo.read { it.mood.current }
    val energy: Float = repo.read { it.mood.energy }
    private var shown by mutableStateOf("")
    /** What he says. Setting it shows the bubble and (in Spoken mode) he says it out loud, mouth in sync. */
    var line: String
        get() = shown
        set(v) { if (v != shown) { shown = v; say(v) } }
    var frame by mutableLongStateOf(0L)
    private var holdUntil = 0f
    private var lastReactAt = 0f
    private var distractions = 0
    private var pipoRun = 0
    private var userRun = 0
    private var finished = false

    init {
        synth.enabled = repo.read { it.settings.sounds }
        voice?.let { v -> v.mode = repo.read { it.settings.voiceMode }; v.synth.enabled = synth.enabled }
        rig.glow = moodGlow(mood)
        rig.energy = energy
        rig.mood = mood
        rig.expr = if (traits.confidence > 0.5f) Expr.MISCHIEF else Expr.CURIOUS
        line = openingLine()
    }

    /** He remembers how it's been going between you two in this game. */
    private fun openingLine(): String {
        val rec = if (gameId.isBlank()) null else repo.read { it.games[gameId]?.copy() }
        val base = Dialogue.pick(if (traits.confidence > 0.5f) Dialogue.gameStartConfident else Dialogue.gameStartNervous, rng)
        if (rec == null || rec.plays == 0) return base
        val days = (System.currentTimeMillis() - rec.lastPlayed) / 86_400_000L
        return when {
            rec.userStreak >= 2 -> "You beat me ${rec.userStreak} times in a row last time. I've been training."
            rec.pipoStreak >= 2 -> "I've won ${rec.pipoStreak} in a row. Want to make it ${rec.pipoStreak + 1}?"
            rec.userWins > rec.pipoWins + 1 -> "You're ahead ${rec.userWins}–${rec.pipoWins} overall. Not for long."
            rec.pipoWins > rec.userWins + 1 -> "Reminder: I'm up ${rec.pipoWins}–${rec.userWins}. Just saying."
            days >= 5 -> "We haven't played this in ages. $base"
            else -> base
        }
    }

    private fun say(text: String) {
        val v = voice ?: return
        if (text.isBlank()) return
        v.stop()
        rig.speak(text, charsPerSecond(mood, SpeechStyles.style(text), v.mode))
        v.speak(text, mood) {}
    }

    fun shutdown() { voice?.stop(); voice?.shutdown() }

    fun react(anim: AnimState, expr: Expr, text: String? = null, sfx: Sfx? = null, emote: EmoteKind? = null, secs: Float = 1.4f) {
        rig.anim = anim; rig.expr = expr
        holdUntil = rig.time + secs
        lastReactAt = rig.time
        distractions = 0
        text?.let { line = it }
        sfx?.let { synth.sfx(it) }
        emote?.let { rig.showEmote(it) }
    }

    fun win(t: String? = null) {
        pipoRun++; userRun = 0
        when {
            pipoRun >= 3 && t == null -> react(AnimState.CELEBRATE, Expr.PROUD, listOf("Three in a row! Are you even trying?", "Unstoppable. That's me.", "I'm on fire. Not literally. Again.").random(rng), Sfx.WIN, EmoteKind.SPARKLE, 1.8f)
            pipoRun == 2 && t == null -> react(AnimState.LAUGH, Expr.LAUGH, listOf("Hehe. Again!", "Two! Do you want a hint?", "Haha. Too easy.").random(rng), Sfx.GIGGLE, EmoteKind.SPARKLE)
            else -> react(AnimState.HAPPY, Expr.PROUD, t ?: Dialogue.pick(Dialogue.pipoWinsRound, rng), Sfx.HAPPY, EmoteKind.SPARKLE)
        }
    }

    fun lose(t: String? = null) {
        userRun++; pipoRun = 0
        when {
            userRun >= 3 && t == null -> react(AnimState.SULK, Expr.ANNOYED, listOf("I'm not looking at you until I win.", "Stop that. Stop winning.", "This game is broken.").random(rng), Sfx.GRUMBLE, EmoteKind.ANGER, 2f)
            userRun == 2 && t == null -> react(AnimState.TURN_AWAY, Expr.ANNOYED, listOf("Hmph.", "Twice? Suspicious.", "I'm letting you win. Strategically.").random(rng), Sfx.GRUMBLE, secs = 1.6f)
            else -> react(AnimState.ARMS_CROSSED, Expr.ANNOYED, t ?: Dialogue.pick(Dialogue.pipoLosesRound, rng), Sfx.GRUMBLE)
        }
    }
    fun draw(t: String? = null) = react(AnimState.LOOK_AROUND, Expr.SUSPICIOUS, t ?: Dialogue.pick(Dialogue.drawRound, rng), Sfx.BOOP)
    fun think(t: String? = null) = react(AnimState.THINKING, Expr.FOCUSED, t, null, EmoteKind.DOTS, 30f)

    fun update(dt: Float) {
        val idleAnim = if (mood == Mood.HAPPY || mood == Mood.EXCITED) AnimState.CHEERFUL else AnimState.IDLE
        if (rig.time > holdUntil && rig.anim != idleAnim && rig.anim != AnimState.THINKING) {
            rig.anim = idleAnim
            rig.expr = if (mood == Mood.SLEEPY) Expr.SLEEPY else Expr.CONTENT
        }
        // You're taking a while. He has a short attention span.
        if (!finished && rig.time > holdUntil && rig.anim != AnimState.THINKING && distractions < 2 && rig.time - lastReactAt > 8f + distractions * 10f) {
            distractions++
            lastReactAt = rig.time
            when {
                energy < 0.35f -> { rig.anim = AnimState.YAWN; rig.expr = Expr.SLEEPY; line = "I'm awake. I'm playing. Go."; synth.sfx(Sfx.YAWN) }
                traits.patience < 0.45f -> { rig.anim = AnimState.ARMS_CROSSED; rig.expr = Expr.BORED; line = listOf("Any day now.", "I'm growing a beard. Robots can't grow beards. That's how long this is taking.").random(rng) }
                else -> { rig.anim = AnimState.LOOK_AROUND; rig.expr = Expr.CURIOUS; line = listOf("Take your time. I'm... was that a moth?", "Hm? Oh. Your turn. Still.").random(rng) }
            }
            holdUntil = rig.time + 2.2f
        }
        rig.update(dt)
        frame++
    }

    fun finalWords(pipoWon: Boolean?): String = also { finished = true; pipoRun = 0; userRun = 0 }.let { finalLine(pipoWon) }

    /** Call when a new round of the same game starts again. */
    fun restart() { finished = false; lastReactAt = rig.time }

    private fun finalLine(pipoWon: Boolean?): String = when (pipoWon) {
        true -> Dialogue.pick(Dialogue.pipoWinsGame, rng)
        false -> Dialogue.pick(if (traits.confidence > 0.6f) Dialogue.overconfidentLoss else Dialogue.pipoLosesGame, rng)
        null -> "A tie. Which means I won. Emotionally."
    }
}

@Composable
fun rememberGamePipo(gameId: String = ""): GamePipo {
    val ctx = LocalContext.current
    val gp = remember { GamePipo(PipoRepository.get(ctx), gameId, PipoVoice(ctx)) }
    DisposableEffect(gp) { onDispose { gp.shutdown() } }
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
            val foot = Offset(size.width / 2f, size.height * 0.95f)
            // a little lit stage so he stands somewhere instead of floating on the UI
            withTransform({ scale(1f, 0.22f, pivot = foot) }) {
                drawCircle(Brush.radialGradient(listOf(PipoPalette.mint.copy(alpha = 0.22f), Color.Transparent), center = foot, radius = h * 0.8f), h * 0.8f, foot)
            }
            drawPipo(gp.rig, foot.x, foot.y, h, light = PipoLight(dir = -0.4f, keyStrength = 0.7f, rim = Color(gp.rig.glow), rimStrength = 0.35f))
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
            PillButton("Again", { gp.restart(); onAgain() })
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
