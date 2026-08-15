package com.wordbattle.com.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordbattle.com.R
import com.wordbattle.com.WordBattleApplication
import com.wordbattle.com.data.audio.GameSound
import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.game.AiDifficulty
import com.wordbattle.com.data.game.CampaignLevelCatalog
import com.wordbattle.com.data.game.CampaignRules
import com.wordbattle.com.data.game.ComputerAI
import com.wordbattle.com.data.game.ProfileRules
import com.wordbattle.com.data.game.PuzzleEngine
import com.wordbattle.com.data.game.RoomManager
import com.wordbattle.com.data.model.AppErrorCode
import com.wordbattle.com.data.model.CampaignProgress
import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameRequest
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.JoinRoomResult
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.LevelType
import com.wordbattle.com.data.model.PlacementOutcome
import com.wordbattle.com.data.model.PlacementResult
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.RequestStatus
import com.wordbattle.com.data.model.RequestType
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
import java.time.Instant
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as WordBattleApplication).container
    private val auth = container.authRepository
    private val users = container.userRepository
    private val rooms = container.roomRepository
    private val campaignRepo = container.campaignRepository
    private val requestRepo = container.requestRepository
    private val messageRepo = container.messageRepository
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
    private var puzzleTimerJob: Job? = null
    private var toastJob: Job? = null
    private val reportedGameIds = mutableSetOf<String>()
    private val celebratedGameIds = mutableSetOf<String>()

    private var subscribedRoomId: String? = null
    private var subscribedGameId: String? = null
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
            // Lives regen on app open (time-based, no running timer)
            val regenProfile = profile?.let { refreshLivesIfNeeded(it) }
            _uiState.update {
                it.copy(
                    rootScreen = landingScreenFor(regenProfile),
                    profile = regenProfile,
                    campaignLevels = CampaignLevelCatalog.allLevels()
                )
            }
            refreshHomeData()
        } else {
            _uiState.update {
                it.copy(
                    rootScreen = RootScreen.LOGIN,
                    campaignLevels = CampaignLevelCatalog.allLevels()
                )
            }
        }
    }

    private suspend fun refreshLivesIfNeeded(profile: UserProfile): UserProfile {
        return try {
            val now = Instant.now()
            val result = CampaignRules.regenLives(profile.livesCurrent, profile.livesMax, profile.lastLifeRegenAt, now)
            if (result.regenerated > 0) {
                users.updateLives(profile.uid, result.newCurrent, result.newLastRegenAtIso)
            } else profile
        } catch (_: Exception) {
            profile
        }
    }

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
            loadRequests()
            loadMessages()
        }
        _uiState.update { it.copy(isReconnecting = false) }
        showToast(UiText.Res(R.string.toast_back_online), ToastKind.SUCCESS)
    }

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

    fun signInWithEmail(email: String, password: String) = requireOnline {
        launchBusy {
            val profile = auth.signInOrSignUpWithEmail(email, password)
            onSignedIn(profile)
        }
    }

    private suspend fun onSignedIn(profile: UserProfile) {
        val regenProfile = refreshLivesIfNeeded(profile)
        _uiState.update {
            it.copy(
                rootScreen = landingScreenFor(regenProfile),
                profile = regenProfile,
                isOfflineGuest = false,
                identityEditing = false,
                campaignLevels = CampaignLevelCatalog.allLevels()
            )
        }
        showToast(UiText.of(R.string.toast_welcome, regenProfile.displayName), ToastKind.SUCCESS)
        refreshHomeData()
    }

    private fun landingScreenFor(profile: UserProfile?): RootScreen =
        if (profile != null && !profile.hasIdentity) RootScreen.IDENTITY else RootScreen.MAIN

    fun continueOffline() {
        val guest = UserProfile(
            "guest-${UUID.randomUUID()}",
            getApplication<Application>().getString(R.string.profile_guest_name),
            coins = 250,
            level = 1
        )
        _uiState.update {
            it.copy(
                rootScreen = RootScreen.MAIN,
                profile = guest,
                isOfflineGuest = true,
                campaignLevels = CampaignLevelCatalog.allLevels()
            )
        }
        showToast(UiText.Res(R.string.toast_offline_ready), ToastKind.SUCCESS)
    }

    fun selectMainTab(tab: MainTab) {
        when (tab) {
            MainTab.RANK -> requireOnlineTab(tab) { loadLeaderboard() }
            MainTab.FRIENDS -> requireOnlineTab(tab) {
                loadFriends()
                loadRequests()
                loadMessages()
            }
            else -> _uiState.update { it.copy(mainTab = tab) }
        }
    }

    fun selectFriendsTab(tab: FriendsTab) {
        _uiState.update { it.copy(friendsTab = tab) }
        when (tab) {
            FriendsTab.REQUEST -> loadRequests()
            FriendsTab.MESSAGE -> loadMessages()
            else -> {}
        }
    }

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

    fun openCampaign() {
        // Refresh lives and campaign levels
        viewModelScope.launch {
            val profile = uiState.value.profile
            if (profile != null) {
                val refreshed = refreshLivesIfNeeded(profile)
                _uiState.update { it.copy(profile = refreshed) }
                loadCampaignProgress()
            }
            _uiState.update { it.copy(rootScreen = RootScreen.LEVEL_SELECT, campaignLevels = CampaignLevelCatalog.allLevels()) }
        }
    }

    fun selectCampaignLevel(level: LevelDefinition) {
        val profile = uiState.value.profile
        val unlocked = profile?.campaignLevel ?: 1
        if (level.levelNumber > unlocked) {
            showToast(UiText.Res(R.string.error_unknown), ToastKind.WARNING)
            return
        }
        // Lives check for puzzle
        if (level.type == LevelType.PUZZLE_FILL) {
            val lives = profile?.livesCurrent ?: 3
            if (!CampaignRules.canEnterPuzzle(lives)) {
                // Calculate countdown for bottom sheet
                val last = profile?.lastLifeRegenAt
                val now = Instant.now()
                val lastInstant = ProfileRules.parseInstant(last) ?: now
                val elapsedMin = java.time.Duration.between(lastInstant, now).toMinutes()
                val remaining = (20 - (elapsedMin % 20)).coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        showLifeBottomSheet = true,
                        lifeRegenCountdownMinutes = remaining,
                        selectedLevel = level
                    )
                }
                return
            }
        }
        startCampaignLevel(level)
    }

    fun startCampaignLevel(level: LevelDefinition) {
        _uiState.update { it.copy(selectedLevel = level) }
        when (level.type) {
            LevelType.SCORE_ATTACK -> {
                val game = games.createCampaignScoreGame(level, uiState.value.profile?.displayName ?: "You")
                // AI difficulty from level
                ai = ComputerAI(dictionary, difficulty = level.aiDifficulty ?: AiDifficulty.HARD)
                enterLocalGame(game, setOf(game.players.first().id))
            }
            LevelType.PUZZLE_FILL -> {
                val puzzle = PuzzleEngine.fromDefinition(level)
                _uiState.update {
                    it.copy(
                        puzzleState = puzzle,
                        puzzleElapsedSeconds = 0,
                        puzzleIsRunning = false,
                        puzzleWrongCells = emptySet(),
                        rootScreen = RootScreen.PUZZLE_GAME
                    )
                }
                // Stop theme for puzzle? Keep same
            }
        }
    }

    fun dismissLifeBottomSheet() {
        _uiState.update { it.copy(showLifeBottomSheet = false) }
    }

    fun buyLife() {
        val profile = uiState.value.profile ?: return
        viewModelScope.launch {
            try {
                val updated = users.purchaseLife(profile.uid, profile.livesCurrent, profile.livesMax, profile.coins)
                _uiState.update { it.copy(profile = updated, showLifeBottomSheet = false) }
                showToast(UiText.Raw("Life purchased"), ToastKind.SUCCESS)
            } catch (e: Exception) {
                showToast(UiText.Res(R.string.toast_something_wrong), ToastKind.WARNING)
            }
        }
    }

    fun requestLifeFromFriend(friendId: String) {
        val uid = uiState.value.profile?.uid ?: return
        requireOnline {
            viewModelScope.launch {
                try {
                    requestRepo.sendRequest(RequestType.LIFE, uid, friendId)
                    showToast(UiText.Res(R.string.toast_friend_request_sent), ToastKind.SUCCESS)
                    _uiState.update { it.copy(showLifeBottomSheet = false) }
                } catch (e: Exception) {
                    showToast(e.toUiText(R.string.toast_something_wrong), ToastKind.WARNING)
                }
            }
        }
    }

    // Puzzle interaction
    fun puzzleSelectLetter(letter: Char) {
        _uiState.update { it.copy(selectedLetter = letter.uppercaseChar()) }
    }

    fun puzzlePlaceLetter(row: Int, col: Int) {
        val state = uiState.value
        val puzzle = state.puzzleState ?: return
        val letter = state.selectedLetter ?: run {
            showToast(UiText.Res(R.string.toast_pick_letter_first), ToastKind.WARNING)
            return
        }
        val newPuzzle = PuzzleEngine.withLetter(puzzle, row, col, letter)
        // Start timer on first placement
        if (!state.puzzleIsRunning) {
            startPuzzleTimer()
            _uiState.update { it.copy(puzzleIsRunning = true) }
        }

        // Check wrong guess – if completed line invalid
        val isWrong = PuzzleEngine.isWrongGuess(newPuzzle, row, col, dictionary)
        var wrongCells = state.puzzleWrongCells
        var livesProfile = state.profile

        if (isWrong) {
            wrongCells = wrongCells + (row to col)
            // Consume life
            val profile = state.profile
            if (profile != null) {
                viewModelScope.launch {
                    val updated = users.consumeLife(profile.uid, profile.livesCurrent)
                    _uiState.update { it.copy(profile = updated) }
                    // If lives now 0, block further?
                    if (updated.livesCurrent <= 0) {
                        _uiState.update { it.copy(showLifeBottomSheet = true) }
                    }
                }
            }
            showToast(UiText.of(R.string.toast_move_rejected), ToastKind.WARNING)
        }

        _uiState.update {
            it.copy(
                puzzleState = newPuzzle,
                puzzleWrongCells = wrongCells,
                selectedLetter = null
            )
        }

        // Check solved
        if (PuzzleEngine.isSolved(newPuzzle, dictionary)) {
            stopPuzzleTimer()
            val elapsed = uiState.value.puzzleElapsedSeconds
            val level = uiState.value.selectedLevel ?: return
            val stars = CampaignRules.starsForPuzzle(level, elapsed)
            onCampaignLevelCompleted(level, stars, elapsed, null)
        }
    }

    private fun startPuzzleTimer() {
        puzzleTimerJob?.cancel()
        puzzleTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(puzzleElapsedSeconds = it.puzzleElapsedSeconds + 1) }
            }
        }
    }

    private fun stopPuzzleTimer() {
        puzzleTimerJob?.cancel()
        puzzleTimerJob = null
        _uiState.update { it.copy(puzzleIsRunning = false) }
    }

    private fun onCampaignLevelCompleted(
        level: LevelDefinition,
        stars: Int,
        elapsedSeconds: Int?,
        turnsUsed: Int?
    ) {
        val profile = uiState.value.profile ?: return
        val existingProgress = uiState.value.campaignProgress.firstOrNull { it.levelNumber == level.levelNumber }
        val isFirst = existingProgress == null
        val newCampaignLevel = CampaignRules.nextCampaignLevel(profile.campaignLevel, level.levelNumber, true)
        val shouldSaveStars = CampaignRules.shouldSaveStars(existingProgress?.stars, stars)
        val coinsReward = CampaignRules.coinsRewardForFirstCompletion(stars, isFirst)

        viewModelScope.launch {
            try {
                // Save progress locally and remotely
                campaignRepo.saveProgress(profile.uid, level.levelNumber, if (shouldSaveStars) stars else existingProgress?.stars ?: stars, elapsedSeconds, turnsUsed)
                val totalStars = if (shouldSaveStars) {
                    val currentTotal = profile.campaignStarsTotal
                    val diff = stars - (existingProgress?.stars ?: 0)
                    currentTotal + diff.coerceAtLeast(0)
                } else profile.campaignStarsTotal

                val updatedProfile = users.updateCampaignProgress(
                    profile.uid,
                    newCampaignLevel,
                    totalStars,
                    coinsReward,
                    profile.coins
                )
                _uiState.update {
                    it.copy(
                        profile = updatedProfile,
                        campaignProgress = it.campaignProgress.toMutableList().apply {
                            val idx = indexOfFirst { p -> p.levelNumber == level.levelNumber }
                            if (idx >= 0) this[idx] = CampaignProgress(level.levelNumber, stars, elapsedSeconds, turnsUsed)
                            else add(CampaignProgress(level.levelNumber, stars, elapsedSeconds, turnsUsed))
                        }
                    )
                }
                // Go to results
                _uiState.update { it.copy(rootScreen = RootScreen.RESULTS) }
                showToast(UiText.Raw("Level ${level.levelNumber} cleared! $stars★"), ToastKind.SUCCESS)
            } catch (e: Exception) {
                showToast(e.toUiText(R.string.toast_something_wrong), ToastKind.WARNING)
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
        if (!RoomManager.canPlay(game, state.ownedPlayerIds) && game.mode != GameMode.CAMPAIGN_SCORE) return
        _uiState.update { it.copy(selectedLetter = letter.uppercaseChar()) }
    }

    fun placeSelectedLetter(row: Int, col: Int) {
        val state = uiState.value
        val game = state.game ?: return
        val playerId = game.currentTurnPlayerId
        // Campaign score allows play even if not in owned? Simplify: owner check same as before except campaign
        if (game.mode != GameMode.CAMPAIGN_SCORE && !RoomManager.canPlay(game, state.ownedPlayerIds)) {
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

            // Campaign level completion handling for SCORE_ATTACK
            if (placement.gameState.status == GameStatus.FINISHED && placement.gameState.mode == GameMode.CAMPAIGN_SCORE) {
                val level = state.selectedLevel
                if (level != null) {
                    val turns = placement.gameState.playerTurnsUsed
                    val stars = CampaignRules.starsForScoreAttack(level, turns)
                    onCampaignLevelCompleted(level, stars, null, turns)
                    return
                }
            }
            if (placement.gameState.status == GameStatus.LEVEL_FAILED) {
                _uiState.update { it.copy(rootScreen = RootScreen.RESULTS) }
                showToast(UiText.Res(R.string.toast_move_rejected), ToastKind.WARNING)
                return
            }

            afterGameChanged(placement.gameState)
        }
    }

    fun skipCurrentTurn() {
        val state = uiState.value
        val game = state.game ?: return
        if (game.mode != GameMode.CAMPAIGN_SCORE && !RoomManager.canPlay(game, state.ownedPlayerIds)) return
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
        // Default HARD stays
        ai = ComputerAI(dictionary, difficulty = AiDifficulty.HARD)
        enterLocalGame(game, setOf(game.players.first().id))
    }

    private fun enterLocalGame(game: GameState, ownedIds: Set<String>, campaign: Boolean = false) {
        stopRealtime()
        pauseSocialRealtime()
        _uiState.update {
            it.copy(
                rootScreen = if (campaign || game.mode == GameMode.CAMPAIGN_SCORE) RootScreen.GAME else RootScreen.GAME,
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
            if (current.gameId != expected.gameId || current.currentTurnPlayerId != "computer" && current.currentTurnPlayerId != "campaign-ai") return@launch

            // Use level difficulty if campaign
            val levelAi = if (current.mode == GameMode.CAMPAIGN_SCORE) {
                val lvl = uiState.value.selectedLevel
                ComputerAI(dictionary, difficulty = lvl?.aiDifficulty ?: AiDifficulty.HARD)
            } else ai

            val move = levelAi.chooseMove(current) ?: return@launch
            games.placeLetter(current, move.row, move.col, move.letter, current.currentTurnPlayerId).let { res ->
                // For campaign, we need to wrap result similar to placeSelectedLetter handling
                res.onSuccess { placement ->
                    var gameState = placement.gameState
                    // Handle campaign turn counting for AI? AI turns not counted, so keep same turns
                    if (current.mode == GameMode.CAMPAIGN_SCORE) {
                        // AI move does not increment human turns, preserve
                        gameState = gameState.copy(playerTurnsUsed = current.playerTurnsUsed)
                    }
                    _uiState.update { it.copy(game = gameState) }
                    playPlacementSound(placement)
                    showToast(
                        UiText.of(R.string.toast_bot_move, resolve(placement.asUiText())),
                        if (placement.pointsAwarded > 0) ToastKind.SUCCESS else ToastKind.DEFAULT
                    )
                    // Check level failed for campaign? Failure only based on human turns, so not here
                    afterGameChanged(gameState)
                }
            }
        }
    }

    private fun startTurnTimer(game: GameState) {
        turnTimerJob?.cancel()
        // Use level's turnTimeSeconds if campaign, else default 45
        val timeout = if (game.mode == GameMode.CAMPAIGN_SCORE) {
            uiState.value.selectedLevel?.turnTimeSeconds ?: TURN_TIMEOUT_SECONDS
        } else TURN_TIMEOUT_SECONDS

        if (timeout <= 0) {
            _uiState.update { it.copy(turnSecondsRemaining = 0) }
            return
        }

        _uiState.update { it.copy(turnSecondsRemaining = timeout) }
        turnTimerJob = viewModelScope.launch {
            for (remaining in timeout - 1 downTo 0) {
                delay(1_000)
                val current = uiState.value.game ?: return@launch
                if (current.gameId != game.gameId || current.currentTurnPlayerId != game.currentTurnPlayerId) return@launch
                _uiState.update { it.copy(turnSecondsRemaining = remaining) }
                playTimerSound(remaining)
                if (remaining == 0 && (RoomManager.canPlay(current, uiState.value.ownedPlayerIds) || current.mode == GameMode.CAMPAIGN_SCORE)) {
                    skipCurrentTurn()
                }
            }
        }
    }

    private fun playPlacementSound(placement: PlacementResult) {
        sound.play(GameSound.LETTER_PLACED)
        if (placement.outcome == PlacementOutcome.SCORED) {
            viewModelScope.launch {
                delay(90)
                sound.play(GameSound.WORD_SCORED)
            }
        }
    }

    private fun playTimerSound(remaining: Int) {
        when {
            remaining == 0 -> Unit
            remaining <= TIMER_WARNING_SECONDS -> sound.play(GameSound.TIMER_WARNING)
            remaining <= TIMER_TICK_SECONDS -> sound.play(GameSound.TIMER_TICK)
        }
    }

    private fun playEndOfGameSound(game: GameState) {
        if (!celebratedGameIds.add(game.gameId)) return
        sound.stopTheme()
        val owned = uiState.value.ownedPlayerIds
        val didWin = game.players.any { it.rank == 1 && (it.id in owned || owned.isEmpty()) } || game.status == GameStatus.FINISHED
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
                            rootScreen = if (game.status == GameStatus.FINISHED || game.status == GameStatus.LEVEL_FAILED) RootScreen.RESULTS else RootScreen.GAME
                        )
                    }
                    if (game.status == GameStatus.FINISHED || game.status == GameStatus.LEVEL_FAILED) {
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

    // --- New: Requests & Messages ---

    fun loadRequests() {
        val uid = uiState.value.profile?.uid ?: return
        if (uiState.value.isOfflineGuest || !uiState.value.isOnline) return
        viewModelScope.launch {
            val reqs = runCatching { requestRepo.getAllForUser(uid) }.getOrDefault(emptyList())
            _uiState.update { it.copy(requests = reqs) }
        }
    }

    fun loadMessages() {
        val uid = uiState.value.profile?.uid ?: return
        if (uiState.value.isOfflineGuest || !uiState.value.isOnline) return
        viewModelScope.launch {
            val msgs = runCatching { messageRepo.getConversations(uid) }.getOrDefault(emptyList())
            _uiState.update { it.copy(messages = msgs) }
        }
    }

    fun loadCampaignProgress() {
        val uid = uiState.value.profile?.uid ?: return
        viewModelScope.launch {
            val prog = runCatching { campaignRepo.getProgress(uid) }.getOrDefault(emptyList())
            _uiState.update { it.copy(campaignProgress = prog) }
        }
    }

    fun openThread(friend: UserProfile) {
        val uid = uiState.value.profile?.uid ?: return
        viewModelScope.launch {
            val thread = runCatching { messageRepo.getThread(uid, friend.uid) }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    messageThread = thread,
                    selectedThreadFriend = friend,
                    rootScreen = RootScreen.MESSAGE_THREAD
                )
            }
        }
    }

    fun openThreadFromMessage(msg: ChatMessage) {
        val uid = uiState.value.profile?.uid ?: return
        val otherId = if (msg.senderId == uid) msg.receiverId else msg.senderId
        viewModelScope.launch {
            val profile = runCatching { users.getProfile(otherId) }.getOrNull()
            val friend = profile ?: UserProfile(otherId, "Player")
            openThread(friend)
        }
    }

    fun sendMessage(body: String) {
        val uid = uiState.value.profile?.uid ?: return
        val friend = uiState.value.selectedThreadFriend ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            try {
                val sent = messageRepo.sendMessage(uid, friend.uid, body)
                _uiState.update { it.copy(messageThread = it.messageThread + sent) }
            } catch (e: Exception) {
                showToast(e.toUiText(R.string.toast_something_wrong), ToastKind.WARNING)
            }
        }
    }

    fun acceptRequest(req: GameRequest) {
        viewModelScope.launch {
            try {
                requestRepo.updateStatus(req.id, RequestStatus.accepted)
                loadRequests()
                showToast(UiText.Res(R.string.toast_friend_added), ToastKind.SUCCESS)
            } catch (e: Exception) {
                showToast(e.toUiText(R.string.toast_something_wrong), ToastKind.WARNING)
            }
        }
    }

    fun declineRequest(req: GameRequest) {
        viewModelScope.launch {
            try {
                requestRepo.updateStatus(req.id, RequestStatus.declined)
                loadRequests()
            } catch (e: Exception) {
                showToast(e.toUiText(R.string.toast_something_wrong), ToastKind.WARNING)
            }
        }
    }

    fun sendLifeForRequest(req: GameRequest) {
        // LIFE request: accept it, trigger server-side life+coin reward
        acceptRequest(req)
    }

    fun acceptGameInvite(req: GameRequest) {
        // payload contains roomCode/passcode
        // For now just show toast and try to join if info present
        val payload = req.payload?.get("json")?.toString()
        // Payload is stored as jsonb; parsing would be needed – placeholder joins via code in payload
        // We'll try to extract roomCode if payload contains it
        viewModelScope.launch {
            try {
                requestRepo.updateStatus(req.id, RequestStatus.accepted)
                showToast(UiText.of(R.string.toast_invite_hint, req.senderId), ToastKind.SUCCESS)
                // If we had room code, we would join – for scaffold we just close
                loadRequests()
            } catch (e: Exception) {
                showToast(e.toUiText(R.string.toast_something_wrong), ToastKind.WARNING)
            }
        }
    }

    fun toggleSound() {
        val enabled = !uiState.value.soundEnabled
        sound.enabled = enabled
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun toggleNotifications() = _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }

    fun setLanguage(language: AppLanguage) {
        _uiState.update { it.copy(language = language) }
        AppLanguage.apply(language)
    }

    fun openIdentityEditor() {
        _uiState.update { it.copy(rootScreen = RootScreen.IDENTITY, identityEditing = true) }
    }

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

    fun displayNameCooldownDays(): Int =
        ProfileRules.cooldownDaysRemaining(uiState.value.profile?.displayNameUpdatedAt)

    fun playAgain() {
        val game = uiState.value.game
        val selectedLevel = uiState.value.selectedLevel

        // Campaign replay
        if (game?.mode == GameMode.CAMPAIGN_SCORE && selectedLevel != null) {
            startCampaignLevel(selectedLevel)
            return
        }
        if (uiState.value.rootScreen == RootScreen.PUZZLE_GAME && selectedLevel != null) {
            // Restart puzzle
            val puzzle = PuzzleEngine.fromDefinition(selectedLevel)
            _uiState.update {
                it.copy(
                    puzzleState = puzzle,
                    puzzleElapsedSeconds = 0,
                    puzzleIsRunning = false,
                    puzzleWrongCells = emptySet()
                )
            }
            return
        }

        if (game == null) {
            goHome()
            return
        }
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
            RootScreen.ASSIGNMENT, RootScreen.JOIN_ROOM, RootScreen.LEVEL_SELECT, RootScreen.MESSAGE_THREAD -> _uiState.update { it.copy(rootScreen = RootScreen.MAIN) }
            RootScreen.IDENTITY ->
                if (uiState.value.identityEditing) {
                    _uiState.update { it.copy(rootScreen = RootScreen.MAIN, identityEditing = false) }
                } else Unit
            RootScreen.ROOM_SETUP, RootScreen.LOBBY -> {
                stopRealtime()
                _uiState.update { it.copy(rootScreen = RootScreen.MAIN, room = null, isReconnecting = false) }
            }
            RootScreen.GAME -> {
                // If campaign, go back to level select
                if (uiState.value.game?.mode == GameMode.CAMPAIGN_SCORE) {
                    _uiState.update { it.copy(rootScreen = RootScreen.LEVEL_SELECT, game = null) }
                    stopRealtime()
                    sound.stopTheme()
                } else goHome()
            }
            RootScreen.PUZZLE_GAME -> {
                stopPuzzleTimer()
                _uiState.update { it.copy(rootScreen = RootScreen.LEVEL_SELECT, puzzleState = null) }
            }
            RootScreen.RESULTS -> {
                // After campaign, go to level select, else home
                val isCampaign = uiState.value.game?.mode == GameMode.CAMPAIGN_SCORE || uiState.value.selectedLevel != null
                if (isCampaign) {
                    _uiState.update { it.copy(rootScreen = RootScreen.LEVEL_SELECT, game = null) }
                } else goHome()
            }
            else -> Unit
        }
    }

    fun goHome() {
        stopRealtime()
        stopPuzzleTimer()
        sound.stopTheme()
        _uiState.update {
            it.copy(
                rootScreen = RootScreen.MAIN,
                mainTab = MainTab.HOME,
                game = null,
                puzzleState = null,
                room = null,
                selectedLetter = null,
                isReconnecting = false
            )
        }
        refreshHomeData()
    }

    fun logout() = viewModelScope.launch {
        stopRealtime()
        stopPuzzleTimer()
        sound.stopTheme()
        pauseSocialRealtime()
        if (!uiState.value.isOfflineGuest) runCatching { auth.signOut() }
        _uiState.value = MainUiState(
            rootScreen = RootScreen.LOGIN,
            language = uiState.value.language,
            isOnline = network.isCurrentlyOnline,
            campaignLevels = CampaignLevelCatalog.allLevels()
        )
    }

    private fun reportFinishedGame(game: GameState) {
        val state = uiState.value
        val profile = state.profile ?: return
        if (state.isOfflineGuest || !reportedGameIds.add(game.gameId)) return
        if (game.mode == GameMode.CAMPAIGN_SCORE || game.mode == GameMode.CAMPAIGN_PUZZLE) return // campaign handled separately
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
            loadRequests()
            loadMessages()
            loadCampaignProgress()
        }
    }

    private fun startPresence() {
        val uid = uiState.value.profile?.uid ?: return
        if (presenceJob?.isActive == true) return
        presenceJob = viewModelScope.launch {
            users.observeOnlineUsers(uid)
                .catch { }
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
                .catch { }
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
        PlacementOutcome.REPEATED_WORD -> UiText.of(R.string.placement_repeated, pointsAwarded)
        PlacementOutcome.NO_WORD -> UiText.of(R.string.placement_no_word, pointsAwarded)
        PlacementOutcome.LETTER_PLACED -> UiText.of(R.string.placement_letter_placed, pointsAwarded)
        PlacementOutcome.WRONG_GUESS -> UiText.of(R.string.toast_move_rejected)
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

    private fun stopPuzzleTimer() {
        puzzleTimerJob?.cancel()
        puzzleTimerJob = null
    }

    override fun onCleared() {
        stopRealtime()
        stopPuzzleTimer()
        sound.stopTheme()
        presenceJob?.cancel()
        leaderboardJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val TURN_TIMEOUT_SECONDS = 45
        const val TIMER_TICK_SECONDS = 10
        const val TIMER_WARNING_SECONDS = 5
    }
}
