package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.BoardCoordinate
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.Cell
import java.util.Locale

data class CandidateWord(val word: String, val cells: List<BoardCoordinate>)

/** Pure board scanner. It has no Android or network dependency and is safe to use from the local AI. */
object WordEngine {
    fun findCandidateWords(board: BoardState, row: Int, col: Int): List<CandidateWord> {
        val origin = board.cell(row, col) ?: return emptyList()
        if (origin.letter == null) return emptyList()
        return buildList {
            scan(board, row, col, rowDelta = 0, colDelta = 1)?.let(::add)
            scan(board, row, col, rowDelta = 1, colDelta = 0)?.let(::add)
        }
    }

    fun place(board: BoardState, row: Int, col: Int, letter: Char, playerId: String): BoardState? {
        val target = board.cell(row, col) ?: return null
        if (target.letter != null || !letter.isLetter()) return null
        val normalized = letter.uppercaseChar()
        val rows = board.cells.mapIndexed { r, cells ->
            if (r != row) cells else cells.mapIndexed { c, cell ->
                if (c != col) cell else cell.copy(letter = normalized, placedByPlayerId = playerId)
            }
        }
        return board.copy(cells = rows)
    }

    private fun scan(
        board: BoardState,
        originRow: Int,
        originCol: Int,
        rowDelta: Int,
        colDelta: Int
    ): CandidateWord? {
        var startRow = originRow
        var startCol = originCol
        while (letterAt(board, startRow - rowDelta, startCol - colDelta) != null) {
            startRow -= rowDelta
            startCol -= colDelta
        }

        val letters = StringBuilder()
        val coordinates = mutableListOf<BoardCoordinate>()
        var row = startRow
        var col = startCol
        while (true) {
            val letter = letterAt(board, row, col) ?: break
            letters.append(letter)
            coordinates += BoardCoordinate(row, col)
            row += rowDelta
            col += colDelta
        }
        if (coordinates.size < 2) return null
        return CandidateWord(letters.toString().uppercase(Locale.ROOT), coordinates)
    }

    private fun letterAt(board: BoardState, row: Int, col: Int): Char? =
        board.cells.getOrNull(row)?.getOrNull(col)?.letter
}
