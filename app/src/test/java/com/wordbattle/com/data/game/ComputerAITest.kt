package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ComputerAITest {
    @Test
    fun `AI exhaustively finds a local scoring move without network`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 2, 'A', "human"))
        val game = GameState(
            "offline", GameMode.COMPUTER, 100, board,
            listOf(
                Player("human", "You", PlayerType.HUMAN_LOCAL, turnOrder = 0),
                Player("computer", "Bot", PlayerType.COMPUTER, turnOrder = 1)
            ),
            currentTurnPlayerId = "computer"
        )
        val move = ComputerAI(WordDictionary.fromWords(listOf("AT")), Random(7)).chooseMove(game)

        assertNotNull(move)
        move!!
        // 1 point for the letter itself + 2 letters of the word "AT".
        assertEquals(3, move.score)
        assertEquals('T', move.letter)
        // The search visits cells in a fixed order and keeps the FIRST move with the top
        // score, and the game scores a word read in either direction. So completing the
        // existing 'A' as "AT" to the right/below or as "TA" (= "AT" read backwards)
        // above/left are four equally optimal moves worth exactly 3 points. Pin the real
        // invariant (score, letter, adjacency) instead of one arbitrary tie-broken cell.
        assertTrue(
            "AI must extend the existing 'A' at (2,2), but chose (${move.row}, ${move.col})",
            abs(move.row - 2) + abs(move.col - 2) == 1
        )
    }
}
