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

/** A still picture of a ride that gains detail with each tier. No animation, on purpose. */
@Composable
fun RideArt(rideId: String, tier: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawGround(tier)
        when (rideId) {
            "snack_stand" -> drawSnackStand(tier)
            "carousel" -> drawCarousel(tier)
            "bumper_cars" -> drawBumperCars(tier)
            "teacups" -> drawTeacups(tier)
            "ferris_wheel" -> drawFerrisWheel(tier)
            "pirate_ship" -> drawPirateShip(tier)
            "log_flume" -> drawLogFlume(tier)
            "haunted_house" -> drawHauntedHouse(tier)
            "drop_tower" -> drawDropTower(tier)
            "roller_coaster" -> drawCoaster(tier, Palette.ParkGreen, Palette.Red)
            "splash_zone" -> drawSplashZone(tier)
            "mega_coaster" -> drawCoaster(tier, Palette.Purple, Palette.Sky, extraLoop = true)
            "golden_coaster" -> drawCoaster(tier, Palette.GoldDark, Palette.Gold)
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

private fun DrawScope.drawCoaster(tier: Int, track: Color, car: Color, extraLoop: Boolean = false) {
    val trackColor = locked(tier, track)
    val trackPath = Path().apply {
        moveTo(size.width * 0.06f, size.height * 0.80f)
        cubicTo(size.width * 0.22f, size.height * 0.10f, size.width * 0.40f, size.height * 0.12f, size.width * 0.52f, size.height * 0.56f)
        cubicTo(size.width * 0.62f, size.height * 0.88f, size.width * 0.80f, size.height * 0.80f, size.width * 0.94f, size.height * 0.30f)
    }
    drawPath(trackPath, trackColor, style = if (tier == 0) outline(tier) else Stroke(width = u(0.045f), cap = StrokeCap.Round))
    if (tier == 0) return
    // Supports: more as it grows.
    val supports = listOf(0.20f to 0.38f, 0.50f to 0.52f, 0.80f to 0.62f, 0.32f to 0.24f, 0.90f to 0.40f, 0.64f to 0.80f)
    for ((x, top) in supports.take(2 + tier)) {
        drawLine(Palette.Wood, p(x, top), p(x, 0.86f), strokeWidth = u(0.02f))
    }
    // Cars along the first hill: one per tier.
    val carColor = car
    val cars = listOf(0.12f to 0.56f, 0.17f to 0.40f, 0.23f to 0.28f, 0.30f to 0.22f)
    for ((x, y) in cars.take(tier)) {
        drawRoundRect(carColor, topLeft = p(x - 0.04f, y - 0.04f), size = Size(size.width * 0.08f, size.height * 0.07f), cornerRadius = CornerRadius(u(0.02f)))
    }
    if (tier >= 3) {
        drawCircle(trackColor, radius = u(0.11f), center = p(0.70f, 0.50f), style = Stroke(width = u(0.035f)))
    }
    if (extraLoop && tier >= 2) {
        drawCircle(trackColor, radius = u(0.09f), center = p(0.38f, 0.58f), style = Stroke(width = u(0.03f)))
    }
}

private fun DrawScope.drawBumperCars(tier: Int) {
    drawRoundRect(
        locked(tier, Palette.Wood),
        topLeft = p(0.10f, 0.40f),
        size = Size(size.width * 0.80f, size.height * 0.44f),
        cornerRadius = CornerRadius(u(0.06f)),
        style = outline(tier),
    )
    if (tier == 0) return
    // Ceiling grid the cars draw power from.
    drawLine(Palette.InkMuted, p(0.10f, 0.30f), p(0.90f, 0.30f), strokeWidth = u(0.012f))
    drawLine(Palette.InkMuted, p(0.14f, 0.30f), p(0.14f, 0.40f), strokeWidth = u(0.015f))
    drawLine(Palette.InkMuted, p(0.86f, 0.30f), p(0.86f, 0.40f), strokeWidth = u(0.015f))
    val cars = listOf(0.18f to 0.52f, 0.52f to 0.66f, 0.62f to 0.46f, 0.30f to 0.68f, 0.72f to 0.62f)
    val colors = listOf(Palette.Red, Palette.Sky, Palette.Gold, Palette.Purple, Palette.ParkGreen)
    for ((i, xy) in cars.take(tier + 1).withIndex()) {
        val (x, y) = xy
        drawRoundRect(colors[i], topLeft = p(x, y), size = Size(size.width * 0.16f, size.height * 0.10f), cornerRadius = CornerRadius(u(0.03f)))
        drawLine(Palette.InkMuted, p(x + 0.08f, y), p(x + 0.08f, 0.30f), strokeWidth = u(0.008f))
    }
}

private fun DrawScope.drawTeacups(tier: Int) {
    drawOval(locked(tier, Palette.Sky), topLeft = p(0.08f, 0.56f), size = Size(size.width * 0.84f, size.height * 0.28f), style = if (tier == 0) outline(tier) else Fill)
    if (tier == 0) return
    val cups = listOf(0.16f to 0.56f, 0.56f to 0.56f, 0.36f to 0.46f, 0.36f to 0.66f, 0.66f to 0.40f)
    val colors = listOf(Palette.Red, Palette.Gold, Palette.Purple, Palette.Card, Palette.ParkGreen)
    for ((i, xy) in cups.take(tier + 1).withIndex()) {
        val (x, y) = xy
        drawArc(colors[i], startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = p(x, y - 0.10f), size = Size(size.width * 0.22f, size.height * 0.20f))
        drawCircle(colors[i], radius = u(0.03f), center = p(x + 0.24f, y + 0.02f), style = Stroke(width = u(0.015f)))
    }
    if (tier >= 2) drawLine(Palette.GoldDark, p(0.5f, 0.70f), p(0.5f, 0.20f), strokeWidth = u(0.02f))
    if (tier >= 2) drawCircle(Palette.Red, radius = u(0.06f), center = p(0.5f, 0.20f))
}

private fun DrawScope.drawPirateShip(tier: Int) {
    val frame = locked(tier, Palette.Wood)
    drawLine(frame, p(0.22f, 0.86f), p(0.5f, 0.14f), strokeWidth = u(0.035f), pathEffect = if (tier == 0) dashed else null)
    drawLine(frame, p(0.78f, 0.86f), p(0.5f, 0.14f), strokeWidth = u(0.035f), pathEffect = if (tier == 0) dashed else null)
    if (tier == 0) return
    // Swing arm tilted to one side, holding the boat.
    drawLine(Palette.InkMuted, p(0.5f, 0.14f), p(0.40f, 0.58f), strokeWidth = u(0.02f))
    val hull = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.56f)
        lineTo(size.width * 0.70f, size.height * 0.56f)
        lineTo(size.width * 0.60f, size.height * 0.72f)
        lineTo(size.width * 0.22f, size.height * 0.72f)
        close()
    }
    drawPath(hull, Palette.Red)
    if (tier >= 2) {
        drawLine(Palette.Wood, p(0.40f, 0.56f), p(0.40f, 0.32f), strokeWidth = u(0.02f))
        val sail = Path().apply {
            moveTo(size.width * 0.41f, size.height * 0.33f)
            lineTo(size.width * 0.58f, size.height * 0.50f)
            lineTo(size.width * 0.41f, size.height * 0.50f)
            close()
        }
        drawPath(sail, Palette.Card)
    }
    if (tier >= 4) drawRect(Palette.Gold, topLeft = p(0.14f, 0.56f), size = Size(size.width * 0.56f, size.height * 0.03f))
}

