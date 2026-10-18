package com.themepark.core

import kotlin.math.floor

/**
 * Each UTC day has one weather, worked out from the day number alone, so the game, the widget
 * and every device agree on it without storing anything. It changes which rides earn most.
 */
enum class Weather(val label: String, val effect: String, private val factors: Map<String, Double>) {
    SUNNY("Sunny", "A normal busy day", emptyMap()),
    HOT(
        "Hot",
        "Snacks and water rides earn double",
        mapOf("snack_stand" to 2.0, "log_flume" to 2.0, "splash_zone" to 2.0),
    ),
    RAINY(
        "Rainy",
        "Coasters and Drop Tower closed, indoor rides busy",
        mapOf(
            "roller_coaster" to 0.0, "mega_coaster" to 0.0, "golden_coaster" to 0.0, "drop_tower" to 0.0,
            "carousel" to 1.5, "haunted_house" to 1.5, "ferris_wheel" to 0.5,
        ),
    ),
    WINDY(
        "Windy",
        "Ferris Wheel and Drop Tower closed",
        mapOf("ferris_wheel" to 0.0, "drop_tower" to 0.0, "pirate_ship" to 0.5),
    ),
    ;

    /** Income multiplier for a ride on a day with this weather; 0 means closed. */
    fun factor(rideId: String): Double = factors[rideId] ?: 1.0

    companion object {
        fun on(day: Long): Weather {
            val roll = (mix(day) ushr 1) % 100
            return when {
                roll < 40 -> SUNNY
                roll < 60 -> HOT
                roll < 85 -> RAINY
                else -> WINDY
            }
        }

        fun at(time: Long): Weather = on(dayOf(time))

        fun dayOf(time: Long): Long = floor(time.toDouble() / Rules.DAY_MS).toLong()
    }
}

/** SplitMix64: a fixed, well-spread hash, so "random" game events are the same everywhere. */
internal fun mix(seed: Long): Long {
    var z = seed + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}
