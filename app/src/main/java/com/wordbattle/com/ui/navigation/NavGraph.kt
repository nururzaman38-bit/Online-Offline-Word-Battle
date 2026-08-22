package com.wordbattle.com.ui.navigation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wordbattle.com.R
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.ui.MainUiState
import com.wordbattle.com.ui.MainViewModel
import com.wordbattle.com.ui.RootScreen
import com.wordbattle.com.ui.components.BattleToastOverlay
import com.wordbattle.com.ui.components.ConnectionBanner
import com.wordbattle.com.ui.components.OfflineDialog
import com.wordbattle.com.ui.screens.AssignmentScreen
import com.wordbattle.com.ui.screens.GameScreen
import com.wordbattle.com.ui.screens.IdentityScreen
import com.wordbattle.com.ui.screens.JoinRoomScreen
import com.wordbattle.com.ui.screens.LevelSelectScreen
import com.wordbattle.com.ui.screens.LobbyScreen
import com.wordbattle.com.ui.screens.LoginScreen
import com.wordbattle.com.ui.screens.MainShellScreen
import com.wordbattle.com.ui.screens.MessageThreadScreen
import com.wordbattle.com.ui.screens.PuzzleGameScreen
import com.wordbattle.com.ui.screens.ResultsScreen
import com.wordbattle.com.ui.screens.RoomSetupScreen
import com.wordbattle.com.ui.screens.SplashScreen
import com.wordbattle.com.ui.screens.LivesBottomSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBattleNavGraph(state: MainUiState, viewModel: MainViewModel, context: Context) {
    BackHandler(enabled = state.rootScreen !in setOf(RootScreen.SPLASH, RootScreen.LOGIN, RootScreen.MAIN)) {
        viewModel.goBack()
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ConnectionBanner(reconnecting = state.isReconnecting, offline = !state.isOnline)
            Box(Modifier.fillMaxSize()) {
                when (state.rootScreen) {
                    RootScreen.SPLASH -> SplashScreen()
                    RootScreen.LOGIN -> LoginScreen(
                        busy = state.isBusy,
                        onGoogle = { viewModel.signInWithGoogle(context) },
                        onEmail = viewModel::signInWithEmail,
                        onOffline = viewModel::continueOffline
                    )
                    RootScreen.IDENTITY -> IdentityScreen(
                        profile = state.profile,
                        busy = state.isBusy,
                        editing = state.identityEditing,
                        cooldownDays = viewModel.displayNameCooldownDays(),
                        onSave = viewModel::saveIdentity,
                        onBack = viewModel::goBack
                    )
                    RootScreen.MAIN -> MainShellScreen(
                        state = state,
                        onTab = viewModel::selectMainTab,
                        onSelectMode = viewModel::selectMode,
                        onPlay = viewModel::playSelectedMode,
                        onJoinRoom = viewModel::openJoinRoom,
                        onCreateRoom = viewModel::createQuickRoom,
                        onLeaderboardToggle = viewModel::setLeaderboardWeekly,
                        onSearchFriends = viewModel::searchFriends,
                        onAddFriend = viewModel::addFriend,
                        onInviteFriend = viewModel::inviteFriend,
                        onAcceptFriend = viewModel::acceptFriend,
                        onSound = viewModel::toggleSound,
                        onNotifications = viewModel::toggleNotifications,
                        onLanguage = viewModel::setLanguage,
                        onEditIdentity = viewModel::openIdentityEditor,
                        onLogout = viewModel::logout,
                        onCampaignClick = viewModel::openCampaign,
                        onFriendsTabChange = viewModel::selectFriendsTab,
                        onRequestAccept = viewModel::acceptRequest,
                        onRequestDecline = viewModel::declineRequest,
                        onSendLife = viewModel::sendLifeForRequest,
                        onAcceptGameInvite = viewModel::acceptGameInvite,
                        onOpenThread = viewModel::openThread,
                        onMessageClick = viewModel::openThreadFromMessage
                    )
                    RootScreen.ASSIGNMENT -> AssignmentScreen(
                        state.assignmentPlayerCount,
                        state.onlineSlots,
                        state.isBusy,
                        viewModel::toggleOnlineSlot,
                        viewModel::continueAssignment,
                        viewModel::goBack
                    )
                    RootScreen.JOIN_ROOM -> JoinRoomScreen(state.isBusy, viewModel::joinRoom, viewModel::goBack)
                    RootScreen.ROOM_SETUP -> RoomSetupScreen(
                        state.room,
                        state.isBusy,
                        viewModel::startHostedGame,
                        { shareRoom(context, it) },
                        viewModel::goBack
                    )
                    RootScreen.LOBBY -> LobbyScreen(
                        state.room,
                        state.profile?.uid,
                        state.isBusy,
                        viewModel::setReady,
                        viewModel::goBack,
                        viewModel::refreshLobby
                    )
                    RootScreen.GAME -> GameScreen(
                        state.game,
                        state.selectedLetter,
                        state.ownedPlayerIds,
                        state.turnSecondsRemaining,
                        viewModel::selectLetter,
                        viewModel::placeSelectedLetter,
                        viewModel::skipCurrentTurn,
                        viewModel::goHome,
                        hostCanSkip = state.isHostDevice
                    )
                    RootScreen.RESULTS -> ResultsScreen(
                        game = state.game,
                        // A completed campaign level (score attack or puzzle) is always a win;
                        // puzzles keep no GameState so the flag comes from campaignResult.
                        didWin = state.didWinCurrentGame || state.campaignResult != null,
                        onPlayAgain = viewModel::playAgain,
                        onHome = viewModel::goHome,
                        campaignResult = state.campaignResult
                    )
                    RootScreen.LEVEL_SELECT -> LevelSelectScreen(
                        levels = state.campaignLevels,
                        progress = state.campaignProgress,
                        currentUnlocked = state.profile?.campaignLevel ?: 1,
                        onSelectLevel = viewModel::selectCampaignLevel,
                        onBack = viewModel::goBack
                    )
                    RootScreen.PUZZLE_GAME -> {
                        val level = state.selectedLevel
                        val puzzle = state.puzzleState
                        if (level != null && puzzle != null) {
                            PuzzleGameScreen(
                                level = level,
                                puzzleState = puzzle,
                                elapsedSeconds = state.puzzleElapsedSeconds,
                                livesCurrent = state.profile?.livesCurrent ?: 3,
                                livesMax = state.profile?.livesMax ?: 3,
                                selectedLetter = state.selectedLetter,
                                wrongCells = state.puzzleWrongCells,
                                onSelectLetter = viewModel::puzzleSelectLetter,
                                onPlaceLetter = { r, c -> viewModel.puzzlePlaceLetter(r, c) },
                                onBack = viewModel::goBack
                            )
                        }
                    }
                    RootScreen.MESSAGE_THREAD -> MessageThreadScreen(
                        friend = state.selectedThreadFriend,
                        messages = state.messageThread,
                        currentUid = state.profile?.uid,
                        onSend = viewModel::sendMessage,
                        onBack = viewModel::goBack
                    )
                }
            }
        }
        if (state.showOfflineDialog) {
            val dialogContext = LocalContext.current
            OfflineDialog(
                onRetry = viewModel::retryAfterOffline,
                onNetworkSettings = { openNetworkSettings(dialogContext) },
                onDismiss = viewModel::dismissOfflineDialog
            )
        }
        BattleToastOverlay(state.toast, Modifier.align(Alignment.BottomCenter))

        if (state.showLifeBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissLifeBottomSheet,
                sheetState = rememberModalBottomSheetState()
            ) {
                LivesBottomSheet(
                    livesCurrent = state.profile?.livesCurrent ?: 0,
                    livesMax = state.profile?.livesMax ?: 3,
                    regenMinutes = state.lifeRegenCountdownMinutes,
                    coins = state.profile?.coins ?: 0,
                    friends = state.friends,
                    onBuyLife = viewModel::buyLife,
                    onRequestLife = { friendId -> viewModel.requestLifeFromFriend(friendId) },
                    onDismiss = viewModel::dismissLifeBottomSheet
                )
            }
        }
    }
}

/** Share the room code/passcode with the current locale's wording. */
private fun shareRoom(context: Context, room: Room) {
    val message = context.getString(R.string.room_share_message, room.roomCode, room.passcode)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            },
            context.getString(R.string.room_share_title)
        )
    )
}

/** Opens the system Wi-Fi/data settings; falls back to the general settings page if unavailable. */
private fun openNetworkSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in intents) {
        val launchable = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (launchable) return
    }
}
