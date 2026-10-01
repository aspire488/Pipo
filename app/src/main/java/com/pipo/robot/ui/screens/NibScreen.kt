package com.pipo.robot.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.data.MemoryType
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.engine.NibLife
import com.pipo.robot.ui.common.PipoTopBar
import com.pipo.robot.ui.common.relativeDay
import com.pipo.robot.ui.render.PetAnim
import com.pipo.robot.ui.render.PetFace
import com.pipo.robot.ui.render.PetRig
import com.pipo.robot.ui.render.PetThought
import com.pipo.robot.ui.render.drawPet
import com.pipo.robot.ui.theme.PipoPalette

/** Nib's page: who Nib is, how close they are, what it's learned, what it loves, what they've done together. */
@Composable
fun NibScreen(onBack: () -> Unit) {
    val repo = PipoRepository.get(LocalContext.current)
    val v by repo.version.collectAsState()
    data class Info(val stage: NibLife.Stage, val hearts: Float, val tricks: List<NibLife.Trick>, val learning: NibLife.Trick?, val spot: String?, val game: String?,
                    val memories: List<Pair<String, Long>>, val rivalry: List<String>, val days: Long, val tail: Boolean)
    val info = remember(v) {
        repo.read { s ->
            val sports = listOf("FOOTBALL" to "Football", "CRICKET" to "Cricket", "TABLE_TENNIS" to "Table tennis", "BADMINTON" to "Badminton").mapNotNull { (k, n) ->
                val p = s.records["vs:$k:pipo"] ?: 0; val o = s.records["vs:$k:opp"] ?: 0
                if (p + o == 0) null else "$n: Pipo $p, Nib $o"
            }
            Info(NibLife.stage(s), NibLife.hearts(s), NibLife.tricks(s), NibLife.learning(s), NibLife.favoriteSpot(s), NibLife.favoriteGame(s),
                s.memories.filter { it.type == MemoryType.PET }.sortedByDescending { it.timestamp }.distinctBy { it.content }.take(12).map { it.content to it.timestamp },
                sports, ((System.currentTimeMillis() - s.pet.adoptedAt.coerceAtLeast(s.profile.createdAt)) / 86_400_000L).coerceAtLeast(0), com.pipo.robot.engine.Inventor.has(s, "nib_tail"))
        }
    }
    val rig = remember { PetRig(21) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        rig.anim = PetAnim.WAG; rig.tailGlow = info.tail
        var last = 0L; var next = 1f
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val dt = (t - last) / 1e9f
                rig.update(dt); next -= dt
                if (next <= 0f) { // Nib on its page is still Nib: it feels things
                    next = 2.5f + (t % 3)
                    when ((t / 1000 % 5).toInt()) {
                        0 -> { rig.feel(PetFace.HAPPY); rig.think(PetThought.HEART) }
                        1 -> { rig.feel(PetFace.CURIOUS); rig.look(0f, 0.8f) }
                        2 -> { rig.feel(PetFace.SMUG); rig.anim = PetAnim.HOP }
                        3 -> { rig.feel(PetFace.LOVE); rig.think(PetThought.PIPO); rig.anim = PetAnim.WAG }
                        else -> { rig.feel(PetFace.HAPPY); rig.think(PetThought.BALL) }
                    }
                }
            }
            last = t; frame = t
        }
    }
    Column(Modifier.fillMaxSize().background(PipoPalette.night)) {
        PipoTopBar("Nib", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(PipoPalette.card).semantics { contentDescription = "Nib, ${info.stage.title}" }) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                val c = Offset(size.width / 2f, size.height * 0.82f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB86B).copy(alpha = 0.18f), Color.Transparent), c, size.height * 0.7f), size.height * 0.7f, c)
                drawPet(rig, c.x, c.y, size.height * 0.42f)
            }
            Spacer(Modifier.height(14.dp))
            Text(info.stage.title, style = MaterialTheme.typography.headlineSmall, color = PipoPalette.text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 5) {
                    val fill = (info.hearts - i).coerceIn(0f, 1f)
                    Canvas(Modifier.padding(top = 6.dp).height(22.dp).fillMaxWidth(0.08f)) {
                        val w = size.minDimension; val cx = size.width / 2f; val cy = size.height / 2f
                        val heart = Path().apply {
                            moveTo(cx, cy + w * 0.4f)
                            cubicTo(cx - w * 0.9f, cy - w * 0.05f, cx - w * 0.4f, cy - w * 0.7f, cx, cy - w * 0.2f)
                            cubicTo(cx + w * 0.4f, cy - w * 0.7f, cx + w * 0.9f, cy - w * 0.05f, cx, cy + w * 0.4f)
                        }
                        drawPath(heart, Color(0xFF3B4658))
                        if (fill > 0f) drawPath(heart, Color(0xFFFF7A90).copy(alpha = 0.35f + 0.65f * fill))
                    }
                }
            }
            Text("Together for ${info.days} day${if (info.days == 1L) "" else "s"}.", color = PipoPalette.muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(16.dp))
            Section("Tricks Pipo taught Nib") {
                if (info.tricks.isEmpty()) Line("None yet. They're practising.")
                info.tricks.forEach { Line("✓ ${it.title.replaceFirstChar { c -> c.uppercase() }}") }
                info.learning?.let { Line("Learning now: ${it.title}", PipoPalette.mint) }
            }
            Section("Nib's favourites") {
                Line("Nap spot: ${info.spot ?: "still deciding"}")
                Line("Game: ${info.game ?: "anything with a ball"}")
                Line("Person: Pipo. Obviously.")
            }
            if (info.rivalry.isNotEmpty()) Section("The rivalry") { info.rivalry.forEach { Line(it) } }
            Section("Their story") {
                if (info.memories.isEmpty()) Line("Nothing yet. Give them time.")
                info.memories.forEach { (m, at) -> Line("${relativeDay(at)}: ${m.replaceFirstChar { c -> c.uppercase() }}.") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(PipoPalette.card).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = PipoPalette.text)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Line(t: String, c: Color = PipoPalette.text.copy(alpha = 0.85f)) = Text(t, color = c, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
