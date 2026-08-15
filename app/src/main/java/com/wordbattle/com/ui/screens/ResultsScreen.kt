package com.wordbattle.com.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.VictoryTrophy
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.components.WinCelebration
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.GoldLight
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import kotlinx.coroutines.delay

@Composable
fun ResultsScreen(
    game: GameState?,
    didWin: Boolean,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit
) {
    val players = game?.players?.sortedWith(compareBy({ it.rank ?: Int.MAX_VALUE }, { -it.score })).orEmpty()

    Box(Modifier.fillMaxSize()) {
        GradientBackground {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.size(12.dp))
                VictoryTrophy()
                Text(
                    stringResource(if (didWin) R.string.results_you_win else R.string.results_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    stringResource(if (didWin) R.string.results_win_subtitle else R.string.results_subtitle),
                    color = Color.White.copy(alpha = .75f)
                )
                Spacer(Modifier.size(18.dp))
                WhiteCard(Modifier.fillMaxWidth()) {
                    players.forEachIndexed { index, player ->
                        StandingRow(player = player, index = index)
                    }
                }
                Spacer(Modifier.size(20.dp))
                GoldButton(stringResource(R.string.results_play_again), onPlayAgain)
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = onHome,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = .16f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Home, null)
                    Spacer(Modifier.size(7.dp))
                    Text(stringResource(R.string.results_home))
                }
                Spacer(Modifier.size(16.dp))
            }
        }
        // Confetti sits on top but never steals a tap: it is drawn in a non-clickable Canvas.
        WinCelebration(visible = didWin)
    }
}

/** One line of the final standings, sliding in with a small stagger for a livelier reveal. */
@Composable
private fun StandingRow(player: Player, index: Int) {
    var shown by remember(player.id) { mutableStateOf(false) }
    LaunchedEffect(player.id) {
        delay(120L * index)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 2 }
    ) {
        val winner = player.rank == 1
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .background(
                    if (winner) Brush.horizontalGradient(listOf(GoldLight.copy(alpha = .40f), Color.Transparent))
                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (player.rank ?: index + 1) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> stringResource(R.string.results_rank, player.rank ?: index + 1)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.size(40.dp)
            )
            Surface(shape = CircleShape, color = Purple.copy(alpha = .12f), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        player.name.firstOrNull()?.uppercase() ?: "W",
                        color = Purple,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    player.name,
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (winner) FontWeight.ExtraBold else FontWeight.SemiBold
                )
                Text(stringResource(R.string.results_points, player.score), color = Muted)
            }
            if (winner) {
                Surface(shape = CircleShape, color = Gold) {
                    Text(
                        stringResource(R.string.results_winner),
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Ink,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
