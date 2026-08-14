package com.wordbattle.com.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.ui.MainUiState
import com.wordbattle.com.ui.MainViewModel
import com.wordbattle.com.ui.RootScreen
import com.wordbattle.com.ui.components.BattleToastOverlay
import com.wordbattle.com.ui.screens.AssignmentScreen
import com.wordbattle.com.ui.screens.GameScreen
import com.wordbattle.com.ui.screens.JoinRoomScreen
import com.wordbattle.com.ui.screens.LobbyScreen
import com.wordbattle.com.ui.screens.LoginScreen
import com.wordbattle.com.ui.screens.MainShellScreen
import com.wordbattle.com.ui.screens.ResultsScreen
import com.wordbattle.com.ui.screens.RoomSetupScreen
import com.wordbattle.com.ui.screens.SplashScreen

@Composable
fun WordBattleNavGraph(state: MainUiState, viewModel: MainViewModel, context: Context) {
    BackHandler(enabled = state.rootScreen !in setOf(RootScreen.SPLASH, RootScreen.LOGIN, RootScreen.MAIN)) {
        viewModel.goBack()
    }
    Box(Modifier.fillMaxSize()) {
        when (state.rootScreen) {
            RootScreen.SPLASH -> SplashScreen()
            RootScreen.LOGIN -> LoginScreen(
                busy = state.isBusy,
                onGoogle = { viewModel.signInWithGoogle(context) },
                onEmail = viewModel::signInWithEmail,
                onOffline = viewModel::continueOffline
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
                onLogout = viewModel::logout
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
                viewModel::goBack
            )
            RootScreen.GAME -> GameScreen(
                state.game,
                state.selectedLetter,
                state.ownedPlayerIds,
                state.turnSecondsRemaining,
                viewModel::selectLetter,
                viewModel::placeSelectedLetter,
                viewModel::skipCurrentTurn,
                viewModel::goHome
            )
            RootScreen.RESULTS -> ResultsScreen(state.game, viewModel::playAgain, viewModel::goHome)
        }
        BattleToastOverlay(state.toast, Modifier.align(Alignment.BottomCenter))
    }
}

private fun shareRoom(context: Context, room: Room) {
    val message = "Join my Word Battle! Room: ${room.roomCode} • Passcode: ${room.passcode}"
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            },
            "Share battle room"
        )
    )
}
