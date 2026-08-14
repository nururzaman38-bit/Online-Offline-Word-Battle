package com.wordbattle.com.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Blue
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.PurpleLight
import com.wordbattle.com.ui.theme.Teal

private data class BattleMode(val players: Int, val title: String, val subtitle: String, val icon: ImageVector, val color: Color)

@Composable
fun HomeScreen(
    selectedPlayers: Int,
    onSelectMode: (Int) -> Unit,
    onPlay: () -> Unit,
    onJoinRoom: () -> Unit,
    onCreateRoom: () -> Unit
) {
    val modes = listOf(
        BattleMode(1, "Computer", "Offline solo", Icons.Default.SmartToy, Teal),
        BattleMode(2, "2 Players", "Head to head", Icons.Default.Person, Blue),
        BattleMode(3, "3 Players", "Triple threat", Icons.Default.Groups, PurpleLight),
        BattleMode(4, "4 Players", "Party battle", Icons.Default.Groups, Gold)
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.Transparent, shadowElevation = 10.dp) {
                Box(
                    Modifier.fillMaxWidth().height(142.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF241242), Purple))),
                ) {
                    Column(Modifier.padding(20.dp).align(Alignment.CenterStart)) {
                        Surface(shape = CircleShape, color = Gold) {
                            Text("WEEKLY QUEST", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Ink, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Spell. Score. Conquer!", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Text("Earn double coins in your next battle", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("🏆", modifier = Modifier.align(Alignment.CenterEnd).padding(20.dp), style = MaterialTheme.typography.displayLarge)
                }
            }
        }
        item { Text("Choose your battle", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        items(modes.chunked(2)) { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowModes.forEach { mode ->
                    WhiteCard(
                        modifier = Modifier.weight(1f).aspectRatio(1.15f),
                        selected = selectedPlayers == mode.players,
                        selectedColor = mode.color,
                        onClick = { onSelectMode(mode.players) }
                    ) {
                        Surface(shape = CircleShape, color = mode.color.copy(alpha = .14f)) {
                            Icon(mode.icon, null, tint = mode.color, modifier = Modifier.padding(10.dp).size(25.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(mode.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(mode.subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                }
            }
        }
        item { GoldButton("PLAY NOW", onClick = onPlay) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Join Room", Icons.Default.VpnKey, Blue, Modifier.weight(1f), onJoinRoom)
                QuickAction("Create Room", Icons.Default.AddCircle, Teal, Modifier.weight(1f), onCreateRoom)
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    WhiteCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = color.copy(alpha = .13f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(title, color = Ink, style = MaterialTheme.typography.labelLarge)
        }
    }
}
