package com.themepark.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.themepark.app.widget.refreshParkWidgets
import com.themepark.core.Catalog
import com.themepark.core.Outcome
import com.themepark.core.Park
import com.themepark.core.ParkState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { ParkScreen() }
            }
        }
    }
}

@Composable
private fun ParkScreen() {
    val context = LocalContext.current
    val store = remember { SaveStore.get(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(store.load()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }

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
        scope.launch { refreshParkWidgets(context) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state, now) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { act { s, t -> Outcome.Ok(Park.collect(s, t)) } }, modifier = Modifier.weight(1f)) {
                    Text("Collect ${formatAmount(Park.pending(state, now))}")
                }
                OutlinedButton(onClick = { act(Park::startBoost) }, modifier = Modifier.weight(1f)) {
                    Text(if (now < state.boostUntil) "2× on · extend" else "2× boost")
                }
            }
        }
        item {
            OutlinedButton(onClick = { act(Park::claimDailyVisit) }, modifier = Modifier.fillMaxWidth()) {
                Text("Claim daily visit gems")
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        item { SectionTitle("Rides") }
        items(Catalog.rides.filter { !it.shopOnly || state.level(it.id) > 0 }, key = { it.id }) { ride ->
            val level = state.level(ride.id)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ride.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (level == 0) "Not built" else "Lv $level · +${formatAmount(Park.rideIncome(state, ride))}/s",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!ride.shopOnly) {
                        Button(onClick = { act { s, t -> Park.upgrade(s, ride.id, t) } }) {
                            Text("${if (level == 0) "Build" else "Upgrade"} · ${formatAmount(Park.upgradeCost(ride, level))}")
                        }
                    }
                }
            }
        }

        item { SectionTitle("Shop · gems are earned by playing") }
        items(Catalog.shop, key = { it.id }) { item ->
            val owned = !item.repeatable && item.id in state.ownedItems
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (owned) {
                        Text("Owned")
                    } else {
                        Button(onClick = { act { s, t -> Park.buy(s, item.id, t) } }, enabled = state.gems >= item.gemPrice) {
                            Text("${item.gemPrice} gems")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: ParkState, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sunny Hills Park", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Coins ${formatAmount(state.coins)} · Gems ${state.gems} · +${formatAmount(Park.incomeAt(state, now))}/s")
        Text("Membership: no ads, boosts are free", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}
