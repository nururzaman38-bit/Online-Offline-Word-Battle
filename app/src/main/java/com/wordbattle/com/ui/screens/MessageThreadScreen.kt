package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.model.ChatMessage
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple

@Composable
fun MessageThreadScreen(
    friend: UserProfile?,
    messages: List<ChatMessage>,
    currentUid: String?,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text(friend?.displayName ?: "Chat", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        WhiteCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages yet. Say hi!", color = Muted)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages) { msg ->
                        val isMe = msg.senderId == currentUid
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isMe) Purple else Color(0xFFE0E0E0)
                            ) {
                                Text(msg.body, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = if (isMe) Color.White else Ink)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") },
                shape = CircleShape
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            }) {
                Icon(Icons.Default.Send, null, tint = Color.White)
            }
        }
    }
}
