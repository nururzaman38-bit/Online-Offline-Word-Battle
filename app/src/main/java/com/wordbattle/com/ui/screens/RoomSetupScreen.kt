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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.RoomSlot
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.LetterTile
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.Teal

@Composable
fun RoomSetupScreen(room: Room?, busy: Boolean, onStart: () -> Unit, onShare: (Room) -> Unit, onBack: () -> Unit) {
    GradientBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.room_title), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.room_subtitle), color = Color.White.copy(alpha = .7f))
                }
                if (room != null) IconButton(onClick = { onShare(room) }) { Icon(Icons.Default.Share, stringResource(R.string.action_share_room), tint = Gold) }
            }
            if (room == null) {
                WhiteCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.room_creating), color = Ink) }
                return@Column
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.room_code_label), color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.size(8.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    room.roomCode.forEachIndexed { i, c -> LetterTile(c, size = 39.dp, rotationSeed = i) }
                }
                Spacer(Modifier.size(13.dp))
                Surface(shape = CircleShape, color = Purple.copy(alpha = .09f), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.room_passcode, room.passcode), Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Purple, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                stringResource(R.string.room_players, room.slots.count { it.filledByName != null }, room.totalSlots),
                color = Color.White, style = MaterialTheme.typography.titleLarge)
            room.slots.sortedBy { it.slotIndex }.forEach { slot -> SlotCard(slot, isLocal = slot.slotIndex < room.localSlotsCount) }
            val canStart = room.slots.size == room.totalSlots && room.slots.all { it.filledByName != null && it.isReady }
            GoldButton(stringResource(R.string.room_start_battle), onStart, enabled = canStart && !busy)
            if (!canStart) Text(stringResource(R.string.room_waiting_note), color = Color.White.copy(alpha = .72f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun SlotCard(slot: RoomSlot, isLocal: Boolean) {
    WhiteCard(Modifier.fillMaxWidth(), selected = slot.isReady, selectedColor = Teal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Purple.copy(alpha = .11f), modifier = Modifier.size(42.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(slot.filledByName?.firstOrNull()?.uppercase() ?: "?", color = Purple, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(slot.filledByName ?: stringResource(R.string.slot_waiting), color = Ink, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isLocal) stringResource(R.string.slot_local) else stringResource(R.string.slot_online_seat, slot.slotIndex + 1),
                    color = Muted
                )
            }
            Surface(shape = CircleShape, color = when { slot.isReady -> Teal; slot.filledByName == null -> Color(0xFFF0EDF3); else -> Gold }) {
                Text(
                    stringResource(
                        when {
                            slot.isReady -> R.string.slot_ready
                            slot.filledByName == null -> R.string.slot_open
                            else -> R.string.slot_not_ready
                        }
                    ),
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (slot.isReady) Color.White else if (slot.filledByName == null) Muted else Ink,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
