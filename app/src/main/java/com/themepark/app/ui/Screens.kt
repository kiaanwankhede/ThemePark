package com.themepark.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.themepark.app.formatAmount
import com.themepark.core.Catalog
import com.themepark.core.Park
import com.themepark.core.ParkState
import com.themepark.core.RideDef
import com.themepark.core.Rules
import com.themepark.core.ShopItem
import com.themepark.core.Weather
import kotlinx.coroutines.delay

enum class Tab { Park, Shop }

/** How many levels an upgrade button buys: one, ten, or as many as the coins on hand allow. */
enum class BuyMode(val label: String) { One("×1"), Ten("×10"), Max("Max") }

/** Callbacks from the screens; MainActivity applies them to the save file. */
class ParkActions(
    val boost: () -> Unit,
    val claimDaily: () -> Unit,
    val upgrade: (rideId: String, count: Int) -> Unit,
    val fix: (rideId: String) -> Unit,
    val fixAll: () -> Unit,
    val buy: (itemId: String) -> Unit,
)

@Composable
fun ParkApp(
    state: ParkState,
    now: Long,
    message: String?,
    tab: Tab,
    onTab: (Tab) -> Unit,
    buyMode: BuyMode,
    onBuyMode: (BuyMode) -> Unit,
    actions: ParkActions,
) {
    Column(modifier = Modifier.fillMaxSize().background(Palette.Card)) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.Park -> ParkTab(state, now, message, buyMode, onBuyMode, actions)
                Tab.Shop -> ShopTab(state, message, actions)
            }
        }
        BottomBar(tab, onTab)
    }
}

// ---------- Park ----------

@Composable
private fun ParkTab(state: ParkState, now: Long, message: String?, buyMode: BuyMode, onBuyMode: (BuyMode) -> Unit, actions: ParkActions) {
    // While the game is open, earnings are collected for the player, so the balance includes them.
    val pending = Park.pending(state, now)
    val boostLeft = state.boostUntil - now
    Column(modifier = Modifier.fillMaxSize().background(Palette.Grass)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Palette.ParkGreen).statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sunny Hills Park", color = Palette.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (boostLeft > 0) "2× boost · ${formatDuration(boostLeft)} left" else "Away earnings stop after 8h",
                        color = Palette.OnDarkMuted,
                        fontSize = 13.sp,
                    )
                }
                Pill("MEMBER · NO ADS", Palette.Purple)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(formatAmount(state.coins + pending), Modifier.weight(1f)) { CoinIcon() }
                Chip("${state.gems}", Modifier.weight(1f)) { GemIcon() }
                Chip("+${formatAmount(Park.incomeAt(state, now))}/s", Modifier.weight(1f)) { RateIcon() }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BigButton(
                    if (boostLeft > 0) "Extend 2× boost · ${formatDuration(boostLeft)} left" else "2× Income for 4h · Free",
                    Palette.Gold, Palette.Ink, Modifier.fillMaxWidth(),
                    enabled = boostLeft < Rules.BOOST_MAX_STACK_MS,
                    onClick = actions.boost,
                )
            }
            item { WeatherCard(now) }
            val broken = Park.brokenRides(state, now)
            if (broken.size >= 2) {
                item {
                    BigButton("Fix all ${broken.size} broken rides", Palette.RedDark, Palette.OnDark, Modifier.fillMaxWidth(), onClick = actions.fixAll)
                }
            }
            message?.let { item { MessageLine(it) } }
            item { BuyModeRow(buyMode, onBuyMode) }
            items(Park.displayOrder(state), key = { it.id }) { ride ->
                RideCard(state, ride, now, pending, buyMode, onUpgrade = { count -> actions.upgrade(ride.id, count) }, onFix = { actions.fix(ride.id) })
            }
            item {
                BigButton("Claim daily visit · +${Rules.DAILY_VISIT_GEMS} gems", Palette.Card, Palette.Ink, Modifier.fillMaxWidth(), onClick = actions.claimDaily)
            }
        }
    }
}

