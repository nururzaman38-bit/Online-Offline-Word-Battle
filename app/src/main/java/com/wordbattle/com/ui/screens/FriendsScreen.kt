package com.wordbattle.com.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.components.EmptyState
import com.wordbattle.com.ui.components.PlayerAvatar
import com.wordbattle.com.ui.components.WhiteCard
import com.wordbattle.com.ui.theme.Blue
import com.wordbattle.com.ui.theme.Ink
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.Purple
import com.wordbattle.com.ui.theme.Teal

@Composable
fun FriendsScreen(
    friends: List<FriendProfile>,
    searchResults: List<UserProfile>,
    offline: Boolean,
    onSearch: (String) -> Unit,
    onAdd: (UserProfile) -> Unit,
    onInvite: (FriendProfile) -> Unit,
    onAccept: (FriendProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
        Text("Friends", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Find players and start a battle", color = Color.White.copy(alpha = .7f))
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (offline) "Sign in to search" else "Search players…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            enabled = !offline,
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = .7f),
                focusedBorderColor = Teal,
                unfocusedBorderColor = Color.Transparent
            )
        )
        Spacer(Modifier.size(12.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                query.length >= 2 && searchResults.isNotEmpty() -> {
                    Text("Players", color = Ink, style = MaterialTheme.typography.titleLarge)
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(searchResults, key = { it.uid }) { profile ->
                            PersonRow(profile, status = "Tap to connect") {
                                Button(onClick = { onAdd(profile) }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Add") }
                            }
                        }
                    }
                }
                friends.isEmpty() -> EmptyState(Icons.Default.GroupAdd, "Build your squad", if (offline) "Sign in to add and invite friends." else "Search by player name to send a request.")
                else -> {
                    Text("Your friends", color = Ink, style = MaterialTheme.typography.titleLarge)
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(friends, key = { it.profile.uid }) { friend ->
                            PersonRow(
                                friend.profile,
                                status = when {
                                    friend.status == "pending" -> "Wants to be your friend"
                                    friend.status == "pending_outgoing" -> "Friend request sent"
                                    friend.isOnline -> "Online"
                                    else -> "Offline"
                                },
                                online = friend.isOnline
                            ) {
                                when (friend.status) {
                                    "pending" -> Button(onClick = { onAccept(friend) }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Accept") }
                                    "pending_outgoing" -> Text("Sent", color = Muted, style = MaterialTheme.typography.labelLarge)
                                    else -> Button(onClick = { onInvite(friend) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Invite") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    profile: UserProfile,
    status: String,
    online: Boolean = false,
    action: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            PlayerAvatar(profile, size = 44.dp, borderWidth = 2.dp)
            Box(Modifier.align(Alignment.BottomEnd).size(11.dp).background(if (online) Teal else Muted, CircleShape))
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.displayName, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(status, color = if (online) Teal else Muted, style = MaterialTheme.typography.bodyMedium)
        }
        action()
    }
}