private fun DrawScope.drawLogFlume(tier: Int) {
    if (tier >= 3) {
        val hill = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.84f)
            lineTo(size.width * 0.34f, size.height * 0.24f)
            lineTo(size.width * 0.60f, size.height * 0.84f)
            close()
        }
        drawPath(hill, Palette.ParkGreen)
    }
    val channel = Path().apply {
        moveTo(size.width * 0.10f, size.height * 0.30f)
        cubicTo(size.width * 0.40f, size.height * 0.30f, size.width * 0.50f, size.height * 0.80f, size.width * 0.90f, size.height * 0.78f)
    }
    drawPath(channel, locked(tier, Palette.Sky), style = if (tier == 0) outline(tier) else Stroke(width = u(0.07f), cap = StrokeCap.Round))
    if (tier == 0) return
    val logs = listOf(0.18f to 0.27f, 0.44f to 0.52f, 0.66f to 0.72f)
    for ((x, y) in logs.take(tier.coerceAtMost(3))) {
        drawRoundRect(Palette.Wood, topLeft = p(x, y - 0.04f), size = Size(size.width * 0.14f, size.height * 0.07f), cornerRadius = CornerRadius(u(0.03f)))
    }
    if (tier >= 2) {
        drawCircle(Palette.SkyLight, radius = u(0.05f), center = p(0.84f, 0.70f))
        drawCircle(Palette.SkyLight, radius = u(0.035f), center = p(0.92f, 0.66f))
    }
}

