package com.wordbattle.com.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.model.UserProfile
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
    language: String,
    onSound: () -> Unit,
    onNotifications: () -> Unit,
    onLanguage: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 105.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(modifier = Modifier.size(88.dp), shape = CircleShape, color = Purple, border = BorderStroke(4.dp, Gold), shadowElevation = 10.dp) {
            Box(contentAlignment = Alignment.Center) {
                Text(profile?.displayName?.firstOrNull()?.uppercase() ?: "W", color = Color.White, style = MaterialTheme.typography.displayLarge)
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(profile?.displayName ?: "Word Player", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(if (offline) "Offline Guest" else "Level ${profile?.level ?: 1} Word Warrior", color = Gold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(14.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Games", profile?.gamesPlayed ?: 0)
                Stat("Wins", profile?.wins ?: 0)
                Stat("Win rate", if ((profile?.gamesPlayed ?: 0) == 0) 0 else (profile!!.wins * 100 / profile.gamesPlayed), "%")
            }
        }
        Spacer(Modifier.size(12.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth()) {
            Text("Settings", color = Ink, style = MaterialTheme.typography.titleLarge)
            SettingRow(Icons.Default.VolumeUp, "Sound", "Game effects and feedback") {
                Switch(checked = sound, onCheckedChange = { onSound() }, colors = SwitchDefaults.colors(checkedTrackColor = Teal))
            }
            SettingRow(Icons.Default.Notifications, "Notifications", "Invites and match updates") {
                Switch(checked = notifications, onCheckedChange = { onNotifications() }, colors = SwitchDefaults.colors(checkedTrackColor = Teal))
            }
            SettingRow(Icons.Default.Language, "Language", language) {
                TextButton(onClick = { onLanguage(if (language == "English") "বাংলা" else "English") }) { Text("Change") }
            }
            SettingRow(Icons.Default.Logout, if (offline) "Exit guest mode" else "Logout", "See you next battle", tint = Red) {
                TextButton(onClick = onLogout) { Text("Logout", color = Red) }
            }
        }
    }
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
