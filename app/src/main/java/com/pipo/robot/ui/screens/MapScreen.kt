package com.pipo.robot.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import com.pipo.robot.ui.render.drawItem
import com.pipo.robot.data.Npcs
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.data.Place
import com.pipo.robot.data.PlaceKind
import com.pipo.robot.data.PlaceMemory
import com.pipo.robot.data.Places
import com.pipo.robot.data.TripPurpose
import com.pipo.robot.data.TripState
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.MINUTE
import com.pipo.robot.engine.Trips
import com.pipo.robot.engine.Weather
import com.pipo.robot.engine.WeatherEngine
import com.pipo.robot.engine.WildlifeLife
import com.pipo.robot.ui.common.PipoTopBar
import com.pipo.robot.ui.common.relativeDay
import com.pipo.robot.ui.home.PhotoCard
import com.pipo.robot.ui.render.PetAnim
import com.pipo.robot.ui.render.PetRig
import com.pipo.robot.ui.render.PipoLight
import com.pipo.robot.ui.render.PipoRig
import com.pipo.robot.ui.render.dayFactor
import com.pipo.robot.ui.render.drawPet
import com.pipo.robot.ui.render.drawPipo
import com.pipo.robot.ui.render.drawSymbol
import com.pipo.robot.ui.render.drawOutside
import com.pipo.robot.ui.render.lerp
import com.pipo.robot.ui.theme.PipoPalette
import kotlin.math.hypot
import kotlin.math.sin

private val mapPaper = Color(0xFFF1E8D6)
private val mapInk = Color(0xFF5E4A3A)

/**
 * Pipo's map. Not a level select: it's his record of where he's been, in his handwriting.
 * Places appear once he's visited them. If he's out, you can see where — and peek.
 */
@Composable
fun MapScreen(onBack: () -> Unit) {
    val repo = PipoRepository.get(LocalContext.current)
    val v by repo.version.collectAsState()
    val known = remember(v) { repo.read { it.places.toMap() } }
    val trip = remember(v) { repo.read { it.trip } }
    val stage = remember(v) { repo.read { it.mystery.stage } }
    // little memories written next to places: the animals he named there
    val friends = remember(v) { repo.read { s -> s.creatures.values.filter { it.name.isNotEmpty() && it.lastPlace.isNotEmpty() }.groupBy({ it.lastPlace }, { it.name }) } }
    val today = remember(v) { repo.read { WeatherEngine.at(it.seed, System.currentTimeMillis()).kind } }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val unexplored = Places.all.count { it.kind != PlaceKind.HIDDEN && it.id !in known.keys }
    var selected by remember(trip?.id) { mutableStateOf<String?>(trip?.placeId) }

    Column(Modifier.fillMaxSize().background(PipoPalette.night)) {
        PipoTopBar("Pipo's Map", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.82f).clip(RoundedCornerShape(20.dp)).background(mapPaper)) {
                var tick by remember { mutableLongStateOf(0L) }
                LaunchedEffect(trip) { if (trip != null) while (true) withFrameNanos { tick = it } }
                Canvas(
                    Modifier.fillMaxSize()
                        .semantics { contentDescription = "Map of places Pipo has been. ${if (trip != null) "He's out right now." else ""}" }
                        .pointerInput(known, trip) {
                            detectTapGestures { o ->
                                val hit = mapPlaces(known, trip).minByOrNull { p -> hypot(o.x - p.mapX * size.width, o.y - p.mapY * size.height) }
                                if (hit != null && hypot(o.x - hit.mapX * size.width, o.y - hit.mapY * size.height) < 70f) selected = hit.id
                            }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    drawMap(known, trip, System.nanoTime() / 1e9f, stage)
                    drawMapWriting(measurer, known, friends, today)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    trip != null -> "He's out. Tap where he is to peek."
                    known.isEmpty() -> "He hasn't been anywhere yet. He's working up to it."
                    stage >= 3 && "observatory" !in known.keys -> "Tap a place. (There's a path near the hills he says he didn't draw.)"
                    unexplored > 0 -> "Tap a place. There's more out there — he hasn't been everywhere yet."
                    else -> "Tap a place. He's been everywhere. Everywhere he knows about."
                },
                color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            val sel = selected?.let { Places.byId(it) }
            if (sel != null) {
                if (trip != null && trip.placeId == sel.id) PeekCard(sel, trip)
                else PlaceCard(sel, known[sel.id], repo)
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(24.dp))
        }
    }
}

private val hand = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive, color = mapInk)

