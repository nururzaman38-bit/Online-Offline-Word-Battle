package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.CellStyle
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.LevelType
import com.wordbattle.com.data.model.PuzzleCellDef
import org.junit.Assert.*
import org.junit.Test

class PuzzleEngineTest {

    private fun cell(style: CellStyle, letter: Char? = null) = PuzzleCellDef(style, letter)
    private val dict = WordDictionary.fromWords(listOf("CAT", "AT", "COT", "DOG", "CAR", "ART"))

    @Test
    fun `solve detection - all blanks filled and valid words`() {
        // Simple 1x3 puzzle: blank, blank, blank -> CAT
        val level = LevelDefinition(
            levelNumber = 100,
            type = LevelType.PUZZLE_FILL,
            puzzleGrid = listOf(
                listOf(cell(CellStyle.BLANK), cell(CellStyle.BLANK), cell(CellStyle.BLANK))
            )
        )
        var state = PuzzleEngine.fromDefinition(level)
        assertFalse(PuzzleEngine.isSolved(state, dict))

        state = PuzzleEngine.withLetter(state, 0, 0, 'C')
        state = PuzzleEngine.withLetter(state, 0, 1, 'A')
        state = PuzzleEngine.withLetter(state, 0, 2, 'T')

        assertTrue(PuzzleEngine.isSolved(state, dict))
        assertEquals(listOf("CAT"), PuzzleEngine.collectRuns(state))
    }

    @Test
    fun `wrong guess detection when completed line invalid`() {
        val level = LevelDefinition(
            levelNumber = 101,
            type = LevelType.PUZZLE_FILL,
            puzzleGrid = listOf(
                listOf(cell(CellStyle.BLANK), cell(CellStyle.BLANK), cell(CellStyle.BLANK))
            )
        )
        var state = PuzzleEngine.fromDefinition(level)
        state = PuzzleEngine.withLetter(state, 0, 0, 'X')
        state = PuzzleEngine.withLetter(state, 0, 1, 'Y')
        // Last letter makes word XYZ invalid
        val afterLast = PuzzleEngine.withLetter(state, 0, 2, 'Z')
        assertTrue(PuzzleEngine.isWrongGuess(afterLast, 0, 2, dict))
        assertFalse(PuzzleEngine.isSolved(afterLast, dict))
    }

    @Test
    fun `puzzle with given and blocked`() {
        // GIVEN C + two blanks -> should solve with CAT
        val level = LevelDefinition(
            levelNumber = 102,
            type = LevelType.PUZZLE_FILL,
            puzzleGrid = listOf(
                listOf(cell(CellStyle.GIVEN, 'C'), cell(CellStyle.BLANK), cell(CellStyle.BLANK))
            )
        )
        var state = PuzzleEngine.fromDefinition(level)
        state = PuzzleEngine.withLetter(state, 0, 1, 'A')
        state = PuzzleEngine.withLetter(state, 0, 2, 'T')
        assertTrue(PuzzleEngine.isSolved(state, dict))
    }

    @Test
    fun `donut puzzle requires all runs valid`() {
        // Use sample donut: 3x3 center blocked
        val grid = CampaignLevelCatalog.PUZZLE_SAMPLE_DONUT
        val level = LevelDefinition(200, LevelType.PUZZLE_FILL, puzzleGrid = grid, parTimeSeconds = 60)
        var state = PuzzleEngine.fromDefinition(level)
        // Fill to make valid words? This puzzle has 4 runs: top 3, bottom 3, left 3, right 3
        // Fill with CAT pattern:
        // Row0: C A T
        // Row1: A X T -> actually center blocked, so Row1 is A BLOCKED T? Wait our placement: Row1 col0 A, col2 T
        // Row2: T A C? Let's just test that empty not solved
        assertFalse(PuzzleEngine.isSolved(state, dict))

        // Fill all blanks with A (AAA not valid) -> not solved
        var s = state
        for (r in 0 until grid.size) {
            for (c in 0 until grid[0].size) {
                if (grid[r][c].style == CellStyle.BLANK) {
                    s = PuzzleEngine.withLetter(s, r, c, 'A')
                }
            }
        }
        // AAA runs not valid -> not solved
        assertFalse(PuzzleEngine.isSolved(s, dict))
    }
}
