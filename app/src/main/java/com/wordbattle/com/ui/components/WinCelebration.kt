package com.wordbattle.com.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.wordbattle.com.ui.theme.Blue
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.GoldLight
import com.wordbattle.com.ui.theme.PurpleLight
import com.wordbattle.com.ui.theme.Teal
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Confetto(
    val x: Float,
    val delay: Float,
    val duration: Float,
    val swayAmplitude: Float,
    val swayPhase: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val round: Boolean
)

/**
 * Full-screen victory celebration: falling confetti, radiating light rays and a trophy that pops
 * in with a spring. Purely decorative and non-interactive, so it is always drawn behind the result
 * content and never intercepts touches.
 */
@Composable
fun WinCelebration(
    visible: Boolean,
    modifier: Modifier = Modifier,
    pieceCount: Int = 90
) {
    if (!visible) return

    val palette = listOf(Gold, GoldLight, Teal, Blue, PurpleLight, Color.White)
    val pieces = remember(pieceCount) {
        val random = Random(20260815)
        List(pieceCount) {
            Confetto(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.6f,
                duration = 2.2f + random.nextFloat() * 2.6f,
                swayAmplitude = 12f + random.nextFloat() * 46f,
                swayPhase = random.nextFloat() * (2f * PI.toFloat()),
                spin = (if (random.nextBoolean()) 1f else -1f) * (180f + random.nextFloat() * 900f),
                width = 7f + random.nextFloat() * 9f,
                height = 10f + random.nextFloat() * 16f,
                color = palette[random.nextInt(palette.size)],
                round = random.nextInt(4) == 0
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "confetti-progress"
    )
    val rayRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rays"
    )
    val glowPulse by transition.animateFloat(
        initialValue = .55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Soft rotating light rays behind everything.
            rotate(rayRotation, pivot = Offset(size.width / 2f, size.height * 0.30f)) {
                val rays = 12
                repeat(rays) { index ->
                    rotate(
                        degrees = index * (360f / rays),
                        pivot = Offset(size.width / 2f, size.height * 0.30f)
                    ) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = .10f * glowPulse), Color.Transparent),
                                startY = size.height * 0.30f,
                                endY = size.height * 0.30f + size.maxDimension
                            ),
                            topLeft = Offset(size.width / 2f - 26f, size.height * 0.30f),
                            size = Size(52f, size.maxDimension)
                        )
                    }
                }
            }

            pieces.forEach { piece ->
                val local = ((progress * 4.2f / piece.duration) + piece.delay) % 1f
                val y = -60f + local * (size.height + 140f)
                val sway = sin(local * 6.6f + piece.swayPhase) * piece.swayAmplitude
                val x = piece.x * size.width + sway
                val fade = when {
                    local < 0.06f -> local / 0.06f
                    local > 0.86f -> (1f - local) / 0.14f
                    else -> 1f
                }
                rotate(degrees = piece.spin * local, pivot = Offset(x, y)) {
                    if (piece.round) {
                        drawCircle(
                            color = piece.color.copy(alpha = .92f * fade),
                            radius = piece.width / 1.6f,
                            center = Offset(x, y)
                        )
                    } else {
                        drawRect(
                            color = piece.color.copy(alpha = .92f * fade),
                            topLeft = Offset(x - piece.width / 2f, y - piece.height / 2f),
                            size = Size(piece.width, piece.height)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The trophy that bursts in above the standings: scales up with an overshoot, then keeps a slow
 * breathing motion and a gentle tilt.
 */
@Composable
fun VictoryTrophy(modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, tween(620, easing = EaseOutBack))
    }
    val transition = rememberInfiniteTransition(label = "trophy")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "trophy-breathe"
    )
    val tilt by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "trophy-tilt"
    )
    val glow by transition.animateFloat(
        initialValue = .25f,
        targetValue = .6f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "trophy-glow"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Gold.copy(alpha = glow), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f
            )
        }
        Text(
            "🏆",
            modifier = Modifier
                .scale(pop.value * breathe)
                .rotate(tilt * pop.value)
                .alpha(pop.value.coerceIn(0f, 1f)),
            style = MaterialTheme.typography.displayLarge
        )
    }
}
