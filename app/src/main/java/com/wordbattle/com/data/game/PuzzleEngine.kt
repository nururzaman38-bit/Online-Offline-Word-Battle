package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.Cell
import com.wordbattle.com.data.model.CellStyle
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.PuzzleCellDef
import java.util.Locale

/**
 * Solve-check for PUZZLE_FILL reusing WordEngine run-scanning idea but without creating new engine.
 * No single correct answer required – any valid dictionary words matching pattern are accepted.
 */
object PuzzleEngine {

    data class PuzzleState(
        val rows: Int,
        val cols: Int,
        val defs: List<List<PuzzleCellDef>>, // original
        val filled: List<List<Char?>> // user filled letters for BLANK cells, null if empty
    ) {
        fun charAt(r: Int, c: Int): Char? {
            if (r !in 0 until rows || c !in 0 until cols) return null
            val def = defs[r][c]
            return when (def.style) {
                CellStyle.BLOCKED -> null
                CellStyle.GIVEN -> def.letter
                CellStyle.BLANK -> filled[r][c]
            }
        }

        fun isBlocked(r: Int, c: Int): Boolean = defs[r][c].style == CellStyle.BLOCKED
        fun isGiven(r: Int, c: Int): Boolean = defs[r][c].style == CellStyle.GIVEN
        fun isBlank(r: Int, c: Int): Boolean = defs[r][c].style == CellStyle.BLANK

        fun isAllBlankFilled(): Boolean {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (defs[r][c].style == CellStyle.BLANK && filled[r][c] == null) return false
                }
            }
            return true
        }

        fun toBoardState(): BoardState {
            val cells = List(rows) { r ->
                List(cols) { c ->
                    val ch = charAt(r, c)
                    Cell(r, c, ch, null)
                }
            }
            return BoardState(rows, cols, cells)
        }
    }

    fun fromDefinition(level: LevelDefinition): PuzzleState {
        val grid = level.puzzleGrid ?: emptyList()
        val rows = grid.size
        val cols = grid.firstOrNull()?.size ?: 0
        val filled = List(rows) { r ->
            List(cols) { c ->
                null // initially no BLANK filled; GIVEN handled via charAt
            }
        }
        return PuzzleState(rows, cols, grid, filled)
    }

    fun withLetter(state: PuzzleState, row: Int, col: Int, letter: Char?): PuzzleState {
        if (row !in 0 until state.rows || col !in 0 until state.cols) return state
        if (state.defs[row][col].style != CellStyle.BLANK) return state
        val newFilled = state.filled.mapIndexed { r, rowList ->
            if (r != row) rowList else rowList.mapIndexed { c, ch -> if (c == col) letter?.uppercaseChar() else ch }
        }
        return state.copy(filled = newFilled)
    }

    /**
     * Returns list of words (contiguous runs length>=2) on board – used for validation.
     * Each run is extracted as string.
     */
    fun collectRuns(state: PuzzleState): List<String> {
        val runs = mutableListOf<String>()
        // Horizontal
        for (r in 0 until state.rows) {
            var c = 0
            while (c < state.cols) {
                if (state.isBlocked(r, c) || state.charAt(r, c) == null) {
                    c++
                    continue
                }
                val start = c
                val sb = StringBuilder()
                while (c < state.cols && !state.isBlocked(r, c) && state.charAt(r, c) != null) {
                    sb.append(state.charAt(r, c))
                    c++
                }
                if (sb.length >= WordEngine.MIN_WORD_LENGTH) runs += sb.toString().uppercase(Locale.ROOT)
                // if single letter run, ignore (not a word)
            }
        }
        // Vertical
        for (col in 0 until state.cols) {
            var r = 0
            while (r < state.rows) {
                if (state.isBlocked(r, col) || state.charAt(r, col) == null) {
                    r++
                    continue
                }
                val sb = StringBuilder()
                while (r < state.rows && !state.isBlocked(r, col) && state.charAt(r, col) != null) {
                    sb.append(state.charAt(r, col))
                    r++
                }
                if (sb.length >= WordEngine.MIN_WORD_LENGTH) runs += sb.toString().uppercase(Locale.ROOT)
            }
        }
        return runs
    }

    /**
     * Solved = all BLANK filled + every 2+ run valid dictionary word.
     */
    fun isSolved(state: PuzzleState, dictionary: WordDictionary): Boolean {
        if (!state.isAllBlankFilled()) return false
        val runs = collectRuns(state)
        if (runs.isEmpty()) return false // at least one word expected
        return runs.all { dictionary.isValidWord(it) }
    }

    /**
     * After filling a BLANK at (row,col), check if that placement completes a line that is
     * invalid. A guess only costs a life when it *completes* a run (every cell of the segment
     * between blockers is now filled) and the finished word is not in the dictionary.
     *
     * Placing letters on an unfinished line is never wrong: in `C _ _` the player must be free to
     * try `A` (making the partial `CA`) without losing a life before completing `CAT`.
     *
     * @return true when a completed run through (row,col) is not a valid dictionary word.
     */
    fun isWrongGuess(state: PuzzleState, row: Int, col: Int, dictionary: WordDictionary): Boolean {
        if (!state.isBlank(row, col)) return false
        listOf(true, false).forEach { horizontal ->
            val segment = segmentCells(state, row, col, horizontal) ?: return@forEach
            // The line is not finished until every cell in the segment is filled.
            if (segment.any { (r, c) -> state.charAt(r, c) == null }) return@forEach
            val word = segment.map { (r, c) -> state.charAt(r, c)!! }.joinToString("")
            if (word.length >= WordEngine.MIN_WORD_LENGTH && !dictionary.isValidWord(word)) {
                return true
            }
        }
        return false
    }

    /**
     * The full segment of cells between blocked cells (or the board edge) that contains
     * (row,col), for the given axis. Returns null when (row,col) is itself blocked.
     */
    private fun segmentCells(state: PuzzleState, row: Int, col: Int, horizontal: Boolean): List<Pair<Int, Int>>? {
        if (state.isBlocked(row, col)) return null
        var startR = row
        var startC = col
        while (true) {
            val nr = startR - (if (horizontal) 0 else 1)
            val nc = startC - (if (horizontal) 1 else 0)
            if (nr !in 0 until state.rows || nc !in 0 until state.cols || state.isBlocked(nr, nc)) break
            startR = nr
            startC = nc
        }
        val cells = mutableListOf<Pair<Int, Int>>()
        var cr = startR
        var cc = startC
        while (cr in 0 until state.rows && cc in 0 until state.cols && !state.isBlocked(cr, cc)) {
            cells += cr to cc
            cr += if (horizontal) 0 else 1
            cc += if (horizontal) 1 else 0
        }
        return cells
    }
}
