package com.themepark.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.themepark.app.ui.ParkActions
import com.themepark.app.ui.ParkApp
import com.themepark.app.ui.Tab
import com.themepark.app.widget.refreshParkWidgets
import com.themepark.core.Outcome
import com.themepark.core.Park
import com.themepark.core.ParkState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { Game() }
        }
    }
}

/** Holds the live state and applies every tap to the shared save file. */
@Composable
private fun Game() {
    val context = LocalContext.current
    val store = remember { SaveStore.get(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(store.load()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(Tab.Park) }
    var widgetRefresh by remember { mutableStateOf<Job?>(null) }

    // The widget may have changed the save while the game was in the background.
    LifecycleResumeEffect(Unit) {
        state = store.load()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    fun act(action: (ParkState, Long) -> Outcome) {
        val outcome = store.act { action(it, System.currentTimeMillis()) }
        if (outcome is Outcome.Ok) state = outcome.state
        message = outcome.message()
        // Holding an upgrade button fires many actions a second; redraw the widget once they settle.
        widgetRefresh?.cancel()
        widgetRefresh = scope.launch {
            delay(500)
            refreshParkWidgets(context)
        }
    }

    ParkApp(
        state = state,
        now = now,
        message = message,
        tab = tab,
        onTab = { tab = it; message = null },
        actions = ParkActions(
            collect = { act { s, t -> Outcome.Ok(Park.collect(s, t)) } },
            boost = { act(Park::startBoost) },
            claimDaily = { act(Park::claimDailyVisit) },
            upgrade = { id -> act { s, t -> Park.upgrade(s, id, t) } },
            buy = { id -> act { s, t -> Park.buy(s, id, t) } },
        ),
    )
}
