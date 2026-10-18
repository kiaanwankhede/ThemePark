package com.themepark.app

import com.themepark.core.Outcome
import java.util.Locale

/** 950 → "950", 12_400 → "12.4K", 3_200_000 → "3.2M". */
fun formatAmount(value: Double): String {
    val units = listOf(1e12 to "T", 1e9 to "B", 1e6 to "M", 1e3 to "K")
    for ((size, suffix) in units) {
        if (value >= size) return String.format(Locale.US, "%.1f%s", value / size, suffix)
    }
    return value.toLong().toString()
}

/** A short line for the player when an action did not go through; null when it did. */
fun Outcome.message(): String? = when (this) {
    is Outcome.Ok -> null
    is Outcome.NotEnoughCoins -> "Need ${formatAmount(needed)} more coins"
    is Outcome.NotEnoughGems -> "Need $needed more gems"
    is Outcome.Refused -> reason
}
