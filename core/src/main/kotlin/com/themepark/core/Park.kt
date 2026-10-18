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
        var income = ride.baseIncomePerSec * level * 2.0.pow(milestones)
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

    /** Income per second right now, including the boost if it is running. */
    fun incomeAt(state: ParkState, now: Long): Double =
        baseIncome(state) * if (now < state.boostUntil) Rules.BOOST_FACTOR else 1.0

    /** Coins waiting to be collected. Stops growing [Rules.OFFLINE_CAP_MS] after the last collect. */
    fun pending(state: ParkState, now: Long): Double {
        val start = state.lastCollectedAt
        val end = min(now, start + Rules.OFFLINE_CAP_MS)
        if (end <= start) return 0.0
        val boostedMs = max(0L, min(end, state.boostUntil) - start)
        val boostedSeconds = boostedMs / 1000.0
        val totalSeconds = (end - start) / 1000.0
        val base = baseIncome(state)
        return base * totalSeconds + base * (Rules.BOOST_FACTOR - 1.0) * boostedSeconds
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
            is ShopEffect.UnlockRide -> paid.copy(rideLevels = paid.rideLevels + (effect.rideId to max(1, paid.level(effect.rideId))))
            is ShopEffect.RideMultiplier, is ShopEffect.GlobalMultiplier, ShopEffect.Cosmetic -> paid
        }
        return Outcome.Ok(result)
    }

    /** The cheapest next upgrade, which is what the widget offers. Null if nothing can be upgraded. */
    fun suggestedUpgrade(state: ParkState): RideDef? =
        Catalog.rides
            .filter { !it.shopOnly || state.level(it.id) > 0 }
            .minByOrNull { upgradeCost(it, state.level(it.id)) }
}
