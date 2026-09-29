package com.pipo.robot.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pipo.robot.data.ItemShape
import com.pipo.robot.engine.AnimState
import com.pipo.robot.engine.EmoteKind
import com.pipo.robot.engine.Expr
import com.pipo.robot.engine.Sfx
import com.pipo.robot.ui.render.drawItem
import com.pipo.robot.ui.theme.PipoPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================ Memory Match ============================ */

private class Card(val shape: ItemShape) {
    var up by mutableStateOf(false)
    var owner by mutableIntStateOf(0) // 0 none, 1 you, 2 Pipo
}

@Composable
fun MemoryGame(onExit: () -> Unit) {
    val gp = rememberGamePipo()
    val scope = rememberCoroutineScope()
    fun deal(): List<Card> {
        val pool = listOf(ItemShape.GEAR, ItemShape.BATTERY, ItemShape.MARBLE, ItemShape.KEY, ItemShape.PEBBLE, ItemShape.BUTTON, ItemShape.SPRING, ItemShape.TUBE)
        return pool.shuffled(gp.rng).take(6).flatMap { listOf(Card(it), Card(it)) }.shuffled(gp.rng)
    }
    val cards = remember { mutableStateListOf<Card>().apply { addAll(deal()) } }
    var yourTurn by remember { mutableStateOf(true) }
    var first by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    val seen = remember { mutableMapOf<Int, ItemShape>() } // Pipo's (imperfect) memory
    val recall = 0.45f + gp.traits.curiosity * 0.25f + gp.energy * 0.15f

    val you = cards.count { it.owner == 1 } / 2
    val pipo = cards.count { it.owner == 2 } / 2

    fun memorize(i: Int) { if (gp.rng.nextFloat() < recall) seen[i] = cards[i].shape }

    fun checkOver() {
        if (cards.all { it.owner != 0 }) {
            over = true
            val y = cards.count { it.owner == 1 } / 2; val p = cards.count { it.owner == 2 } / 2
            val pw = if (y == p) null else p > y
            gp.react(if (pw == true) AnimState.DANCING else AnimState.IDLE, if (pw == true) Expr.PROUD else Expr.CONTENT, gp.finalWords(pw), if (pw == true) Sfx.WIN else Sfx.BEEP, secs = 3f)
            GameLog.record(gp.repo, "memory", pw, "Pairs: Pipo $p, you $y.")
        }
    }

    suspend fun resolve(a: Int, b: Int, byPipo: Boolean): Boolean {
        delay(700)
        return if (cards[a].shape == cards[b].shape) {
            cards[a].owner = if (byPipo) 2 else 1; cards[b].owner = cards[a].owner
            seen.remove(a); seen.remove(b)
            if (byPipo) gp.win(listOf("Pair! My memory is perfect.", "Got it!", "I knew it.").random(gp.rng))
            else gp.lose(listOf("Hey. Good memory.", "Lucky.", "I was about to pick that.").random(gp.rng))
            checkOver(); true
        } else {
            cards[a].up = false; cards[b].up = false
            false
        }
    }

    fun pipoTurn() {
        scope.launch {
            busy = true
            while (!over) {
                gp.think(listOf("Hmm…", "Let me think…", "I remember… something.").random(gp.rng))
                delay(900)
                val open = cards.indices.filter { cards[it].owner == 0 }
                if (open.isEmpty()) break
                // A pair I remember?
                val known = seen.entries.filter { cards[it.key].owner == 0 }.groupBy { it.value }.values.firstOrNull { it.size >= 2 }
                val a = known?.get(0)?.key ?: open.filter { it !in seen }.randomOrNull(gp.rng) ?: open.random(gp.rng)
                cards[a].up = true; memorize(a)
                delay(650)
                val match = seen.entries.firstOrNull { it.key != a && it.value == cards[a].shape && cards[it.key].owner == 0 }?.key
                val b = match ?: (open - a).filter { it !in seen }.randomOrNull(gp.rng) ?: (open - a).random(gp.rng)
                cards[b].up = true; memorize(b)
                if (!resolve(a, b, true)) {
                    gp.react(AnimState.SAD, Expr.EMBARRASSED, listOf("Oops.", "Those looked the same in my head.", "My memory needs a reboot.").random(gp.rng), Sfx.BOOP)
                    break
                }
            }
            yourTurn = true; busy = false
        }
    }

    fun tap(i: Int) {
        if (!yourTurn || busy || over || cards[i].up || cards[i].owner != 0) return
        cards[i].up = true; memorize(i)
        if (first < 0) { first = i; return }
        val a = first; first = -1
        busy = true
        scope.launch {
            val hit = resolve(a, i, false)
            busy = false
            if (!hit && !over) { yourTurn = false; gp.react(AnimState.MISCHIEVOUS, Expr.MISCHIEF, "My turn!", Sfx.BEEP); pipoTurn() }
        }
    }

    GameScaffold("Memory Match", gp, onExit, "Pipo $pipo  ·  You $you  ·  ${if (over) "done" else if (yourTurn) "your turn" else "Pipo's turn"}") {
        if (over) GameOver(gp, if (pipo > you) "Pipo remembers everything." else if (you > pipo) "You win!" else "Tie!",
            onAgain = { cards.clear(); cards.addAll(deal()); seen.clear(); over = false; yourTurn = true; first = -1 }, onExit = onExit)
        else Column(Modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in 0 until 4) {
                    val i = r * 4 + c
                    val card = cards[i]
                    Box(
                        Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(14.dp))
                            .background(when { card.owner == 1 -> PipoPalette.amber.copy(alpha = 0.35f); card.owner == 2 -> PipoPalette.mint.copy(alpha = 0.3f); card.up -> PipoPalette.paper; else -> PipoPalette.cardHi })
                            .clickable { tap(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (card.up || card.owner != 0) Canvas(Modifier.fillMaxSize().padding(10.dp)) { drawItem(card.shape, Offset(size.width / 2, size.height / 2), size.minDimension * 0.8f) }
                        else Text("?", color = PipoPalette.muted, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

/* ============================ Tic-Tac-Toe ============================ */

private val lines = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))
private fun winner(b: List<Int>): Int = lines.firstOrNull { l -> b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]] }?.let { b[it[0]] } ?: 0

/** 1 = you (X), 2 = Pipo (O). Returns score from Pipo's view. */
private fun minimax(b: MutableList<Int>, pipoToMove: Boolean, depth: Int): Int {
    when (winner(b)) { 2 -> return 10 - depth; 1 -> return depth - 10 }
    if (b.none { it == 0 }) return 0
    var best = if (pipoToMove) Int.MIN_VALUE else Int.MAX_VALUE
    for (i in 0 until 9) if (b[i] == 0) {
        b[i] = if (pipoToMove) 2 else 1
        val s = minimax(b, !pipoToMove, depth + 1)
        b[i] = 0
        best = if (pipoToMove) maxOf(best, s) else minOf(best, s)
    }
    return best
}

@Composable
fun TicTacToeGame(onExit: () -> Unit) {
    val gp = rememberGamePipo()
    val scope = rememberCoroutineScope()
    val board = remember { mutableStateListOf(0, 0, 0, 0, 0, 0, 0, 0, 0) }
    var youStart by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) } // 0 draw, 1 you, 2 Pipo
    // Tired / nervous Pipo makes mistakes. Confident Pipo rarely does.
    val mistake = (0.28f + (1f - gp.energy) * 0.2f - gp.traits.confidence * 0.15f).coerceIn(0.05f, 0.5f)

    fun end() {
        val w = winner(board)
        val r = if (w != 0) w else if (board.none { it == 0 }) 0 else return
        result = r
        val pw = when (r) { 2 -> true; 1 -> false; else -> null }
        gp.react(if (pw == true) AnimState.DANCING else if (pw == false) AnimState.SAD else AnimState.IDLE,
            if (pw == true) Expr.PROUD else if (pw == false) Expr.SAD else Expr.SUSPICIOUS, gp.finalWords(pw),
            if (pw == true) Sfx.WIN else if (pw == false) Sfx.LOSE else Sfx.BOOP, secs = 3f)
        GameLog.record(gp.repo, "tictactoe", pw, "")
    }

    fun pipoMove() {
        scope.launch {
            busy = true
            gp.think(listOf("Hmm.", "Where… where…", "I see a trap. Maybe.").random(gp.rng))
            delay(700L + gp.rng.nextInt(600))
            val empty = (0 until 9).filter { board[it] == 0 }
            val move = if (gp.rng.nextFloat() < mistake) empty.random(gp.rng) else empty.maxByOrNull { i ->
                val b = board.toMutableList(); b[i] = 2; minimax(b, false, 0) * 10 + gp.rng.nextInt(3)
            }!!
            board[move] = 2
            gp.react(AnimState.HOP, Expr.MISCHIEF, listOf("There.", "Your move.", "Hehe.", "Try that.").random(gp.rng), Sfx.BOOP, secs = 0.8f)
            end()
            busy = false
        }
    }

    fun tap(i: Int) {
        if (busy || result != null || board[i] != 0) return
        board[i] = 1
        end()
        if (result == null) pipoMove()
    }

    fun reset() {
        for (i in 0 until 9) board[i] = 0
        result = null; youStart = !youStart
        if (!youStart) { gp.react(AnimState.PROUD, Expr.MISCHIEF, "I go first this time.", Sfx.BEEP); pipoMove() }
    }

    GameScaffold("Tic-Tac-Toe", gp, onExit, "You are X · Pipo is O") {
        Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(PipoPalette.card).clickable { tap(i) }, contentAlignment = Alignment.Center) {
                        when (board[i]) {
                            1 -> Canvas(Modifier.size(46.dp)) {
                                drawLine(PipoPalette.amber, Offset(0f, 0f), Offset(size.width, size.height), size.width * 0.16f, StrokeCap.Round)
                                drawLine(PipoPalette.amber, Offset(size.width, 0f), Offset(0f, size.height), size.width * 0.16f, StrokeCap.Round)
                            }
                            2 -> Canvas(Modifier.size(50.dp)) { drawCircle(PipoPalette.mint, size.width * 0.4f, style = Stroke(size.width * 0.15f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (result != null) GameOver(gp, when (result) { 2 -> "Pipo wins."; 1 -> "You win!"; else -> "Draw." }, onAgain = { reset() }, onExit = onExit)
    }
}
