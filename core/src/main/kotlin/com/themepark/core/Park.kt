package com.themepark.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

sealed interface Outcome {
    data class Ok(val state: ParkState) : Outcome

    data class NotEnoughCoins(val needed: Double) : Outcome

    data class NotEnoughGems(val needed: Int) : Outcome

    data class Refused(val reason: String) : Outcome
}

/**
 * The game rules. Pure functions of (state, now): no clock, no I/O, no randomness, so the app,
 * the widget and the tests all get the same answer for the same inputs.
 */
object Park {

    fun upgradeCost(ride: RideDef, currentLevel: Int): Double =
        ride.baseCost * Rules.COST_GROWTH.pow(currentLevel)

    /** Income of one ride per second, without the temporary boost. */
    fun rideIncome(state: ParkState, ride: RideDef): Double {
        val level = state.level(ride.id)
        if (level <= 0) return 0.0
        val milestones = level / Rules.MILESTONE_EVERY
        var income = ride.baseIncomePerSec * level * Rules.MILESTONE_FACTOR.pow(milestones)
        for (id in state.ownedItems) {
            val effect = Catalog.shopItem(id).effect
            if (effect is ShopEffect.RideMultiplier && effect.rideId == ride.id) income *= effect.factor
        }
        return income
    }

    /** Income of the whole park per second, without the temporary boost. */
    fun baseIncome(state: ParkState): Double {
        var total = Catalog.rides.sumOf { rideIncome(state, it) }
        for (id in state.ownedItems) {
            val effect = Catalog.shopItem(id).effect
            if (effect is ShopEffect.GlobalMultiplier) total *= effect.factor
        }
        return total
    }

    /** Whether the player owns a shop item with this effect type. */
    private inline fun <reified T : ShopEffect> owns(state: ParkState): Boolean =
        state.ownedItems.any { Catalog.shopItem(it).effect is T }

    /**
     * When a built ride breaks down, or null if it never will (mechanic owned, not built, or
     * already counted as broken because it has no fix time).
     */
    fun breaksAt(state: ParkState, rideId: String): Long? {
        if (state.level(rideId) <= 0 || owns<ShopEffect.NoBreakdowns>(state)) return null
        val fixedAt = state.lastFixedAt[rideId] ?: return null
        val spread = Math.floorMod(mix(rideId.hashCode() * 31L + fixedAt), Rules.BREAKDOWN_SPREAD_MS)
        return fixedAt + Rules.BREAKDOWN_MIN_MS + spread
    }

    fun isBroken(state: ParkState, rideId: String, time: Long): Boolean {
        if (state.level(rideId) <= 0 || owns<ShopEffect.NoBreakdowns>(state)) return false
        val breaksAt = breaksAt(state, rideId) ?: return true
        return time >= breaksAt
    }

    fun brokenRides(state: ParkState, now: Long): List<RideDef> =
        Catalog.rides.filter { isBroken(state, it.id, now) }

    /** Income per second at this moment: weather, breakdowns and the boost included. */
    fun incomeAt(state: ParkState, time: Long): Double {
        val weather = Weather.at(time)
        var total = Catalog.rides.sumOf { ride ->
            if (isBroken(state, ride.id, time)) 0.0 else rideIncome(state, ride) * weather.factor(ride.id)
        }
        for (id in state.ownedItems) {
            val effect = Catalog.shopItem(id).effect
            if (effect is ShopEffect.GlobalMultiplier) total *= effect.factor
        }
        return total * if (time < state.boostUntil) Rules.BOOST_FACTOR else 1.0
    }

    /**
     * Coins waiting to be collected. Stops growing [Rules.OFFLINE_CAP_MS] after the last collect.
     * Income only changes at a new day (weather), the end of the boost or a breakdown, so the
     * window is split at those moments and each piece is paid at its own steady rate.
     */
    fun pending(state: ParkState, now: Long): Double {
        val start = state.lastCollectedAt
        val end = min(now, start + Rules.OFFLINE_CAP_MS)
        if (end <= start) return 0.0
        val cuts = sortedSetOf(start, end)
        var day = (Weather.dayOf(start) + 1) * Rules.DAY_MS
        while (day < end) {
            cuts += day
            day += Rules.DAY_MS
        }
        if (state.boostUntil in (start + 1) until end) cuts += state.boostUntil
        for (ride in Catalog.rides) {
            breaksAt(state, ride.id)?.takeIf { it in (start + 1) until end }?.let { cuts += it }
        }
        return cuts.zipWithNext().sumOf { (from, to) -> incomeAt(state, from) * (to - from) / 1000.0 }
    }

    fun collect(state: ParkState, now: Long): ParkState =
        state.copy(coins = state.coins + pending(state, now), lastCollectedAt = now)

