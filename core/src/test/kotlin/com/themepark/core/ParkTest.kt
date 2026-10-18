package com.themepark.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParkTest {
    private fun firstDay(weather: Weather): Long = generateSequence(20_000L) { it + 1 }.first { Weather.on(it) == weather }

    // One hour into a sunny day, so weather does not change any numbers and 8h stays in the day.
    private val t0 = firstDay(Weather.SUNNY) * Rules.DAY_MS + Rules.HOUR_MS
    private val second = 1_000L
    private val eps = 1e-6

    /** By default the mechanic is owned, so breakdowns do not change any numbers either. */
    private fun state(
        coins: Double = 0.0,
        gems: Int = 0,
        levels: Map<String, Int> = mapOf("snack_stand" to 1),
        boostUntil: Long = 0,
        owned: Set<String> = setOf("mechanic"),
        fixedAt: Map<String, Long> = levels.keys.associateWith { t0 },
        collectedAt: Long = t0,
    ) = ParkState(
        coins, gems, levels, lastCollectedAt = collectedAt, boostUntil = boostUntil, ownedItems = owned, lastFixedAt = fixedAt,
    )

    private fun ok(outcome: Outcome): ParkState = assertIs<Outcome.Ok>(outcome).state

    @Test
    fun `upgrade cost grows 15 percent per level`() {
        val ride = Catalog.ride("carousel")
        assertEquals(150.0, Park.upgradeCost(ride, 0), eps)
        assertEquals(172.5, Park.upgradeCost(ride, 1), eps)
        assertEquals(150.0 * 1.15 * 1.15, Park.upgradeCost(ride, 2), eps)
    }

    @Test
    fun `income doubles at every 25th level`() {
        val ride = Catalog.ride("snack_stand")
        assertEquals(24.0, Park.rideIncome(state(levels = mapOf("snack_stand" to 24)), ride), eps)
        assertEquals(50.0, Park.rideIncome(state(levels = mapOf("snack_stand" to 25)), ride), eps)
        assertEquals(200.0, Park.rideIncome(state(levels = mapOf("snack_stand" to 50)), ride), eps)
    }

    @Test
    fun `unbuilt rides earn nothing`() {
        assertEquals(0.0, Park.rideIncome(state(), Catalog.ride("ferris_wheel")), eps)
    }

    @Test
    fun `pending grows with time and stops at the offline cap`() {
        val s = state() // 1 coin per second
        assertEquals(60.0, Park.pending(s, t0 + 60 * second), eps)
        val cap = Rules.OFFLINE_CAP_MS / 1000.0
        assertEquals(cap, Park.pending(s, t0 + Rules.OFFLINE_CAP_MS), eps)
        assertEquals(cap, Park.pending(s, t0 + 3 * Rules.OFFLINE_CAP_MS), eps)
    }

    @Test
    fun `pending never goes negative if the clock moves backwards`() {
        assertEquals(0.0, Park.pending(state(), t0 - 60 * second), eps)
    }

    @Test
    fun `boost doubles only the boosted part of the window`() {
        val s = state(boostUntil = t0 + 100 * second)
        // 100 s boosted at 2/s, then 50 s at 1/s.
        assertEquals(250.0, Park.pending(s, t0 + 150 * second), eps)
    }

    @Test
    fun `collect moves pending into coins and restarts the clock`() {
        val now = t0 + 60 * second
        val s = Park.collect(state(coins = 5.0), now)
        assertEquals(65.0, s.coins, eps)
        assertEquals(now, s.lastCollectedAt)
        assertEquals(0.0, Park.pending(s, now), eps)
    }

    @Test
    fun `upgrade settles earnings at the old rate before paying`() {
        val now = t0 + 100 * second
        val s = ok(Park.upgrade(state(coins = 0.0), "snack_stand", now))
        // Earned 100 at level 1, paid 15 * 1.15 for level 2.
        assertEquals(100.0 - 15.0 * 1.15, s.coins, eps)
        assertEquals(2, s.level("snack_stand"))
        assertEquals(now, s.lastCollectedAt)
    }

    @Test
    fun `upgrade without enough coins is refused and says how much is missing`() {
        val outcome = Park.upgrade(state(coins = 100.0), "ferris_wheel", t0)
        assertEquals(1_100.0, assertIs<Outcome.NotEnoughCoins>(outcome).needed, eps)
    }

    @Test
    fun `reaching a milestone level awards gems`() {
        val s = ok(Park.upgrade(state(coins = 1e9, levels = mapOf("snack_stand" to 24)), "snack_stand", t0))
        assertEquals(Rules.MILESTONE_GEMS, s.gems)
        val next = ok(Park.upgrade(s, "snack_stand", t0))
        assertEquals(Rules.MILESTONE_GEMS, next.gems)
    }

    @Test
    fun `shop-only rides cannot be built with coins`() {
        assertIs<Outcome.Refused>(Park.upgrade(state(coins = 1e9), "golden_coaster", t0))
    }

    @Test
    fun `boost is free but capped`() {
        val first = ok(Park.startBoost(state(), t0))
        assertEquals(t0 + Rules.BOOST_DURATION_MS, first.boostUntil)
        val second = ok(Park.startBoost(first, t0))
        assertEquals(t0 + Rules.BOOST_MAX_STACK_MS, second.boostUntil)
        assertIs<Outcome.Refused>(Park.startBoost(second, t0))
    }

    @Test
    fun `daily visit pays once per UTC day`() {
        val first = ok(Park.claimDailyVisit(state(), t0))
        assertEquals(Rules.DAILY_VISIT_GEMS, first.gems)
        assertIs<Outcome.Refused>(Park.claimDailyVisit(first, t0 + 60 * second))
        val tomorrow = ok(Park.claimDailyVisit(first, t0 + Rules.DAY_MS))
        assertEquals(2 * Rules.DAILY_VISIT_GEMS, tomorrow.gems)
    }

    @Test
    fun `buying needs enough gems`() {
        val outcome = Park.buy(state(gems = 100), "golden_coaster", t0)
        assertEquals(200, assertIs<Outcome.NotEnoughGems>(outcome).needed)
    }

    @Test
    fun `one-time items cannot be bought twice`() {
        val s = ok(Park.buy(state(gems = 1_000), "fountain", t0))
        assertEquals(940, s.gems)
        assertTrue("fountain" in s.ownedItems)
        assertIs<Outcome.Refused>(Park.buy(s, "fountain", t0))
    }

    @Test
    fun `time warp pays an hour of income and can be bought again`() {
        val s = ok(Park.buy(state(gems = 100), "time_warp_1h", t0))
        assertEquals(3_600.0, s.coins, eps)
        val again = ok(Park.buy(s, "time_warp_1h", t0))
        assertEquals(7_200.0, again.coins, eps)
        assertEquals(20, again.gems)
    }

    @Test
    fun `multipliers settle earnings at the old rate first`() {
        val now = t0 + 100 * second
        val s = ok(Park.buy(state(gems = 600), "double_income", now))
        assertEquals(100.0, s.coins, eps)
        assertEquals(2.0, Park.baseIncome(s), eps)
    }

    @Test
    fun `golden coaster unlock builds the ride`() {
        val s = ok(Park.buy(state(gems = 300), "golden_coaster", t0))
        assertEquals(1, s.level("golden_coaster"))
        assertEquals(501.0, Park.baseIncome(s), eps)
    }

    @Test
    fun `vip manager triples only the ferris wheel`() {
        val s = state(levels = mapOf("snack_stand" to 1, "ferris_wheel" to 1), owned = setOf("vip_manager"))
        assertEquals(36.0, Park.rideIncome(s, Catalog.ride("ferris_wheel")), eps)
        assertEquals(1.0, Park.rideIncome(s, Catalog.ride("snack_stand")), eps)
    }

    @Test
    fun `suggested upgrade is the cheapest one`() {
        assertEquals("snack_stand", Park.suggestedUpgrade(state())?.id)
        val pricey = state(levels = mapOf("snack_stand" to 40))
        assertEquals("carousel", Park.suggestedUpgrade(pricey)?.id)
    }

    @Test
    fun `suggested upgrade never offers a locked shop-only ride`() {
        val all = Catalog.rides.filter { !it.shopOnly }.associate { it.id to 500 }
        assertTrue(Park.suggestedUpgrade(state(levels = all))?.shopOnly != true)
    }

    @Test
    fun `display order puts the next ride first, then built rides newest first`() {
        assertEquals(listOf("carousel", "snack_stand"), Park.displayOrder(state()).map { it.id })
        val three = state(levels = mapOf("snack_stand" to 3, "carousel" to 2, "ferris_wheel" to 1))
        assertEquals(listOf("roller_coaster", "ferris_wheel", "carousel", "snack_stand"), Park.displayOrder(three).map { it.id })
    }

    @Test
    fun `display order with everything built has no next ride and shows the golden coaster first`() {
        val all = Catalog.rides.associate { it.id to 1 }
        assertEquals(Catalog.rides.reversed().map { it.id }, Park.displayOrder(state(levels = all)).map { it.id })
    }

    @Test
    fun `display order never offers the shop-only ride as the next to build`() {
        val buildable = Catalog.rides.filter { !it.shopOnly }.associate { it.id to 1 }
        assertTrue(Park.displayOrder(state(levels = buildable)).none { it.shopOnly })
    }

    @Test
    fun `weather is the same for the same day and every kind happens`() {
        assertEquals(Weather.on(20_123), Weather.on(20_123))
        val seen = (20_000L until 20_400L).map { Weather.on(it) }.toSet()
        assertEquals(Weather.entries.toSet(), seen)
    }

    @Test
    fun `rain closes the coaster and fills the carousel`() {
        val rainy = firstDay(Weather.RAINY) * Rules.DAY_MS + Rules.HOUR_MS
        val s = state(levels = mapOf("carousel" to 1, "roller_coaster" to 1), collectedAt = rainy, fixedAt = mapOf("carousel" to rainy, "roller_coaster" to rainy))
        assertEquals(4.0 * 1.5, Park.incomeAt(s, rainy), eps)
    }

    @Test
    fun `pending pays each day at that day's weather`() {
        // Find a sunny day followed by a hot one; snack stand earns 1/s then 2/s.
        val day = generateSequence(20_000L) { it + 1 }.first { Weather.on(it) == Weather.SUNNY && Weather.on(it + 1) == Weather.HOT }
        val midnight = (day + 1) * Rules.DAY_MS
        val start = midnight - 100 * second
        val s = state(collectedAt = start)
        assertEquals(100.0 + 2 * 50.0, Park.pending(s, midnight + 50 * second), eps)
    }

    @Test
    fun `a ride breaks down between 3 and 6 hours after it was fixed and then earns nothing`() {
        val s = state(owned = emptySet())
        val breaksAt = Park.breaksAt(s, "snack_stand")!!
        assertTrue(breaksAt - t0 in Rules.BREAKDOWN_MIN_MS until Rules.BREAKDOWN_MIN_MS + Rules.BREAKDOWN_SPREAD_MS)
        assertTrue(!Park.isBroken(s, "snack_stand", breaksAt - 1))
        assertTrue(Park.isBroken(s, "snack_stand", breaksAt))
        val earned = (breaksAt - t0) / 1000.0
        assertEquals(earned, Park.pending(s, t0 + Rules.OFFLINE_CAP_MS), eps)
    }

    @Test
    fun `fixing a broken ride pays what it earned and starts it again`() {
        val s = state(owned = emptySet())
        val later = Park.breaksAt(s, "snack_stand")!! + Rules.HOUR_MS
        assertIs<Outcome.Refused>(Park.fix(s, "snack_stand", t0))
        val fixed = ok(Park.fix(s, "snack_stand", later))
        assertEquals((later - Rules.HOUR_MS - t0) / 1000.0, fixed.coins, eps)
        assertTrue(!Park.isBroken(fixed, "snack_stand", later))
        assertEquals(60.0, Park.pending(fixed, later + 60 * second), eps)
    }

    @Test
    fun `fix all repairs every broken ride`() {
        val levels = mapOf("snack_stand" to 1, "carousel" to 1)
        val s = state(levels = levels, owned = emptySet(), fixedAt = emptyMap())
        assertEquals(2, Park.brokenRides(s, t0).size)
        val fixed = ok(Park.fixAll(s, t0))
        assertTrue(Park.brokenRides(fixed, t0).isEmpty())
        assertIs<Outcome.Refused>(Park.fixAll(fixed, t0))
    }

    @Test
    fun `a built ride with no fix time counts as broken, as in saves from before breakdowns`() {
        assertTrue(Park.isBroken(state(owned = emptySet(), fixedAt = emptyMap()), "snack_stand", t0))
    }

    @Test
    fun `the mechanic stops breakdowns`() {
        val s = state(owned = setOf("mechanic"), fixedAt = emptyMap())
        assertTrue(!Park.isBroken(s, "snack_stand", t0 + Rules.DAY_MS))
        assertEquals(null, Park.breaksAt(s, "snack_stand"))
    }

    @Test
    fun `building a ride starts its breakdown clock`() {
        val s = ok(Park.upgrade(state(coins = 1_000.0, owned = emptySet()), "carousel", t0))
        assertEquals(t0, s.lastFixedAt["carousel"])
        assertTrue(!Park.isBroken(s, "carousel", t0))
    }

    @Test
    fun `save file round-trips`() {
        val s = state(coins = 12.5, gems = 7, boostUntil = t0 + 5, owned = setOf("fountain"))
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }

    @Test
    fun `unknown save version is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SaveCodec.decode("""{"version":99,"state":{"coins":0,"gems":0,"rideLevels":{},"lastCollectedAt":0}}""")
        }
    }
}
