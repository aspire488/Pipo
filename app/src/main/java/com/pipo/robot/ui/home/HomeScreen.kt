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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import com.pipo.robot.ui.render.SceneGeo
import com.pipo.robot.ui.render.drawBlanket
import com.pipo.robot.ui.render.drawEmote
import com.pipo.robot.ui.render.drawForeground
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import com.pipo.robot.ui.render.HideSpot
import com.pipo.robot.ui.render.PinnedPhoto
import com.pipo.robot.ui.render.WallDrawing
import com.pipo.robot.ui.render.drawDoodle
import com.pipo.robot.ui.render.drawPhotoImage
import com.pipo.robot.data.Places
import com.pipo.robot.engine.Economy
import com.pipo.robot.engine.Weather
import com.pipo.robot.ui.common.relativeDay
import com.pipo.robot.ui.render.drawItem
import com.pipo.robot.ui.render.drawLighting
import com.pipo.robot.ui.render.drawOccluder
import com.pipo.robot.ui.render.drawPet
import com.pipo.robot.ui.render.drawPipo
import com.pipo.robot.ui.render.drawRoom
import com.pipo.robot.ui.theme.PipoPalette
import kotlin.math.min
import kotlin.math.roundToInt

data class LaunchInfo(val action: String?, val game: String?, val record: Long, val voice: String? = null)

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
                Lifecycle.Event.ON_RESUME -> { val li = consumeLaunch(); vm.onResume(li?.action, li?.game, li?.record ?: 0L, li?.voice) }
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
    // "Pipo, look!": your own camera app takes one photo into Pipo's private cache (no camera permission for Pipo)
    val lookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onLookTaken(ok) }
    LaunchedEffect(vm.lookShot) { vm.lookShot?.let { runCatching { lookLauncher.launch(it) }.onFailure { vm.onLookTaken(false) } } }
    // System photo picker: no storage permission; the user chooses exactly one photo.
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> vm.onPhotoPicked(uri) }
    LaunchedEffect(vm.pickPhoto) {
        if (vm.pickPhoto) {
            vm.pickPhoto = false
            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun mic() {
        if (vm.listening) { vm.stopListening(); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize().background(PipoPalette.night)) {
        // ---------------- the world
        Canvas(
            Modifier.fillMaxSize()
                // screen readers: what's actually going on in the room, in plain words
                .semantics { contentDescription = vm.sceneDescription; liveRegion = LiveRegionMode.Polite }
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
                val light = vm.lightNow(room)
                // his faint reflection in the window glass at night
                if (vm.reflectionVisible()) {
                    val wl = (88f - vm.camU) * g.u; val wr = (120f - vm.camU) * g.u
                    val wt = g.floorY - 74f * g.u; val wb = g.floorY - 40f * g.u
                    clipRect(wl, wt, wr, wb) {
                        drawIntoCanvas { c ->
                            c.saveLayer(Rect(wl, wt, wr, wb), Paint().apply { alpha = 0.13f })
                            drawPipo(vm.reflection, (vm.pipoX - vm.camU) * g.u, wb + 2f * g.u, g.pipoH * 0.8f, shadow = false, light = light)
                            c.restore()
                        }
                    }
                }
                // his reflection in the hallway mirror (when he's near it). Usually it copies him.
                if (vm.mirrorVisible()) {
                    val ml = (SceneGeo.MIRROR_L - vm.camU) * g.u; val mr = (SceneGeo.MIRROR_R - vm.camU) * g.u
                    val mt = g.floorY - 46f * g.u; val mb = g.floorY - 6f * g.u
                    clipRect(ml, mt, mr, mb) {
                        drawIntoCanvas { c ->
                            c.saveLayer(Rect(ml, mt, mr, mb), Paint().apply { alpha = 0.55f })
                            drawPipo(vm.reflection, (vm.mirrorPipoX() - vm.camU) * g.u, g.floorY - 17f * g.u, g.pipoH * 0.55f, shadow = false, light = light)
                            c.restore()
                        }
                    }
                }
                val foot = vm.footScreen()
                val petFoot = vm.petFootScreen()
                if ("pipo" !in skip && !vm.away) drawPipo(vm.rig, foot.x, foot.y, g.pipoH, vm.lift * g.u, shadow = !inBed && vm.hideSpot != HideSpot.BOX, light = light)
                if (inBed && !vm.away) drawBlanket(g, foot, g.pipoH / 100f, t, overHead = vm.hideSpot == HideSpot.BLANKET)
                if (!vm.away) drawOccluder(g, vm.camU, room, vm.hideSpot, t)
                // Nib walks a little nearer the camera than Pipo, so Nib is drawn in front
                if (vm.petHome) drawPet(vm.pet, petFoot.x, petFoot.y, 12f * g.u, light)
                val head = vm.headScreen()
                if ("light" !in skip) drawLighting(g, vm.camU, room, if (vm.away) Offset(-9999f, -9999f) else head, vm.glowColor(), t)
                if ("fg" !in skip) drawForeground(g, vm.camU, room)
                if (!vm.away && vm.hideSpot == null) drawEmote(vm.rig, head.x, head.y, g.pipoH / 100f)
            }
        }

        // ---------------- he's out playing: watch the match
        vm.outPlaying?.let { sport ->
            @Suppress("UNUSED_VARIABLE") val tick = vm.frame
            Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 70.dp)
                .clip(RoundedCornerShape(50)).background(PipoPalette.mint).clickable { onNavigate("map") }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Watch the match: Pipo vs Nib, ${sport.lowercase().replace('_', ' ')}", color = Color(0xFF0F2A2A), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // ---------------- Nib's own little bubble (Nib talks for itself), over Nib's head
        vm.nibLine?.let { line ->
            Box(
                Modifier
                    .layout { m, c ->
                        @Suppress("UNUSED_VARIABLE") val tick = vm.frame
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = min(c.maxWidth, 600)))
                        layout(c.maxWidth, c.maxHeight) {
                            val h = vm.nibHeadView()
                            val x = (h.x - p.width / 2f).coerceIn(16f, (c.maxWidth - p.width - 16f).coerceAtLeast(16f))
                            val y = (h.y - 24f - p.height).coerceIn(80f, (c.maxHeight - p.height).toFloat().coerceAtLeast(80f))
                            p.place(x.roundToInt(), y.roundToInt())
                        }
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFFE7C7))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text(line, color = Color(0xFF7A3F12), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
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

        // ---------------- a text from his little phone, while he's out
        vm.text?.let { msg ->
            Row(
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 64.dp, start = 24.dp, end = 24.dp)
                    .widthIn(max = 340.dp).clip(RoundedCornerShape(18.dp)).background(PipoPalette.card.copy(alpha = 0.96f)).padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Message from Pipo: $msg" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(PipoPalette.mint), contentAlignment = Alignment.Center) { Text("P", color = Color(0xFF0F2A2A), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Pipo", color = PipoPalette.muted, fontSize = 12.sp)
                    Text(msg, color = PipoPalette.text, fontSize = 15.sp, lineHeight = 20.sp)
                }
            }
        }

        // ---------------- the note on the door (tap the door while he's out)
        vm.doorNote?.let { note ->
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.doorNote = null }, contentAlignment = Alignment.Center) {
                Box(Modifier.rotate(-3f).widthIn(max = 260.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF3DB7A)).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(note, color = PipoPalette.ink, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
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
            RoundButton(Glyph.MENU, { menuOpen = true }, size = 48.dp, bg = PipoPalette.card.copy(alpha = 0.55f), description = "Menu")
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Journal") }, onClick = { menuOpen = false; onNavigate("journal") })
                DropdownMenuItem(text = { Text("Map") }, onClick = { menuOpen = false; onNavigate("map") })
                DropdownMenuItem(text = { Text("Pipo's things") }, onClick = { menuOpen = false; onNavigate("collection") })
                DropdownMenuItem(text = { Text("Nib") }, onClick = { menuOpen = false; onNavigate("nib") })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onNavigate("settings") })
            }
        }

        // ---------------- bottom: chat field or the three little buttons
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(16.dp)) {
            when {
                vm.listening -> ListeningPill(vm) { vm.stopListening() }
                vm.openChat -> ChatField(chatText, { chatText = it }, placeholder = if (vm.away) "Text Pipo…" else "Say something to Pipo…", onSend = {
                    // sent: the keyboard goes away so you can watch him answer (and do it)
                    vm.sendChat(chatText); chatText = ""; keyboard?.hide(); focus.clearFocus(); vm.openChat = false
                }, onClose = { vm.openChat = false })
                else -> Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RoundButton(Glyph.CHAT, { vm.openChat = true }, size = 54.dp, description = if (vm.away) "Text Pipo" else "Talk to Pipo")
                    RoundButton(Glyph.MIC, { mic() }, size = 54.dp, bg = PipoPalette.mint.copy(alpha = 0.9f), tint = Color(0xFF0F2A2A), description = "Speak to Pipo")
                    RoundButton(Glyph.GAMES, { gamesOpen = true }, size = 54.dp, description = "Play a game with Pipo")
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
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.windowInsetsPadding(WindowInsets.safeDrawing)
                        .heightIn(max = 620.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
                ) {
                    Text("Play with Pipo", style = MaterialTheme.typography.headlineSmall, color = PipoPalette.text)
                    Spacer(Modifier.height(14.dp))
                    listOf("rps" to "Rock Paper Scissors", "memory" to "Memory Match", "reaction" to "Reaction Race", "tictactoe" to "Tic-Tac-Toe",
                        "flappy" to "Flappy Pipo", "shooter" to "Pixel Shooter", "cricket" to "Cricket", "pingpong" to "Table Tennis").forEach { (id, name) ->
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
private fun ChatField(text: String, onText: (String) -> Unit, placeholder: String, onSend: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Glyph.CLOSE, onClose, size = 48.dp, description = "Close")
        Spacer(Modifier.width(8.dp))
        TextField(
            value = text, onValueChange = onText,
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text(placeholder) },
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
        RoundButton(Glyph.SEND, { if (text.isNotBlank()) onSend() }, size = 48.dp, bg = PipoPalette.mint, tint = Color(0xFF0F2A2A), description = "Send")
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
        RoundButton(Glyph.STOP, onStop, size = 48.dp, bg = PipoPalette.cardHi, description = "Stop listening")
    }
}

@Composable
private fun RevealCard(r: Reveal) {
    Column(
        Modifier.padding(28.dp).widthIn(max = 380.dp).clip(RoundedCornerShape(26.dp)).background(PipoPalette.paper).padding(24.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (r) {
            is Reveal.Item, is Reveal.Project -> {
                val shape = if (r is Reveal.Item) r.def.shape else (r as Reveal.Project).def.shape
                Canvas(Modifier.size(120.dp)) {
                    drawCircle(PipoPalette.mint.copy(alpha = 0.25f), size.minDimension / 2f)
                    drawItem(shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.62f)
                }
                Spacer(Modifier.height(12.dp))
            }
            else -> Unit
        }
        when (r) {
            is Reveal.Item -> {
                Text(r.def.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PipoPalette.ink)
                Spacer(Modifier.height(6.dp))
                Text("“${r.def.foundLine}”", color = PipoPalette.ink.copy(alpha = 0.8f), textAlign = TextAlign.Center)
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
            is Reveal.Trip -> {
                val place = Places.byId(r.report.placeId)
                Text(place?.name ?: "Out", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PipoPalette.ink)
                val things = r.report.bought.mapNotNull { Economy.shapeOf(it) }
                if (things.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        things.take(5).forEach { sh ->
                            Canvas(Modifier.size(44.dp)) {
                                drawCircle(PipoPalette.mint.copy(alpha = 0.2f), size.minDimension / 2f)
                                drawItem(sh, Offset(size.width / 2, size.height / 2), size.minDimension * 0.7f)
                            }
                        }
                    }
                }
                r.photo?.let { p ->
                    Spacer(Modifier.height(12.dp))
                    PhotoCard(p, 150.dp)
                }
                Spacer(Modifier.height(10.dp))
                r.report.story.take(3).forEach { Text(it, color = PipoPalette.ink.copy(alpha = 0.85f), textAlign = TextAlign.Center, fontSize = 15.sp) }
                if (r.report.npcLine.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(r.report.npcLine, color = PipoPalette.ink.copy(alpha = 0.55f), textAlign = TextAlign.Center, fontSize = 13.sp)
                }
                if (r.report.spent > 0 || r.report.earned > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(if (r.report.earned > 0) "+${r.report.earned} coins" else "spent ${r.report.spent} coins", color = PipoPalette.ink.copy(alpha = 0.45f), fontSize = 12.sp)
                }
            }
            is Reveal.Art -> {
                Canvas(Modifier.size(180.dp, 230.dp)) {
                    drawRect(Color(0xFFEDE3D2))
                    drawDoodle(WallDrawing(r.drawing.subject, r.drawing.seed), Offset.Zero, size.width, size.height, size.width / 7f)
                }
                Spacer(Modifier.height(12.dp))
                Text("“${r.drawing.caption}”", color = PipoPalette.ink.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("by Pipo · ${relativeDay(r.drawing.createdAt).lowercase()}", color = PipoPalette.ink.copy(alpha = 0.45f), fontSize = 12.sp)
            }
            is Reveal.Snap -> {
                PhotoCard(r.photo, 220.dp)
                Spacer(Modifier.height(12.dp))
                Text(r.photo.caption, color = PipoPalette.ink.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                if (r.photo.anomaly && r.photo.anomalySeen) {
                    Spacer(Modifier.height(6.dp))
                    Text("...look behind.", color = Color(0xFF3D7D72), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("tap to close", color = PipoPalette.ink.copy(alpha = 0.4f), fontSize = 12.sp)
    }
}

/** One of his photos, as a polaroid. */
@Composable
fun PhotoCard(p: com.pipo.robot.data.Photo, width: androidx.compose.ui.unit.Dp) {
    val pinned = PinnedPhoto(p.subject, p.seed, Places.byId(p.placeId)?.color ?: 0xFF8C9B7A, p.hour >= 20 || p.hour < 6,
        runCatching { Weather.valueOf(p.weather) }.getOrDefault(Weather.CLEAR), p.anomaly && p.anomalySeen, p.ref)
    Canvas(Modifier.size(width, width * 1.1f).semantics { contentDescription = "Photo: ${p.caption}" }) {
        drawRect(Color(0xFFF7F4EE))
        val pad = size.width * 0.06f
        drawPhotoImage(pinned, Offset(pad, pad), size.width - pad * 2, size.width - pad * 2, 0f)
    }
}
