package com.wordbattle.com.data.repository

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.game.WordAxis
import com.wordbattle.com.data.game.WordEngine
import com.wordbattle.com.data.local.CachedGameEntity
import com.wordbattle.com.data.local.GameDao
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.AppErrorCode
import com.wordbattle.com.data.model.AppException
import com.wordbattle.com.data.model.PlacementOutcome
import com.wordbattle.com.data.model.PlacementResult
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.UsedWord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

/** Offline-first game rules. This class deliberately has no Supabase/network dependency. */
class GameRepository(
    private val dictionary: WordDictionary,
    private val gameDao: GameDao? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    fun createComputerGame(humanName: String = "You", targetScore: Int = 100): GameState = newGame(
        mode = GameMode.COMPUTER,
        targetScore = targetScore,
        players = listOf(
            Player("local-human", humanName, PlayerType.HUMAN_LOCAL, turnOrder = 0),
            Player("computer", "Word Bot", PlayerType.COMPUTER, turnOrder = 1)
        )
    )

    fun createLocalGame(playerCount: Int, humanName: String = "Player 1", targetScore: Int = 100): GameState {
        require(playerCount in 2..4) { "Word Battle supports 2–4 players" }
        return newGame(
            mode = GameMode.LOCAL,
            targetScore = targetScore,
            players = List(playerCount) { index ->
                Player(
                    id = "local-${index + 1}",
                    name = if (index == 0) humanName else "Player ${index + 1}",
                    type = PlayerType.HUMAN_LOCAL,
                    turnOrder = index
                )
            }
        )
    }

    fun createCampaignScoreGame(
        level: com.wordbattle.com.data.model.LevelDefinition,
        humanName: String = "You",
        humanId: String = "campaign-human"
    ): GameState {
        require(level.type == com.wordbattle.com.data.model.LevelType.SCORE_ATTACK)
        val target = level.targetScore ?: 100
        return newGame(
            mode = GameMode.CAMPAIGN_SCORE,
            targetScore = target,
            players = listOf(
                Player(humanId, humanName, PlayerType.HUMAN_LOCAL, turnOrder = 0),
                Player("campaign-ai", "Word Bot", PlayerType.COMPUTER, turnOrder = 1)
            ),
            campaignLevelNumber = level.levelNumber,
            campaignType = level.type
        )
    }

    fun newGame(
        mode: GameMode,
        targetScore: Int = 100,
        players: List<Player>,
        gameId: String = UUID.randomUUID().toString(),
        campaignLevelNumber: Int? = null,
        campaignType: com.wordbattle.com.data.model.LevelType? = null
    ): GameState {
        require(players.size in 2..4)
        require(targetScore > 0)
        val ordered = players.sortedBy(Player::turnOrder)
        return GameState(
            gameId = gameId,
            mode = mode,
            targetScore = targetScore,
            board = BoardState.empty(),
            players = ordered,
            currentTurnPlayerId = ordered.first().id,
            status = GameStatus.IN_PROGRESS,
            campaignLevelNumber = campaignLevelNumber,
            campaignType = campaignType,
            playerTurnsUsed = 0
        )
    }

    fun placeLetter(game: GameState, playerId: String, row: Int, col: Int, letter: Char): Result<PlacementResult> {
        if (game.status != GameStatus.IN_PROGRESS) {
            return Result.failure(AppException(AppErrorCode.GAME_NOT_IN_PROGRESS))
        }
        if (game.currentTurnPlayerId != playerId) {
            return Result.failure(AppException(AppErrorCode.NOT_YOUR_TURN))
        }
        if (game.board.cell(row, col) == null) return Result.failure(IllegalArgumentException("Cell is outside the board"))
        if (game.board.cell(row, col)?.letter != null) return Result.failure(IllegalArgumentException("That cell is already filled"))
        if (!letter.isLetter()) return Result.failure(IllegalArgumentException("Choose a letter A–Z"))

        val board = WordEngine.place(game.board, row, col, letter, playerId)
            ?: return Result.failure(IllegalArgumentException("Unable to place letter"))
        val alreadyUsed = game.usedWords.map { it.word.uppercase(Locale.ROOT) }.toMutableSet()
        val newWords = mutableListOf<String>()
        val repeatedWords = mutableListOf<String>()
        val invalidWords = mutableListOf<String>()
        val additions = mutableListOf<UsedWord>()

        // Every letter placed on the board is worth one point, word or no word.
        var points = POINTS_PER_LETTER

        // One word may be claimed per axis. Inside an axis the longest dictionary word that runs
        // through the new cell wins, so placing T after "…BDOCA" scores CAT and not AT.
        WordAxis.entries.forEach { axis ->
            val segments = WordEngine.segments(board, row, col, axis)
            val scored = segments.firstOrNull {
                dictionary.isValidWord(it.word) && it.word.uppercase(Locale.ROOT) !in alreadyUsed
            }
            when {
                scored != null -> {
                    val word = scored.word.uppercase(Locale.ROOT)
                    points += word.length
                    newWords += word
                    alreadyUsed += word
                    additions += UsedWord(word, playerId, scored.cells)
                }
                else -> {
                    val repeated = segments.firstOrNull { dictionary.isValidWord(it.word) }
                    if (repeated != null) {
                        repeatedWords += repeated.word.uppercase(Locale.ROOT)
                    } else {
                        // Only report the full run as "not a word" so the toast stays readable.
                        segments.firstOrNull()?.let { invalidWords += it.word.uppercase(Locale.ROOT) }
                    }
                }
            }
        }

        var players = game.players.map { player ->
            if (player.id == playerId) player.copy(score = player.score + points) else player
        }
        var rankings = game.rankingsAssigned
        val scorer = players.first { it.id == playerId }
        if (scorer.score >= game.targetScore && scorer.rank == null) {
            val rank = rankings.size + 1
            players = players.map { if (it.id == playerId) it.copy(rank = rank) else it }
            rankings = rankings + playerId
        }

        val unranked = players.filter { it.rank == null }
        var status = game.status
        if (players.size > 1 && unranked.size == 1) {
            val last = unranked.single()
            players = players.map { if (it.id == last.id) it.copy(rank = players.size) else it }
            rankings = rankings + last.id
            status = GameStatus.FINISHED
        }

        // Campaign: count only human turns
        var newTurnsUsed = game.playerTurnsUsed
        var starsEarned = game.starsEarned
        if (game.mode == GameMode.CAMPAIGN_SCORE) {
            // Increment for human player only
            if (players.firstOrNull { it.id == playerId }?.type == PlayerType.HUMAN_LOCAL) {
                newTurnsUsed = game.playerTurnsUsed + 1
            }
            // Check failure: turnLimit exceeded and target not reached
            val levelNumber = game.campaignLevelNumber
            if (levelNumber != null) {
                val levelDef = com.wordbattle.com.data.game.CampaignLevelCatalog.generateLevelDefinition(levelNumber)
                val hasReachedTarget = scorer.score >= game.targetScore
                if (com.wordbattle.com.data.game.CampaignRules.isScoreAttackFailed(levelDef, newTurnsUsed, hasReachedTarget)) {
                    status = GameStatus.LEVEL_FAILED
                } else if (hasReachedTarget) {
                    status = GameStatus.FINISHED
                    starsEarned = com.wordbattle.com.data.game.CampaignRules.starsForScoreAttack(levelDef, newTurnsUsed)
                }
            }
        }

        val nextPlayerId = when {
            status == GameStatus.FINISHED || status == GameStatus.LEVEL_FAILED -> playerId
            else -> nextTurn(players, playerId)
        }
        val updated = game.copy(
            board = board,
            players = players,
            usedWords = game.usedWords + additions,
            currentTurnPlayerId = nextPlayerId,
            status = status,
            rankingsAssigned = rankings,
            playerTurnsUsed = newTurnsUsed,
            starsEarned = starsEarned
        )
        val outcome = when {
            newWords.isNotEmpty() -> PlacementOutcome.SCORED
            repeatedWords.isNotEmpty() -> PlacementOutcome.REPEATED_WORD
            invalidWords.isNotEmpty() -> PlacementOutcome.NO_WORD
            else -> PlacementOutcome.LETTER_PLACED
        }
        return Result.success(
            PlacementResult(updated, points, newWords, repeatedWords, invalidWords, outcome)
        )
    }

    fun skipTurn(game: GameState, playerId: String): Result<GameState> {
        if (game.status != GameStatus.IN_PROGRESS) {
            return Result.failure(AppException(AppErrorCode.GAME_NOT_IN_PROGRESS))
        }
        if (game.currentTurnPlayerId != playerId) {
            return Result.failure(AppException(AppErrorCode.TURN_ALREADY_ADVANCED))
        }
        return Result.success(game.copy(currentTurnPlayerId = nextTurn(game.players, playerId)))
    }

    suspend fun cache(game: GameState) {
        gameDao?.upsert(CachedGameEntity(game.gameId, json.encodeToString(game)))
    }

    suspend fun loadCached(gameId: String): GameState? = gameDao?.get(gameId)?.let {
        runCatching { json.decodeFromString<GameState>(it.json) }.getOrNull()
    }

    private fun nextTurn(players: List<Player>, currentId: String): String {
        val ordered = players.sortedBy(Player::turnOrder)
        val currentIndex = ordered.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        for (offset in 1..ordered.size) {
            val candidate = ordered[(currentIndex + offset) % ordered.size]
            if (candidate.rank == null && candidate.isConnected) return candidate.id
        }
        return currentId
    }

    companion object {
        /** Dropping a legal letter on the board always pays this much, word or no word. */
        const val POINTS_PER_LETTER = 1
    }
}
