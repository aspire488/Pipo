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
        val action = i?.getStringExtra(Notifier.EXTRA_ACTION) ?: return null
        return LaunchInfo(action, i.getStringExtra(Notifier.EXTRA_GAME), i.getLongExtra(Notifier.EXTRA_RECORD, 0L))
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
        screen == "settings" -> SettingsScreen(back)
        screen.startsWith("game:") -> GameHost(screen.removePrefix("game:"), back)
        else -> HomeScreen(vm, consumeLaunch = { null }, onNavigate = { screen = it })
    }
}
