package com.themepark.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plays every level of the park with the real rules, always buying the best-value upgrade, and
 * measures each one in seconds of the park's income at that moment ("payback"). This is what
 * says whether the game still feels right after tuning [Catalog] or [Rules]: upgrades should
 * come every second or so at the start and every few minutes near the end, never hours.
 */
class BalanceTest {
    private fun median(xs: List<Double>) = xs.sorted()[xs.size / 2]

    @Test
    fun `upgrades take seconds early, minutes late, and the park takes days of income`() {
        val t = 20_000L * Rules.DAY_MS
        var s = ParkState.newGame(t).copy(ownedItems = setOf("mechanic"))
        val payback = mutableListOf<Double>()
        val unlockedAtHours = mutableListOf(0.0)
        var incomeSeconds = 0.0

        while (true) {
            val ride = bestValue(s) ?: break
            val cost = Park.upgradeCost(ride, s.level(ride.id))
            val seconds = cost / Park.baseIncome(s)
            payback += seconds
            incomeSeconds += maxOf(0.0, (cost - s.coins) / Park.baseIncome(s))
            if (s.level(ride.id) == 0) unlockedAtHours += incomeSeconds / 3600
            // Pay for the upgrade with exactly the coins it needs; time is measured as income above.
            val outcome = Park.upgrade(s.copy(coins = maxOf(s.coins, cost), lastCollectedAt = t), ride.id, t)
            s = (outcome as Outcome.Ok).state
        }

        val early = median(payback.take(300))
        val middle = median(payback.subList(payback.size / 2 - 150, payback.size / 2 + 150))
        val late = median(payback.takeLast(300))
        val hours = incomeSeconds / 3600
        println("Levels bought: ${payback.size}; income-hours to finish: %.0f".format(hours))
        println("Median seconds of income per upgrade: early %.1f, middle %.0f, late %.0f".format(early, middle, late))
        println("Rides opened after this many income-hours: ${unlockedAtHours.map { "%.1f".format(it) }}")

        assertEquals(3_699, payback.size, "every level but the Snack Stand's first, which a new game starts with")
        assertTrue(early < 5, "early upgrades should cost seconds of income, got $early")
        assertTrue(middle in 5.0..120.0, "mid-game upgrades should cost under two minutes, got $middle")
        assertTrue(late in 60.0..1_800.0, "late upgrades should cost minutes, not hours: $late")
        assertTrue(payback.sorted()[payback.size * 99 / 100] < 3_600, "almost no upgrade should cost an hour of income")
        assertTrue(hours in 80.0..300.0, "finishing the park should take days of income, got $hours h")
        assertTrue(unlockedAtHours.size == 12 && unlockedAtHours[5] < 1.0, "the first six rides open within an hour")
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
