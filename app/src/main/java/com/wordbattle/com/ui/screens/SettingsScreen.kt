package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.AppLanguage
import com.wordbattle.com.ui.components.SettingRow
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Red
import com.wordbattle.com.ui.theme.Teal

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    offline: Boolean,
    sound: Boolean,
    notifications: Boolean,
    language: AppLanguage,
    onSound: () -> Unit,
    onNotifications: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 105.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.profile_settings), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(12.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth()) {
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
