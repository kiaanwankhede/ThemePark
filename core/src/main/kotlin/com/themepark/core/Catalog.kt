package com.themepark.core

/** A ride (or stall) type. Numbers here are first-pass tuning, expected to change. */
data class RideDef(
    val id: String,
    val name: String,
    /** Coins per second at level 1, before milestones and multipliers. */
    val baseIncomePerSec: Double,
    /** Cost of going from level 0 (not built) to level 1. */
    val baseCost: Double,
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
    val rides: List<RideDef> = listOf(
        RideDef("snack_stand", "Snack Stand", baseIncomePerSec = 1.0, baseCost = 15.0),
        RideDef("carousel", "Carousel", baseIncomePerSec = 4.0, baseCost = 150.0),
        RideDef("ferris_wheel", "Ferris Wheel", baseIncomePerSec = 12.0, baseCost = 1_200.0),
        RideDef("roller_coaster", "Roller Coaster", baseIncomePerSec = 60.0, baseCost = 25_000.0),
        RideDef("golden_coaster", "Golden Coaster", baseIncomePerSec = 500.0, baseCost = 0.0, shopOnly = true),
    )

    val shop: List<ShopItem> = listOf(
        ShopItem("time_warp_1h", "Time Warp 1h", 40, ShopEffect.TimeWarp(3_600), repeatable = true),
        ShopItem("fountain", "Fountain Decor", 60, ShopEffect.Cosmetic),
        ShopItem("vip_manager", "VIP Manager", 150, ShopEffect.RideMultiplier("ferris_wheel", 3.0)),
        ShopItem("golden_coaster", "Golden Coaster", 300, ShopEffect.UnlockRide("golden_coaster")),
        ShopItem("double_income", "Double Income Forever", 600, ShopEffect.GlobalMultiplier(2.0)),
    )

    private val ridesById = rides.associateBy { it.id }
    private val shopById = shop.associateBy { it.id }

    fun ride(id: String): RideDef = ridesById[id] ?: throw IllegalArgumentException("Unknown ride: $id")

    fun shopItem(id: String): ShopItem = shopById[id] ?: throw IllegalArgumentException("Unknown shop item: $id")
}
