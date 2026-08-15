package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.BoardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordEngineTest {
    @Test
    fun `horizontal scan expands through contiguous cells and stops at gap`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 0, 'C', "p1"))
        board = requireNotNull(WordEngine.place(board, 2, 1, 'A', "p1"))
        board = requireNotNull(WordEngine.place(board, 2, 2, 'T', "p1"))
        board = requireNotNull(WordEngine.place(board, 2, 4, 'S', "p1"))

        val words = WordEngine.findCandidateWords(board, 2, 2).map { it.word }
        // The full run comes first, then the shorter segments that still contain the new cell.
        assertEquals("CAT", words.first())
        assertTrue(words.contains("AT"))
        assertTrue(words.none { it == "CA" }) // does not contain the placed cell
    }

    @Test
    fun `a word buried inside a longer run is still offered`() {
        var board = BoardState.empty(5, 12)
        "PLSHBDOCA".forEachIndexed { index, letter ->
            board = requireNotNull(WordEngine.place(board, 2, index, letter, "p1"))
        }
        board = requireNotNull(WordEngine.place(board, 2, 9, 'T', "p1"))

        val words = WordEngine.findCandidateWords(board, 2, 9).map { it.word }
        assertEquals("PLSHBDOCAT", words.first())
        assertTrue(words.contains("CAT"))
        assertTrue(words.contains("AT"))
    }

    @Test
    fun `one placement can complete horizontal and vertical words`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 1, 'C', "p1"))
        board = requireNotNull(WordEngine.place(board, 2, 3, 'T', "p1"))
        board = requireNotNull(WordEngine.place(board, 1, 2, 'M', "p1"))
        board = requireNotNull(WordEngine.place(board, 3, 2, 'N', "p1"))
        board = requireNotNull(WordEngine.place(board, 2, 2, 'A', "p1"))

        val horizontal = WordEngine.segments(board, 2, 2, WordAxis.HORIZONTAL).map { it.word }
        val vertical = WordEngine.segments(board, 2, 2, WordAxis.VERTICAL).map { it.word }
        assertEquals("CAT", horizontal.first())
        assertEquals("MAN", vertical.first())
    }

    @Test
    fun `single isolated letter forms no candidate`() {
        var board = BoardState.empty(5, 5)
        board = requireNotNull(WordEngine.place(board, 2, 2, 'A', "p1"))
        assertTrue(WordEngine.findCandidateWords(board, 2, 2).isEmpty())
    }

    @Test
    fun `occupied cells cannot be overwritten`() {
        var board = BoardState.empty(3, 3)
        board = requireNotNull(WordEngine.place(board, 1, 1, 'A', "p1"))
        assertTrue(WordEngine.place(board, 1, 1, 'B', "p2") == null)
        assertEquals('A', board.cell(1, 1)?.letter)
    }
}
