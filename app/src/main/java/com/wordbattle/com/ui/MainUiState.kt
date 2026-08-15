package com.wordbattle.com.ui

import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.UserProfile

enum class RootScreen { SPLASH, LOGIN, IDENTITY, MAIN, ASSIGNMENT, ROOM_SETUP, JOIN_ROOM, LOBBY, GAME, RESULTS }
enum class MainTab { HOME, RANK, FRIENDS, PROFILE }
enum class ToastKind { DEFAULT, WARNING, SUCCESS }

/**
 * A transient message.
 *
 * [text] is a [UiText] rather than a [String] so the message follows the selected language even if
 * it is still on screen while the user switches between English and Bengali.
 */
data class BattleToast(val id: Long, val text: UiText, val kind: ToastKind = ToastKind.DEFAULT)

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
    val language: AppLanguage = AppLanguage.DEFAULT,
    /** Live internet availability reported by `NetworkConnectivityObserver`. */
    val isOnline: Boolean = true,
    /** True while the "no internet" dialog (Retry / Network settings) is visible. */
    val showOfflineDialog: Boolean = false,
    /** True when an online battle or lobby lost its connection and is being re-subscribed. */
    val isReconnecting: Boolean = false,
    /** Set when the identity screen is opened from Profile rather than at first login. */
    val identityEditing: Boolean = false,
    val toast: BattleToast? = null
) {
    /** Online play needs both a signed-in account and a working connection. */
    val canPlayOnline: Boolean get() = !isOfflineGuest && profile != null && isOnline

    /**
     * True when a player controlled by this device finished first. Drives the victory fanfare and
     * the confetti animation on the results screen.
     */
    val didWinCurrentGame: Boolean
        get() {
            val finished = game?.takeIf { it.status == GameStatus.FINISHED } ?: return false
            return finished.players.any { it.rank == 1 && (it.id in ownedPlayerIds || ownedPlayerIds.isEmpty()) }
        }
}
