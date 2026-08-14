package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.theme.Gold

@Composable
fun LobbyScreen(room: Room?, currentUid: String?, busy: Boolean, onReady: (Boolean) -> Unit, onBack: () -> Unit) {
    val mySlot = room?.slots?.firstOrNull { it.filledByUid == currentUid }
    GradientBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Column {
                    Text("Room Lobby", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("Host will start when everyone is ready", color = Color.White.copy(alpha = .7f))
                }
            }
            Spacer(Modifier.size(14.dp))
            Text("Code  ${room?.roomCode ?: "——"}", color = Gold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(10.dp))
            room?.slots?.sortedBy { it.slotIndex }?.forEach { slot ->
                SlotCard(slot, isLocal = slot.slotIndex < room.localSlotsCount)
                Spacer(Modifier.size(9.dp))
            }
            Spacer(Modifier.size(8.dp))
            GoldButton(
                if (mySlot?.isReady == true) "I'M READY ✓" else "READY UP",
                onClick = { onReady(mySlot?.isReady != true) },
                enabled = !busy && mySlot != null
            )
            Text(
                if (mySlot?.isReady == true) "Ready! Waiting for the host…" else "Tap when you're ready to battle.",
                color = Color.White.copy(alpha = .75f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
