package com.themepark.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.themepark.app.MainActivity
import com.themepark.app.SaveStore
import com.themepark.app.formatAmount
import com.themepark.core.Outcome
import com.themepark.core.Park
import com.themepark.core.ParkState

/**
 * Home-screen widget: collect earnings and buy the cheapest upgrade without opening the game.
 * Tapping anywhere else opens the game.
 */
class ParkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = SaveStore.get(context)
        provideContent {
            // Reading the version subscribes this composition to it, so a bump redraws the widget.
            currentState(SaveVersion)
            WidgetContent(store.load(), System.currentTimeMillis())
        }
    }
}

/** Glance only redraws a running widget when its own state changes, so saves bump this. */
private val SaveVersion = longPreferencesKey("save_version")

suspend fun refreshParkWidget(context: Context, id: GlanceId) {
    updateAppWidgetState(context, id) { it[SaveVersion] = System.nanoTime() }
    ParkWidget().update(context, id)
}

suspend fun refreshParkWidgets(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(ParkWidget::class.java).forEach { refreshParkWidget(context, it) }
}

class ParkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParkWidget()
}

@Composable
private fun WidgetContent(state: ParkState, now: Long) {
    val pending = Park.pending(state, now)
    val suggested = Park.suggestedUpgrade(state)
    val cost = suggested?.let { Park.upgradeCost(it, state.level(it.id)) }
    // Upgrading collects first, so pending coins count toward the cost.
    val canUpgrade = cost != null && state.coins + pending >= cost

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text("Sunny Hills Park", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
        Text("Coins ${formatAmount(state.coins)} · +${formatAmount(Park.incomeAt(state, now))}/s")
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row {
            Button(
                text = if (pending >= 1) "Collect ${formatAmount(pending)}" else "Earning…",
                onClick = actionRunCallback<CollectAction>(),
                enabled = pending >= 1,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (suggested != null && cost != null) {
                Button(
                    text = "${suggested.name} Lv ${state.level(suggested.id) + 1} · ${formatAmount(cost)}",
                    onClick = actionRunCallback<UpgradeAction>(),
                    enabled = canUpgrade,
                )
            }
        }
    }
}

class CollectAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SaveStore.get(context).act { Outcome.Ok(Park.collect(it, System.currentTimeMillis())) }
        refreshParkWidget(context, glanceId)
    }
}

class UpgradeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SaveStore.get(context).act { state ->
            val ride = Park.suggestedUpgrade(state) ?: return@act Outcome.Refused("Nothing to upgrade")
            Park.upgrade(state, ride.id, System.currentTimeMillis())
        }
        refreshParkWidget(context, glanceId)
    }
}