    /**
     * Builds or upgrades a ride by one level. Collects first, because the upgrade changes the
     * income rate and earnings so far must be paid at the old rate.
     */
    fun upgrade(state: ParkState, rideId: String, now: Long): Outcome {
        val ride = Catalog.ride(rideId)
        if (ride.shopOnly && state.level(rideId) == 0) return Outcome.Refused("${ride.name} is unlocked in the shop")
        if (state.level(rideId) >= ride.maxLevel) return Outcome.Refused("${ride.name} is at its top level")
        val settled = collect(state, now)
        val level = settled.level(rideId)
        val cost = upgradeCost(ride, level)
        if (settled.coins < cost) return Outcome.NotEnoughCoins(cost - settled.coins)
        val newLevel = level + 1
        val milestoneGems = if (newLevel % Rules.MILESTONE_EVERY == 0) Rules.MILESTONE_GEMS else 0
        return Outcome.Ok(
            settled.copy(
                coins = settled.coins - cost,
                gems = settled.gems + milestoneGems,
                rideLevels = settled.rideLevels + (rideId to newLevel),
                lastFixedAt = if (level == 0) settled.lastFixedAt + (rideId to now) else settled.lastFixedAt,
            ),
        )
    }

    /** Free for members (no ad), but it runs for a fixed time and stacks only up to a limit. */
    fun startBoost(state: ParkState, now: Long): Outcome {
        val from = max(now, state.boostUntil)
        if (from - now >= Rules.BOOST_MAX_STACK_MS) return Outcome.Refused("Boost is already at its limit")
        val settled = collect(state, now)
        val until = min(from + Rules.BOOST_DURATION_MS, now + Rules.BOOST_MAX_STACK_MS)
        return Outcome.Ok(settled.copy(boostUntil = until))
    }

    /** Repairs a broken ride. Collects first, so earnings before the repair are paid. */
    fun fix(state: ParkState, rideId: String, now: Long): Outcome {
        val ride = Catalog.ride(rideId)
        if (!isBroken(state, rideId, now)) return Outcome.Refused("${ride.name} is running fine")
        val settled = collect(state, now)
        return Outcome.Ok(settled.copy(lastFixedAt = settled.lastFixedAt + (rideId to now)))
    }

    fun fixAll(state: ParkState, now: Long): Outcome {
        val broken = brokenRides(state, now)
        if (broken.isEmpty()) return Outcome.Refused("Every ride is running")
        val settled = collect(state, now)
        return Outcome.Ok(settled.copy(lastFixedAt = settled.lastFixedAt + broken.map { it.id to now }))
    }

    fun claimDailyVisit(state: ParkState, now: Long): Outcome {
        val today = floor(now.toDouble() / Rules.DAY_MS).toLong()
        if (state.lastDailyClaimDay >= today) return Outcome.Refused("Already claimed today")
        return Outcome.Ok(state.copy(gems = state.gems + Rules.DAILY_VISIT_GEMS, lastDailyClaimDay = today))
    }

    fun buy(state: ParkState, itemId: String, now: Long): Outcome {
        val item = Catalog.shopItem(itemId)
        if (!item.repeatable && itemId in state.ownedItems) return Outcome.Refused("${item.name} is already owned")
        if (state.gems < item.gemPrice) return Outcome.NotEnoughGems(item.gemPrice - state.gems)
        // Settle at the old rate before any multiplier changes it.
        val settled = collect(state, now)
        val paid = settled.copy(
            gems = settled.gems - item.gemPrice,
            ownedItems = if (item.repeatable) settled.ownedItems else settled.ownedItems + itemId,
        )
        val result = when (val effect = item.effect) {
            is ShopEffect.TimeWarp -> paid.copy(coins = paid.coins + incomeAt(paid, now) * effect.seconds)
            is ShopEffect.UnlockRide -> paid.copy(
                rideLevels = paid.rideLevels + (effect.rideId to max(1, paid.level(effect.rideId))),
                lastFixedAt = paid.lastFixedAt + (effect.rideId to now),
            )
            is ShopEffect.RideMultiplier, is ShopEffect.GlobalMultiplier, ShopEffect.NoBreakdowns, ShopEffect.Cosmetic -> paid
        }
        return Outcome.Ok(result)
    }

    fun isMaxed(state: ParkState, ride: RideDef): Boolean = state.level(ride.id) >= ride.maxLevel

    /** The cheapest next upgrade, which is what the widget offers. Null if nothing can be upgraded. */
    fun suggestedUpgrade(state: ParkState): RideDef? =
        displayOrder(state)
            .filter { !isMaxed(state, it) && !(it.shopOnly && state.level(it.id) == 0) }
            .minByOrNull { upgradeCost(it, state.level(it.id)) }

    /**
     * Rides in the order the park screen lists them: the next ride to build on top, then the
     * built rides, newest first. Rides after the next one stay hidden until it is built.
     */
    fun displayOrder(state: ParkState): List<RideDef> {
        val next = Catalog.rides.firstOrNull { !it.shopOnly && state.level(it.id) == 0 }
        val built = Catalog.rides.filter { state.level(it.id) > 0 }.reversed()
        return listOfNotNull(next) + built
    }
}