/** Today's weather and tomorrow's, so the player can plan which rides to push. */
@Composable
private fun WeatherCard(now: Long) {
    val today = Weather.at(now)
    val tomorrow = Weather.on(Weather.dayOf(now) + 1)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Today: ${today.label}", color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(today.effect, color = Palette.InkMuted, fontSize = 14.sp)
        Text("Tomorrow: ${tomorrow.label} · ${tomorrow.effect}", color = Palette.InkMuted, fontSize = 13.sp)
    }
}

@Composable
private fun RideCard(
    state: ParkState,
    ride: RideDef,
    now: Long,
    pending: Double,
    buyMode: BuyMode,
    onUpgrade: (count: Int) -> Unit,
    onFix: () -> Unit,
) {
    val level = state.level(ride.id)
    val broken = Park.isBroken(state, ride.id, now)
    val maxed = Park.isMaxed(state, ride)
    val weather = Weather.at(now)
    val factor = weather.factor(ride.id)
    // Upgrading collects first, so pending coins count toward the cost.
    val coins = state.coins + pending
    val remaining = ride.maxLevel - level
    val count = when (buyMode) {
        BuyMode.One -> 1
        BuyMode.Ten -> minOf(10, remaining)
        BuyMode.Max -> maxOf(1, Park.affordableLevels(ride, level, coins))
    }
    val cost = Park.upgradeCost(ride, level, count)
    val affordable = coins >= cost
    val income = Park.rideIncome(state, ride) * Park.globalMultiplier(state)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Card).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideArt(
            ride.id,
            artTier(level),
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).background(Palette.SkyLight),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ride.name, color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    level == 0 -> "Not built yet · top level ${ride.maxLevel}"
                    maxed -> "Lv ${ride.maxLevel} MAX · +${formatAmount(income)}/s"
                    else -> "Lv $level/${ride.maxLevel} · +${formatAmount(income)}/s"
                },
                color = Palette.InkMuted,
                fontSize = 13.sp,
            )
            if (level > 0) {
                when {
                    factor == 0.0 -> Text(
                        "Closed today (${weather.label})${if (broken) " · needs repair" else ""}",
                        color = Palette.RedDark,
                        fontSize = 13.sp,
                    )
                    broken -> Text("Needs repair · half speed", color = Palette.RedDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    factor > 1.0 -> Text("Busy today · ×${formatFactor(factor)}", color = Palette.ParkGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    factor < 1.0 -> Text("Slow today · ×${formatFactor(factor)}", color = Palette.InkMuted, fontSize = 13.sp)
                }
            }
            if (broken) {
                BigButton("Fix · back to full speed", Palette.RedDark, Palette.OnDark, Modifier.fillMaxWidth(), onClick = onFix)
            }
            if (level > 0 && !maxed) MilestoneProgress(level, ride.maxLevel)
            if (!maxed && !(ride.shopOnly && level == 0)) {
                val verb = if (level == 0) "Build" else "Upgrade"
                HoldToRepeatButton(
                    if (count == 1) "$verb · ${formatAmount(cost)}" else "$verb ×$count · ${formatAmount(cost)}",
                    Modifier.fillMaxWidth(), enabled = affordable, onAction = { onUpgrade(count) },
                )
            }
        }
    }
}

private fun formatFactor(factor: Double): String =
    if (factor == factor.toLong().toDouble()) factor.toLong().toString() else factor.toString()

/** 3h 12m, 45m, or "under a minute". */
private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes >= 1 -> "${minutes}m"
        else -> "under a minute"
    }
}

/** Picks how many levels each upgrade button buys. */
@Composable
private fun BuyModeRow(mode: BuyMode, onMode: (BuyMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Card).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Buy", color = Palette.InkMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        for (m in BuyMode.entries) {
            val selected = m == mode
            BigButton(
                m.label,
                if (selected) Palette.Blue else Palette.Card,
                if (selected) Palette.OnDark else Palette.Ink,
                Modifier.weight(1f),
                onClick = { onMode(m) },
            )
        }
    }
}

/** Progress towards the next milestone (every 25 levels), or the top level if that comes first. */
@Composable
private fun MilestoneProgress(level: Int, maxLevel: Int) {
    val every = Rules.MILESTONE_EVERY
    val start = level / every * every
    val milestone = start + every
    val next = minOf(milestone, maxLevel)
    val fraction = (level - start).toFloat() / (next - start)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Palette.Track)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.ParkGreen))
        }
        Text(
            if (next == milestone) "×${formatFactor(Rules.MILESTONE_FACTOR)} income at Lv $next" else "Top level at Lv $next",
            color = Palette.InkMuted,
            fontSize = 12.sp,
        )
    }
}