/** A tiny doodle for each place, so the map reads like his drawing, not a legend. */
private fun doodleFor(id: String): com.pipo.robot.data.ItemShape? = when (id) {
    "bakery" -> com.pipo.robot.data.ItemShape.BREAD; "market" -> com.pipo.robot.data.ItemShape.APPLE; "hardware" -> com.pipo.robot.data.ItemShape.SCREW
    "electronics" -> com.pipo.robot.data.ItemShape.MOTOR; "cafe" -> com.pipo.robot.data.ItemShape.MUG; "secondhand" -> com.pipo.robot.data.ItemShape.DUCK
    "repair" -> com.pipo.robot.data.ItemShape.GEAR; "library" -> com.pipo.robot.data.ItemShape.SKETCHBOOK; "lake" -> com.pipo.robot.data.ItemShape.DUCK
    "field" -> com.pipo.robot.data.ItemShape.MARBLE; "hills" -> com.pipo.robot.data.ItemShape.UMBRELLA; "observatory" -> com.pipo.robot.data.ItemShape.TELESCOPE
    else -> null
}

/** His handwriting on the map: names, his notes, who he met there, and today's weather in the corner. */
private fun DrawScope.drawMapWriting(tm: androidx.compose.ui.text.TextMeasurer, known: Map<String, PlaceMemory>, friends: Map<String, List<String>>, today: Weather) {
    val w = size.width; val h = size.height
    val small = hand.copy(fontSize = (w / 34f / density).sp)
    val tiny = small.copy(fontSize = (w / 42f / density).sp, color = mapInk.copy(alpha = 0.7f))
    for (p in Places.all.filter { it.id in known.keys }) {
        val c = Offset(p.mapX * w, p.mapY * h)
        doodleFor(p.id)?.let { drawItem(it, Offset(c.x + w * 0.05f, c.y - w * 0.035f), w * 0.045f) }
        val name = if (p.kind == PlaceKind.HIDDEN && p.id == "otherside") "???" else p.name.removePrefix("The ")
        drawText(tm, name, Offset(c.x - w * 0.06f, c.y + w * 0.04f), small)
        known[p.id]?.note?.takeIf { it.isNotBlank() }?.let { drawText(tm, "\u201C$it\u201D", Offset(c.x - w * 0.06f, c.y + w * 0.075f), tiny) }
        friends[p.id]?.take(2)?.let { drawText(tm, "(${it.joinToString(", ")})", Offset(c.x - w * 0.06f, c.y + w * 0.105f), tiny) }
    }
    drawText(tm, "home", Offset(homeX * w - w * 0.03f, homeY * h + w * 0.04f), small)
    // the places he hasn't been: a pencilled question mark
    for (p in Places.all.filter { it.kind != PlaceKind.HIDDEN && it.id !in known.keys }) {
        val r = tm.measure("?", tiny)
        drawText(r, topLeft = Offset(p.mapX * w - r.size.width / 2f, p.mapY * h - r.size.height / 2f))
    }
    drawText(tm, "today: ${WeatherEngine.describe(today)}", Offset(w * 0.62f, h * 0.93f), tiny)
}

/** Home, every place he's visited, and wherever he is right now. */
private fun mapPlaces(known: Map<String, PlaceMemory>, trip: TripState?): List<Place> =
    Places.all.filter { it.id in known.keys || it.id == trip?.placeId }

