package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
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
        assertEquals(2, move!!.score)
        assertEquals('T', move.letter)
        assertEquals(2, move.row)
        assertEquals(3, move.col)
    }
}
