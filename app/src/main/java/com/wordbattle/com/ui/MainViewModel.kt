package com.wordbattle.com.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordbattle.com.R
import com.wordbattle.com.WordBattleApplication
import com.wordbattle.com.data.audio.GameSound
import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.game.ComputerAI
import com.wordbattle.com.data.game.ProfileRules
import com.wordbattle.com.data.game.RoomManager
import com.wordbattle.com.data.model.AppErrorCode
import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.JoinRoomResult
import com.wordbattle.com.data.model.PlacementOutcome
import com.wordbattle.com.data.model.PlacementResult
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.data.model.appErrorCode
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
    private val network = container.network
    private val sound = container.sound

    private val _uiState = MutableStateFlow(MainUiState(language = AppLanguage.current()))
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
    private val celebratedGameIds = mutableSetOf<String>()

    /** Ids of the Realtime subscriptions to rebuild once the internet comes back. */
    private var subscribedRoomId: String? = null
    private var subscribedGameId: String? = null

    /** Action to replay when the user taps Retry in the offline dialog. */
    private var pendingOnlineAction: (() -> Unit)? = null

    init {
        initialize()
        observeConnectivity()
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
            val profile = uid?.let { runCatching { users.getProfile(it) }.getOrNull() }
                ?: uid?.let { UserProfile(it, getApplication<Application>().getString(R.string.profile_default_name)) }
            _uiState.update {
                it.copy(rootScreen = landingScreenFor(profile), profile = profile)
            }
            refreshHomeData()
        } else {
            _uiState.update { it.copy(rootScreen = RootScreen.LOGIN) }
        }
    }

    /**
     * Mirrors [com.wordbattle.com.data.network.NetworkConnectivityObserver] into the UI state and
     * repairs online sessions: Realtime subscriptions are rebuilt as soon as the link is back.
     */
    private fun observeConnectivity() {
        network.start(viewModelScope)
        viewModelScope.launch {
            network.isOnline.collect { online ->
                val wasOnline = uiState.value.isOnline
                _uiState.update {
                    it.copy(
                        isOnline = online,
                        showOfflineDialog = if (online) false else it.showOfflineDialog,
                        isReconnecting = !online && it.hasLiveOnlineSession()
                    )
                }
                if (online && !wasOnline) resubscribeRealtime()
            }
        }
    }

    private fun MainUiState.hasLiveOnlineSession(): Boolean =
        rootScreen in setOf(RootScreen.ROOM_SETUP, RootScreen.LOBBY, RootScreen.GAME) &&
            (room != null || game?.mode == GameMode.MIXED_ONLINE)

    private fun resubscribeRealtime() {
        val state = uiState.value
        if (!state.hasLiveOnlineSession()) {
            _uiState.update { it.copy(isReconnecting = false) }
            return
        }
        subscribedRoomId?.let { observeRoom(it) }
        subscribedGameId?.let { observeGame(it) }
        if (!state.isOfflineGuest) {
            loadLeaderboard()
            loadFriends()
        }
        _uiState.update { it.copy(isReconnecting = false) }
        showToast(UiText.Res(R.string.toast_back_online), ToastKind.SUCCESS)
    }

    /** Runs [block] only when there is internet, otherwise shows the offline dialog. */
    private fun requireOnline(block: () -> Unit) {
        if (network.refresh()) {
            block()
        } else {
            pendingOnlineAction = block
            _uiState.update { it.copy(isOnline = false, showOfflineDialog = true) }
        }
    }

    fun dismissOfflineDialog() {
        pendingOnlineAction = null
        _uiState.update { it.copy(showOfflineDialog = false) }
    }

    /** Retry button of the offline dialog: re-check the link and replay the blocked action. */
    fun retryAfterOffline() {
        val online = network.refresh()
        _uiState.update { it.copy(isOnline = online, showOfflineDialog = !online) }
        if (online) {
            val action = pendingOnlineAction
            pendingOnlineAction = null
            action?.invoke()
        }
    }

    fun signInWithGoogle(context: Context) = requireOnline {
        launchBusy {
            val profile = auth.signInWithGoogle(context)
            onSignedIn(profile)
        }
    }

    /**
     * Single email entry point: sign in, and create the account transparently when it does not
     * exist yet. There is no separate "register" mode in the UI any more.
     */
    fun signInWithEmail(email: String, password: String) = requireOnline {
        launchBusy {
            val profile = auth.signInOrSignUpWithEmail(email, password)
            onSignedIn(profile)
        }
    }

    private suspend fun onSignedIn(profile: UserProfile) {
        _uiState.update {
            it.copy(
                rootScreen = landingScreenFor(profile),
                profile = profile,
                isOfflineGuest = false,
                identityEditing = false
            )
        }
        showToast(UiText.of(R.string.toast_welcome, profile.displayName), ToastKind.SUCCESS)
        refreshHomeData()
    }

    /** First login sends the user to the identity screen until they own a unique username. */
    private fun landingScreenFor(profile: UserProfile?): RootScreen =
        if (profile != null && !profile.hasIdentity) RootScreen.IDENTITY else RootScreen.MAIN

    fun continueOffline() {
        val guest = UserProfile(
            "guest-${UUID.randomUUID()}",
            getApplication<Application>().getString(R.string.profile_guest_name),
            coins = 250,
            level = 1
        )
        _uiState.update { it.copy(rootScreen = RootScreen.MAIN, profile = guest, isOfflineGuest = true) }
        showToast(UiText.Res(R.string.toast_offline_ready), ToastKind.SUCCESS)
    }

    fun selectMainTab(tab: MainTab) {
        when (tab) {
            MainTab.RANK -> requireOnlineTab(tab) { loadLeaderboard() }
            MainTab.FRIENDS -> requireOnlineTab(tab) { loadFriends() }
            else -> _uiState.update { it.copy(mainTab = tab) }
        }
    }

    /** Rank and Friends need the network; guests and offline devices stay on the current tab. */
    private fun requireOnlineTab(tab: MainTab, load: () -> Unit) {
        if (uiState.value.isOfflineGuest) {
            _uiState.update { it.copy(mainTab = tab) }
            return
        }
        requireOnline {
            _uiState.update { it.copy(mainTab = tab) }
            load()
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
        // A fully local pass-and-play battle must keep working with no network at all.
        if (state.onlineSlots.isEmpty()) {
            val game = games.createLocalGame(
                state.assignmentPlayerCount,
                state.profile?.displayName
                    ?: getApplication<Application>().getString(R.string.assignment_player, 1)
            )
            enterLocalGame(game, game.players.map { it.id }.toSet())
            return
        }
        if (state.isOfflineGuest || state.profile == null) {
            showToast(UiText.Res(R.string.toast_sign_in_online_room), ToastKind.WARNING)
            return
        }
        requireOnline {
            val profile = uiState.value.profile ?: return@requireOnline
            val localCount = state.assignmentPlayerCount - state.onlineSlots.size
            val slotError = RoomManager.validateSlots(localCount, state.onlineSlots.size)
            if (slotError != null) {
                showToast(AppErrorCode.INVALID_SLOTS.asUiText(), ToastKind.WARNING)
                return@requireOnline
            }
            launchBusy {
                val room = rooms.createRoom(profile, localCount, state.onlineSlots.size)
                _uiState.update {
                    it.copy(
                        room = room,
                        rootScreen = RootScreen.ROOM_SETUP,
                        isHostDevice = true,
                        ownedPlayerIds = setOf(profile.uid)
                    )
                }
                observeRoom(room.roomId)
            }
        }
    }

    fun openJoinRoom() {
        if (uiState.value.isOfflineGuest) {
            showToast(UiText.Res(R.string.toast_sign_in_join), ToastKind.WARNING)
            return
        }
        requireOnline { _uiState.update { it.copy(rootScreen = RootScreen.JOIN_ROOM) } }
    }

    fun createQuickRoom() {
        val state = uiState.value
        if (state.isOfflineGuest || state.profile == null) {
            showToast(UiText.Res(R.string.toast_sign_in_create), ToastKind.WARNING)
            return
        }
        requireOnline {
            _uiState.update {
                it.copy(
                    rootScreen = RootScreen.ASSIGNMENT,
                    assignmentPlayerCount = 2,
                    selectedModePlayers = 2,
                    onlineSlots = setOf(1)
                )
            }
        }
    }

    fun joinRoom(code: String, passcode: String) {
        val profile = uiState.value.profile ?: return
        requireOnline {
            launchBusy {
                when (val result = rooms.joinRoom(code, passcode, profile)) {
                    is JoinRoomResult.Error -> showToast(result.code.asUiText(), ToastKind.WARNING)
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
    }

    fun setReady(ready: Boolean) {
        val state = uiState.value
        val uid = state.profile?.uid ?: return
        val roomId = state.room?.roomId ?: return
        requireOnline { launchBusy { rooms.setReady(roomId, uid, ready) } }
    }

    fun startHostedGame() {
        val state = uiState.value
        val uid = state.profile?.uid ?: return
        val room = state.room ?: return
        requireOnline {
            launchBusy {
                val game = rooms.startGame(room.roomId, uid)
                enterOnlineGame(game, isHost = true, roomLocalSlots = room.localSlotsCount)
            }
        }
    }

    fun selectLetter(letter: Char) {
        if (letter.uppercaseChar() !in 'A'..'Z') return
        val state = uiState.value
        val game = state.game ?: return
        // Read-only devices may look at the board but never pick a letter.
        if (!RoomManager.canPlay(game, state.ownedPlayerIds)) return
        _uiState.update { it.copy(selectedLetter = letter.uppercaseChar()) }
    }

    fun placeSelectedLetter(row: Int, col: Int) {
        val state = uiState.value
        val game = state.game ?: return
        val playerId = game.currentTurnPlayerId
        if (!RoomManager.canPlay(game, state.ownedPlayerIds)) {
            val playerName = RoomManager.waitingForName(game)
            showToast(
                if (playerName != null) UiText.of(R.string.game_waiting_for, playerName)
                else UiText.Res(R.string.game_waiting_for_player),
                ToastKind.WARNING
            )
            return
        }
        val letter = state.selectedLetter ?: run {
            showToast(UiText.Res(R.string.toast_pick_letter_first), ToastKind.WARNING); return
        }
        val result = games.placeLetter(game, playerId, row, col, letter)
        result.onFailure { showToast(it.toUiText(R.string.toast_move_rejected), ToastKind.WARNING) }
        result.onSuccess { placement ->
            _uiState.update { it.copy(game = placement.gameState, selectedLetter = null) }
            playPlacementSound(placement)
            showToast(placement.asUiText(), placement.toastKind())
            afterGameChanged(placement.gameState)
        }
    }

    fun skipCurrentTurn() {
        val state = uiState.value
        val game = state.game ?: return
        // Only the device owning the current turn may skip it — a host cannot skip for a remote player.
        if (!RoomManager.canPlay(game, state.ownedPlayerIds)) return
        games.skipTurn(game, game.currentTurnPlayerId).onSuccess {
            _uiState.update { state -> state.copy(game = it, selectedLetter = null) }
            showToast(UiText.Res(R.string.toast_turn_skipped), ToastKind.WARNING)
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
                }.onFailure {
                    _uiState.update { state -> state.copy(isReconnecting = true) }
                    showToast(UiText.Res(R.string.toast_move_saved_locally), ToastKind.WARNING)
                }
            }
        }
        if (game.status == GameStatus.FINISHED) {
            _uiState.update { it.copy(rootScreen = RootScreen.RESULTS) }
            playEndOfGameSound(game)
            reportFinishedGame(game)
            return
        }
        val next = game.players.firstOrNull { it.id == game.currentTurnPlayerId }
        if (next?.type == PlayerType.COMPUTER) runComputerTurn(game)
    }

    private fun startComputerGame() {
        val fallback = getApplication<Application>().getString(R.string.game_you)
        val game = games.createComputerGame(uiState.value.profile?.displayName ?: fallback)
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
                isReconnecting = false,
                selectedLetter = null
            )
        }
        celebratedGameIds -= game.gameId
        sound.startTheme()
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
        celebratedGameIds -= game.gameId
        sound.startTheme()
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
                playPlacementSound(placement)
                showToast(
                    UiText.of(R.string.toast_bot_move, resolve(placement.asUiText())),
                    if (placement.pointsAwarded > 0) ToastKind.SUCCESS else ToastKind.DEFAULT
                )
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
                playTimerSound(remaining)
                // Auto-skip only for a turn this device actually owns; remote turns time out remotely.
                if (remaining == 0 && RoomManager.canPlay(current, uiState.value.ownedPlayerIds)) {
                    skipCurrentTurn()
                }
            }
        }
    }

    /** Board feedback: a richer chime when the placement actually formed a new word. */
    private fun playPlacementSound(placement: PlacementResult) {
        sound.play(GameSound.LETTER_PLACED)
        if (placement.outcome == PlacementOutcome.SCORED) {
            viewModelScope.launch {
                delay(90)
                sound.play(GameSound.WORD_SCORED)
            }
        }
    }

    /** Ticks down the last seconds of a turn, with a sharper warning inside the final five. */
    private fun playTimerSound(remaining: Int) {
        when {
            remaining == 0 -> Unit
            remaining <= TIMER_WARNING_SECONDS -> sound.play(GameSound.TIMER_WARNING)
            remaining <= TIMER_TICK_SECONDS -> sound.play(GameSound.TIMER_TICK)
        }
    }

    /** Fanfare (or a soft consolation phrase) once the battle is over. */
    private fun playEndOfGameSound(game: GameState) {
        if (!celebratedGameIds.add(game.gameId)) return
        sound.stopTheme()
        val owned = uiState.value.ownedPlayerIds
        val didWin = game.players.any { it.rank == 1 && (it.id in owned || owned.isEmpty()) }
        sound.play(if (didWin) GameSound.VICTORY else GameSound.DEFEAT)
    }

    private fun observeRoom(roomId: String) {
        subscribedRoomId = roomId
        roomJob?.cancel()
        roomJob = viewModelScope.launch {
            rooms.observeRoom(roomId)
                .catch { onRealtimeDropped(R.string.toast_lobby_connection_lost) }
                .collect { room ->
                    _uiState.update { it.copy(room = room, isReconnecting = false) }
                    if (room.status == "in_progress" && room.gameId != null &&
                        uiState.value.rootScreen !in setOf(RootScreen.GAME, RootScreen.RESULTS)
                    ) {
                        runCatching { rooms.getGame(room.gameId) }.getOrNull()
                            ?.let { enterOnlineGame(it, uiState.value.isHostDevice, room.localSlotsCount) }
                    }
                }
        }
    }

    private fun observeGame(gameId: String) {
        subscribedGameId = gameId
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            rooms.observeGame(gameId)
                .catch { onRealtimeDropped(R.string.toast_game_connection_lost) }
                .collect { game ->
                    _uiState.update {
                        it.copy(
                            game = game,
                            isReconnecting = false,
                            rootScreen = if (game.status == GameStatus.FINISHED) RootScreen.RESULTS else RootScreen.GAME
                        )
                    }
                    if (game.status == GameStatus.FINISHED) {
                        playEndOfGameSound(game)
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

    /** A dropped Realtime stream shows the reconnecting banner; recovery happens on reconnect. */
    private fun onRealtimeDropped(messageRes: Int) {
        _uiState.update { it.copy(isReconnecting = true) }
        showToast(UiText.Res(messageRes), ToastKind.WARNING)
    }

    fun setLeaderboardWeekly(weekly: Boolean) {
        _uiState.update { it.copy(leaderboardWeekly = weekly) }
        loadLeaderboard()
    }

    fun searchFriends(query: String) {
        val uid = uiState.value.profile?.uid ?: return
        if (uiState.value.isOfflineGuest || !uiState.value.isOnline) return
        viewModelScope.launch {
            val results = runCatching { users.searchProfiles(query, uid) }.getOrElse { emptyList() }
            _uiState.update { it.copy(friendSearchResults = results) }
        }
    }

    fun addFriend(profile: UserProfile) {
        val uid = uiState.value.profile?.uid ?: return
        requireOnline {
            launchBusy {
                users.addFriend(uid, profile.uid)
                showToast(UiText.Res(R.string.toast_friend_request_sent), ToastKind.SUCCESS)
                _uiState.update { it.copy(friendSearchResults = emptyList()) }
                loadFriends()
            }
        }
    }

    fun acceptFriend(friend: FriendProfile) {
        val uid = uiState.value.profile?.uid ?: return
        requireOnline {
            launchBusy {
                users.acceptFriend(uid, friend.profile.uid)
                showToast(UiText.Res(R.string.toast_friend_added), ToastKind.SUCCESS)
                loadFriends()
            }
        }
    }

    fun inviteFriend(friend: FriendProfile) {
        createQuickRoom()
        showToast(UiText.of(R.string.toast_invite_hint, friend.profile.displayName))
    }

    fun toggleSound() {
        val enabled = !uiState.value.soundEnabled
        sound.enabled = enabled
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun toggleNotifications() = _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }

    /** Applies the locale immediately (AppCompat recreates the activity) and remembers it. */
    fun setLanguage(language: AppLanguage) {
        _uiState.update { it.copy(language = language) }
        AppLanguage.apply(language)
    }

    fun openIdentityEditor() {
        _uiState.update { it.copy(rootScreen = RootScreen.IDENTITY, identityEditing = true) }
    }

    /** Saves the display name + unique username chosen on the first-login/identity screen. */
    fun saveIdentity(displayName: String, username: String) {
        val profile = uiState.value.profile ?: return
        if (uiState.value.isOfflineGuest) {
            _uiState.update { it.copy(rootScreen = RootScreen.MAIN, identityEditing = false) }
            return
        }
        requireOnline {
            launchBusy {
                val updated = users.updateIdentity(profile, displayName, username)
                _uiState.update {
                    it.copy(profile = updated, rootScreen = RootScreen.MAIN, identityEditing = false)
                }
                showToast(UiText.Res(R.string.toast_profile_saved), ToastKind.SUCCESS)
            }
        }
    }

    /** Whole days left before the display name may change again (0 = allowed now). */
    fun displayNameCooldownDays(): Int =
        ProfileRules.cooldownDaysRemaining(uiState.value.profile?.displayNameUpdatedAt)

    fun playAgain() {
        val game = uiState.value.game ?: return
        if (game.mode == GameMode.MIXED_ONLINE) {
            goHome()
            showToast(UiText.Res(R.string.toast_rematch_new_room))
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
            RootScreen.IDENTITY ->
                if (uiState.value.identityEditing) {
                    _uiState.update { it.copy(rootScreen = RootScreen.MAIN, identityEditing = false) }
                } else Unit // first login must complete the identity step
            RootScreen.ROOM_SETUP, RootScreen.LOBBY -> {
                stopRealtime()
                _uiState.update { it.copy(rootScreen = RootScreen.MAIN, room = null, isReconnecting = false) }
            }
            RootScreen.GAME, RootScreen.RESULTS -> goHome()
            else -> Unit
        }
    }

    fun goHome() {
        stopRealtime()
        sound.stopTheme()
        _uiState.update {
            it.copy(
                rootScreen = RootScreen.MAIN,
                mainTab = MainTab.HOME,
                game = null,
                room = null,
                selectedLetter = null,
                isReconnecting = false
            )
        }
        refreshHomeData()
    }

    fun logout() = viewModelScope.launch {
        stopRealtime()
        sound.stopTheme()
        pauseSocialRealtime()
        if (!uiState.value.isOfflineGuest) runCatching { auth.signOut() }
        _uiState.value = MainUiState(
            rootScreen = RootScreen.LOGIN,
            language = uiState.value.language,
            isOnline = network.isCurrentlyOnline
        )
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
        if (!uiState.value.isOfflineGuest && uiState.value.isOnline) {
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
        if (uiState.value.isOfflineGuest || !uiState.value.isOnline) return
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
        if (uiState.value.isOfflineGuest || !uiState.value.isOnline) return
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
            runCatching { block() }.onFailure { failure ->
                val code = failure.appErrorCode()
                if (code == AppErrorCode.NO_INTERNET) {
                    _uiState.update { it.copy(isOnline = false, showOfflineDialog = true) }
                }
                showToast(code.asUiText(), ToastKind.WARNING)
            }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun showToast(text: UiText, kind: ToastKind = ToastKind.DEFAULT) {
        val toast = BattleToast(System.nanoTime(), text, kind)
        _uiState.update { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(1_750)
            _uiState.update { state -> if (state.toast?.id == toast.id) state.copy(toast = null) else state }
        }
    }

    /** Resolves a [UiText] with the application context, used when composing a longer message. */
    private fun resolve(text: UiText): String = text.resolve(getApplication())

    private fun Throwable.toUiText(fallbackRes: Int): UiText {
        val code = appErrorCode()
        return if (code == AppErrorCode.UNKNOWN) UiText.Res(fallbackRes) else code.asUiText()
    }

    private fun PlacementResult.asUiText(): UiText = when (outcome) {
        PlacementOutcome.SCORED -> UiText.of(
            R.string.placement_scored,
            pointsAwarded,
            newWords.joinToString(" + ")
        )
        // Every placement still banks a point, so the toast always leads with the score.
        PlacementOutcome.REPEATED_WORD -> UiText.of(R.string.placement_repeated, pointsAwarded)
        PlacementOutcome.NO_WORD -> UiText.of(R.string.placement_no_word, pointsAwarded)
        PlacementOutcome.LETTER_PLACED -> UiText.of(R.string.placement_letter_placed, pointsAwarded)
    }

    private fun PlacementResult.toastKind(): ToastKind = when {
        pointsAwarded > 0 -> ToastKind.SUCCESS
        repeatedWords.isNotEmpty() -> ToastKind.WARNING
        else -> ToastKind.DEFAULT
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
        subscribedRoomId = null
        subscribedGameId = null
    }

    override fun onCleared() {
        stopRealtime()
        sound.stopTheme()
        presenceJob?.cancel()
        leaderboardJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val TURN_TIMEOUT_SECONDS = 45

        /** Below this many seconds the turn timer starts ticking audibly. */
        const val TIMER_TICK_SECONDS = 10

        /** Below this many seconds the tick becomes an urgent warning beep. */
        const val TIMER_WARNING_SECONDS = 5
    }
}
