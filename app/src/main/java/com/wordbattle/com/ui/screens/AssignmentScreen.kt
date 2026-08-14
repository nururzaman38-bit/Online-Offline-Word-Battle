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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Blue
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.Teal

@Composable
fun AssignmentScreen(
    playerCount: Int,
    onlineSlots: Set<Int>,
    busy: Boolean,
    onToggle: (Int) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    GradientBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Column {
                    Text("Who's playing?", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("Choose local or online for each seat", color = Color.White.copy(alpha = .7f))
                }
            }
            repeat(playerCount) { index ->
                val online = index in onlineSlots
                WhiteCard(modifier = Modifier.fillMaxWidth(), selected = online, selectedColor = Blue) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = (if (online) Blue else Purple).copy(alpha = .12f)) {
                            Icon(if (online) Icons.Default.Devices else Icons.Default.Person, null, tint = if (online) Blue else Purple, modifier = Modifier.padding(11.dp).size(24.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (index == 0) "You • Host" else "Player ${index + 1}", color = Ink, style = MaterialTheme.typography.titleMedium)
                            Text(if (index == 0) "Host device" else if (online) "Joins with room code" else "Pass-and-play", color = Muted)
                        }
                        if (index == 0) {
                            Surface(shape = CircleShape, color = Gold) { Text("LOCAL", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Ink, style = MaterialTheme.typography.labelMedium) }
                        } else {
                            Row {
                                AssignmentChip("Local", !online, Teal) { if (online) onToggle(index) }
                                Spacer(Modifier.size(5.dp))
                                AssignmentChip("Online", online, Blue) { if (!online) onToggle(index) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(5.dp))
            GoldButton(
                text = if (onlineSlots.isEmpty()) "START LOCAL GAME" else "CREATE ONLINE ROOM",
                onClick = onContinue,
                enabled = !busy
            )
            Text(
                if (onlineSlots.isEmpty()) "No network needed — pass the device on each turn."
                else "Online seats join live; local seats share the host device.",
                color = Color.White.copy(alpha = .75f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun AssignmentChip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = if (selected) color else Color(0xFFF0EDF3), onClick = onClick) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelMedium)
    }
}
