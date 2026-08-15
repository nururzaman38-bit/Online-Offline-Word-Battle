package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.CellStyle
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.LevelType
import com.wordbattle.com.data.model.PuzzleCellDef

/**
 * Campaign level catalog – 500 levels scaled.
 *
 * Level 1-20: exact table from spec (SCORE_ATTACK only).
 * Level 21-500: generator function with formula, alternating 3 SCORE_ATTACK + 2 PUZZLE_FILL per 5-block.
 *
 * This file is intentionally marked as scaffold for 21-500 – later hand-tuned.
 */
object CampaignLevelCatalog {

    // -----------------------------------------------------------------------
    // Sample PUZZLE_FILL grids – used for both 21-500 generator and explicit examples
    // -----------------------------------------------------------------------

    private fun cell(style: CellStyle, letter: Char? = null) = PuzzleCellDef(style, letter)

    // 3 blanks in a row – any valid 3-letter word solves it
    val PUZZLE_SAMPLE_3ROW: List<List<PuzzleCellDef>> = listOf(
        listOf(cell(CellStyle.BLANK), cell(CellStyle.BLANK), cell(CellStyle.BLANK))
    )

    // 3x3 with center blocked – top/bottom rows 3 letters, left/right cols 3 letters
    val PUZZLE_SAMPLE_DONUT: List<List<PuzzleCellDef>> = listOf(
        listOf(cell(CellStyle.BLANK), cell(CellStyle.BLANK), cell(CellStyle.BLANK)),
        listOf(cell(CellStyle.BLANK), cell(CellStyle.BLOCKED), cell(CellStyle.BLANK)),
        listOf(cell(CellStyle.BLANK), cell(CellStyle.BLANK), cell(CellStyle.BLANK))
    )

    // Cross shape
    val PUZZLE_SAMPLE_CROSS: List<List<PuzzleCellDef>> = listOf(
        listOf(cell(CellStyle.BLOCKED), cell(CellStyle.BLANK), cell(CellStyle.BLOCKED)),
        listOf(cell(CellStyle.BLANK), cell(CellStyle.BLANK), cell(CellStyle.BLANK)),
        listOf(cell(CellStyle.BLOCKED), cell(CellStyle.BLANK), cell(CellStyle.BLOCKED))
    )

    // Two separate words
    val PUZZLE_SAMPLE_TWO_WORDS: List<List<PuzzleCellDef>> = listOf(
        listOf(cell(CellStyle.BLANK), cell(CellStyle.BLANK), cell(CellStyle.BLOCKED), cell(CellStyle.BLANK), cell(CellStyle.BLANK))
    )

    // GIVEN anchor + blanks – e.g., C A T with first letter given
    val PUZZLE_SAMPLE_GIVEN: List<List<PuzzleCellDef>> = listOf(
        listOf(cell(CellStyle.GIVEN, 'C'), cell(CellStyle.BLANK), cell(CellStyle.BLANK))
    )

    private val SAMPLE_PUZZLES = listOf(
        PUZZLE_SAMPLE_3ROW,
        PUZZLE_SAMPLE_DONUT,
        PUZZLE_SAMPLE_CROSS,
        PUZZLE_SAMPLE_TWO_WORDS,
        PUZZLE_SAMPLE_GIVEN
    )

    // -----------------------------------------------------------------------
    // Exact table 1-20
    // -----------------------------------------------------------------------

