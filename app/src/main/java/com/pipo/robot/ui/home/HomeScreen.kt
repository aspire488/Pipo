package com.pipo.robot.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pipo.robot.data.ProjectState
import com.pipo.robot.ui.common.Glyph
import com.pipo.robot.ui.common.GlyphIcon
import com.pipo.robot.ui.common.RoundButton
import com.pipo.robot.ui.render.drawBlanket
import com.pipo.robot.ui.render.drawEmote
import com.pipo.robot.ui.render.drawForeground
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pipo.robot.ui.render.drawItem
import com.pipo.robot.ui.render.drawLighting
import com.pipo.robot.ui.render.drawPipo
import com.pipo.robot.ui.render.drawRoom
import com.pipo.robot.ui.theme.PipoPalette
import kotlin.math.min
import kotlin.math.roundToInt

data class LaunchInfo(val action: String?, val game: String?, val record: Long)

@Composable
fun HomeScreen(vm: HomeViewModel, consumeLaunch: () -> LaunchInfo?, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var menuOpen by remember { mutableStateOf(false) }
    var gamesOpen by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }

    // Lifecycle → Pipo. Adding the observer replays ON_RESUME if we're already resumed
    // (e.g. coming back from the Journal), which triggers his "welcome back" staging.
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> { val li = consumeLaunch(); vm.onResume(li?.action, li?.game, li?.record ?: 0L) }
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); vm.onPause() }
    }

    // Frame loop
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) vm.frame((t - last) / 1_000_000_000f)
                last = t
            }
        }
    }

    LaunchedEffect(vm.navRequest) {
        vm.navRequest?.let { vm.navRequest = null; onNavigate(it) }
    }

    // Permissions: only requested at the moment they're needed.
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startListening() else vm.micDenied()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm.askNotifPermission) {
        if (vm.askNotifPermission) {
            vm.askNotifPermission = false
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // System photo picker: no storage permission; the user chooses exactly one photo.
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> vm.onPhotoPicked(uri) }
    LaunchedEffect(vm.pickPhoto) {
        if (vm.pickPhoto) {
            vm.pickPhoto = false
            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    fun mic() {
        if (vm.listening) { vm.stopListening(); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize().background(PipoPalette.night)) {
        // ---------------- the world
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.firstOrNull()?.let { if (it.pressed) vm.onPointer(it.position.x, it.position.y) }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { vm.onTap(it.x, it.y) },
                        onDoubleTap = { vm.onDoubleTap(it.x, it.y) },
                        onLongPress = { vm.onLongPress(it.x, it.y) },
                        onPress = { tryAwaitRelease(); vm.onPressEnd() },
                    )
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { o -> tracker.resetTracking(); vm.onDragStart(o.x, o.y) },
                        onDragEnd = { vm.onDragEnd(tracker.calculateVelocity().x) },
                        onDragCancel = { vm.onDragEnd(0f) },
                        onDrag = { change, amount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            vm.onDrag(amount.x, amount.y)
                        },
                    )
                }
        ) {
            @Suppress("UNUSED_VARIABLE") val tick = vm.frame // redraw every frame
            vm.setViewport(size.width, size.height)
            val g = vm.geo ?: return@Canvas
            val t = vm.clock
            val room = vm.roomNow()
            val inBed = vm.bedBlend > 0.5f
            val z = vm.camZoom
            // Camera: a gentle dolly towards Pipo when he talks to you or shows you something.
            withTransform({ scale(z, z, pivot = vm.zoomPivotPublic()) }) {
                val skip = PerfProbe.skip // debug builds only: layer cost attribution
                if ("room" !in skip) drawRoom(g, vm.camU, room, t, inBed)
                val foot = vm.footScreen()
                if ("pipo" !in skip) drawPipo(vm.rig, foot.x, foot.y, g.pipoH, vm.lift * g.u, shadow = !inBed, light = vm.lightNow(room))
                if (inBed) drawBlanket(g, foot, g.pipoH / 100f, t)
                val head = vm.headScreen()
                if ("light" !in skip) drawLighting(g, vm.camU, room, head, vm.glowColor(), t)
                if ("fg" !in skip) drawForeground(g, vm.camU, room)
                drawEmote(vm.rig, head.x, head.y, g.pipoH / 100f)
            }
        }

        // ---------------- speech bubble, anchored above Pipo's head every frame
        val bubble = vm.bubble
        if (bubble != null) {
            val density = LocalDensity.current
            val gap = with(density) { 14.dp.toPx() }
            val maxW = with(density) { 280.dp.roundToPx() }
            Column(
                Modifier
                    .layout { m, c ->
                        @Suppress("UNUSED_VARIABLE") val tick = vm.frame
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = min(c.maxWidth, maxW)))
                        layout(c.maxWidth, c.maxHeight) {
                            val h = vm.headView()
                            val x = (h.x - p.width / 2f).coerceIn(16f, (c.maxWidth - p.width - 16f).coerceAtLeast(16f))
                            val lo = gap * 5
                            val hi = (c.maxHeight - p.height).toFloat().coerceAtLeast(lo)
                            val y = (h.y - gap - p.height).coerceIn(lo, hi)
                            p.place(x.roundToInt(), y.roundToInt())
                        }
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(PipoPalette.paper)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(bubble.text, color = PipoPalette.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
                if (bubble.choices.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bubble.choices.forEachIndexed { i, c ->
                            Box(
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(if (i == 0) PipoPalette.mint else PipoPalette.ink.copy(alpha = 0.08f))
                                    .clickable { vm.pickChoice(c) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) { Text(c.label, color = PipoPalette.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                        }
                    }
                }
            }
        }

        // ---------------- your last line
        vm.userLine?.let {
            Box(
                Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
                    .padding(end = 16.dp, bottom = if (vm.openChat) 84.dp else 92.dp).widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(16.dp)).background(PipoPalette.mint.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text(it, color = Color(0xFF0F2A2A), fontSize = 14.sp) }
        }

        // ---------------- top-right menu (subtle)
        Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            RoundButton(Glyph.MENU, { menuOpen = true }, size = 42.dp, bg = PipoPalette.card.copy(alpha = 0.55f))
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Journal") }, onClick = { menuOpen = false; onNavigate("journal") })
                DropdownMenuItem(text = { Text("Pipo's things") }, onClick = { menuOpen = false; onNavigate("collection") })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onNavigate("settings") })
            }
        }

        // ---------------- bottom: chat field or the three little buttons
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(16.dp)) {
            when {
                vm.listening -> ListeningPill(vm) { vm.stopListening() }
                vm.openChat -> ChatField(chatText, { chatText = it }, onSend = {
                    vm.sendChat(chatText); chatText = ""
                }, onClose = { vm.openChat = false })
                else -> Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RoundButton(Glyph.CHAT, { vm.openChat = true }, size = 54.dp)
                    RoundButton(Glyph.MIC, { mic() }, size = 54.dp, bg = PipoPalette.mint.copy(alpha = 0.9f), tint = Color(0xFF0F2A2A))
                    RoundButton(Glyph.GAMES, { gamesOpen = true }, size = 54.dp)
                }
            }
        }

        // ---------------- a photo you chose to show Pipo (memory only)
        vm.photo?.let { bmp ->
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.dismissPhoto() }) {
                Box(
                    Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 70.dp)
                        .rotate(-4f).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp)
                ) {
                    Image(bmp.asImageBitmap(), contentDescription = "Your photo", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(170.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
        }

        // ---------------- reveal card (discoveries / projects)
        AnimatedVisibility(vm.reveal != null, enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut()) {
            val r = vm.reveal
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { vm.dismissReveal() }, contentAlignment = Alignment.Center) {
                if (r != null) RevealCard(r)
            }
        }

        // ---------------- games picker
        if (gamesOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { gamesOpen = false }, contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(PipoPalette.card)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
                ) {
                    Text("Play with Pipo", style = MaterialTheme.typography.headlineSmall, color = PipoPalette.text)
                    Spacer(Modifier.height(14.dp))
                    listOf("rps" to "Rock Paper Scissors", "memory" to "Memory Match", "reaction" to "Reaction Race", "tictactoe" to "Tic-Tac-Toe").forEach { (id, name) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(PipoPalette.cardHi)
                                .clickable { gamesOpen = false; vm.requestGame(id) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlyphIcon(Glyph.GAMES, color = PipoPalette.mint)
                            Spacer(Modifier.width(12.dp))
                            Text(name, style = MaterialTheme.typography.titleMedium, color = PipoPalette.text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatField(text: String, onText: (String) -> Unit, onSend: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Glyph.CLOSE, onClose, size = 44.dp)
        Spacer(Modifier.width(8.dp))
        TextField(
            value = text, onValueChange = onText,
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text("Say something to Pipo…") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PipoPalette.card, unfocusedContainerColor = PipoPalette.card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        RoundButton(Glyph.SEND, { if (text.isNotBlank()) onSend() }, size = 48.dp, bg = PipoPalette.mint, tint = Color(0xFF0F2A2A))
    }
}

/** Always visible while the mic is on. Nothing is ever recorded in the background. */
@Composable
private fun ListeningPill(vm: HomeViewModel, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(PipoPalette.card).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF6B6B)))
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.width(46.dp).height(24.dp)) {
            @Suppress("UNUSED_VARIABLE") val tick = vm.frame
            for (i in 0 until 5) {
                val h = size.height * (0.2f + 0.8f * vm.micLevel * (0.5f + 0.5f * kotlin.math.abs(kotlin.math.sin(vm.clock * 9f + i))))
                drawRoundRect(PipoPalette.mint, Offset(i * size.width / 5f + 2f, (size.height - h) / 2f),
                    androidx.compose.ui.geometry.Size(size.width / 5f - 4f, h), androidx.compose.ui.geometry.CornerRadius(4f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(vm.heard.ifBlank { "Pipo is listening…" }, color = if (vm.heard.isBlank()) PipoPalette.muted else PipoPalette.text,
            modifier = Modifier.weight(1f), maxLines = 2, fontSize = 15.sp)
        RoundButton(Glyph.STOP, onStop, size = 40.dp, bg = PipoPalette.cardHi)
    }
}

@Composable
private fun RevealCard(r: Reveal) {
    Column(
        Modifier.padding(32.dp).clip(RoundedCornerShape(26.dp)).background(PipoPalette.paper).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = when (r) { is Reveal.Item -> r.def.shape; is Reveal.Project -> r.def.shape }
        Canvas(Modifier.size(120.dp)) {
            drawCircle(PipoPalette.mint.copy(alpha = 0.25f), size.minDimension / 2f)
            drawItem(shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.62f)
        }
        Spacer(Modifier.height(12.dp))
        when (r) {
            is Reveal.Item -> {
                Text(r.def.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PipoPalette.ink)
                Spacer(Modifier.height(6.dp))
                Text("\u201C${r.def.foundLine}\u201D", color = PipoPalette.ink.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(if (r.item.revealed) r.def.secret else "Purpose: ???", color = if (r.item.revealed) Color(0xFF3D7D72) else PipoPalette.ink.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            is Reveal.Project -> {
                Text(r.project.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PipoPalette.ink)
                Spacer(Modifier.height(6.dp))
                Text(when (r.project.state) { ProjectState.DONE -> "It works!"; ProjectState.EVOLVED -> "It became something else."; else -> "It didn't work." },
                    color = PipoPalette.ink.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(r.project.result, color = PipoPalette.ink.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                // how it got here: the little story of the build
                val story = r.project.log.takeLast(3)
                if (story.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    story.forEach { Text("· $it", color = PipoPalette.ink.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("tap to close", color = PipoPalette.ink.copy(alpha = 0.4f), fontSize = 12.sp)
    }
}
