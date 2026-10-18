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
import com.themepark.core.ShopItem
import kotlinx.coroutines.delay

enum class Tab { Park, Shop }

/** Callbacks from the screens; MainActivity applies them to the save file. */
class ParkActions(
    val collect: () -> Unit,
    val boost: () -> Unit,
    val claimDaily: () -> Unit,
    val upgrade: (rideId: String) -> Unit,
    val buy: (itemId: String) -> Unit,
)

@Composable
fun ParkApp(state: ParkState, now: Long, message: String?, tab: Tab, onTab: (Tab) -> Unit, actions: ParkActions) {
    Column(modifier = Modifier.fillMaxSize().background(Palette.Card)) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.Park -> ParkTab(state, now, message, actions)
                Tab.Shop -> ShopTab(state, message, actions)
            }
        }
        BottomBar(tab, onTab)
    }
}

// ---------- Park ----------

@Composable
private fun ParkTab(state: ParkState, now: Long, message: String?, actions: ParkActions) {
    val pending = Park.pending(state, now)
    Column(modifier = Modifier.fillMaxSize().background(Palette.Grass)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Palette.ParkGreen).statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sunny Hills Park", color = Palette.OnDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (now < state.boostUntil) "2× boost running" else "Earnings stop 8h after you collect",
                        color = Palette.OnDarkMuted,
                        fontSize = 13.sp,
                    )
                }
                Pill("MEMBER · NO ADS", Palette.Purple)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(formatAmount(state.coins), Modifier.weight(1f)) { CoinIcon() }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(
                        if (pending >= 1) "Collect ${formatAmount(pending)}" else "Earning…",
                        Palette.Gold, Palette.Ink, Modifier.weight(1f), enabled = pending >= 1, onClick = actions.collect,
                    )
                    BigButton(
                        if (now < state.boostUntil) "Extend 2×" else "2× Income · Free",
                        Palette.Card, Palette.Ink, Modifier.weight(1f), onClick = actions.boost,
                    )
                }
            }
            message?.let { item { MessageLine(it) } }
            items(Park.displayOrder(state), key = { it.id }) { ride ->
                RideCard(state, ride, pending, onUpgrade = { actions.upgrade(ride.id) })
            }
            item {
                BigButton("Claim daily visit · +10 gems", Palette.Card, Palette.Ink, Modifier.fillMaxWidth(), onClick = actions.claimDaily)
            }
        }
    }
}

@Composable
private fun RideCard(state: ParkState, ride: RideDef, pending: Double, onUpgrade: () -> Unit) {
    val level = state.level(ride.id)
    val cost = Park.upgradeCost(ride, level)
    // Upgrading collects first, so pending coins count toward the cost.
    val affordable = state.coins + pending >= cost
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
                if (level == 0) "Not built yet" else "Lv $level · +${formatAmount(Park.rideIncome(state, ride))}/s",
                color = Palette.InkMuted,
                fontSize = 13.sp,
            )
            nextArtLevel(level)?.takeIf { level > 0 }?.let { next ->
                NewLookProgress(level, next)
            }
            if (!ride.shopOnly) {
                HoldToRepeatButton(
                    "${if (level == 0) "Build" else "Upgrade"} · ${formatAmount(cost)}",
                    Modifier.fillMaxWidth(), enabled = affordable, onAction = onUpgrade,
                )
            }
        }
    }
}

/** Progress towards the level where the ride's picture next changes. */
@Composable
private fun NewLookProgress(level: Int, next: Int) {
    val start = listOf(1, 10, 25, 50).last { it <= level }
    val fraction = (level - start).toFloat() / (next - start)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Palette.Track)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.ParkGreen))
        }
        Text("New look at Lv $next", color = Palette.InkMuted, fontSize = 12.sp)
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
                EarnTile("Daily visit", "+10", Modifier.weight(1f))
                EarnTile("Ride Lv 25, 50…", "+25", Modifier.weight(1f))
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
