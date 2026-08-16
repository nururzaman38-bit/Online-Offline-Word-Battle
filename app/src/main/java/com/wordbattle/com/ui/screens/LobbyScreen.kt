package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.theme.Gold

@Composable
fun LobbyScreen(
    room: Room?,
    currentUid: String?,
    busy: Boolean,
    onReady: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    val mySlot = room?.slots?.firstOrNull { it.filledByUid == currentUid }
    GradientBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lobby_title), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.lobby_subtitle), color = Color.White.copy(alpha = .7f))
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.action_retry), tint = Color.White) }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                stringResource(R.string.lobby_code, room?.roomCode ?: stringResource(R.string.lobby_code_placeholder)),
                color = Gold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(10.dp))
            room?.slots?.sortedBy { it.slotIndex }?.forEach { slot ->
                SlotCard(slot, isLocal = slot.slotIndex < room.localSlotsCount)
                Spacer(Modifier.size(9.dp))
            }
            Spacer(Modifier.size(8.dp))
            GoldButton(
                stringResource(if (mySlot?.isReady == true) R.string.lobby_ready_done else R.string.lobby_ready_up),
                onClick = { onReady(mySlot?.isReady != true) },
                enabled = !busy && mySlot != null
            )
            Text(
                stringResource(if (mySlot?.isReady == true) R.string.lobby_note_ready else R.string.lobby_note_tap),
                color = Color.White.copy(alpha = .75f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
