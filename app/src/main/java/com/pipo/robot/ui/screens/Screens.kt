package com.pipo.robot.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.BuildConfig
import com.pipo.robot.data.Catalog
import com.pipo.robot.data.Frequency
import com.pipo.robot.data.JournalCategory
import com.pipo.robot.data.NotifCategory
import com.pipo.robot.data.PipoRepository
import com.pipo.robot.data.ProjectState
import com.pipo.robot.data.UserResponse
import com.pipo.robot.data.VoiceMode
import com.pipo.robot.engine.HOUR
import com.pipo.robot.ai.Brains
import com.pipo.robot.notify.Notifier
import com.pipo.robot.notify.PipoNotificationListener
import com.pipo.robot.notify.PipoWorker
import com.pipo.robot.ui.common.PipoTopBar
import com.pipo.robot.ui.common.clockTime
import com.pipo.robot.ui.common.relativeDay
import com.pipo.robot.ui.render.drawItem
import com.pipo.robot.ui.render.drawDoodle
import com.pipo.robot.ui.render.WallDrawing
import com.pipo.robot.ui.home.PhotoCard
import com.pipo.robot.ui.home.Reveal
import com.pipo.robot.data.Foods
import com.pipo.robot.data.Npcs
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import com.pipo.robot.ui.theme.PipoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun catColor(c: JournalCategory) = when (c) {
    JournalCategory.DISCOVERY -> PipoPalette.mint
    JournalCategory.PROJECT -> PipoPalette.amber
    JournalCategory.GAME -> PipoPalette.lilac
    JournalCategory.MOMENT -> PipoPalette.pink
    JournalCategory.CONVERSATION -> Color(0xFF9AD0FF)
    JournalCategory.MILESTONE -> Color(0xFFFFE08A)
    JournalCategory.MISCHIEF -> PipoPalette.lilac
    JournalCategory.PLACE -> Color(0xFF9ACB7A)
    JournalCategory.FOOD -> Color(0xFFFFB27A)
    JournalCategory.PET -> Color(0xFFFFC27A)
    JournalCategory.STRANGE -> Color(0xFF9FF3E0)
    JournalCategory.THOUGHT -> Color(0xFFE8D4B0)
}

private fun catLabel(c: JournalCategory) = when (c) {
    JournalCategory.DISCOVERY -> "found"; JournalCategory.PROJECT -> "built"; JournalCategory.GAME -> "played"
    JournalCategory.MOMENT -> "moment"; JournalCategory.CONVERSATION -> "talked"; JournalCategory.MILESTONE -> "milestone"
    JournalCategory.MISCHIEF -> "mischief"; JournalCategory.PLACE -> "went out"; JournalCategory.FOOD -> "ate"
    JournalCategory.PET -> "Nib"; JournalCategory.STRANGE -> "???"
    JournalCategory.THOUGHT -> "his notes"
}

/* =============================== Journal =============================== */

@Composable
fun JournalScreen(onBack: () -> Unit) {
    val repo = PipoRepository.get(LocalContext.current)
    val v by repo.version.collectAsState()
    val entries = remember(v) { repo.read { it.journal.sortedByDescending { e -> e.timestamp }.take(200) } }
    Column(Modifier.fillMaxSize().background(PipoPalette.night)) {
        PipoTopBar("Pipo's Journal", onBack)
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing yet.\nPipo says he's \"working on it.\"", color = PipoPalette.muted, textAlign = TextAlign.Center)
            }
        } else JournalList(entries)
    }
}

private val notebookPaper = Color(0xFFF6F0E2)
private val notebookRule = Color(0xFFB8CCD8)
private val notebookInk = Color(0xFF2B3342)
private val handwriting = androidx.compose.ui.text.font.FontFamily.Cursive

