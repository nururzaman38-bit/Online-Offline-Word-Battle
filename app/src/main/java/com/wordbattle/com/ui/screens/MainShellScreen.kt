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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wordbattle.com.ui.MainTab
import com.wordbattle.com.ui.MainUiState
import com.wordbattle.com.ui.components.GradientBackground
import com.wordbattle.com.ui.components.TopPlayerBar
import com.wordbattle.com.ui.theme.Muted
import com.wordbattle.com.ui.theme.PurpleLight

private data class TabItem(val tab: MainTab, val title: String, val icon: ImageVector)

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
    onLanguage: (String) -> Unit,
    onLogout: () -> Unit
) {
    GradientBackground {
        Column(Modifier.fillMaxSize()) {
            TopPlayerBar(state.profile) { onTab(MainTab.PROFILE) }
            Box(Modifier.weight(1f)) {
                when (state.mainTab) {
                    MainTab.HOME -> HomeScreen(state.selectedModePlayers, onSelectMode, onPlay, onJoinRoom, onCreateRoom)
                    MainTab.RANK -> RankScreen(state.leaderboardWeekly, state.leaderboard, onLeaderboardToggle)
                    MainTab.FRIENDS -> FriendsScreen(
                        state.friends, state.friendSearchResults, state.isOfflineGuest,
                        onSearchFriends, onAddFriend, onInviteFriend, onAcceptFriend
                    )
                    MainTab.PROFILE -> ProfileScreen(
                        state.profile, state.isOfflineGuest, state.soundEnabled, state.notificationsEnabled,
                        state.language, onSound, onNotifications, onLanguage, onLogout
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
        TabItem(MainTab.HOME, "Home", Icons.Default.Home),
        TabItem(MainTab.RANK, "Rank", Icons.Default.Leaderboard),
        TabItem(MainTab.FRIENDS, "Friends", Icons.Default.Groups),
        TabItem(MainTab.PROFILE, "Profile", Icons.Default.Person)
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
                NavigationBarItem(
                    selected = active,
                    onClick = { onSelect(item.tab) },
                    icon = {
                        Icon(
                            item.icon,
                            item.title,
                            modifier = Modifier.size(23.dp).offset(y = if (active) (-2).dp else 0.dp)
                        )
                    },
                    label = { Text(item.title, style = MaterialTheme.typography.labelMedium) },
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
