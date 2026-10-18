package com.themepark.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plays the real rules the way a calm player would (four check-ins a day: fix, claim, boost,
 * collect, then buy the best-value upgrades) and checks the pacing stays in the intended band.
 * If tuning in [Catalog] or [Rules] changes, this is what says whether the game still feels right.
 */
class BalanceTest {
    private val checkIns = listOf(8, 12, 17, 21)

    @Test
    fun `with four check-ins a day a new ride every few days and the park done in five to ten weeks`() {
        val midnight = 20_000L * Rules.DAY_MS
        var s = ParkState.newGame(midnight + 8 * Rules.HOUR_MS)
        val unlockedOn = mutableMapOf("snack_stand" to 0.0)
        val upgradesPerDay = mutableListOf<Int>()
        var doneOn: Int? = null
        val buildable = Catalog.rides.filter { !it.shopOnly }

        for (day in 0 until 120) {
            var today = 0
            for (hour in checkIns) {
                val now = midnight + day * Rules.DAY_MS + hour * Rules.HOUR_MS
                s = (Park.fixAll(s, now) as? Outcome.Ok)?.state ?: s
                s = (Park.claimDailyVisit(s, now) as? Outcome.Ok)?.state ?: s
                s = (Park.startBoost(s, now) as? Outcome.Ok)?.state ?: s
                s = Park.collect(s, now)
                while (true) {
                    val choice = bestValue(s) ?: break
                    val outcome = Park.upgrade(s, choice.id, now)
                    if (outcome !is Outcome.Ok) break
                    if (s.level(choice.id) == 0) unlockedOn[choice.id] = day + hour / 24.0
                    s = outcome.state
                    today++
                }
            }
            upgradesPerDay += today
            if (buildable.all { Park.isMaxed(s, it) }) {
                doneOn = day
                break
            }
        }

        val unlocks = buildable.map { unlockedOn[it.id] }
        println("Unlocked on day: ${unlocks.map { it?.let { d -> "%.1f".format(d) } }}")
        println("Park done on day: $doneOn")
        println("Upgrades per day: $upgradesPerDay")

        assertTrue(doneOn != null && doneOn in 35..70, "park done on day $doneOn, want 35-70")
        assertTrue(unlocks.all { it != null && it <= 40.0 }, "every ride unlocked by day 40: $unlocks")
        val gaps = unlocks.filterNotNull().zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it <= 7.0 }, "no more than a week between new rides: $gaps")
        assertTrue(upgradesPerDay.first() in 3..40, "first day has some upgrades but not a flood: ${upgradesPerDay.first()}")
    }

    /** The upgrade adding the most income per coin among built rides and the next ride. */
    private fun bestValue(s: ParkState): RideDef? {
        val next = Park.displayOrder(s).firstOrNull { s.level(it.id) == 0 }
        return Catalog.rides
            .filter { !it.shopOnly && !Park.isMaxed(s, it) && (s.level(it.id) > 0 || it == next) }
            .maxByOrNull { ride ->
                val level = s.level(ride.id)
                val gain = Park.rideIncome(s.copy(rideLevels = s.rideLevels + (ride.id to level + 1)), ride) - Park.rideIncome(s, ride)
                gain / Park.upgradeCost(ride, level)
            }
    }
}
