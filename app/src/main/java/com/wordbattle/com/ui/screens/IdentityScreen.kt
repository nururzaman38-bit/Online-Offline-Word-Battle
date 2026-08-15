package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.data.game.ProfileRules
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.Red

/**
 * First-login (and later "edit profile") screen where the player picks a display name and a
 * globally unique username.
 *
 * Validation mirrors [ProfileRules] exactly, which is also what the database trigger enforces, so
 * the user sees the problem before a round trip. The display name is locked while the 10-day
 * cooldown is still running and the remaining days are shown.
 */
@Composable
fun IdentityScreen(
    profile: UserProfile?,
    busy: Boolean,
    editing: Boolean,
    cooldownDays: Int,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var displayName by remember(profile?.uid) { mutableStateOf(profile?.displayName.orEmpty()) }
    var username by remember(profile?.uid) { mutableStateOf(profile?.username.orEmpty()) }

    val displayNameLocked = cooldownDays > 0 && !profile?.displayName.isNullOrBlank()
    val nameError = ProfileRules.validateDisplayName(displayName)?.messageRes()
    val usernameError = ProfileRules.validateUsername(username)?.messageRes()
    val canSave = !busy && nameError == null && usernameError == null

    GradientBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.size(18.dp))
            Text(stringResource(R.string.identity_title), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.identity_subtitle),
                color = Color.White.copy(alpha = .75f),
                style = MaterialTheme.typography.bodyMedium
            )
            WhiteCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(ProfileRules.MAX_DISPLAY_NAME) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.identity_display_name_label)) },
                    singleLine = true,
                    enabled = !displayNameLocked,
                    isError = nameError != null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple)
                )
                Text(
                    text = stringResource(nameError ?: R.string.identity_display_name_helper),
                    color = if (nameError != null) Red else Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = if (cooldownDays > 0) {
                        stringResource(R.string.identity_cooldown_note, cooldownDays)
                    } else {
                        stringResource(R.string.identity_cooldown_ready)
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.size(14.dp))
                OutlinedTextField(
                    value = username,
                    // Normalizing while typing keeps the field consistent with what is stored.
                    onValueChange = { username = ProfileRules.normalizeUsername(it).take(ProfileRules.MAX_USERNAME) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.identity_username_label)) },
                    prefix = { Text("@", color = Muted) },
                    singleLine = true,
                    isError = usernameError != null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple)
                )
                Text(
                    text = stringResource(usernameError ?: R.string.identity_username_helper),
                    color = if (usernameError != null) Red else Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (!profile?.username.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.username_handle, profile?.username.orEmpty()),
                        color = Ink,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            GoldButton(
                text = stringResource(R.string.identity_save),
                onClick = { onSave(displayName, username) },
                enabled = canSave
            )
            // First login must complete this step, so "Not now" only exists when editing.
            if (editing) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_not_now), color = Color.White)
                }
            }
        }
    }
}

private fun ProfileRules.DisplayNameError.messageRes(): Int = when (this) {
    ProfileRules.DisplayNameError.TOO_SHORT -> R.string.identity_error_name_short
    ProfileRules.DisplayNameError.TOO_LONG -> R.string.identity_error_name_long
}

private fun ProfileRules.UsernameError.messageRes(): Int = when (this) {
    ProfileRules.UsernameError.TOO_SHORT -> R.string.identity_error_username_short
    ProfileRules.UsernameError.TOO_LONG -> R.string.identity_error_username_long
    ProfileRules.UsernameError.INVALID_CHARACTERS -> R.string.identity_error_username_chars
}
