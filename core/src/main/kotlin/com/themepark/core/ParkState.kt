package com.themepark.core

import kotlinx.serialization.Serializable

/**
 * Everything that is saved. Immutable: every action in [Park] returns a new copy, so the app
 * and the widget can both apply actions to the same save file without sharing mutable objects.
 *
 * All times are epoch milliseconds.
 */
@Serializable
data class ParkState(
    val coins: Double,
    val gems: Int,
    /** Ride id to level. Missing or 0 means not built. */
    val rideLevels: Map<String, Int>,
    /** Earnings accrue from this moment until the next collect (capped by [Rules.OFFLINE_CAP_MS]). */
    val lastCollectedAt: Long,
    /** The free membership boost is active until this time. */
    val boostUntil: Long = 0,
    /** Ids of one-time shop items already bought. */
    val ownedItems: Set<String> = emptySet(),
    /** UTC day number (epoch days) of the last daily-visit gem claim; -1 if never. */
    val lastDailyClaimDay: Long = -1,
) {
    fun level(rideId: String): Int = rideLevels[rideId] ?: 0

    companion object {
        fun newGame(now: Long): ParkState = ParkState(
            coins = 20.0,
            gems = 0,
            rideLevels = mapOf("snack_stand" to 1),
            lastCollectedAt = now,
        )
    }
}

object Rules {
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 24 * HOUR_MS

    /** Earnings stop after this long without collecting, so a few check-ins a day is enough. */
    const val OFFLINE_CAP_MS = 8 * HOUR_MS

    /** Each upgrade costs this much more than the last. */
    const val COST_GROWTH = 1.15

    /** Every this many levels a ride's income doubles and the player earns [MILESTONE_GEMS]. */
    const val MILESTONE_EVERY = 25
    const val MILESTONE_GEMS = 25

    const val DAILY_VISIT_GEMS = 10

    /** Membership boost: no ad needed, but it still runs for a fixed time and stacks only so far. */
    const val BOOST_FACTOR = 2.0
    const val BOOST_DURATION_MS = 4 * HOUR_MS
    const val BOOST_MAX_STACK_MS = 8 * HOUR_MS
}
