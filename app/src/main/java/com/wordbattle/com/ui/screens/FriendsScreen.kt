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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.wordbattle.com.data.model.ChatMessage
import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.GameRequest
import com.wordbattle.com.data.model.RequestType
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.ui.FriendsTab
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
    onAccept: (FriendProfile) -> Unit,
    // New campaign features
    friendsTab: FriendsTab = FriendsTab.FRIENDS,
    onTabChange: (FriendsTab) -> Unit = {},
    requests: List<GameRequest> = emptyList(),
    messages: List<ChatMessage> = emptyList(),
    onRequestAccept: (GameRequest) -> Unit = {},
    onRequestDecline: (GameRequest) -> Unit = {},
    onSendLife: (GameRequest) -> Unit = {},
    onAcceptGameInvite: (GameRequest) -> Unit = {},
    onOpenThread: (UserProfile) -> Unit = {},
    onMessageClick: (ChatMessage) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
        Text(stringResource(R.string.friends_title), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.friends_subtitle), color = Color.White.copy(alpha = .7f))
        Spacer(Modifier.size(8.dp))

        TabRow(selectedTabIndex = friendsTab.ordinal, containerColor = Color.White.copy(.12f), contentColor = Color.White) {
            Tab(selected = friendsTab == FriendsTab.FRIENDS, onClick = { onTabChange(FriendsTab.FRIENDS) }, text = { Text("Friends") }, icon = { Icon(Icons.Default.GroupAdd, null) })
            Tab(selected = friendsTab == FriendsTab.MESSAGE, onClick = { onTabChange(FriendsTab.MESSAGE) }, text = { Text("Message") }, icon = { Icon(Icons.Default.Mail, null) })
            Tab(selected = friendsTab == FriendsTab.REQUEST, onClick = { onTabChange(FriendsTab.REQUEST) }, text = { Text("Requests (${requests.size})") }, icon = { Icon(Icons.Default.PersonAdd, null) })
        }

        Spacer(Modifier.size(10.dp))

        when (friendsTab) {
            FriendsTab.FRIENDS -> FriendsTabContent(
                friends = friends,
                searchResults = searchResults,
                offline = offline,
                query = query,
                onQueryChange = { query = it; onSearch(it) },
                onAdd = onAdd,
                onInvite = onInvite,
                onAccept = onAccept
            )
            FriendsTab.MESSAGE -> MessageTabContent(
                messages = messages,
                friends = friends,
                onOpenThread = onOpenThread,
                onMessageClick = onMessageClick
            )
            FriendsTab.REQUEST -> RequestTabContent(
                requests = requests,
                onAccept = onRequestAccept,
                onDecline = onRequestDecline,
                onSendLife = onSendLife,
                onAcceptGameInvite = onAcceptGameInvite
            )
        }
    }
}

