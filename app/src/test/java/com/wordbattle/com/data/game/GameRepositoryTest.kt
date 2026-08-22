package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.BoardCoordinate
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.PlacementOutcome
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.UsedWord
import com.wordbattle.com.data.repository.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRepositoryTest {
    private val dictionary = WordDictionary.fromWords(listOf("AT", "CAT", "MAN", "MAT"))
    private val repository = GameRepository(dictionary)
    private val players = listOf(
        Player("p1", "One", PlayerType.HUMAN_LOCAL, turnOrder = 0),
        Player("p2", "Two", PlayerType.HUMAN_LOCAL, turnOrder = 1)
    )

    @Test
    fun `placing a letter always pays one point`() {
        val game = repository.newGame(GameMode.LOCAL, players = players)
        val result = repository.placeLetter(game, "p1", 1, 1, 'A').getOrThrow()
        assertEquals(GameRepository.POINTS_PER_LETTER, result.pointsAwarded)
        assertEquals(PlacementOutcome.LETTER_PLACED, result.outcome)
        assertEquals("p2", result.gameState.currentTurnPlayerId)
    }

    @Test
    fun `completing a word pays one point per letter of the word plus the placed letter`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 0, 'C', "p2"))
        board = requireNotNull(WordEngine.place(board, 2, 1, 'A', "p2"))
        val game = GameState("g", GameMode.LOCAL, 100, board, players, currentTurnPlayerId = "p1")

        val result = repository.placeLetter(game, "p1", 2, 2, 't').getOrThrow()
        assertEquals(listOf("CAT"), result.newWords)
        assertEquals(1 + 3, result.pointsAwarded)
        assertEquals(PlacementOutcome.SCORED, result.outcome)
    }

    @Test
    fun `a word hidden inside a longer run of letters still scores`() {
        var board = BoardState.empty(5, 12)
        "PLSHBDOCA".forEachIndexed { index, letter ->
            board = requireNotNull(WordEngine.place(board, 2, index, letter, "p2"))
        }
        val game = GameState("g", GameMode.LOCAL, 100, board, players, currentTurnPlayerId = "p1")

        val result = repository.placeLetter(game, "p1", 2, 9, 'T').getOrThrow()
        assertEquals(listOf("CAT"), result.newWords)
        assertEquals(1 + 3, result.pointsAwarded)
    }

    @Test
    fun `both axes can score on the same placement`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 1, 'C', "p2"))
        board = requireNotNull(WordEngine.place(board, 2, 3, 'T', "p2"))
        board = requireNotNull(WordEngine.place(board, 1, 2, 'M', "p2"))
        board = requireNotNull(WordEngine.place(board, 3, 2, 'N', "p2"))
        val game = GameState("g", GameMode.LOCAL, 100, board, players, currentTurnPlayerId = "p1")

        val result = repository.placeLetter(game, "p1", 2, 2, 'A').getOrThrow()
        assertEquals(listOf("CAT", "MAN"), result.newWords)
        assertEquals(1 + 3 + 3, result.pointsAwarded)
    }

    @Test
    fun `used words are case insensitive and only pay the letter point`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 0, 'M', "p2"))
        board = requireNotNull(WordEngine.place(board, 2, 1, 'A', "p2"))
        val game = GameState(
            "g", GameMode.LOCAL, 100, board, players,
            usedWords = listOf(
                UsedWord("mat", "p2", listOf(BoardCoordinate(0, 0))),
                UsedWord("at", "p2", listOf(BoardCoordinate(0, 1)))
            ),
            currentTurnPlayerId = "p1"
        )

        val result = repository.placeLetter(game, "p1", 2, 2, 't').getOrThrow()
        assertEquals(GameRepository.POINTS_PER_LETTER, result.pointsAwarded)
        assertEquals(listOf("MAT"), result.repeatedWords)
        assertEquals(PlacementOutcome.REPEATED_WORD, result.outcome)
        assertEquals('T', result.gameState.board.cell(2, 2)?.letter)
    }

    @Test
    fun `reaching target assigns first and auto last rank`() {
        var board = BoardState.empty(3, 3)
        board = requireNotNull(WordEngine.place(board, 1, 0, 'A', "p2"))
        val game = GameState("g", GameMode.LOCAL, 2, board, players, currentTurnPlayerId = "p1")

        val result = repository.placeLetter(game, "p1", 1, 1, 'T').getOrThrow().gameState
        assertEquals(GameStatus.FINISHED, result.status)
        assertEquals(1, result.players.first { it.id == "p1" }.rank)
        assertEquals(2, result.players.first { it.id == "p2" }.rank)
    }

    @Test
    fun `wrong player and filled cell are rejected`() {
        val game = repository.newGame(GameMode.LOCAL, players = players)
        assertTrue(repository.placeLetter(game, "p2", 1, 1, 'A').isFailure)
        val moved = repository.placeLetter(game, "p1", 1, 1, 'A').getOrThrow().gameState
        assertTrue(repository.placeLetter(moved.copy(currentTurnPlayerId = "p1"), "p1", 1, 1, 'B').isFailure)
    }

    @Test
    fun `computer reaching the campaign target does not finish the level`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 0, 'M', "campaign-ai"))
        board = requireNotNull(WordEngine.place(board, 2, 1, 'A', "campaign-ai"))
        val campaignPlayers = listOf(
            Player("campaign-human", "You", PlayerType.HUMAN_LOCAL, score = 0, turnOrder = 0),
            Player("campaign-ai", "Word Bot", PlayerType.COMPUTER, score = 4, turnOrder = 1)
        )
        val game = GameState(
            "cg1", GameMode.CAMPAIGN_SCORE, targetScore = 5, board = board,
            players = campaignPlayers, currentTurnPlayerId = "campaign-ai", campaignLevelNumber = 1
        )

        // The AI places T completing "MAT": its score crosses the target.
        val result = repository.placeLetter(game, "campaign-ai", 2, 2, 'T').getOrThrow().gameState
        assertEquals(GameStatus.IN_PROGRESS, result.status)
        assertEquals(null, result.players.first { it.id == "campaign-human" }.rank)
        assertEquals("campaign-human", result.currentTurnPlayerId)
    }

    @Test
    fun `human reaching the campaign target finishes the level with stars`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 0, 'M', "campaign-human"))
        board = requireNotNull(WordEngine.place(board, 2, 1, 'A', "campaign-human"))
        val campaignPlayers = listOf(
            Player("campaign-human", "You", PlayerType.HUMAN_LOCAL, score = 4, turnOrder = 0),
            Player("campaign-ai", "Word Bot", PlayerType.COMPUTER, score = 0, turnOrder = 1)
        )
        val game = GameState(
            "cg2", GameMode.CAMPAIGN_SCORE, targetScore = 5, board = board,
            players = campaignPlayers, currentTurnPlayerId = "campaign-human",
            campaignLevelNumber = 1, playerTurnsUsed = 1
        )

        val result = repository.placeLetter(game, "campaign-human", 2, 2, 'T').getOrThrow().gameState
        assertEquals(GameStatus.FINISHED, result.status)
        assertEquals(1, result.players.first { it.id == "campaign-human" }.rank)
        assertEquals(2, result.playerTurnsUsed)
        assertTrue(result.starsEarned in 1..3)
    }
}
