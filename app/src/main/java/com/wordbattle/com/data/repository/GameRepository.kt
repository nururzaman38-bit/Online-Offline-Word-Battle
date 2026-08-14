package com.wordbattle.com.data.repository

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.game.WordEngine
import com.wordbattle.com.data.local.CachedGameEntity
import com.wordbattle.com.data.local.GameDao
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
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

    fun newGame(
        mode: GameMode,
        targetScore: Int = 100,
        players: List<Player>,
        gameId: String = UUID.randomUUID().toString()
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
            status = GameStatus.IN_PROGRESS
        )
    }

    fun placeLetter(game: GameState, playerId: String, row: Int, col: Int, letter: Char): Result<PlacementResult> {
        if (game.status != GameStatus.IN_PROGRESS) return Result.failure(IllegalStateException("Game is not in progress"))
        if (game.currentTurnPlayerId != playerId) return Result.failure(IllegalStateException("Wait for your turn"))
        if (game.board.cell(row, col) == null) return Result.failure(IllegalArgumentException("Cell is outside the board"))
        if (game.board.cell(row, col)?.letter != null) return Result.failure(IllegalArgumentException("That cell is already filled"))
        if (!letter.isLetter()) return Result.failure(IllegalArgumentException("Choose a letter A–Z"))

        val board = WordEngine.place(game.board, row, col, letter, playerId)
            ?: return Result.failure(IllegalArgumentException("Unable to place letter"))
        val candidates = WordEngine.findCandidateWords(board, row, col)
        val alreadyUsed = game.usedWords.map { it.word.uppercase(Locale.ROOT) }.toMutableSet()
        val newWords = mutableListOf<String>()
        val repeatedWords = mutableListOf<String>()
        val invalidWords = mutableListOf<String>()
        val additions = mutableListOf<UsedWord>()
        var points = 0

        candidates.forEach { candidate ->
            when {
                !dictionary.isValidWord(candidate.word) -> invalidWords += candidate.word
                candidate.word.uppercase(Locale.ROOT) in alreadyUsed -> repeatedWords += candidate.word
                else -> {
                    points += candidate.word.length
                    newWords += candidate.word
                    alreadyUsed += candidate.word.uppercase(Locale.ROOT)
                    additions += UsedWord(candidate.word.uppercase(Locale.ROOT), playerId, candidate.cells)
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

        val nextPlayerId = if (status == GameStatus.FINISHED) playerId else nextTurn(players, playerId)
        val updated = game.copy(
            board = board,
            players = players,
            usedWords = game.usedWords + additions,
            currentTurnPlayerId = nextPlayerId,
            status = status,
            rankingsAssigned = rankings
        )
        val message = when {
            newWords.isNotEmpty() -> "+$points! New word: ${newWords.joinToString(" + ")}"
            repeatedWords.isNotEmpty() -> "This Word Already Used ⚠️"
            invalidWords.isNotEmpty() -> "No dictionary word formed"
            else -> "Letter placed"
        }
        return Result.success(
            PlacementResult(updated, points, newWords, repeatedWords, invalidWords, message)
        )
    }

    fun skipTurn(game: GameState, playerId: String): Result<GameState> {
        if (game.status != GameStatus.IN_PROGRESS) {
            return Result.failure(IllegalStateException("Game is not in progress"))
        }
        if (game.currentTurnPlayerId != playerId) {
            return Result.failure(IllegalStateException("The turn has already advanced"))
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
}