@Composable
private fun FriendsTabContent(
    friends: List<FriendProfile>,
    searchResults: List<UserProfile>,
    offline: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: (UserProfile) -> Unit,
    onInvite: (FriendProfile) -> Unit,
    onAccept: (FriendProfile) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(if (offline) R.string.friends_search_hint_offline else R.string.friends_search_hint))
            },
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
        Spacer(Modifier.size(10.dp))
        WhiteCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                query.length >= 2 && searchResults.isNotEmpty() -> {
                    Text(stringResource(R.string.friends_players), color = Ink, style = MaterialTheme.typography.titleLarge)
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(searchResults, key = { it.uid }) { profile ->
                            PersonRow(profile, status = stringResource(R.string.friends_tap_to_connect)) {
                                Button(onClick = { onAdd(profile) }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text(stringResource(R.string.action_add)) }
                            }
                        }
                    }
                }
                friends.isEmpty() -> EmptyState(
                    Icons.Default.GroupAdd,
                    stringResource(R.string.friends_empty_title),
                    stringResource(if (offline) R.string.friends_empty_body_offline else R.string.friends_empty_body)
                )
                else -> {
                    Text(stringResource(R.string.friends_your_friends), color = Ink, style = MaterialTheme.typography.titleLarge)
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(friends, key = { it.profile.uid }) { friend ->
                            PersonRow(
                                friend.profile,
                                status = stringResource(
                                    when {
                                        friend.status == "pending" -> R.string.friends_status_incoming
                                        friend.status == "pending_outgoing" -> R.string.friends_status_outgoing
                                        friend.isOnline -> R.string.friends_status_online
                                        else -> R.string.friends_status_offline
                                    }
                                ),
                                online = friend.isOnline
                            ) {
                                when (friend.status) {
                                    "pending" -> Button(onClick = { onAccept(friend) }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text(stringResource(R.string.action_accept)) }
                                    "pending_outgoing" -> Text(stringResource(R.string.action_sent), color = Muted, style = MaterialTheme.typography.labelLarge)
                                    else -> Button(onClick = { onInvite(friend) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text(stringResource(R.string.action_invite)) }
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
private fun MessageTabContent(
    messages: List<ChatMessage>,
    friends: List<FriendProfile>,
    onOpenThread: (UserProfile) -> Unit,
    onMessageClick: (ChatMessage) -> Unit
) {
    WhiteCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (messages.isEmpty() && friends.isEmpty()) {
            EmptyState(Icons.Default.ChatBubble, "No messages", "Start a conversation with a friend")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                // Show conversation partners from messages + friends for new chat
                val grouped = messages.groupBy { if (it.senderId == it.receiverId) it.senderId else if (it.senderId < it.receiverId) "${it.senderId}-${it.receiverId}" else "${it.receiverId}-${it.senderId}" }
                items(messages.distinctBy { it.senderId to it.receiverId }.take(50)) { msg ->
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mail, null, tint = Teal, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(msg.body.take(40), color = Ink, style = MaterialTheme.typography.bodyMedium)
                            Text(msg.createdAt.take(16), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { onMessageClick(msg) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                            Text("Open")
                        }
                    }
                }
                // Friend list to start new chat
                items(friends.take(10)) { friend ->
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(friend.profile, size = 32.dp, borderWidth = 1.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(friend.profile.displayName, modifier = Modifier.weight(1f), color = Ink)
                        Button(onClick = { onOpenThread(friend.profile) }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                            Text("Chat")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestTabContent(
    requests: List<GameRequest>,
    onAccept: (GameRequest) -> Unit,
    onDecline: (GameRequest) -> Unit,
    onSendLife: (GameRequest) -> Unit,
    onAcceptGameInvite: (GameRequest) -> Unit
) {
    WhiteCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (requests.isEmpty()) {
            EmptyState(Icons.Default.PersonAdd, "No requests", "Friend, life and game invites will appear here")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(requests, key = { it.id }) { req ->
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (req.type) {
                                RequestType.FRIEND -> Icons.Default.PersonAdd
                                RequestType.LIFE -> Icons.Default.Favorite
                                RequestType.GAME_INVITE -> Icons.Default.VpnKey
                            },
                            null,
                            tint = when (req.type) {
                                RequestType.FRIEND -> Purple
                                RequestType.LIFE -> Color.Red
                                RequestType.GAME_INVITE -> Blue
                            }
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${req.type} from ${req.senderId.take(6)}", color = Ink, style = MaterialTheme.typography.titleSmall)
                            Text("Status ${req.status} • ${req.createdAt.take(10)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            req.payload?.let { Text(it.toString().take(60), color = Muted, style = MaterialTheme.typography.bodySmall) }
                        }
                        when (req.type) {
                            RequestType.FRIEND -> {
                                Button(onClick = { onAccept(req) }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Accept") }
                                Spacer(Modifier.size(4.dp))
                                Button(onClick = { onDecline(req) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Decline") }
                            }
                            RequestType.LIFE -> {
                                Button(onClick = { onSendLife(req) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Send Life") }
                            }
                            RequestType.GAME_INVITE -> {
                                Button(onClick = { onAcceptGameInvite(req) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Join") }
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
            profile.username?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.username_handle, it), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Text(status, color = if (online) Teal else Muted, style = MaterialTheme.typography.bodyMedium)
        }
        action()
    }
}