/** A tiny doodle in the margin, by kind of entry. */
private fun marginDoodle(c: JournalCategory): com.pipo.robot.data.ItemShape? = when (c) {
    JournalCategory.DISCOVERY -> com.pipo.robot.data.ItemShape.PEBBLE
    JournalCategory.PROJECT -> com.pipo.robot.data.ItemShape.GEAR
    JournalCategory.GAME -> com.pipo.robot.data.ItemShape.MARBLE
    JournalCategory.FOOD -> com.pipo.robot.data.ItemShape.NOODLES
    JournalCategory.PLACE -> com.pipo.robot.data.ItemShape.UMBRELLA
    JournalCategory.STRANGE -> com.pipo.robot.data.ItemShape.TOKEN
    JournalCategory.MISCHIEF -> com.pipo.robot.data.ItemShape.HAT
    JournalCategory.MILESTONE -> com.pipo.robot.data.ItemShape.LAMP
    else -> null
}

/**
 * His own notes sometimes have a word crossed out. Always the same word for the same entry
 * (seeded by its id), never in the engine's summaries.
 */
private fun crossedOut(e: com.pipo.robot.data.JournalEntry): androidx.compose.ui.text.AnnotatedString {
    val words = e.description.split(' ')
    if (e.category != JournalCategory.THOUGHT || words.size < 6 || e.id % 3L != 0L) return androidx.compose.ui.text.AnnotatedString(e.description)
    val i = (e.id % (words.size - 2)).toInt() + 1
    return androidx.compose.ui.text.buildAnnotatedString {
        words.forEachIndexed { j, w ->
            if (j == i) {
                pushStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, color = notebookInk.copy(alpha = 0.45f)))
                append(listOf("definitely", "probably", "obviously", "maybe").let { it[(e.id % it.size).toInt()] })
                pop(); append(" ")
            }
            append(w); if (j < words.size - 1) append(" ")
        }
    }
}