private val homeX = 0.46f
private val homeY = 0.5f

private fun DrawScope.drawMap(known: Map<String, PlaceMemory>, trip: TripState?, t: Float, stage: Int) {
    val w = size.width; val h = size.height
    fun o(x: Float, y: Float) = Offset(x * w, y * h)
    // paper grain + a river and the hills he's always drawn at the top
    for (i in 0 until 40) drawCircle(mapInk.copy(alpha = 0.04f), 2f, o(((i * 37) % 100) / 100f, ((i * 53) % 100) / 100f))
    val river = Path().apply { moveTo(-10f, h * 0.2f); cubicTo(w * 0.2f, h * 0.35f, w * 0.1f, h * 0.7f, w * 0.35f, h * 1.05f) }
    drawPath(river, Color(0xFF9CC5DA), style = Stroke(w * 0.035f, cap = StrokeCap.Round))
    for (i in 0 until 5) {
        val cx = w * (0.2f + i * 0.16f)
        drawPath(Path().apply { moveTo(cx - w * 0.09f, h * 0.14f); lineTo(cx, h * 0.04f); lineTo(cx + w * 0.09f, h * 0.14f) }, Color(0xFF9DB08A).copy(alpha = 0.55f), style = Stroke(3f))
    }
    val places = mapPlaces(known, trip)
    // places he hasn't been yet: faint pencil circles with a "?" (his world is bigger than he knows)
    for (p in Places.all.filter { it.kind != PlaceKind.HIDDEN && it.id !in known.keys && it.id != trip?.placeId }) {
        val c = o(p.mapX, p.mapY)
        drawCircle(mapInk.copy(alpha = 0.18f), w * 0.03f, c, style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 6f))))
    }
    // the edge of the map he never drew: fog, and something faint in it
    if ("otherside" !in known.keys) {
        for (i in 0 until 7) {
            val fc = o(0.82f + (i % 3) * 0.06f + sin(t * 0.3f + i) * 0.01f, 0.08f + (i / 3) * 0.07f)
            drawCircle(Color(0xFFB8C2CC).copy(alpha = 0.35f), w * 0.07f, fc)
        }
        if (stage >= 1) drawSymbol(o(0.88f, 0.16f), w * 0.02f, mapInk.copy(alpha = 0.15f + 0.1f * sin(t * 1.5f)))
    }
    // wobbly paths from home to everywhere he's been
    for (p in places) {
        val a = o(homeX, homeY); val b = o(p.mapX, p.mapY)
        val mid = Offset((a.x + b.x) / 2f + (p.mapY - 0.5f) * w * 0.08f, (a.y + b.y) / 2f + (p.mapX - 0.5f) * h * 0.05f)
        val path = Path().apply { moveTo(a.x, a.y); quadraticBezierTo(mid.x, mid.y, b.x, b.y) }
        drawPath(path, mapInk.copy(alpha = 0.35f), style = Stroke(3f, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 9f))))
    }
    // A path from the hills towards nothing. He didn't draw it. (Only once the photo has gone wrong,
    // and only until he's found where it leads.)
    if (stage >= 3 && "observatory" !in known.keys) {
        val a = o(0.48f, 0.12f); val b = o(0.7f, 0.1f)
        val path = Path().apply { moveTo(a.x, a.y); quadraticBezierTo((a.x + b.x) / 2f, a.y - h * 0.05f, b.x, b.y) }
        drawPath(path, mapInk.copy(alpha = 0.18f), style = Stroke(2f, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 10f))))
    }
    // home
    val home = o(homeX, homeY)
    drawRect(Color(0xFFC49A6C), Offset(home.x - 16f, home.y - 10f), Size(32f, 22f))
    drawPath(Path().apply { moveTo(home.x - 20f, home.y - 10f); lineTo(home.x, home.y - 28f); lineTo(home.x + 20f, home.y - 10f); close() }, Color(0xFFB8604F))
    drawRect(Color(0xFFFFE08A), Offset(home.x - 6f, home.y - 4f), Size(8f, 8f))
    // places
    for (p in places) {
        val c = o(p.mapX, p.mapY)
        val r = w * 0.035f
        if (p.kind == PlaceKind.HIDDEN) {
            drawCircle(Color(p.color).copy(alpha = 0.35f + 0.15f * sin(t * 2f)), r * 1.6f, c)
            drawSymbol(c, r * 0.9f, mapInk)
        } else {
            drawCircle(Color(p.color), r, c)
            drawCircle(mapInk, r, c, style = Stroke(2.5f))
        }
    }
    // he's out: a little pulsing Pipo where he is
    if (trip != null) Places.byId(trip.placeId)?.let { p ->
        val c = o(p.mapX, p.mapY)
        val pulse = 1f + 0.25f * sin(t * 4f)
        drawCircle(Color(0xFF8FF5E2).copy(alpha = 0.35f), w * 0.06f * pulse, c)
        drawRoundRect(Color(0xFFF4F6F9), Offset(c.x - 13f, c.y - 44f), Size(26f, 22f), CornerRadius(7f))
        drawRoundRect(Color(0xFF141B27), Offset(c.x - 10f, c.y - 41f), Size(20f, 16f), CornerRadius(5f))
        drawCircle(Color(0xFF8FF5E2), 2.5f, Offset(c.x - 4f, c.y - 33f)); drawCircle(Color(0xFF8FF5E2), 2.5f, Offset(c.x + 4f, c.y - 33f))
    }
}

