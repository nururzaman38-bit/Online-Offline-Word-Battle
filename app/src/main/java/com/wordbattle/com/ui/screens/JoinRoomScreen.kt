package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wordbattle.com.ui.components.GoldButton
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple

@Composable
fun JoinRoomScreen(busy: Boolean, onJoin: (String, String) -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    GradientBackground {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text("Join a Room", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.weight(.35f))
            WhiteCard(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                Icon(Icons.Default.MeetingRoom, null, tint = Purple, modifier = Modifier.align(Alignment.CenterHorizontally).size(44.dp))
                Text("Enter battle details", style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Ask the host for both codes.", color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.size(18.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                    label = { Text("6-character Room Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple)
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { value -> passcode = value.filter(Char::isDigit).take(4) },
                    label = { Text("4-digit Passcode") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple)
                )
                Spacer(Modifier.size(18.dp))
                GoldButton("JOIN BATTLE", { onJoin(code, passcode) }, enabled = !busy && code.length == 6 && passcode.length == 4)
            }
            Spacer(Modifier.weight(.65f))
        }
    }
}