@Composable
private fun JournalList(entries: List<com.pipo.robot.data.JournalEntry>) {
        val grouped = entries.groupBy { relativeDay(it.timestamp) }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    // the date, written at the top of a new page
                    Text(day, color = notebookInk.copy(alpha = 0.75f), fontFamily = handwriting, fontSize = 22.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .background(notebookPaper).padding(start = 44.dp, top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().background(notebookPaper)
                            .drawBehind {
                                // ruled lines and the red margin line
                                var y = 22.dp.toPx()
                                while (y < size.height) { drawLine(notebookRule.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f); y += 22.dp.toPx() }
                                drawLine(Color(0xFFE0A0A0).copy(alpha = 0.6f), Offset(36.dp.toPx(), 0f), Offset(36.dp.toPx(), size.height), 1.5f)
                            }
                            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Box(Modifier.width(30.dp), contentAlignment = Alignment.TopCenter) {
                            marginDoodle(e.category)?.let { shape -> Canvas(Modifier.size(22.dp)) { drawItem(shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.85f) } }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.title, color = notebookInk, fontFamily = handwriting, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                Text(clockTime(e.timestamp), color = notebookInk.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
                            }
                            if (e.description.isNotBlank()) {
                                Text(crossedOut(e), color = notebookInk.copy(alpha = 0.85f), fontFamily = handwriting, fontSize = 16.sp, lineHeight = 22.sp)
                            }
                            Text(catLabel(e.category), color = catColor(e.category).let { lerp(it, notebookInk, 0.45f) }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
}

/* =============================== Collection =============================== */

@Composable
fun CollectionScreen(onBack: () -> Unit) {
    val repo = PipoRepository.get(LocalContext.current)
    val v by repo.version.collectAsState()
    val all = remember(v) { repo.read { it.world.items.toList().sortedByDescending { i -> i.foundAt } } }
    val things = all.filter { Catalog.item(it.catalogId)?.shopOnly != true }
    val bought = all.filter { Catalog.item(it.catalogId)?.shopOnly == true }
    val projects = remember(v) { repo.read { it.projects.toList().sortedByDescending { p -> p.startedAt } } }
    val drawings = remember(v) { repo.read { it.drawings.toList().reversed() } }
    val photos = remember(v) { repo.read { it.photos.toList().reversed() } }
    val life = remember(v) {
        repo.read { s ->
            LifeBits(s.pantry.toList(), s.coins, s.creatures.values.filter { c -> c.sightings > 0 }.sortedByDescending { c -> c.sightings },
                s.records.toMap(), s.pet.adopted, s.npcs.filterValues { n -> n.visits > 0 }.keys.mapNotNull { id -> Npcs.byId(id)?.name },
                s.recordings.takeLast(6).reversed().map { r -> "${r.label} (${relativeDay(r.at).lowercase()})" })
        }
    }
    var detail by remember { mutableStateOf<Long?>(null) }
    var art by remember { mutableStateOf<Reveal?>(null) }
    val hidden = Catalog.items.count { d -> !d.shopOnly && things.none { it.catalogId == d.id } }

    Column(Modifier.fillMaxSize().background(PipoPalette.night)) {
        PipoTopBar("Pipo's Things", onBack)
        LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (things.isEmpty()) "Nothing found yet. Pipo is looking." else "Found ${things.size}. ${if (hidden > 0) "More things are hidden somewhere." else "He found everything. Allegedly."}",
                    color = PipoPalette.muted)
            }
            items(things, key = { it.id }) { it2 ->
                val d = Catalog.item(it2.catalogId)
                if (d != null) Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(PipoPalette.card).clickable { detail = it2.id }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) { drawItem(d.shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.62f) }
                    Text(d.name, color = PipoPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                    Text(if (it2.revealed) "understood" else "???", color = if (it2.revealed) PipoPalette.mint else PipoPalette.muted, fontSize = 11.sp)
                }
            }
            if (bought.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("From the shops") }
                items(bought, key = { "b${it.id}" }) { b ->
                    val d = Catalog.item(b.catalogId)
                    if (d != null) Column(
                        Modifier.clip(RoundedCornerShape(18.dp)).background(PipoPalette.card).clickable { detail = b.id }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) { drawItem(d.shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.62f) }
                        Text(d.name, color = PipoPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                        Text(if (b.usedInProjectId != 0L) "in a project" else "spare", color = PipoPalette.muted, fontSize = 11.sp)
                    }
                }
            }
            if (drawings.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Drawings") }
                items(drawings.take(30), key = { "d${it.id}" }) { d ->
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFEDE3D2)).clickable { art = Reveal.Art(d) }.padding(6.dp)
                            .semantics(mergeDescendants = true) { contentDescription = "Drawing: ${d.caption}" },
                    ) {
                        Canvas(Modifier.fillMaxWidth().aspectRatio(0.78f)) { drawDoodle(WallDrawing(d.subject, d.seed), Offset.Zero, size.width, size.height, size.width / 7f) }
                    }
                }
            }
            if (photos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Photos") }
                items(photos.take(40), key = { "ph${it.id}" }) { ph ->
                    Box(Modifier.clickable { art = Reveal.Snap(ph) }) { PhotoCard(ph, 100.dp) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(18.dp)).background(PipoPalette.card).padding(16.dp)) {
                    Text("The kitchen", style = MaterialTheme.typography.titleMedium, color = PipoPalette.text)
                    Spacer(Modifier.height(8.dp))
                    if (life.pantry.isEmpty()) Text("Empty. Echo empty.", color = PipoPalette.muted)
                    else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        life.pantry.take(8).mapNotNull { Foods.byId(it) }.forEach { f ->
                            Canvas(Modifier.size(34.dp).semantics { contentDescription = f.name }) { drawItem(f.shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.85f) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Coin jar: ${life.coins} coins", color = PipoPalette.amber, style = MaterialTheme.typography.labelLarge)
                    val bests = listOfNotNull(life.records["kickups"]?.let { "$it kick-ups" }, life.records["shot"]?.let { "a $it-step shot" })
                    if (bests.isNotEmpty()) Text("Personal bests: ${bests.joinToString(", ")}", color = PipoPalette.mint, style = MaterialTheme.typography.labelLarge)
                    val arcade = listOfNotNull(life.records["pipo:flappy"]?.let { "Flappy Pipo $it" }, life.records["pipo:shooter"]?.let { "Pixel Shooter $it" })
                    if (arcade.isNotEmpty()) Text("His arcade records: ${arcade.joinToString(", ")}", color = PipoPalette.mint, style = MaterialTheme.typography.labelLarge)
                    if (life.sounds.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Sounds he recorded", style = MaterialTheme.typography.titleSmall, color = PipoPalette.text)
                        life.sounds.forEach { Text("\u2022 $it", color = PipoPalette.text.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (life.creatures.isNotEmpty() || life.people.isNotEmpty() || life.nib) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(PipoPalette.card).padding(16.dp)) {
                        Text("Friends", style = MaterialTheme.typography.titleMedium, color = PipoPalette.text)
                        Spacer(Modifier.height(6.dp))
                        if (life.nib) Text("Nib — small, round, beeps, steals things.", color = PipoPalette.text.copy(alpha = 0.85f))
                        life.creatures.take(12).forEach { c ->
                            Text(if (c.name.isNotEmpty()) "${c.name} — ${c.look}. Seen ${c.sightings} times." else "${c.look.replaceFirstChar { it.uppercase() }}. No name yet.",
                                color = PipoPalette.text.copy(alpha = 0.85f))
                        }
                        if (life.people.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text("Around town: ${life.people.joinToString(", ")}", color = PipoPalette.muted) }
                    }
                }
            }
            if (projects.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Projects", style = MaterialTheme.typography.headlineSmall, color = PipoPalette.text, modifier = Modifier.padding(top = 18.dp))
                }
                items(projects, key = { "p${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { p ->
                    val d = Catalog.project(p.templateId)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PipoPalette.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (d != null) Canvas(Modifier.size(52.dp)) { drawItem(d.shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.8f) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.title, color = PipoPalette.text, style = MaterialTheme.typography.titleMedium)
                            val (label, col) = when (p.state) {
                                ProjectState.GATHERING -> "collecting parts" to PipoPalette.muted
                                ProjectState.BUILDING -> "building" to PipoPalette.amber
                                ProjectState.DONE -> "it works!" to PipoPalette.mint
                                ProjectState.EVOLVED -> "became something else" to PipoPalette.lilac
                                ProjectState.FAILED -> "didn't work" to PipoPalette.pink
                            }
                            Text(label, color = col, style = MaterialTheme.typography.labelSmall)
                            if (p.active) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(progress = { p.progress }, modifier = Modifier.fillMaxWidth(), color = PipoPalette.amber, trackColor = PipoPalette.cardHi)
                            } else if (p.result.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(p.result, color = PipoPalette.text.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    art?.let { r ->
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { art = null }, contentAlignment = Alignment.Center) {
            Column(Modifier.padding(28.dp).clip(RoundedCornerShape(24.dp)).background(PipoPalette.paper).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (r) {
                    is Reveal.Art -> {
                        Canvas(Modifier.size(200.dp, 256.dp)) { drawRect(Color(0xFFEDE3D2)); drawDoodle(WallDrawing(r.drawing.subject, r.drawing.seed), Offset.Zero, size.width, size.height, size.width / 7f) }
                        Spacer(Modifier.height(10.dp))
                        Text("\u201C${r.drawing.caption}\u201D", color = PipoPalette.ink, textAlign = TextAlign.Center)
                        Text(relativeDay(r.drawing.createdAt), color = PipoPalette.ink.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    is Reveal.Snap -> {
                        PhotoCard(r.photo, 230.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(r.photo.caption, color = PipoPalette.ink, textAlign = TextAlign.Center)
                        if (r.photo.anomaly && r.photo.anomalySeen) Text("...look behind.", color = Color(0xFF3D7D72), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(relativeDay(r.photo.createdAt), color = PipoPalette.ink.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    else -> Unit
                }
            }
        }
    }

    val sel = all.firstOrNull { it.id == detail }
    val d = sel?.let { Catalog.item(it.catalogId) }
    if (sel != null && d != null) {
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = { TextButton({ detail = null }) { Text("Nice") } },
            title = { Text(d.name) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(110.dp)) { drawItem(d.shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.7f) }
                    Text("\u201C${d.foundLine}\u201D", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(if (sel.revealed) d.secret else "Purpose: ??? Pipo is still figuring it out.", color = if (sel.revealed) PipoPalette.mint else PipoPalette.muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Found ${relativeDay(sel.foundAt).lowercase()} at ${clockTime(sel.foundAt)}", style = MaterialTheme.typography.labelSmall, color = PipoPalette.muted)
                }
            },
        )
    }
}

private class LifeBits(val pantry: List<String>, val coins: Int, val creatures: List<com.pipo.robot.data.Creature>, val records: Map<String, Int>, val nib: Boolean, val people: List<String>,
                       val sounds: List<String> = emptyList())

@Composable
private fun SectionTitle(t: String) = Text(t, style = MaterialTheme.typography.headlineSmall, color = PipoPalette.text, modifier = Modifier.padding(top = 18.dp))

/* =============================== Settings =============================== */

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(PipoPalette.card).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = PipoPalette.mint)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PipoPalette.text, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = PipoPalette.mint, checkedThumbColor = Color(0xFF0F2A2A)))
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(PipoPalette.cardHi).padding(4.dp)) {
        options.forEach { o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (o == selected) PipoPalette.mint else Color.Transparent)
                    .clickable { onPick(o) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(o), color = if (o == selected) Color(0xFF0F2A2A) else PipoPalette.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun HourStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PipoPalette.text, modifier = Modifier.weight(1f))
        TextButton({ onChange((value + 23) % 24) }) { Text("−", fontSize = 20.sp) }
        Text("%02d:00".format(value), color = PipoPalette.text, fontWeight = FontWeight.SemiBold)
        TextButton({ onChange((value + 1) % 24) }) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = PipoRepository.get(ctx)
    val v by repo.version.collectAsState()
    val st = remember(v) { repo.read { it.settings.copy(categories = it.settings.categories.toMutableMap()) } }
    val recent = remember(v) { repo.read { it.notifications.takeLast(5).reversed() } }
    var name by remember(v) { mutableStateOf(repo.read { it.profile.userName }) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun edit(block: (com.pipo.robot.data.PipoSettings) -> Unit) { repo.mutate { block(it.settings) } }

    LazyColumn(Modifier.fillMaxSize().background(PipoPalette.night)) {
        item { PipoTopBar("Settings", onBack) }
        item {
            Section("You") {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) }, label = { Text("What Pipo calls you") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { repo.mutate { it.profile.userName = name.trim() } }),
                )
                TextButton({ repo.mutate { it.profile.userName = name.trim() } }) { Text("Save name") }
            }
        }
        item {
            Section("Messages from Pipo") {
                ToggleRow("Pipo can send messages", st.notificationsEnabled) { on ->
                    edit { it.notificationsEnabled = on }
                    if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (st.notificationsEnabled) {
                    Text("How often", color = PipoPalette.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    Segmented(Frequency.entries, st.frequency, { it.label }) { f -> edit { it.frequency = f } }
                    HourStepper("Quiet from", st.quietStartHour) { h -> edit { it.quietStartHour = h } }
                    HourStepper("Quiet until", st.quietEndHour) { h -> edit { it.quietEndHour = h } }
                    NotifCategory.entries.forEach { c ->
                        ToggleRow(c.label, st.categoryOn(c)) { on -> edit { it.categories[c.name] = on } }
                    }
                    Text("Pipo only writes when something actually happened. Ignore him and he gets quieter.", color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Section("Pipo notices your notifications") {
                // Re-checked whenever you come back from Android's settings page.
                var access by remember { mutableStateOf(PipoNotificationListener.hasAccess(ctx)) }
                val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                        if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) access = PipoNotificationListener.hasAccess(ctx)
                    }
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
                Text("When WhatsApp, Instagram, Telegram, Messages and friends buzz while Pipo is on screen, he notices: “Ooh, someone sent you a reel!”. " +
                    "He only learns which app and what kind of thing it was. He never sees who it's from or what it says, never keeps it, and never opens or answers anything.",
                    color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                ToggleRow(if (access) "On (tap to change in Android settings)" else "Off", access) { _ ->
                    val detail = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, PipoNotificationListener.component(ctx).flattenToString()) else null
                    val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    runCatching { ctx.startActivity(detail ?: list) }.onFailure { runCatching { ctx.startActivity(list) } }
                }
            }
        }
        item {
            Section("Phone control") {
                val actions = remember { com.pipo.robot.phone.PhoneActions(ctx) }
                var status by remember { mutableStateOf(actions.accessStatus()) }
                val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) status = actions.accessStatus() }
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
                Text("Pipo only touches your phone when you ask him (“brighter”, “do not disturb”, “what's playing?”, “play lo-fi on Spotify”, “open Netflix”). " +
                    "Some of it needs your okay first. Tap one to open Android's page for it. He never calls, texts, buys or posts anything.",
                    color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                listOf(
                    Triple("write_settings", "Brightness & auto-rotate", "Modify system settings"),
                    Triple("dnd", "Do not disturb & silent mode", "Do Not Disturb access"),
                    Triple("notification_access", "What's playing, and media controls", "Notification access"),
                ).forEach { (key, what, page) ->
                    ToggleRow("$what\n${if (status[key] == true) "Allowed" else "Tap to allow ($page)"}", status[key] == true) { _ -> actions.openAccessPage(key) }
                }
            }
        }
        item {
            Section("Pipo's voice") {
                Segmented(VoiceMode.entries, st.voiceMode, { it.label }) { m -> edit { it.voiceMode = m } }
                ToggleRow("Beeps and sound effects", st.sounds) { on -> edit { it.sounds = on } }
            }
        }
        item {
            Section("Motion") {
                ToggleRow("Calmer camera (no zoom or tilt)", st.calmMotion) { on -> edit { it.calmMotion = on } }
                Text("Pipo still moves the way he moves. This only keeps the room still around him. It's also on automatically when Android's \"remove animations\" is on.",
                    color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Section("Chatting") {
                Text(
                    if (Brains.configured) "When you're online, Pipo thinks up his chat replies with Gemini (Groq as backup). Only what you type or say to him in chat, plus his mood and a few of his memories, is sent to make the reply. Offline he uses his own words. Phone actions never leave the phone."
                    else "Pipo is chatting with his own offline words.",
                    color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Section("Privacy") {
                ToggleRow("Online AI (chat, \"look\", \"find out\")", st.onlineAi) { on -> edit { it.onlineAi = on } }
                Text(if (st.onlineAi) "What you say to Pipo (and a photo, only when you show him one) is sent to Google's Gemini, or Groq as a backup, to make his replies. Nothing is kept."
                    else "Pipo talks with his own built-in words. Nothing you say leaves the phone.", color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                ToggleRow("Real weather", st.realWeather) { on -> edit { it.realWeather = on } }
                Text(if (st.realWeather) "Pipo's weather is yours. Your approximate city (from your network) is used to look it up; nothing else is shared."
                    else "Pipo has his own little made-up climate.", color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                Text("Everything Pipo remembers stays on this phone. No account, no ads, no analytics. " +
                    "The mic only listens while you hold the conversation open, and the camera and photos are only used when you ask. " +
                    "Pipo never reads messages, contacts, or your gallery on his own.",
                    color = PipoPalette.text.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (recent.isNotEmpty()) item {
            Section("Recent messages") {
                recent.forEach { n ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Text(n.text, color = PipoPalette.text, style = MaterialTheme.typography.bodyMedium)
                        Text("${relativeDay(n.timestamp)} ${clockTime(n.timestamp)} · " + when (n.response) {
                            UserResponse.NONE -> "unanswered"; UserResponse.OPENED -> "you came to see"; UserResponse.PLAYED -> "you played"; UserResponse.NOT_NOW -> "not now"
                        }, color = PipoPalette.muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Section("Start over") {
                Text("Resets Pipo completely: memories, things, journal, personality.", color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                TextButton({ confirmReset = true }) { Text("Reset Pipo", color = Color(0xFFFF8A7A)) }
            }
        }
        if (BuildConfig.DEBUG) item {
            Section("Debug") {
                TextButton({
                    scope.launch {
                        repo.mutate { s -> val d = 6 * HOUR; s.lastSimulatedAt -= d; s.lastSeenByUserAt -= d; s.lastUserInteractionAt -= d }
                        val msg = withContext(Dispatchers.IO) { PipoWorker.runOnce(ctx, ignoreForeground = true) }
                        Toast.makeText(ctx, msg ?: "6 hours passed. Pipo stayed quiet.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Skip 6 hours (simulate life + maybe notify)") }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).height(24.dp)) }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        confirmButton = { TextButton({ confirmReset = false; repo.reset(); onBack() }) { Text("Reset", color = Color(0xFFFF8A7A)) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Keep Pipo") } },
        title = { Text("Reset Pipo?") },
        text = { Text("He'll forget everything, including you. A brand new Pipo will be asleep in the room.") },
    )
}