@Composable
private fun PlaceCard(p: Place, mem: PlaceMemory?, repo: PipoRepository) {
    val npc = Npcs.byId(p.npc)
    val (npcMem, creatures, photos) = remember(p.id) {
        repo.read { s ->
            Triple(s.npcs[p.npc], s.creatures.values.filter { it.lastPlace == p.id && it.name.isNotEmpty() }.map { WildlifeLife.label(it) },
                s.photos.filter { it.placeId == p.id }.takeLast(3))
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(PipoPalette.card).padding(18.dp)) {
        Text(p.name, style = MaterialTheme.typography.titleLarge, color = PipoPalette.text)
        mem?.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text("“$it”", color = PipoPalette.amber, fontStyle = FontStyle.Italic, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(p.blurb, color = PipoPalette.text.copy(alpha = 0.8f))
        if (mem != null && mem.visits > 0) {
            Spacer(Modifier.height(8.dp))
            Text(if (mem.visits == 1) "Been once, ${relativeDay(mem.firstVisit).lowercase()}." else "Been ${mem.visits} times. First time: ${relativeDay(mem.firstVisit).lowercase()}.",
                color = PipoPalette.muted, style = MaterialTheme.typography.labelLarge)
        }
        if (npc != null && npcMem != null) {
            Spacer(Modifier.height(6.dp))
            Text(if (npcMem.fondness > 0.55f) "${npc.name} likes him. He's sure." else "${npc.name} knows him. Mostly as \"the small one\".", color = PipoPalette.muted, style = MaterialTheme.typography.labelLarge)
        }
        if (creatures.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Friends here: ${creatures.joinToString(", ")}", color = PipoPalette.mint, style = MaterialTheme.typography.labelLarge)
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { photos.forEach { PhotoCard(it, 90.dp) } }
        }
    }
}

/**
 * A peek at him, right now, out in the world. Drawn with the same painter as his room, so it's
 * really him: browsing, kicking a ball, reading, holding up his phone at a frog.
 */
@Composable
private fun PeekCard(p: Place, trip: TripState) {
    val director = remember(trip.id) { com.pipo.robot.ui.render.OutsideDirector(p.id, trip.purpose, trip.withPet, seed = trip.id.toInt()) }
    var frame by remember { mutableLongStateOf(0L) }
    val now = System.currentTimeMillis()
    val cal = java.util.Calendar.getInstance()
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
    val repo = PipoRepository.get(LocalContext.current)
    val weather = remember { repo.read { WeatherEngine.at(it.seed, now) } }
    val festival = remember { com.pipo.robot.engine.Festivals.today(now) }
    // what they're saying out there: a real back-and-forth, one line every few seconds
    val talk = remember(trip.id) { repo.read { com.pipo.robot.engine.Talks.script(it, trip, p) } }
    val talkStart = remember(trip.id) { System.nanoTime() }
    LaunchedEffect(trip.id) {
        director.rig.hat = com.pipo.robot.ui.render.hatFor(festival, false); director.pet.hat = com.pipo.robot.ui.render.hatFor(festival, true)
        var last = 0L
        while (true) withFrameNanos { t -> if (last != 0L) director.update((t - last) / 1e9f); last = t; frame = t }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(PipoPalette.card).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val lineSecs = 3.4f
        val idx = if (talk.isEmpty()) -1 else (((frame - talkStart).coerceAtLeast(0L) / 1e9f) / lineSecs).toInt() % (talk.size + 2) // a little pause, then again
        val line = talk.getOrNull(idx)
        val npcName = com.pipo.robot.data.Npcs.byId(p.npc)?.name ?: ""
        Box(Modifier.fillMaxWidth().aspectRatio(0.95f)) {
            Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).semantics { contentDescription = "Pipo at ${p.name}. ${trip.reason}" }) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                drawOutside(p, director, hour, weather.kind, festival)
            }
            // the speech bubble, over whoever is talking
            if (line != null) {
                val fx = when (line.who) {
                    com.pipo.robot.engine.Talks.Who.PIPO -> director.pipoX
                    com.pipo.robot.engine.Talks.Who.NIB -> director.petX
                    com.pipo.robot.engine.Talks.Who.NPC -> when (p.id) { "field" -> 0.1f; "cricket" -> 0.9f; "sports_hall" -> 0.09f; "court" -> 0.08f
                        else -> if (p.kind == com.pipo.robot.data.PlaceKind.SHOP || p.kind == com.pipo.robot.data.PlaceKind.INDOOR) 0.08f else 0.85f }
                }
                val bg = when (line.who) { com.pipo.robot.engine.Talks.Who.PIPO -> Color(0xFFFFF8EE); com.pipo.robot.engine.Talks.Who.NIB -> Color(0xFFFFE3B8); else -> Color(0xFFE6F0FF) }
                Box(Modifier.align(androidx.compose.ui.BiasAlignment(((fx.coerceIn(0.15f, 0.85f)) * 2f - 1f), -0.15f)).widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(line.text, color = Color(0xFF1F2733), fontSize = 13.sp, lineHeight = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("He's at ${Trips.placePhrase(p)}.", style = MaterialTheme.typography.titleMedium, color = PipoPalette.text)
        Text(trip.reason, color = PipoPalette.text.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        // what you've overheard so far
        if (idx > 0) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(10.dp)) {
                for (l in talk.take(idx.coerceAtMost(talk.size)).takeLast(4)) {
                    val who = when (l.who) { com.pipo.robot.engine.Talks.Who.PIPO -> "Pipo"; com.pipo.robot.engine.Talks.Who.NIB -> "Nib"; else -> npcName }
                    Text("$who: ${l.text}", color = PipoPalette.text.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
        }
        val mins = ((trip.endsAt - now) / MINUTE).coerceAtLeast(0)
        Spacer(Modifier.height(4.dp))
        Text(if (mins <= 1) "Back any minute." else "Back in about $mins minutes. Or you could text him.", color = PipoPalette.muted, style = MaterialTheme.typography.labelLarge)
    }
}
