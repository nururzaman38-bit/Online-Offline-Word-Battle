package com.wordbattle.com.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.ui.theme.Gold
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Purple

/**
 * Blocking "you need internet for this" dialog.
 *
 * It offers a retry (which re-checks connectivity and replays the pending action) and a shortcut to
 * the system network settings, and it makes clear that offline modes are still playable.
 */
@Composable
fun OfflineDialog(onRetry: () -> Unit, onNetworkSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WifiOff, null, tint = Purple) },
        title = { Text(stringResource(R.string.offline_dialog_title)) },
        text = { Text(stringResource(R.string.offline_dialog_body)) },
        confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onNetworkSettings) { Text(stringResource(R.string.action_network_settings)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

/**
 * Thin status strip shown above the current screen while the device is offline or while an online
 * room/game is being re-subscribed after a drop.
 */
@Composable
fun ConnectionBanner(reconnecting: Boolean, offline: Boolean, modifier: Modifier = Modifier) {
    if (!reconnecting && !offline) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (reconnecting) Gold else Color(0xFF3A2B4D)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                if (reconnecting) Icons.Default.CloudOff else Icons.Default.WifiOff,
                null,
                tint = if (reconnecting) Ink else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                stringResource(if (reconnecting) R.string.banner_reconnecting else R.string.banner_offline),
                color = if (reconnecting) Ink else Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
