package com.wordbattle.com.data.model

import com.wordbattle.com.data.game.AiDifficulty
import kotlinx.serialization.Serializable

@Serializable
enum class PlayerType { HUMAN_LOCAL, HUMAN_ONLINE, COMPUTER }

@Serializable
enum class GameMode { COMPUTER, LOCAL, MIXED_ONLINE, CAMPAIGN_SCORE, CAMPAIGN_PUZZLE }

@Serializable
enum class GameStatus { LOBBY, IN_PROGRESS, FINISHED, LEVEL_FAILED }

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
    val rankingsAssigned: List<String> = emptyList(),
    // Campaign specific – kept optional so existing Computer/2P/3P/4P serialization unchanged
    val campaignLevelNumber: Int? = null,
    val campaignType: LevelType? = null,
    val playerTurnsUsed: Int = 0,
    val starsEarned: Int = 0
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

// ---------------------------------------------------------------------------
// Campaign – two level types
// ---------------------------------------------------------------------------

@Serializable
enum class LevelType { SCORE_ATTACK, PUZZLE_FILL }

@Serializable
enum class CellStyle { GIVEN, BLANK, BLOCKED }

@Serializable
data class PuzzleCellDef(
    val style: CellStyle,
    val letter: Char? = null // GIVEN হলে letter থাকবে
)

@Serializable
data class LevelDefinition(
    val levelNumber: Int,
    val type: LevelType,
    // SCORE_ATTACK-এর জন্য:
    val targetScore: Int? = null,
    val aiDifficulty: AiDifficulty? = null,
    val turnTimeSeconds: Int? = null,   // null = unlimited
    val turnLimit: Int? = null,         // এর মধ্যে target না পূরণ হলে হার
    val parTurns: Int? = null,          // এর মধ্যে জিতলে 3-star
    // PUZZLE_FILL-এর জন্য:
    val puzzleGrid: List<List<PuzzleCellDef>>? = null,
    val parTimeSeconds: Int? = null,    // এর মধ্যে সমাধান করলে 3-star
    val isBoss: Boolean = false
)

@Serializable
data class CampaignProgress(
    val levelNumber: Int,
    val stars: Int,
    val bestTimeSeconds: Int? = null,
    val bestTurns: Int? = null
)

// ---------------------------------------------------------------------------
// Lives system (PUZZLE_FILL only)
// ---------------------------------------------------------------------------

object CampaignConstants {
    const val LIFE_COST_COINS = 30
    const val LIFE_REGEN_MINUTES = 20
    const val LIFE_MAX_DEFAULT = 3
    const val LIFE_CURRENT_DEFAULT = 3
    const val DAILY_LIFE_REQUEST_LIMIT = 5
    const val LIFE_REQUEST_COIN_REWARD = 10
    const val TOTAL_CAMPAIGN_LEVELS = 500
}

// ---------------------------------------------------------------------------
// Requests / Messaging
// ---------------------------------------------------------------------------

@Serializable
enum class RequestType { FRIEND, LIFE, GAME_INVITE }

@Serializable
enum class RequestStatus { pending, accepted, declined, fulfilled }

@Serializable
data class GameRequest(
    val id: String,
    val type: RequestType,
    val senderId: String,
    val receiverId: String,
    val status: RequestStatus = RequestStatus.pending,
    val payload: Map<String, String>? = null, // roomCode etc
    val createdAt: String
)

@Serializable
enum class MessageStatus { SENT, DELIVERED, READ }

@Serializable
data class ChatMessage(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val body: String,
    val createdAt: String,
    val readAt: String? = null
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
    val displayNameUpdatedAt: String? = null,
    // Lives – only relevant for PUZZLE_FILL, but kept here for offline cache + Supabase sync
    val livesCurrent: Int = CampaignConstants.LIFE_CURRENT_DEFAULT,
    val livesMax: Int = CampaignConstants.LIFE_MAX_DEFAULT,
    val lastLifeRegenAt: String? = null, // ISO timestamp
    // Campaign progress
    val campaignLevel: Int = 1, // সর্বোচ্চ আনলক করা লেভেল
    val campaignStarsTotal: Int = 0
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
enum class PlacementOutcome { SCORED, REPEATED_WORD, NO_WORD, LETTER_PLACED, WRONG_GUESS }

data class PlacementResult(
    val gameState: GameState,
    val pointsAwarded: Int,
    val newWords: List<String>,
    val repeatedWords: List<String>,
    val invalidWords: List<String>,
    val outcome: PlacementOutcome,
    val livesRemaining: Int? = null,
    val puzzleSolved: Boolean = false
)

data class AiMove(val row: Int, val col: Int, val letter: Char, val score: Int)

sealed interface JoinRoomResult {
    data class Success(val room: Room) : JoinRoomResult

    /** [code] is localized by the UI; [detail] is technical context for logs only. */
    data class Error(val code: AppErrorCode, val detail: String? = null) : JoinRoomResult
}

