package com.themepark.core

import kotlin.math.pow

/** A ride (or stall) type. */
data class RideDef(
    val id: String,
    val name: String,
    /** Coins per second at level 1, before milestones and multipliers. */
    val baseIncomePerSec: Double,
    /** Cost of going from level 0 (not built) to level 1. */
    val baseCost: Double,
    /** The ride cannot be upgraded past this level. */
    val maxLevel: Int,
    /** Shop-only rides cannot be built with coins; they are unlocked by a [ShopItem]. */
    val shopOnly: Boolean = false,
)

/** What a [ShopItem] does once bought. */
sealed interface ShopEffect {
    /** Instantly pays out this many seconds of current income. */
    data class TimeWarp(val seconds: Long) : ShopEffect

    /** Permanently multiplies one ride's income. */
    data class RideMultiplier(val rideId: String, val factor: Double) : ShopEffect

    /** Permanently multiplies all income. */
    data class GlobalMultiplier(val factor: Double) : ShopEffect

    /** Builds a shop-only ride at level 1. */
    data class UnlockRide(val rideId: String) : ShopEffect

    /** Rides never break down. */
    data object NoBreakdowns : ShopEffect

    /** No gameplay effect; owned for looks. */
    data object Cosmetic : ShopEffect
}

data class ShopItem(
    val id: String,
    val name: String,
    val gemPrice: Int,
    val effect: ShopEffect,
    /** One-time items can only be bought once; repeatable ones (time warps) any number of times. */
    val repeatable: Boolean = false,
)

object Catalog {
    /**
     * Rides are unlocked in this order; 3,700 levels in all. Ride n costs [RIDE_COST_STEP]^n
     * times the first and earns [RIDE_INCOME_STEP]^n times as much. Tuned so an upgrade costs
     * about a second of income at the start, tens of seconds mid-game and minutes near the end,
     * with the six first rides open within the first hour (see BalanceTest).
     */
    val rides: List<RideDef> = listOf(
        ride(0, "snack_stand", "Snack Stand", maxLevel = 200),
        ride(1, "carousel", "Carousel", maxLevel = 250),
        ride(2, "bumper_cars", "Bumper Cars", maxLevel = 250),
        ride(3, "teacups", "Spinning Teacups", maxLevel = 300),
        ride(4, "ferris_wheel", "Ferris Wheel", maxLevel = 300),
        ride(5, "pirate_ship", "Pirate Ship", maxLevel = 300),
        ride(6, "log_flume", "Log Flume", maxLevel = 325),
        ride(7, "haunted_house", "Haunted House", maxLevel = 325),
        ride(8, "drop_tower", "Drop Tower", maxLevel = 350),
        ride(9, "roller_coaster", "Roller Coaster", maxLevel = 350),
        ride(10, "splash_zone", "Splash Zone", maxLevel = 375),
        ride(11, "mega_coaster", "Mega Coaster", maxLevel = 375),
        // Bought with gems; earns like the 7th ride, a big lift whenever it is bought.
        ride(6, "golden_coaster", "Golden Coaster", maxLevel = 200, shopOnly = true),
    )

    private const val FIRST_RIDE_COST = 100.0
    private const val RIDE_COST_STEP = 300.0
    private const val RIDE_INCOME_STEP = 120.0

    private fun ride(index: Int, id: String, name: String, maxLevel: Int, shopOnly: Boolean = false) = RideDef(
        id = id,
        name = name,
        baseIncomePerSec = RIDE_INCOME_STEP.pow(index),
        baseCost = FIRST_RIDE_COST * RIDE_COST_STEP.pow(index),
        maxLevel = maxLevel,
        shopOnly = shopOnly,
    )

    val shop: List<ShopItem> = listOf(
        ShopItem("time_warp_1h", "Time Warp 1h", 40, ShopEffect.TimeWarp(3_600), repeatable = true),
        ShopItem("fountain", "Fountain Decor", 60, ShopEffect.Cosmetic),
        ShopItem("mechanic", "Park Mechanic", 120, ShopEffect.NoBreakdowns),
        ShopItem("vip_manager", "VIP Manager", 150, ShopEffect.RideMultiplier("ferris_wheel", 3.0)),
        ShopItem("golden_coaster", "Golden Coaster", 300, ShopEffect.UnlockRide("golden_coaster")),
        ShopItem("double_income", "Double Income Forever", 600, ShopEffect.GlobalMultiplier(2.0)),
    )

    private val ridesById = rides.associateBy { it.id }
    private val shopById = shop.associateBy { it.id }

    fun ride(id: String): RideDef = ridesById[id] ?: throw IllegalArgumentException("Unknown ride: $id")

    fun shopItem(id: String): ShopItem = shopById[id] ?: throw IllegalArgumentException("Unknown shop item: $id")
}
