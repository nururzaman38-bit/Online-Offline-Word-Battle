package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.components.EmptyState
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple

@Composable
fun RankScreen(weekly: Boolean, players: List<UserProfile>, onToggle: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
        Text(stringResource(R.string.rank_title), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.rank_subtitle), color = Color.White.copy(alpha = .7f))
        Spacer(Modifier.size(12.dp))
        Surface(shape = CircleShape, color = Color.White.copy(alpha = .14f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(4.dp)) {
                RankToggle(stringResource(R.string.rank_weekly), weekly, Modifier.weight(1f)) { onToggle(true) }
                RankToggle(stringResource(R.string.rank_all_time), !weekly, Modifier.weight(1f)) { onToggle(false) }
            }
        }
        Spacer(Modifier.size(12.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (players.isEmpty()) {
                EmptyState(
                    Icons.Default.EmojiEvents,
                    stringResource(R.string.rank_empty_title),
                    stringResource(R.string.rank_empty_body)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(players, key = { _, it -> it.uid }) { index, player ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}" },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.size(38.dp)
                            )
                            Surface(shape = CircleShape, color = Purple.copy(alpha = .12f), modifier = Modifier.size(38.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(player.displayName.firstOrNull()?.uppercase() ?: "W", color = Purple, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(player.displayName, color = Ink, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.rank_level_wins, player.level, player.wins), color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(if (weekly) player.weeklyScore.toString() else player.wins.toString(), color = Purple, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankToggle(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier, shape = CircleShape, color = if (selected) Gold else Color.Transparent, onClick = onClick) {
        Text(label, Modifier.padding(vertical = 9.dp), color = if (selected) Ink else Color.White, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
