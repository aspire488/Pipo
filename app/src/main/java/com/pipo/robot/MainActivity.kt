package com.pipo.robot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pipo.robot.notify.Notifier
import com.pipo.robot.ui.games.GameHost
import com.pipo.robot.ui.home.HomeScreen
import com.pipo.robot.ui.home.HomeViewModel
import com.pipo.robot.ui.home.LaunchInfo
import com.pipo.robot.ui.screens.CollectionScreen
import com.pipo.robot.ui.screens.JournalScreen
import com.pipo.robot.ui.screens.MapScreen
import com.pipo.robot.ui.screens.SettingsScreen
import com.pipo.robot.ui.theme.PipoTheme

class MainActivity : ComponentActivity() {
    private val pending = mutableStateOf<LaunchInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pending.value = launchInfo(intent)
        setContent { PipoTheme { PipoRoot(pending) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pending.value = launchInfo(intent)
    }

    private fun launchInfo(i: Intent?): LaunchInfo? {
        if (i == null) return null
        // Notification taps: only the actions Pipo himself posts are ever real ("open"/"see"/"play");
        // anything else from outside is dropped here rather than acted on later.
        val raw = i.getStringExtra(Notifier.EXTRA_ACTION)
        val action = raw?.takeIf { it == "open" || it == "see" || it == "play" }
        // "Pipo, <command>": words the listener heard and handed to us (typed to the same parser
        // you'd type into chat — no audio, just the transcript).
        // Only from Pipo's own listener: anything without this process's secret is another app
        // trying to make him run a phone command, and is ignored.
        val fromListener = i.getStringExtra(com.pipo.robot.voice.PipoVoiceService.EXTRA_VOICE_TOKEN) == com.pipo.robot.voice.PipoVoiceListener.handoffToken
        val voice = i.getStringExtra(com.pipo.robot.voice.PipoVoiceService.EXTRA_VOICE_COMMAND)?.take(200)?.takeIf { fromListener }
        if (action == null && voice == null) return null
        return LaunchInfo(action, i.getStringExtra(Notifier.EXTRA_GAME), i.getLongExtra(Notifier.EXTRA_RECORD, 0L), voice)
    }
}

@Composable
private fun PipoRoot(pending: MutableState<LaunchInfo?>) {
    val vm: HomeViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") { screen = "home" }
    // A notification tap always brings you to Pipo's room.
    LaunchedEffect(pending.value) { if (pending.value != null) screen = "home" }
    val back = { screen = "home" }
    when {
        screen == "home" -> HomeScreen(vm, consumeLaunch = { pending.value.also { pending.value = null } }, onNavigate = { screen = it })
        screen == "journal" -> JournalScreen(back)
        screen == "collection" -> CollectionScreen(back)
        screen == "map" -> MapScreen(back)
        screen == "nib" -> com.pipo.robot.ui.screens.NibScreen(back)
        screen == "settings" -> SettingsScreen(back)
        screen.startsWith("game:") -> GameHost(screen.removePrefix("game:"), back)
        else -> HomeScreen(vm, consumeLaunch = { null }, onNavigate = { screen = it })
    }
}
