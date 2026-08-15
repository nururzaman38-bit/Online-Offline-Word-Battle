package com.wordbattle.com.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wordbattle.com.R
import com.wordbattle.com.ui.AppLanguage
import com.wordbattle.com.ui.MainTab
import com.wordbattle.com.ui.MainUiState
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.TopPlayerBar
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.PurpleLight

private data class TabItem(val tab: MainTab, @androidx.annotation.StringRes val title: Int, val icon: ImageVector)

@Composable
fun MainShellScreen(
    state: MainUiState,
    onTab: (MainTab) -> Unit,
    onSelectMode: (Int) -> Unit,
    onPlay: () -> Unit,
    onJoinRoom: () -> Unit,
    onCreateRoom: () -> Unit,
    onLeaderboardToggle: (Boolean) -> Unit,
    onSearchFriends: (String) -> Unit,
    onAddFriend: (com.wordbattle.com.data.model.UserProfile) -> Unit,
    onInviteFriend: (com.wordbattle.com.data.model.FriendProfile) -> Unit,
    onAcceptFriend: (com.wordbattle.com.data.model.FriendProfile) -> Unit,
    onSound: () -> Unit,
    onNotifications: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onEditIdentity: () -> Unit,
    onLogout: () -> Unit,
    onCampaignClick: () -> Unit = {},
    onFriendsTabChange: (com.wordbattle.com.ui.FriendsTab) -> Unit = {},
    onRequestAccept: (com.wordbattle.com.data.model.GameRequest) -> Unit = {},
    onRequestDecline: (com.wordbattle.com.data.model.GameRequest) -> Unit = {},
    onSendLife: (com.wordbattle.com.data.model.GameRequest) -> Unit = {},
    onAcceptGameInvite: (com.wordbattle.com.data.model.GameRequest) -> Unit = {},
    onOpenThread: (com.wordbattle.com.data.model.UserProfile) -> Unit = {},
    onMessageClick: (com.wordbattle.com.data.model.ChatMessage) -> Unit = {}
) {
    GradientBackground {
        Column(Modifier.fillMaxSize()) {
            TopPlayerBar(state.profile) { onTab(MainTab.PROFILE) }
            Box(Modifier.weight(1f)) {
                when (state.mainTab) {
                    MainTab.HOME -> HomeScreen(
                        profile = state.profile,
                        selectedPlayers = state.selectedModePlayers,
                        isOffline = !state.isOnline,
                        onSelectMode = onSelectMode,
                        onPlay = onPlay,
                        onJoinRoom = onJoinRoom,
                        onCreateRoom = onCreateRoom,
                        onCampaignClick = onCampaignClick
                    )
                    MainTab.RANK -> RankScreen(state.leaderboardWeekly, state.leaderboard, onLeaderboardToggle)
                    MainTab.FRIENDS -> FriendsScreen(
                        friends = state.friends,
                        searchResults = state.friendSearchResults,
                        offline = state.isOfflineGuest,
                        onSearch = onSearchFriends,
                        onAdd = onAddFriend,
                        onInvite = onInviteFriend,
                        onAccept = onAcceptFriend,
                        friendsTab = state.friendsTab,
                        onTabChange = onFriendsTabChange,
                        requests = state.requests,
                        messages = state.messages,
                        onRequestAccept = onRequestAccept,
                        onRequestDecline = onRequestDecline,
                        onSendLife = onSendLife,
                        onAcceptGameInvite = onAcceptGameInvite,
                        onOpenThread = onOpenThread,
                        onMessageClick = onMessageClick
                    )
                    MainTab.PROFILE -> ProfileScreen(
                        state.profile, state.isOfflineGuest, state.soundEnabled, state.notificationsEnabled,
                        state.language, onSound, onNotifications, onLanguage, onEditIdentity, onLogout
                    )
                }
            }
            BottomBar(state.mainTab, onTab)
        }
    }
}

@Composable
private fun BottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val tabs = listOf(
        TabItem(MainTab.HOME, R.string.tab_home, Icons.Default.Home),
        TabItem(MainTab.RANK, R.string.tab_rank, Icons.Default.Leaderboard),
        TabItem(MainTab.FRIENDS, R.string.tab_friends, Icons.Default.Groups),
        TabItem(MainTab.PROFILE, R.string.tab_profile, Icons.Default.Person)
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 18.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp)) {
            tabs.forEach { item ->
                val active = selected == item.tab
                val title = stringResource(item.title)
                NavigationBarItem(
                    selected = active,
                    onClick = { onSelect(item.tab) },
                    icon = {
                        Icon(
                            item.icon,
                            title,
                            modifier = Modifier.size(23.dp).offset(y = if (active) (-2).dp else 0.dp)
                        )
                    },
                    label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleLight,
                        selectedTextColor = PurpleLight,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted,
                        indicatorColor = PurpleLight.copy(alpha = .11f)
                    )
                )
            }
        }
    }
}
