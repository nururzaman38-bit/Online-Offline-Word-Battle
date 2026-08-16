package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.PlayerAvatar
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    offline: Boolean,
    onEditIdentity: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 105.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlayerAvatar(profile, size = 88.dp, borderWidth = 4.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            profile?.displayName ?: stringResource(R.string.profile_default_name),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
        profile?.username?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.username_handle, it), color = Color.White.copy(alpha = .75f))
        }
        Text(
            if (offline) stringResource(R.string.profile_offline_guest)
            else stringResource(R.string.profile_level_warrior, profile?.level ?: 1),
            color = Gold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.size(14.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(stringResource(R.string.profile_stat_games), profile?.gamesPlayed ?: 0)
                Stat(stringResource(R.string.profile_stat_wins), profile?.wins ?: 0)
                Stat(
                    stringResource(R.string.profile_stat_win_rate),
                    if ((profile?.gamesPlayed ?: 0) == 0) 0 else (profile!!.wins * 100 / profile.gamesPlayed),
                    "%"
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth()) {
            Text("Campaign Progress", color = Ink, style = MaterialTheme.typography.titleLarge)
            Text(
                "Campaign: Level ${profile?.campaignLevel ?: 1}/500 • ⭐ ${profile?.campaignStarsTotal ?: 0}",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Lives: ${profile?.livesCurrent ?: 3}/${profile?.livesMax ?: 3} • Coins: ${profile?.coins ?: 0}",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.size(14.dp))
        GoldButton(
            stringResource(R.string.setting_identity),
            onClick = onEditIdentity,
            enabled = !offline,
            leadingIcon = Icons.Default.Edit
        )
    }
}

@Composable
private fun Stat(label: String, value: Int, suffix: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value$suffix", color = Purple, style = MaterialTheme.typography.headlineMedium)
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