private fun DrawScope.drawHauntedHouse(tier: Int) {
    val body = locked(tier, Palette.PurpleDeep)
    drawRect(body, topLeft = p(0.22f, 0.44f), size = Size(size.width * 0.56f, size.height * 0.40f), style = if (tier == 0) outline(tier) else Fill)
    val roof = Path().apply {
        moveTo(size.width * 0.16f, size.height * 0.44f)
        lineTo(size.width * 0.50f, size.height * 0.18f)
        lineTo(size.width * 0.84f, size.height * 0.44f)
        close()
    }
    drawPath(roof, body, style = if (tier == 0) outline(tier) else Fill)
    if (tier == 0) return
    drawRoundRect(Palette.Wood, topLeft = p(0.44f, 0.66f), size = Size(size.width * 0.12f, size.height * 0.18f), cornerRadius = CornerRadius(u(0.02f)))
    // Lit windows: more of the house glows as it grows.
    val windows = listOf(0.28f to 0.52f, 0.62f to 0.52f, 0.28f to 0.68f, 0.62f to 0.68f, 0.45f to 0.32f)
    for ((x, y) in windows.take(tier + 1)) {
        drawRect(Palette.Gold, topLeft = p(x, y), size = Size(size.width * 0.10f, size.height * 0.08f))
    }
    if (tier >= 3) drawRect(body, topLeft = p(0.72f, 0.22f), size = Size(size.width * 0.10f, size.height * 0.24f))
}

private fun DrawScope.drawDropTower(tier: Int) {
    val tower = locked(tier, Palette.InkMuted)
    drawRect(tower, topLeft = p(0.45f, 0.10f), size = Size(size.width * 0.10f, size.height * 0.76f), style = if (tier == 0) outline(tier) else Fill)
    if (tier == 0) return
    drawRect(Palette.Red, topLeft = p(0.40f, 0.06f), size = Size(size.width * 0.20f, size.height * 0.06f))
    // The seat ring sits higher on the tower as it grows.
    val ringY = 0.70f - tier * 0.12f
    drawRoundRect(Palette.Gold, topLeft = p(0.32f, ringY), size = Size(size.width * 0.36f, size.height * 0.08f), cornerRadius = CornerRadius(u(0.03f)))
    if (tier >= 2) {
        drawLine(Palette.InkMuted, p(0.30f, 0.86f), p(0.45f, 0.60f), strokeWidth = u(0.015f))
        drawLine(Palette.InkMuted, p(0.70f, 0.86f), p(0.55f, 0.60f), strokeWidth = u(0.015f))
    }
    if (tier >= 4) for (i in 0 until 6) drawCircle(Palette.Gold, radius = u(0.012f), center = p(0.5f, 0.18f + i * 0.11f))
}

private fun DrawScope.drawSplashZone(tier: Int) {
    drawRoundRect(
        locked(tier, Palette.Sky),
        topLeft = p(0.10f, 0.62f),
        size = Size(size.width * 0.80f, size.height * 0.22f),
        cornerRadius = CornerRadius(u(0.08f)),
        style = if (tier == 0) outline(tier) else Fill,
    )
    if (tier == 0) return
    drawRect(Palette.Wood, topLeft = p(0.12f, 0.20f), size = Size(size.width * 0.12f, size.height * 0.44f))
    val slideColors = listOf(Palette.Red, Palette.Gold, Palette.Purple, Palette.ParkGreen)
    for (i in 0 until tier.coerceAtMost(4)) {
        val slide = Path().apply {
            moveTo(size.width * 0.24f, size.height * (0.22f + i * 0.08f))
            cubicTo(
                size.width * (0.50f + i * 0.08f), size.height * (0.22f + i * 0.08f),
                size.width * (0.40f + i * 0.08f), size.height * 0.60f,
                size.width * (0.62f + i * 0.08f), size.height * 0.64f,
            )
        }
        drawPath(slide, slideColors[i], style = Stroke(width = u(0.035f), cap = StrokeCap.Round))
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