    private val EXACT_1_20 = listOf(
        // level, target, ai, turnTime, turnLimit, par, boss
        LevelDefinition(1, LevelType.SCORE_ATTACK, targetScore = 30, aiDifficulty = AiDifficulty.EASY, turnTimeSeconds = null, turnLimit = 10, parTurns = 6, isBoss = false),
        LevelDefinition(2, LevelType.SCORE_ATTACK, targetScore = 40, aiDifficulty = AiDifficulty.EASY, turnTimeSeconds = null, turnLimit = 12, parTurns = 7, isBoss = false),
        LevelDefinition(3, LevelType.SCORE_ATTACK, targetScore = 50, aiDifficulty = AiDifficulty.EASY, turnTimeSeconds = 60, turnLimit = 14, parTurns = 8, isBoss = false),
        LevelDefinition(4, LevelType.SCORE_ATTACK, targetScore = 55, aiDifficulty = AiDifficulty.EASY, turnTimeSeconds = 60, turnLimit = 15, parTurns = 8, isBoss = false),
        LevelDefinition(5, LevelType.SCORE_ATTACK, targetScore = 65, aiDifficulty = AiDifficulty.EASY, turnTimeSeconds = 45, turnLimit = 17, parTurns = 9, isBoss = true),
        LevelDefinition(6, LevelType.SCORE_ATTACK, targetScore = 70, aiDifficulty = AiDifficulty.MEDIUM, turnTimeSeconds = 45, turnLimit = 18, parTurns = 10, isBoss = false),
        LevelDefinition(7, LevelType.SCORE_ATTACK, targetScore = 75, aiDifficulty = AiDifficulty.MEDIUM, turnTimeSeconds = 45, turnLimit = 19, parTurns = 11, isBoss = false),
        LevelDefinition(8, LevelType.SCORE_ATTACK, targetScore = 80, aiDifficulty = AiDifficulty.MEDIUM, turnTimeSeconds = 45, turnLimit = 20, parTurns = 11, isBoss = false),
        LevelDefinition(9, LevelType.SCORE_ATTACK, targetScore = 85, aiDifficulty = AiDifficulty.MEDIUM, turnTimeSeconds = 45, turnLimit = 21, parTurns = 12, isBoss = false),
        LevelDefinition(10, LevelType.SCORE_ATTACK, targetScore = 100, aiDifficulty = AiDifficulty.MEDIUM, turnTimeSeconds = 45, turnLimit = 25, parTurns = 14, isBoss = true),
        LevelDefinition(11, LevelType.SCORE_ATTACK, targetScore = 100, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 45, turnLimit = 25, parTurns = 13, isBoss = false),
        LevelDefinition(12, LevelType.SCORE_ATTACK, targetScore = 105, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 26, parTurns = 14, isBoss = false),
        LevelDefinition(13, LevelType.SCORE_ATTACK, targetScore = 110, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 28, parTurns = 15, isBoss = false),
        LevelDefinition(14, LevelType.SCORE_ATTACK, targetScore = 115, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 29, parTurns = 15, isBoss = false),
        LevelDefinition(15, LevelType.SCORE_ATTACK, targetScore = 120, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 30, parTurns = 16, isBoss = true),
        LevelDefinition(16, LevelType.SCORE_ATTACK, targetScore = 125, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 31, parTurns = 16, isBoss = false),
        LevelDefinition(17, LevelType.SCORE_ATTACK, targetScore = 130, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 32, parTurns = 17, isBoss = false),
        LevelDefinition(18, LevelType.SCORE_ATTACK, targetScore = 135, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 33, parTurns = 17, isBoss = false),
        LevelDefinition(19, LevelType.SCORE_ATTACK, targetScore = 140, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 35, parTurns = 18, isBoss = false),
        LevelDefinition(20, LevelType.SCORE_ATTACK, targetScore = 150, aiDifficulty = AiDifficulty.HARD, turnTimeSeconds = 30, turnLimit = 38, parTurns = 19, isBoss = true),
    )

    fun getExactLevel(levelNumber: Int): LevelDefinition? =
        EXACT_1_20.firstOrNull { it.levelNumber == levelNumber }

    /**
     * Generator for 21-500 – PLACEHOLDER / SCAFFOLDING.
     * TODO: hand-tune after playtesting.
     *
     * Rules from spec:
     * - targetScore slowly increasing
     * - aiDifficulty capped to HARD after ~20
     * - turnTimeSeconds floored at 30
     * - every 5th level isBoss = true
     * - alternate 3 SCORE_ATTACK + 2 PUZZLE_FILL per 5-block
     */
    fun generateLevelDefinition(levelNumber: Int): LevelDefinition {
        require(levelNumber in 1..500) { "levelNumber must be 1..500" }
        if (levelNumber <= 20) return getExactLevel(levelNumber)!!

        // Alternate pattern: in each 5-block, index 0,1,2 => SCORE_ATTACK, 3,4 => PUZZLE_FILL
        val blockIndex = (levelNumber - 21) % 5
        val isPuzzle = blockIndex >= 3

        val isBoss = levelNumber % 5 == 0

        return if (isPuzzle) {
            // PUZZLE_FILL scaffold
            // parTime scales slowly: base 60 + level*1, capped maybe 180
            val basePar = 60 + (levelNumber - 20) * 2
            val par = basePar.coerceAtMost(180)
            val puzzleGrid = SAMPLE_PUZZLES[(levelNumber - 21) % SAMPLE_PUZZLES.size]
            LevelDefinition(
                levelNumber = levelNumber,
                type = LevelType.PUZZLE_FILL,
                puzzleGrid = puzzleGrid,
                parTimeSeconds = par,
                isBoss = isBoss
            )
        } else {
            // SCORE_ATTACK scaffold – target slowly increasing
            // From level 20 target 150, +5 per level + small boss bump
            val targetBase = 150 + (levelNumber - 20) * 5
            val targetWithBoss = if (isBoss) targetBase + 10 else targetBase
            val target = targetWithBoss.coerceAtMost(400) // cap to keep reasonable

            // turnLimit grows with target
            val turnLimitBase = 38 + (levelNumber - 20) * 1
            val turnLimit = (turnLimitBase + if (isBoss) 2 else 0).coerceAtMost(60)

            // parTurns is about 55-60% of turnLimit
            val parTurns = (turnLimit * 0.6).toInt().coerceAtLeast(5)

            // turnTime floored at 30
            val turnTime = 30

            LevelDefinition(
                levelNumber = levelNumber,
                type = LevelType.SCORE_ATTACK,
                targetScore = target,
                aiDifficulty = AiDifficulty.HARD, // capped after 20
                turnTimeSeconds = turnTime,
                turnLimit = turnLimit,
                parTurns = parTurns,
                isBoss = isBoss
            )
        }
    }

    fun allLevels(): List<LevelDefinition> = (1..500).map { generateLevelDefinition(it) }

    fun scoreAttackLevels(): List<LevelDefinition> = allLevels().filter { it.type == LevelType.SCORE_ATTACK }

    fun puzzleLevels(): List<LevelDefinition> = allLevels().filter { it.type == LevelType.PUZZLE_FILL }
}
