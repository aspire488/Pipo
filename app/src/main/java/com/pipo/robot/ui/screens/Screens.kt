package com.pipo.robot.ui.screens

import android.Manifest
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.pipo.robot.notify.Notifier
import com.pipo.robot.notify.PipoWorker
import com.pipo.robot.ui.common.PipoTopBar
import com.pipo.robot.ui.common.clockTime
import com.pipo.robot.ui.common.relativeDay
import com.pipo.robot.ui.render.drawItem
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
}

private fun catLabel(c: JournalCategory) = when (c) {
    JournalCategory.DISCOVERY -> "found"; JournalCategory.PROJECT -> "built"; JournalCategory.GAME -> "played"
    JournalCategory.MOMENT -> "moment"; JournalCategory.CONVERSATION -> "talked"; JournalCategory.MILESTONE -> "milestone"
    JournalCategory.MISCHIEF -> "mischief"
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

@Composable
private fun JournalList(entries: List<com.pipo.robot.data.JournalEntry>) {
        val grouped = entries.groupBy { relativeDay(it.timestamp) }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    Text(day, color = PipoPalette.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp))
                }
                items(list, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PipoPalette.card).padding(14.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(catColor(e.category)).align(Alignment.Top))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.title, color = PipoPalette.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(clockTime(e.timestamp), color = PipoPalette.muted, style = MaterialTheme.typography.labelSmall)
                            }
                            if (e.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(e.description, color = PipoPalette.text.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(catLabel(e.category), color = catColor(e.category), style = MaterialTheme.typography.labelSmall)
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
    val things = remember(v) { repo.read { it.world.items.toList().sortedByDescending { i -> i.foundAt } } }
    val projects = remember(v) { repo.read { it.projects.toList().sortedByDescending { p -> p.startedAt } } }
    var detail by remember { mutableStateOf<Long?>(null) }
    val hidden = Catalog.items.count { d -> things.none { it.catalogId == d.id } }

    Column(Modifier.fillMaxSize().background(PipoPalette.night)) {
        PipoTopBar("Pipo's Things", onBack)
        LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (things.isEmpty()) "Nothing found yet. Pipo is looking." else "Found ${things.size}. ${if (hidden > 0) "$hidden more things are hidden somewhere." else "He found everything. Allegedly."}",
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

    val sel = things.firstOrNull { it.id == detail }
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
    var key by remember { mutableStateOf(st.aiApiKey) }
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
            Section("Pipo's voice") {
                Segmented(VoiceMode.entries, st.voiceMode, { it.label }) { m -> edit { it.voiceMode = m } }
                ToggleRow("Beeps and sound effects", st.sounds) { on -> edit { it.sounds = on } }
            }
        }
        item {
            Section("Smarter chatting (optional)") {
                Text("Pipo works fully offline. With your own Anthropic API key, his conversation gets more natural. Only what you type/say in chat is sent. Phone actions never leave the phone.",
                    color = PipoPalette.muted, style = MaterialTheme.typography.bodyMedium)
                ToggleRow("Use AI for conversation", st.aiEnabled) { on -> edit { it.aiEnabled = on } }
                if (st.aiEnabled) {
                    OutlinedTextField(key, { key = it.trim() }, label = { Text("API key (sk-ant-…)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    TextButton({ edit { it.aiApiKey = key } }) { Text("Save key") }
                }
            }
        }
        item {
            Section("Privacy") {
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
