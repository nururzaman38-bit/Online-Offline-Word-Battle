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
     * After filling a BLANK at (row,col), check if that placement creates a completed line that is invalid.
     * Returns true if wrong guess (should cost 1 life).
     */
    fun isWrongGuess(state: PuzzleState, row: Int, col: Int, dictionary: WordDictionary): Boolean {
        // Find horizontal run containing (row,col)
        val hRun = extractRunContaining(state, row, col, horizontal = true)
        val vRun = extractRunContaining(state, row, col, horizontal = false)

        // If a run is fully filled (no null) and length>=2 and invalid => wrong
        listOfNotNull(hRun, vRun).forEach { word ->
            if (word.length >= WordEngine.MIN_WORD_LENGTH && !dictionary.isValidWord(word)) {
                // Only count as wrong if the run is completely filled (no empty BLANK inside)
                // hRun/vRun extracted only if contiguous and all letters present – so if we got a word, it's complete
                return true
            }
        }
        return false
    }

    private fun extractRunContaining(state: PuzzleState, row: Int, col: Int, horizontal: Boolean): String? {
        if (state.isBlocked(row, col)) return null
        if (state.charAt(row, col) == null) return null

        val (dr, dc) = if (horizontal) 0 to 1 else 1 to 0

        // Expand backward
        var r = row
        var c = col
        while (true) {
            val nr = r - dr
            val nc = c - dc
            if (nr !in 0 until state.rows || nc !in 0 until state.cols) break
            if (state.isBlocked(nr, nc) || state.charAt(nr, nc) == null) break
            r = nr
            c = nc
        }
        // Expand forward collecting
        val sb = StringBuilder()
        var cr = r
        var cc = c
        while (cr in 0 until state.rows && cc in 0 until state.cols) {
            if (state.isBlocked(cr, cc) || state.charAt(cr, cc) == null) break
            sb.append(state.charAt(cr, cc))
            // Stop when we have passed the original cell and next cell is blocked/empty – but we want full contiguous run
            cr += dr
            cc += dc
        }
        return if (sb.isNotEmpty()) sb.toString().uppercase(Locale.ROOT) else null
    }

    private fun <T> listOfNotNull(vararg elements: T?): List<T> = elements.filterNotNull()
}
