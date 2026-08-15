package com.wordbattle.com.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerType { HUMAN_LOCAL, HUMAN_ONLINE, COMPUTER }

@Serializable
enum class GameMode { COMPUTER, LOCAL, MIXED_ONLINE }

@Serializable
enum class GameStatus { LOBBY, IN_PROGRESS, FINISHED }

@Serializable
data class Player(
    val id: String,
    val name: String,
    val type: PlayerType,
    val score: Int = 0,
    val rank: Int? = null,
    val isReady: Boolean = false,
    val isConnected: Boolean = true,
    val turnOrder: Int
)

@Serializable
data class Cell(
    val row: Int,
    val col: Int,
    val letter: Char? = null,
    val placedByPlayerId: String? = null
)

@Serializable
data class BoardState(
    val rows: Int = 15,
    val cols: Int = 15,
    val cells: List<List<Cell>> = createCells(rows, cols)
) {
    fun cell(row: Int, col: Int): Cell? = cells.getOrNull(row)?.getOrNull(col)

    companion object {
        fun empty(rows: Int = 15, cols: Int = 15): BoardState =
            BoardState(rows, cols, createCells(rows, cols))

        private fun createCells(rows: Int, cols: Int): List<List<Cell>> =
            List(rows) { row -> List(cols) { col -> Cell(row, col) } }
    }
}

@Serializable
data class BoardCoordinate(val row: Int, val col: Int)

@Serializable
data class UsedWord(
    val word: String,
    val scoredByPlayerId: String,
    val cells: List<BoardCoordinate>
)

@Serializable
data class GameState(
    val gameId: String,
    val mode: GameMode,
    val targetScore: Int = 100,
    val board: BoardState = BoardState.empty(),
    val players: List<Player>,
    val usedWords: List<UsedWord> = emptyList(),
    val currentTurnPlayerId: String,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val rankingsAssigned: List<String> = emptyList()
)

@Serializable
data class Room(
    val roomId: String,
    val roomCode: String,
    val passcode: String,
    val hostId: String,
    val totalSlots: Int,
    val localSlotsCount: Int,
    val onlineSlotsCount: Int,
    val slots: List<RoomSlot> = emptyList(),
    val gameId: String? = null,
    val status: String = "lobby"
)

@Serializable
data class RoomSlot(
    val slotIndex: Int,
    val filledByUid: String?,
    val filledByName: String?,
    val isReady: Boolean = false
)

@Serializable
data class UserProfile(
    val uid: String,
    val displayName: String,
    /** Globally unique, lowercase handle used by friend search. Null until the user picks one. */
    val username: String? = null,
    val photoUrl: String? = null,
    val coins: Int = 0,
    val gems: Int = 0,
    val level: Int = 1,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val weeklyScore: Int = 0,
    /** ISO-8601 timestamp of the last display-name change, used for the 10 day cooldown. */
    val displayNameUpdatedAt: String? = null
) {
    /** True once the user has completed the first-login identity screen. */
    val hasIdentity: Boolean get() = !username.isNullOrBlank()
}

@Serializable
data class FriendProfile(
    val profile: UserProfile,
    val status: String = "accepted",
    val isOnline: Boolean = false
)

/** What happened when a letter was dropped on the board. The UI turns this into localized text. */
enum class PlacementOutcome { SCORED, REPEATED_WORD, NO_WORD, LETTER_PLACED }

data class PlacementResult(
    val gameState: GameState,
    val pointsAwarded: Int,
    val newWords: List<String>,
    val repeatedWords: List<String>,
    val invalidWords: List<String>,
    val outcome: PlacementOutcome
)

data class AiMove(val row: Int, val col: Int, val letter: Char, val score: Int)

sealed interface JoinRoomResult {
    data class Success(val room: Room) : JoinRoomResult

    /** [code] is localized by the UI; [detail] is technical context for logs only. */
    data class Error(val code: AppErrorCode, val detail: String? = null) : JoinRoomResult
}
