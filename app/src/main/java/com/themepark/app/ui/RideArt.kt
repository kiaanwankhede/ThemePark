package com.themepark.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How built-up a ride's picture looks. The picture changes at these levels only, so it stays
 * calm: 0 = not built (grey outline), then 1, 10, 25 and 50.
 */
fun artTier(level: Int): Int = when {
    level <= 0 -> 0
    level < 10 -> 1
    level < 25 -> 2
    level < 50 -> 3
    else -> 4
}

/** Level at which the picture next changes, or null at the top tier. */
fun nextArtLevel(level: Int): Int? = listOf(1, 10, 25, 50).firstOrNull { it > level }

/** A still picture of a ride that gains detail with each tier. No animation, on purpose. */
@Composable
fun RideArt(rideId: String, tier: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawGround(tier)
        when (rideId) {
            "snack_stand" -> drawSnackStand(tier)
            "carousel" -> drawCarousel(tier)
            "ferris_wheel" -> drawFerrisWheel(tier)
            "roller_coaster" -> drawCoaster(tier, gold = false)
            "golden_coaster" -> drawCoaster(tier, gold = true)
        }
        if (tier >= 3) drawBunting(tier)
    }
}

private fun DrawScope.u(f: Float) = size.minDimension * f

private fun DrawScope.p(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun locked(tier: Int, color: Color) = if (tier == 0) Palette.Locked else color

private val dashed = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

private fun DrawScope.outline(tier: Int) = if (tier == 0) Stroke(width = u(0.035f), pathEffect = dashed) else Stroke(width = u(0.04f))

private fun DrawScope.drawGround(tier: Int) {
    val color = if (tier == 0) Palette.Track else Palette.GrassLight
    drawOval(color, topLeft = p(0.06f, 0.80f), size = Size(size.width * 0.88f, size.height * 0.16f))
}

private fun DrawScope.drawSnackStand(tier: Int) {
    val left = 0.24f
    val width = 0.52f
    // Booth.
    drawRoundRect(
        locked(tier, Palette.Gold),
        topLeft = p(left, 0.48f),
        size = Size(size.width * width, size.height * 0.36f),
        cornerRadius = CornerRadius(u(0.04f)),
        style = if (tier == 0) outline(tier) else Fill,
    )
    if (tier == 0) return
    // Awning stripes: more stripes as it grows.
    val stripes = 2 + tier * 2
    val stripeW = width / stripes
    for (i in 0 until stripes) {
        drawRect(
            if (i % 2 == 0) Palette.Red else Palette.Card,
            topLeft = p(left + i * stripeW, 0.36f),
            size = Size(size.width * stripeW, size.height * 0.12f),
        )
    }
    // Counter window.
    drawRoundRect(Palette.Card, topLeft = p(0.34f, 0.56f), size = Size(size.width * 0.32f, size.height * 0.12f), cornerRadius = CornerRadius(u(0.02f)))
    drawCircle(Palette.Red, radius = u(0.035f), center = p(0.42f, 0.62f))
    drawCircle(Palette.Wood, radius = u(0.035f), center = p(0.58f, 0.62f))
    if (tier >= 2) {
        // Sign on top.
        drawRoundRect(Palette.Blue, topLeft = p(0.36f, 0.20f), size = Size(size.width * 0.28f, size.height * 0.12f), cornerRadius = CornerRadius(u(0.03f)))
    }
    if (tier >= 3) {
        // Umbrella table beside it.
        drawLine(Palette.Wood, p(0.86f, 0.56f), p(0.86f, 0.84f), strokeWidth = u(0.025f))
        val umbrella = Path().apply {
            moveTo(size.width * 0.76f, size.height * 0.58f)
            lineTo(size.width * 0.86f, size.height * 0.46f)
            lineTo(size.width * 0.96f, size.height * 0.58f)
            close()
        }
        drawPath(umbrella, Palette.Sky)
    }
    if (tier >= 4) {
        // Gold trim.
        drawRect(Palette.GoldDark, topLeft = p(left, 0.82f), size = Size(size.width * width, size.height * 0.03f))
    }
}

private fun DrawScope.drawCarousel(tier: Int) {
    val roof = Path().apply {
        moveTo(size.width * 0.50f, size.height * 0.10f)
        lineTo(size.width * 0.88f, size.height * 0.36f)
        lineTo(size.width * 0.12f, size.height * 0.36f)
        close()
    }
    if (tier == 0) {
        drawPath(roof, Palette.Locked, style = outline(tier))
        drawLine(Palette.Locked, p(0.5f, 0.36f), p(0.5f, 0.84f), strokeWidth = u(0.03f), pathEffect = dashed)
        return
    }
    drawPath(roof, Palette.Red)
    drawRect(Palette.Gold, topLeft = p(0.14f, 0.36f), size = Size(size.width * 0.72f, size.height * 0.06f))
    drawLine(Palette.Wood, p(0.5f, 0.42f), p(0.5f, 0.86f), strokeWidth = u(0.035f))
    // Horses: one more per tier.
    val horses = tier + 1
    val colors = listOf(Palette.Sky, Palette.Purple, Palette.Card, Palette.Gold, Palette.Red)
    for (i in 0 until horses) {
        val cx = if (horses == 1) 0.30f else 0.22f + i * 0.56f / (horses - 1)
        val y = if (i % 2 == 0) 0.70f else 0.62f
        drawLine(Palette.GoldDark, p(cx, 0.42f), p(cx, y), strokeWidth = u(0.015f))
        drawOval(colors[i % colors.size], topLeft = p(cx - 0.06f, y - 0.03f), size = Size(size.width * 0.12f, size.height * 0.07f))
    }
    if (tier >= 2) {
        // Scalloped canopy edge.
        for (i in 0..6) drawCircle(Palette.Card, radius = u(0.025f), center = p(0.17f + i * 0.11f, 0.42f))
    }
    if (tier >= 4) drawCircle(Palette.Gold, radius = u(0.04f), center = p(0.5f, 0.09f))
}

private fun DrawScope.drawFerrisWheel(tier: Int) {
    val center = p(0.5f, 0.44f)
    val radius = u(0.34f)
    val rimColor = locked(tier, Palette.Red)
    // Legs.
    val legs = Path().apply {
        moveTo(size.width * 0.36f, size.height * 0.88f)
        lineTo(center.x, center.y)
        lineTo(size.width * 0.64f, size.height * 0.88f)
    }
    drawPath(legs, locked(tier, Palette.Wood), style = Stroke(width = u(0.04f), cap = StrokeCap.Round))
    drawCircle(rimColor, radius = radius, center = center, style = outline(tier))
    if (tier == 0) return
    if (tier >= 2) drawCircle(rimColor, radius = radius * 0.62f, center = center, style = Stroke(width = u(0.02f)))
    // Spokes and gondolas: more of each per tier.
    val count = 4 + tier * 2
    val gondolaColors = listOf(Palette.Sky, Palette.Gold, Palette.Purple, Palette.ParkGreen)
    for (i in 0 until count) {
        val angle = (2 * PI * i / count).toFloat()
        val edge = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
        drawLine(rimColor, center, edge, strokeWidth = u(0.015f))
        drawCircle(gondolaColors[i % gondolaColors.size], radius = u(0.045f), center = edge)
    }
    drawCircle(Palette.Gold, radius = u(0.04f), center = center)
    if (tier >= 4) {
        // Ring of lights.
        for (i in 0 until 16) {
            val angle = (2 * PI * i / 16).toFloat()
            drawCircle(Palette.Gold, radius = u(0.012f), center = Offset(center.x + radius * 0.82f * cos(angle), center.y + radius * 0.82f * sin(angle)))
        }
    }
}

private fun DrawScope.drawCoaster(tier: Int, gold: Boolean) {
    val trackColor = locked(tier, if (gold) Palette.GoldDark else Palette.ParkGreen)
    val track = Path().apply {
        moveTo(size.width * 0.06f, size.height * 0.80f)
        cubicTo(size.width * 0.22f, size.height * 0.10f, size.width * 0.40f, size.height * 0.12f, size.width * 0.52f, size.height * 0.56f)
        cubicTo(size.width * 0.62f, size.height * 0.88f, size.width * 0.80f, size.height * 0.80f, size.width * 0.94f, size.height * 0.30f)
    }
    drawPath(track, trackColor, style = if (tier == 0) outline(tier) else Stroke(width = u(0.045f), cap = StrokeCap.Round))
    if (tier == 0) return
    // Supports: more as it grows.
    val supports = listOf(0.20f to 0.38f, 0.50f to 0.52f, 0.80f to 0.62f, 0.32f to 0.24f, 0.90f to 0.40f, 0.64f to 0.80f)
    for ((x, top) in supports.take(2 + tier)) {
        drawLine(Palette.Wood, p(x, top), p(x, 0.86f), strokeWidth = u(0.02f))
    }
    // Cars along the first hill: one per tier.
    val carColor = if (gold) Palette.Gold else Palette.Red
    val cars = listOf(0.12f to 0.56f, 0.17f to 0.40f, 0.23f to 0.28f, 0.30f to 0.22f)
    for ((x, y) in cars.take(tier)) {
        drawRoundRect(carColor, topLeft = p(x - 0.04f, y - 0.04f), size = Size(size.width * 0.08f, size.height * 0.07f), cornerRadius = CornerRadius(u(0.02f)))
    }
    if (tier >= 3) {
        drawCircle(trackColor, radius = u(0.11f), center = p(0.70f, 0.50f), style = Stroke(width = u(0.035f)))
    }
}

private fun DrawScope.drawBunting(tier: Int) {
    val flags = if (tier >= 4) 7 else 5
    val y = 0.06f
    drawLine(Palette.Wood, p(0.05f, y), p(0.95f, y), strokeWidth = u(0.01f))
    val colors = listOf(Palette.Red, Palette.Gold, Palette.Sky, Palette.Purple)
    for (i in 0 until flags) {
        val x = 0.10f + i * (0.80f / (flags - 1))
        val flag = Path().apply {
            moveTo(size.width * (x - 0.04f), size.height * y)
            lineTo(size.width * (x + 0.04f), size.height * y)
            lineTo(size.width * x, size.height * (y + 0.08f))
            close()
        }
        drawPath(flag, colors[i % colors.size])
    }
}
