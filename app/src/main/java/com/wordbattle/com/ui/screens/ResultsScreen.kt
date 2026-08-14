package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple

@Composable
fun ResultsScreen(game: GameState?, onPlayAgain: () -> Unit, onHome: () -> Unit) {
    val players = game?.players?.sortedWith(compareBy({ it.rank ?: Int.MAX_VALUE }, { -it.score })).orEmpty()
    GradientBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.size(20.dp))
            Text("🏆", style = MaterialTheme.typography.displayLarge)
            Text("BATTLE COMPLETE!", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text("What a word showdown", color = Color.White.copy(alpha = .7f))
            Spacer(Modifier.size(18.dp))
            WhiteCard(Modifier.fillMaxWidth()) {
                players.forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(when (player.rank ?: index + 1) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${player.rank ?: index + 1}" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.size(44.dp))
                        Surface(shape = CircleShape, color = Purple.copy(alpha = .12f), modifier = Modifier.size(42.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(player.name.firstOrNull()?.uppercase() ?: "W", color = Purple, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(player.name, color = Ink, style = MaterialTheme.typography.titleMedium)
                            Text("${player.score} points", color = Muted)
                        }
                        if (player.rank == 1) Surface(shape = CircleShape, color = Gold) { Text("WINNER", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Ink, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Spacer(Modifier.size(20.dp))
            GoldButton("PLAY AGAIN", onPlayAgain)
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .16f), contentColor = Color.White)
            ) {
                Icon(Icons.Default.Home, null)
                Spacer(Modifier.size(7.dp))
                Text("Back to Home")
            }
        }
    }
}
