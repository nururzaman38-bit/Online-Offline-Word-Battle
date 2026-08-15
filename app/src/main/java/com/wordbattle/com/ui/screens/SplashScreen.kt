package com.wordbattle.com.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.LetterTile
import com.wordbattle.com.ui.theme.Gold

@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash")
    val glow by transition.animateFloat(
        initialValue = .92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "glow"
    )
    GradientBackground {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(glow)) {
                LetterTile('W', size = 86.dp, rotationSeed = 0)
            }
            Spacer(Modifier.height(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                stringResource(R.string.brand_word).forEachIndexed { index, char ->
                    LetterTile(char, size = 43.dp, rotationSeed = index)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                stringResource(R.string.brand_battle).forEachIndexed { index, char ->
                    LetterTile(char, size = 43.dp, rotationSeed = index + 9)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.brand_tagline), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(42.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    val dotAlpha by transition.animateFloat(
                        initialValue = .25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600, delayMillis = index * 180), RepeatMode.Reverse),
                        label = "dot-$index"
                    )
                    Text("●", color = Gold, modifier = Modifier.alpha(dotAlpha))
                }
            }
        }
    }
}
