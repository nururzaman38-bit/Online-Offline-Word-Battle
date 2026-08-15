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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.AppLanguage
import com.wordbattle.com.ui.components.PlayerAvatar
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.Red
import com.wordbattle.com.ui.theme.Teal

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    offline: Boolean,
    sound: Boolean,
    notifications: Boolean,
    language: AppLanguage,
    onSound: () -> Unit,
    onNotifications: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onEditIdentity: () -> Unit,
    onLogout: () -> Unit
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
            Text(stringResource(R.string.profile_settings), color = Ink, style = MaterialTheme.typography.titleLarge)
            SettingRow(
                Icons.Default.AlternateEmail,
                stringResource(R.string.setting_identity),
                profile?.username?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.username_handle, it) }
                    ?: stringResource(R.string.setting_identity_subtitle_none)
            ) {
                TextButton(onClick = onEditIdentity, enabled = !offline) { Text(stringResource(R.string.action_change)) }
            }
            SettingRow(Icons.Default.VolumeUp, stringResource(R.string.setting_sound), stringResource(R.string.setting_sound_subtitle)) {
                Switch(checked = sound, onCheckedChange = { onSound() }, colors = SwitchDefaults.colors(checkedTrackColor = Teal))
            }
            SettingRow(
                Icons.Default.Notifications,
                stringResource(R.string.setting_notifications),
                stringResource(R.string.setting_notifications_subtitle)
            ) {
                Switch(checked = notifications, onCheckedChange = { onNotifications() }, colors = SwitchDefaults.colors(checkedTrackColor = Teal))
            }
            SettingRow(
                Icons.Default.Language,
                stringResource(R.string.setting_language),
                stringResource(language.labelRes())
            ) {
                // Instant switch: the ViewModel hands the tag to AppCompatDelegate.
                TextButton(
                    onClick = {
                        onLanguage(if (language == AppLanguage.ENGLISH) AppLanguage.BANGLA else AppLanguage.ENGLISH)
                    }
                ) { Text(stringResource(R.string.action_change)) }
            }
            SettingRow(
                Icons.Default.Logout,
                stringResource(if (offline) R.string.setting_exit_guest else R.string.setting_logout),
                stringResource(R.string.setting_logout_subtitle),
                tint = Red
            ) {
                TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout), color = Red) }
            }
        }
    }
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.BANGLA -> R.string.language_bangla
}

@Composable
private fun Stat(label: String, value: Int, suffix: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value$suffix", color = Purple, style = MaterialTheme.typography.headlineMedium)
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, tint: Color = Purple, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = tint.copy(alpha = .11f)) {
            Icon(icon, null, tint = tint, modifier = Modifier.padding(9.dp).size(21.dp))
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        action()
    }
}
