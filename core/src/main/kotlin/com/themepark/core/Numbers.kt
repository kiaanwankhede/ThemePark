package com.themepark.core

import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10

/**
 * Idle-game number names: 950, 12.4K, 3.2M, 7.1B, 4.0T, then aa, ab … az, ba … for every
 * further thousand-fold step (1aa = 10^15).
 */
object Numbers {
    private val named = listOf("", "K", "M", "B", "T")

    fun format(value: Double): String {
        if (value < 1_000) return floor(value).toLong().toString()
        val thousands = floor(log10(value) / 3).toInt()
        val scaled = value / Math.pow(1_000.0, thousands.toDouble())
        // Rounding can show 1000.0 for values just under the next step; never print more than 999.9.
        val shown = minOf(floor(scaled * 10) / 10, 999.9)
        return String.format(Locale.US, "%.1f%s", shown, suffix(thousands))
    }

    fun suffix(thousands: Int): String {
        if (thousands < named.size) return named[thousands]
        val n = thousands - named.size
        return "${'a' + n / 26}${'a' + n % 26}"
    }
}
