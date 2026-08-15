package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.BoardCoordinate
import com.wordbattle.com.data.model.BoardState
import java.util.Locale

/** The two directions a word can run on the board. */
enum class WordAxis { HORIZONTAL, VERTICAL }

data class CandidateWord(
    val word: String,
    val cells: List<BoardCoordinate>,
    val axis: WordAxis = WordAxis.HORIZONTAL
)

/**
 * Pure board scanner. It has no Android or network dependency and is safe to use from the local AI.
 *
 * A placement is never judged by the *whole* contiguous run alone: a run such as `PLSHBDOCAT`
 * contains the real word `CAT`, so every contiguous segment that contains the freshly placed cell
 * is offered as a candidate (longest first). The caller keeps the best segment the dictionary
 * accepts.
 */
object WordEngine {
    /** Shortest sequence of letters that can score. */
    const val MIN_WORD_LENGTH = 2

    /**
     * Every contiguous segment (length >= [MIN_WORD_LENGTH]) that passes through `row`/`col`,
     * horizontal segments first, longest first inside each axis.
     */
    fun findCandidateWords(board: BoardState, row: Int, col: Int): List<CandidateWord> {
        val origin = board.cell(row, col) ?: return emptyList()
        if (origin.letter == null) return emptyList()
        return WordAxis.entries.flatMap { axis -> segments(board, row, col, axis) }
    }

    /** Candidate segments for a single axis, longest first. */
    fun segments(board: BoardState, row: Int, col: Int, axis: WordAxis): List<CandidateWord> {
        val run = fullRun(board, row, col, axis) ?: return emptyList()
        val originIndex = run.cells.indexOfFirst { it.row == row && it.col == col }
        if (originIndex < 0) return emptyList()
        val result = mutableListOf<CandidateWord>()
        for (start in 0..originIndex) {
            for (end in originIndex until run.cells.size) {
                val length = end - start + 1
                if (length < MIN_WORD_LENGTH) continue
                val forward = run.word.substring(start, end + 1)
                val slice = run.cells.subList(start, end + 1).toList()
                result += CandidateWord(
                    word = forward,
                    cells = slice,
                    axis = axis
                )
                val reversed = forward.reversed()
                if (reversed != forward) {
                    // Same cells, only the word read in the opposite direction, so the caller
                    // can check both directions against the dictionary.
                    result += CandidateWord(
                        word = reversed,
                        cells = slice,
                        axis = axis
                    )
                }
            }
        }
        // Longest first so the caller naturally prefers CAT over AT inside the same run.
        return result.sortedWith(compareByDescending<CandidateWord> { it.word.length }.thenBy { it.cells.first().row }.thenBy { it.cells.first().col })
    }

    /** The whole uninterrupted run of letters through `row`/`col` on `axis` (may be a single cell). */
    fun fullRun(board: BoardState, row: Int, col: Int, axis: WordAxis): CandidateWord? {
        if (letterAt(board, row, col) == null) return null
        val rowDelta = if (axis == WordAxis.VERTICAL) 1 else 0
        val colDelta = if (axis == WordAxis.HORIZONTAL) 1 else 0

        var startRow = row
        var startCol = col
        while (letterAt(board, startRow - rowDelta, startCol - colDelta) != null) {
            startRow -= rowDelta
            startCol -= colDelta
        }

        val letters = StringBuilder()
        val coordinates = mutableListOf<BoardCoordinate>()
        var currentRow = startRow
        var currentCol = startCol
        while (true) {
            val letter = letterAt(board, currentRow, currentCol) ?: break
            letters.append(letter)
            coordinates += BoardCoordinate(currentRow, currentCol)
            currentRow += rowDelta
            currentCol += colDelta
        }
        return CandidateWord(letters.toString().uppercase(Locale.ROOT), coordinates, axis)
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

    private fun letterAt(board: BoardState, row: Int, col: Int): Char? =
        board.cells.getOrNull(row)?.getOrNull(col)?.letter
}
