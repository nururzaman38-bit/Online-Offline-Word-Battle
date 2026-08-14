package com.wordbattle.com.ui

import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.UserProfile

enum class RootScreen { SPLASH, LOGIN, MAIN, ASSIGNMENT, ROOM_SETUP, JOIN_ROOM, LOBBY, GAME, RESULTS }
enum class MainTab { HOME, RANK, FRIENDS, PROFILE }
enum class ToastKind { DEFAULT, WARNING, SUCCESS }

data class BattleToast(val id: Long, val text: String, val kind: ToastKind = ToastKind.DEFAULT)

data class MainUiState(
    val rootScreen: RootScreen = RootScreen.SPLASH,
    val mainTab: MainTab = MainTab.HOME,
    val profile: UserProfile? = null,
    val isOfflineGuest: Boolean = false,
    val isBusy: Boolean = false,
    val selectedModePlayers: Int = 1,
    val assignmentPlayerCount: Int = 2,
    val onlineSlots: Set<Int> = emptySet(),
    val room: Room? = null,
    val game: GameState? = null,
    val selectedLetter: Char? = null,
    val ownedPlayerIds: Set<String> = emptySet(),
    val isHostDevice: Boolean = false,
    val turnSecondsRemaining: Int = 45,
    val leaderboardWeekly: Boolean = true,
    val leaderboard: List<UserProfile> = emptyList(),
    val friends: List<FriendProfile> = emptyList(),
    val friendSearchResults: List<UserProfile> = emptyList(),
    val soundEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val language: String = "English",
    val toast: BattleToast? = null
)
