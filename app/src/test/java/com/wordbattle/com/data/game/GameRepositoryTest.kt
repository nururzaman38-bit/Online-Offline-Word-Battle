package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.BoardCoordinate
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.UsedWord
import com.wordbattle.com.data.repository.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRepositoryTest {
    private val dictionary = WordDictionary.fromWords(listOf("AT", "CAT", "MAN"))
    private val repository = GameRepository(dictionary)
    private val players = listOf(
        Player("p1", "One", PlayerType.HUMAN_LOCAL, turnOrder = 0),
        Player("p2", "Two", PlayerType.HUMAN_LOCAL, turnOrder = 1)
    )

    @Test
    fun `used words are case insensitive and remain on board for zero points`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 0, 'C', "p2"))
        board = requireNotNull(WordEngine.place(board, 2, 1, 'A', "p2"))
        val game = GameState(
            "g", GameMode.LOCAL, 100, board, players,
            usedWords = listOf(UsedWord("cat", "p2", listOf(BoardCoordinate(0, 0)))),
            currentTurnPlayerId = "p1"
        )

        val result = repository.placeLetter(game, "p1", 2, 2, 't').getOrThrow()
        assertEquals(0, result.pointsAwarded)
        assertEquals(listOf("CAT"), result.repeatedWords)
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
    fun `one-letter placement advances turn without scoring`() {
        val game = repository.newGame(GameMode.LOCAL, players = players)
        val result = repository.placeLetter(game, "p1", 1, 1, 'A').getOrThrow()
        assertEquals(0, result.pointsAwarded)
        assertEquals("p2", result.gameState.currentTurnPlayerId)
    }

    @Test
    fun `wrong player and filled cell are rejected`() {
        val game = repository.newGame(GameMode.LOCAL, players = players)
        assertTrue(repository.placeLetter(game, "p2", 1, 1, 'A').isFailure)
        val moved = repository.placeLetter(game, "p1", 1, 1, 'A').getOrThrow().gameState
        assertTrue(repository.placeLetter(moved.copy(currentTurnPlayerId = "p1"), "p1", 1, 1, 'B').isFailure)
    }
}
