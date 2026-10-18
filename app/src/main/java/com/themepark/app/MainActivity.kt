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
import com.themepark.app.ui.BuyMode
import com.themepark.app.ui.ParkActions
import com.themepark.app.ui.ParkApp
import com.themepark.app.ui.Tab
import com.themepark.app.widget.refreshParkWidgets
import com.themepark.core.Catalog
import com.themepark.core.Outcome
import com.themepark.core.Park
import com.themepark.core.ParkState
import com.themepark.core.Rules
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

/** Away this long, and opening the game greets the player with what the park earned. */
private const val WELCOME_BACK_AFTER_MS = 5 * 60_000L

/** While the game is open, earnings are banked this often, so the 8-hour away cap never bites. */
private const val AUTO_COLLECT_EVERY_MS = 10 * 60_000L

private const val MESSAGE_SHOWN_MS = 4_000L

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
    var buyMode by remember { mutableStateOf(BuyMode.One) }
    var widgetRefresh by remember { mutableStateOf<Job?>(null) }

    /**
     * Applies an action to the save. A refusal is shown to the player; a success may be
     * described by [describe] (given the state before and after), e.g. a milestone reached.
     */
    fun act(describe: ((ParkState, ParkState) -> String?)? = null, action: (ParkState, Long) -> Outcome) {
        var before: ParkState? = null
        val outcome = store.act {
            before = it
            action(it, System.currentTimeMillis())
        }
        if (outcome is Outcome.Ok) {
            state = outcome.state
            before?.let { describe?.invoke(it, outcome.state) }?.let { message = it }
        } else {
            message = outcome.message()
        }
        // Holding an upgrade button fires many actions a second; redraw the widget once they settle.
        widgetRefresh?.cancel()
        widgetRefresh = scope.launch {
            delay(500)
            refreshParkWidgets(context)
        }
    }

    fun collect() = act { s, t -> Outcome.Ok(Park.collect(s, t)) }

    // The widget may have changed the save while the game was in the background, and the park
    // has been earning: bank it and say how much.
    LifecycleResumeEffect(Unit) {
        val loaded = store.load()
        state = loaded
        val t = System.currentTimeMillis()
        if (t - loaded.lastCollectedAt >= WELCOME_BACK_AFTER_MS) {
            val earned = Park.pending(loaded, t)
            collect()
            if (earned >= 1) message = "Welcome back! Your park earned ${formatAmount(earned)} while you were away."
        }
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (now - state.lastCollectedAt >= AUTO_COLLECT_EVERY_MS) collect()
            delay(1_000)
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_SHOWN_MS)
            message = null
        }
    }

    ParkApp(
        state = state,
        now = now,
        message = message,
        tab = tab,
        onTab = { tab = it; message = null },
        buyMode = buyMode,
        onBuyMode = { buyMode = it },
        actions = ParkActions(
            boost = { act(action = Park::startBoost) },
            claimDaily = { act(describe = { _, _ -> "+${Rules.DAILY_VISIT_GEMS} gems for visiting today" }, action = Park::claimDailyVisit) },
            upgrade = { id, count -> act(describe = { a, b -> upgradeMessage(id, a, b) }) { s, t -> Park.upgrade(s, id, count, t) } },
            fix = { id -> act { s, t -> Park.fix(s, id, t) } },
            fixAll = { act(action = Park::fixAll) },
            buy = { id -> act(describe = { _, _ -> "${Catalog.shopItem(id).name} bought" }) { s, t -> Park.buy(s, id, t) } },
        ),
    )
}

/** A line worth celebrating after an upgrade, or null for an ordinary level. */
private fun upgradeMessage(rideId: String, before: ParkState, after: ParkState): String? {
    val ride = Catalog.ride(rideId)
    val from = before.level(rideId)
    val to = after.level(rideId)
    val milestone = to / Rules.MILESTONE_EVERY * Rules.MILESTONE_EVERY
    return when {
        to >= ride.maxLevel -> "${ride.name} is fully upgraded!"
        from == 0 -> "${ride.name} is open!"
        milestone > from -> "${ride.name} reached Lv $milestone: income ×${Rules.MILESTONE_FACTOR}, +${after.gems - before.gems} gems"
        else -> null
    }
}