// ---------- Shop ----------

@Composable
private fun ShopTab(state: ParkState, message: String?, actions: ParkActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Palette.ShopBackground).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shop", color = Palette.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Chip("${state.gems} gems") { GemIcon() }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.PurpleDeep).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Membership: no ads, no real money", color = Palette.OnDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Boosts are instant. Premium items cost gems you earn by playing.", color = Palette.OnPurpleMuted, fontSize = 14.sp)
            }
        }
        message?.let { item { MessageLine(it) } }
        items(Catalog.shop, key = { it.id }) { item -> ShopRow(state, item, onBuy = { actions.buy(item.id) }) }
        item {
            Text("Earn gems", color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EarnTile("Daily visit", "+${Rules.DAILY_VISIT_GEMS}", Modifier.weight(1f))
                EarnTile("Every 25 ride levels", "+${Rules.MILESTONE_GEMS}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShopRow(state: ParkState, item: ShopItem, onBuy: () -> Unit) {
    val owned = !item.repeatable && item.id in state.ownedItems
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.name, color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (owned) {
            Text("Owned", color = Palette.ParkGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else {
            BigButton("${item.gemPrice} gems", Palette.Blue, Palette.OnDark, enabled = state.gems >= item.gemPrice, onClick = onBuy)
        }
    }
}

@Composable
private fun EarnTile(label: String, amount: String, modifier: Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Card).padding(12.dp)) {
        Text(label, color = Palette.InkMuted, fontSize = 13.sp)
        Text(amount, color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Shared pieces ----------

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Palette.Card).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (t in Tab.entries) {
            val selected = t == tab
            BigButton(
                t.name,
                if (selected) Palette.ParkGreen else Palette.Card,
                if (selected) Palette.OnDark else Palette.InkMuted,
                Modifier.weight(1f),
                onClick = { onTab(t) },
            )
        }
    }
}

@Composable
private fun BigButton(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Palette.Track,
            disabledContentColor = Palette.InkMuted,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * A tap upgrades once; holding keeps upgrading, a little faster the longer it is held, until
 * the finger lifts or the next upgrade is unaffordable.
 */
@Composable
private fun HoldToRepeatButton(text: String, modifier: Modifier, enabled: Boolean, onAction: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val latestAction by rememberUpdatedState(onAction)
    val latestEnabled by rememberUpdatedState(enabled)
    // Set once holding has started repeating, so the release does not count as one more tap.
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        repeated = false
        delay(400)
        var interval = 200L
        while (latestEnabled) {
            repeated = true
            latestAction()
            delay(interval)
            interval = maxOf(60L, interval * 85 / 100)
        }
    }

    Button(
        onClick = {
            if (!repeated) onAction()
            repeated = false
        },
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Blue,
            contentColor = Palette.OnDark,
            disabledContainerColor = Palette.Track,
            disabledContentColor = Palette.InkMuted,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Card).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(text, color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = Palette.OnDark,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun MessageLine(text: String) {
    Text(
        text,
        color = Palette.Ink,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Card).padding(12.dp),
    )
}

@Composable
private fun CoinIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(Palette.Gold)
        drawCircle(Palette.GoldDark, style = Stroke(width = size.minDimension * 0.1f))
    }
}

@Composable
private fun GemIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val gem = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.38f)
            lineTo(w * 0.5f, h)
            lineTo(0f, h * 0.38f)
            close()
        }
        drawPath(gem, Palette.Sky)
    }
}

@Composable
private fun RateIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        drawLine(Palette.ParkGreen, Offset(0f, h * 0.75f), Offset(w * 0.4f, h * 0.4f), strokeWidth = w * 0.14f)
        drawLine(Palette.ParkGreen, Offset(w * 0.4f, h * 0.4f), Offset(w * 0.6f, h * 0.6f), strokeWidth = w * 0.14f)
        drawLine(Palette.ParkGreen, Offset(w * 0.6f, h * 0.6f), Offset(w, h * 0.2f), strokeWidth = w * 0.14f)
    }
}
