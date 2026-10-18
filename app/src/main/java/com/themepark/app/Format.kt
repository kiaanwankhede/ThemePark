package com.themepark.app

import com.themepark.core.Numbers
import com.themepark.core.Outcome

/** 950 → "950", 12_400 → "12.4K", 3.2e6 → "3.2M", then T, aa, ab … (see [Numbers]). */
fun formatAmount(value: Double): String = Numbers.format(value)

/** A short line for the player when an action did not go through; null when it did. */
fun Outcome.message(): String? = when (this) {
    is Outcome.Ok -> null
    is Outcome.NotEnoughCoins -> "Need ${formatAmount(needed)} more coins"
    is Outcome.NotEnoughGems -> "Need $needed more gems"
    is Outcome.Refused -> reason
}
