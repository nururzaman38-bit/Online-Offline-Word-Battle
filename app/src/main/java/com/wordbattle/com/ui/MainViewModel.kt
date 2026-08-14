package com.wordbattle.com.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordbattle.com.WordBattleApplication
import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.game.ComputerAI
import com.wordbattle.com.data.game.RoomManager
import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.JoinRoomResult
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.data.repository.GameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as WordBattleApplication).container
    private val auth = container.authRepository
    private val users = container.userRepository
    private val rooms = container.roomRepository

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private lateinit var dictionary: WordDictionary
    private lateinit var games: GameRepository
    private lateinit var ai: ComputerAI
    private var roomJob: Job? = null
    private var gameJob: Job? = null
    private var leaderboardJob: Job? = null
    private var presenceJob: Job? = null
    private var onlineUserIds: Set<String> = emptySet()
    private var turnTimerJob: Job? = null
    private var toastJob: Job? = null
    private val reportedGameIds = mutableSetOf<String>()

    init {
        initialize()
    }

    private fun initialize() = viewModelScope.launch {
        val minimumSplash = async { delay(2_000) }
        dictionary = WordDictionary.load(getApplication())
        games = GameRepository(dictionary, container.database.gameDao(), container.json)
        ai = ComputerAI(dictionary)
        val signedIn = runCatching { auth.hasSession() }.getOrDefault(false)
        minimumSplash.await()
        if (signedIn) {
            val uid = auth.currentUserId()
            val profile = uid?.let { users.getProfile(it) }
                ?: uid?.let { UserProfile(it, "Word Player") }
            _uiState.update { it.copy(rootScreen = RootScreen.MAIN, profile = profile) }
            refreshHomeData()
        } else {
            _uiState.update { it.copy(rootScreen = RootScreen.LOGIN) }
        }
    }

    fun signInWithGoogle(context: Context) = launchBusy {
        val profile = auth.signInWithGoogle(context)
        _uiState.update { it.copy(rootScreen = RootScreen.MAIN, profile = profile, isOfflineGuest = false) }
        showToast("Welcome, ${profile.displayName}!", ToastKind.SUCCESS)
        refreshHomeData()
    }

    fun signInWithEmail(email: String, password: String, createAccount: Boolean) = launchBusy {
        val profile = if (createAccount) auth.signUpWithEmail(email, password) else auth.signInWithEmail(email, password)
        _uiState.update { it.copy(rootScreen = RootScreen.MAIN, profile = profile, isOfflineGuest = false) }
        showToast("Welcome, ${profile.displayName}!", ToastKind.SUCCESS)
        refreshHomeData()
    }

    fun continueOffline() {
        val guest = UserProfile("guest-${UUID.randomUUID()}", "Guest Player", coins = 250, level = 1)
        _uiState.update { it.copy(rootScreen = RootScreen.MAIN, profile = guest, isOfflineGuest = true) }
        showToast("Offline mode ready", ToastKind.SUCCESS)
    }

    fun selectMainTab(tab: MainTab) {
        _uiState.update { it.copy(mainTab = tab) }
        when (tab) {
            MainTab.RANK -> loadLeaderboard()
            MainTab.FRIENDS -> loadFriends()
            else -> Unit
        }
    }

    fun selectMode(playerCount: Int) {
        require(playerCount in 1..4)
        _uiState.update { it.copy(selectedModePlayers = playerCount) }
    }

    fun playSelectedMode() {
        val count = uiState.value.selectedModePlayers
        if (count == 1) {
            startComputerGame()
        } else {
            _uiState.update {
                it.copy(
                    rootScreen = RootScreen.ASSIGNMENT,
                    assignmentPlayerCount = count,
                    onlineSlots = emptySet()
                )
            }
        }
    }

    fun toggleOnlineSlot(slotIndex: Int) {
        if (slotIndex == 0) return
        _uiState.update { state ->
            val changed = state.onlineSlots.toMutableSet().apply {
                if (!add(slotIndex)) remove(slotIndex)
            }
            state.copy(onlineSlots = changed)
        }
    }

    fun continueAssignment() {
        val state = uiState.value
        if (state.onlineSlots.isEmpty()) {
            val game = games.createLocalGame(state.assignmentPlayerCount, state.profile?.displayName ?: "Player 1")
            enterLocalGame(game, game.players.map { it.id }.toSet())
            return
        }
        if (state.isOfflineGuest || state.profile == null) {
            showToast("Sign in to create an online room", ToastKind.WARNING)
            return
        }
        launchBusy {
            val localCount = state.assignmentPlayerCount - state.onlineSlots.size
            val room = rooms.createRoom(state.profile, localCount, state.onlineSlots.size)
            _uiState.update {
                it.copy(
                    room = room,
                    rootScreen = RootScreen.ROOM_SETUP,
                    isHostDevice = true,
                    ownedPlayerIds = setOf(state.profile.uid)
                )
            }
            observeRoom(room.roomId)
        }
    }

    fun openJoinRoom() {
        if (uiState.value.isOfflineGuest) {
            showToast("Sign in to join online rooms", ToastKind.WARNING)
        } else {
            _uiState.update { it.copy(rootScreen = RootScreen.JOIN_ROOM) }
        }
    }

    fun createQuickRoom() {
        val state = uiState.value
        if (state.isOfflineGuest || state.profile == null) {
            showToast("Sign in to create online rooms", ToastKind.WARNING)
            return
        }
        _uiState.update {
            it.copy(
                rootScreen = RootScreen.ASSIGNMENT,
                assignmentPlayerCount = 2,
                selectedModePlayers = 2,
                onlineSlots = setOf(1)
            )
        }
    }

    fun joinRoom(code: String, passcode: String) {
        val profile = uiState.value.profile ?: return
        launchBusy {
            when (val result = rooms.joinRoom(code, passcode, profile)) {
                is JoinRoomResult.Error -> showToast(result.message, ToastKind.WARNING)
                is JoinRoomResult.Success -> {
                    _uiState.update {
                        it.copy(
                            room = result.room,
                            rootScreen = RootScreen.LOBBY,
                            isHostDevice = false,
                            ownedPlayerIds = setOf(profile.uid)
                        )
                    }
                    observeRoom(result.room.roomId)
                }
            }
        }
    }

    fun setReady(ready: Boolean) {
        val state = uiState.value
        val uid = state.profile?.uid ?: return
        val roomId = state.room?.roomId ?: return
        launchBusy { rooms.setReady(roomId, uid, ready) }
    }

    fun startHostedGame() {
        val state = uiState.value
        val uid = state.profile?.uid ?: return
        val room = state.room ?: return
        launchBusy {
            val game = rooms.startGame(room.roomId, uid)
            enterOnlineGame(game, isHost = true, roomLocalSlots = room.localSlotsCount)
        }
    }

    fun selectLetter(letter: Char) {
        if (letter.uppercaseChar() !in 'A'..'Z') return
        _uiState.update { it.copy(selectedLetter = letter.uppercaseChar()) }
    }

    fun placeSelectedLetter(row: Int, col: Int) {
        val state = uiState.value
        val game = state.game ?: return
        val letter = state.selectedLetter ?: run {
            showToast("Pick a letter first", ToastKind.WARNING); return
        }
        val playerId = game.currentTurnPlayerId
        if (playerId !in state.ownedPlayerIds) {
            val playerName = game.players.firstOrNull { it.id == playerId }?.name ?: "player"
            showToast("Waiting for $playerName", ToastKind.WARNING)
            return
        }
        val result = games.placeLetter(game, playerId, row, col, letter)
        result.onFailure { showToast(it.message ?: "Move rejected", ToastKind.WARNING) }
        result.onSuccess { placement ->
            _uiState.update { it.copy(game = placement.gameState, selectedLetter = null) }
            showToast(
                placement.message,
                if (placement.pointsAwarded > 0) ToastKind.SUCCESS
                else if (placement.repeatedWords.isNotEmpty()) ToastKind.WARNING else ToastKind.DEFAULT
            )
            afterGameChanged(placement.gameState)
        }
    }

    fun skipCurrentTurn() {
        val state = uiState.value
        val game = state.game ?: return
        if (game.currentTurnPlayerId !in state.ownedPlayerIds && !state.isHostDevice) return
        games.skipTurn(game, game.currentTurnPlayerId).onSuccess {
            _uiState.update { state -> state.copy(game = it, selectedLetter = null) }
            showToast("Turn skipped", ToastKind.WARNING)
            afterGameChanged(it)
        }
    }

    private fun afterGameChanged(game: GameState) {
        startTurnTimer(game)
        viewModelScope.launch {
            games.cache(game)
            if (game.mode == GameMode.MIXED_ONLINE) {
                runCatching {
                    rooms.updateGame(game)
                    if (game.status == GameStatus.FINISHED && uiState.value.isHostDevice) {
                        val room = uiState.value.room
                        val hostUid = uiState.value.profile?.uid
                        if (room != null && hostUid != null) rooms.finishRoom(room.roomId, hostUid)
                    }
                }.onFailure { showToast("Move saved locally; reconnecting…", ToastKind.WARNING) }
            }
        }
        if (game.status == GameStatus.FINISHED) {
            _uiState.update { it.copy(rootScreen = RootScreen.RESULTS) }
            reportFinishedGame(game)
            return
        }
        val next = game.players.firstOrNull { it.id == game.currentTurnPlayerId }
        if (next?.type == PlayerType.COMPUTER) runComputerTurn(game)
    }

    private fun startComputerGame() {
        val game = games.createComputerGame(uiState.value.profile?.displayName ?: "You")
        enterLocalGame(game, setOf(game.players.first().id))
    }

    private fun enterLocalGame(game: GameState, ownedIds: Set<String>) {
        stopRealtime()
        pauseSocialRealtime()
        _uiState.update {
            it.copy(
                rootScreen = RootScreen.GAME,
                game = game,
                ownedPlayerIds = ownedIds,
                isHostDevice = true,
                selectedLetter = null
            )
        }
        startTurnTimer(game)
    }

    private fun enterOnlineGame(game: GameState, isHost: Boolean, roomLocalSlots: Int) {
        val profileUid = uiState.value.profile?.uid
        val owned = RoomManager.ownedPlayerIds(game, profileUid, isHost, roomLocalSlots)
        _uiState.update {
            it.copy(
                rootScreen = RootScreen.GAME,
                game = game,
                ownedPlayerIds = owned,
                isHostDevice = isHost,
                selectedLetter = null
            )
        }
        observeGame(game.gameId)
        startTurnTimer(game)
    }

    private fun runComputerTurn(expected: GameState) {
        viewModelScope.launch {
            delay(650)
            val current = uiState.value.game ?: return@launch
            if (current.gameId != expected.gameId || current.currentTurnPlayerId != "computer") return@launch
            val move = ai.chooseMove(current) ?: return@launch
            games.placeLetter(current, "computer", move.row, move.col, move.letter).onSuccess { placement ->
                _uiState.update { it.copy(game = placement.gameState) }
                showToast("Word Bot: ${placement.message}", if (placement.pointsAwarded > 0) ToastKind.SUCCESS else ToastKind.DEFAULT)
                afterGameChanged(placement.gameState)
            }
        }
    }

    private fun startTurnTimer(game: GameState) {
        turnTimerJob?.cancel()
        _uiState.update { it.copy(turnSecondsRemaining = TURN_TIMEOUT_SECONDS) }
        turnTimerJob = viewModelScope.launch {
            for (remaining in TURN_TIMEOUT_SECONDS - 1 downTo 0) {
                delay(1_000)
                val current = uiState.value.game ?: return@launch
                if (current.gameId != game.gameId || current.currentTurnPlayerId != game.currentTurnPlayerId) return@launch
                _uiState.update { it.copy(turnSecondsRemaining = remaining) }
                if (remaining == 0 && (uiState.value.isHostDevice || current.currentTurnPlayerId in uiState.value.ownedPlayerIds)) {
                    skipCurrentTurn()
                }
            }
        }
    }

    private fun observeRoom(roomId: String) {
        roomJob?.cancel()
        roomJob = viewModelScope.launch {
            rooms.observeRoom(roomId).catch { showToast("Lobby connection lost", ToastKind.WARNING) }.collect { room ->
                _uiState.update { it.copy(room = room) }
                if (room.status == "in_progress" && room.gameId != null && uiState.value.rootScreen !in setOf(RootScreen.GAME, RootScreen.RESULTS)) {
                    rooms.getGame(room.gameId)?.let { enterOnlineGame(it, uiState.value.isHostDevice, room.localSlotsCount) }
                }
            }
        }
    }

    private fun observeGame(gameId: String) {
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            rooms.observeGame(gameId).catch { showToast("Game connection lost", ToastKind.WARNING) }.collect { game ->
                _uiState.update { it.copy(game = game, rootScreen = if (game.status == GameStatus.FINISHED) RootScreen.RESULTS else RootScreen.GAME) }
                if (game.status == GameStatus.FINISHED) {
                    reportFinishedGame(game)
                    val state = uiState.value
                    if (state.isHostDevice) {
                        val room = state.room
                        val hostUid = state.profile?.uid
                        if (room != null && hostUid != null) launch { runCatching { rooms.finishRoom(room.roomId, hostUid) } }
                    }
                } else startTurnTimer(game)
            }
        }
    }

    fun setLeaderboardWeekly(weekly: Boolean) {
        _uiState.update { it.copy(leaderboardWeekly = weekly) }
        loadLeaderboard()
    }

    fun searchFriends(query: String) {
        val uid = uiState.value.profile?.uid ?: return
        if (uiState.value.isOfflineGuest) return
        viewModelScope.launch {
            val results = runCatching { users.searchProfiles(query, uid) }.getOrElse { emptyList() }
            _uiState.update { it.copy(friendSearchResults = results) }
        }
    }

    fun addFriend(profile: UserProfile) {
        val uid = uiState.value.profile?.uid ?: return
        launchBusy {
            users.addFriend(uid, profile.uid)
            showToast("Friend request sent", ToastKind.SUCCESS)
            _uiState.update { it.copy(friendSearchResults = emptyList()) }
            loadFriends()
        }
    }

    fun acceptFriend(friend: FriendProfile) {
        val uid = uiState.value.profile?.uid ?: return
        launchBusy {
            users.acceptFriend(uid, friend.profile.uid)
            showToast("Friend added", ToastKind.SUCCESS)
            loadFriends()
        }
    }

    fun inviteFriend(friend: FriendProfile) {
        createQuickRoom()
        showToast("Create the room, then share its code with ${friend.profile.displayName}")
    }

    fun toggleSound() = _uiState.update { it.copy(soundEnabled = !it.soundEnabled) }
    fun toggleNotifications() = _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }
    fun setLanguage(language: String) = _uiState.update { it.copy(language = language) }

    fun playAgain() {
        val game = uiState.value.game ?: return
        if (game.mode == GameMode.MIXED_ONLINE) {
            goHome()
            showToast("Create a fresh room for the rematch")
            return
        }
        val resetPlayers = game.players.map { it.copy(score = 0, rank = null) }
        val rematch = games.newGame(game.mode, game.targetScore, resetPlayers)
        val owned = if (game.mode == GameMode.COMPUTER) setOf(resetPlayers.first().id) else resetPlayers.map { it.id }.toSet()
        enterLocalGame(rematch, owned)
    }

    fun goBack() {
        when (uiState.value.rootScreen) {
            RootScreen.ASSIGNMENT, RootScreen.JOIN_ROOM -> _uiState.update { it.copy(rootScreen = RootScreen.MAIN) }
            RootScreen.ROOM_SETUP, RootScreen.LOBBY -> { stopRealtime(); _uiState.update { it.copy(rootScreen = RootScreen.MAIN, room = null) } }
            RootScreen.GAME, RootScreen.RESULTS -> goHome()
            else -> Unit
        }
    }

    fun goHome() {
        stopRealtime()
        _uiState.update { it.copy(rootScreen = RootScreen.MAIN, mainTab = MainTab.HOME, game = null, room = null, selectedLetter = null) }
        refreshHomeData()
    }

    fun logout() = viewModelScope.launch {
        stopRealtime()
        pauseSocialRealtime()
        if (!uiState.value.isOfflineGuest) runCatching { auth.signOut() }
        _uiState.value = MainUiState(rootScreen = RootScreen.LOGIN)
    }

    private fun reportFinishedGame(game: GameState) {
        val state = uiState.value
        val profile = state.profile ?: return
        if (state.isOfflineGuest || !reportedGameIds.add(game.gameId)) return
        val player = game.players.firstOrNull { it.id == profile.uid }
            ?: game.players.minByOrNull { it.turnOrder }
            ?: return
        viewModelScope.launch {
            runCatching { users.recordFinishedGame(profile, player.rank == 1, player.score) }
                .onSuccess { updated -> _uiState.update { it.copy(profile = updated) } }
        }
    }

    private fun refreshHomeData() {
        if (!uiState.value.isOfflineGuest) {
            loadLeaderboard()
            loadFriends()
            startPresence()
        }
    }

    private fun startPresence() {
        val uid = uiState.value.profile?.uid ?: return
        if (presenceJob?.isActive == true) return
        presenceJob = viewModelScope.launch {
            users.observeOnlineUsers(uid)
                .catch { /* Presence is best-effort; friend data still remains available. */ }
                .collect { online ->
                    onlineUserIds = online
                    _uiState.update { state ->
                        state.copy(friends = state.friends.map { friend ->
                            friend.copy(isOnline = friend.profile.uid in online)
                        })
                    }
                }
        }
    }

    private fun loadLeaderboard() {
        if (uiState.value.isOfflineGuest) return
        leaderboardJob?.cancel()
        val weekly = uiState.value.leaderboardWeekly
        leaderboardJob = viewModelScope.launch {
            users.observeLeaderboard(weekly)
                .catch { /* Keep the last successful board while offline. */ }
                .collect { board -> _uiState.update { it.copy(leaderboard = board) } }
        }
    }

    private fun loadFriends() {
        val uid = uiState.value.profile?.uid ?: return
        if (uiState.value.isOfflineGuest) return
        viewModelScope.launch {
            runCatching { users.friends(uid) }
                .onSuccess { list ->
                    _uiState.update { state ->
                        state.copy(friends = list.map { it.copy(isOnline = it.profile.uid in onlineUserIds) })
                    }
                }
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching { block() }.onFailure { showToast(it.message ?: "Something went wrong", ToastKind.WARNING) }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun showToast(message: String, kind: ToastKind = ToastKind.DEFAULT) {
        val toast = BattleToast(System.nanoTime(), message, kind)
        _uiState.update { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(1_750)
            _uiState.update { state -> if (state.toast?.id == toast.id) state.copy(toast = null) else state }
        }
    }

    private fun pauseSocialRealtime() {
        presenceJob?.cancel(); presenceJob = null
        leaderboardJob?.cancel(); leaderboardJob = null
        onlineUserIds = emptySet()
    }

    private fun stopRealtime() {
        roomJob?.cancel(); roomJob = null
        gameJob?.cancel(); gameJob = null
        turnTimerJob?.cancel(); turnTimerJob = null
    }

    override fun onCleared() {
        stopRealtime()
        presenceJob?.cancel()
        leaderboardJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val TURN_TIMEOUT_SECONDS = 45
    }
}
