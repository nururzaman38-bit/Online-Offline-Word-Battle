package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.AiMove
import com.wordbattle.com.data.model.BoardCoordinate
import com.wordbattle.com.data.model.GameState
import kotlin.random.Random

enum class AiDifficulty { EASY, MEDIUM, HARD }

/** Exhaustive, deterministic-capable, fully offline computer player with difficulty tiers. */
class ComputerAI(
    private val dictionary: WordDictionary,
    private val random: Random = Random.Default,
    private val difficulty: AiDifficulty = AiDifficulty.HARD
) {
    fun chooseMove(game: GameState): AiMove? {
        if (game.board.cells.flatten().none { it.letter == null }) return null
        val used = game.usedWords.map { it.word.uppercase() }.toHashSet()
        val candidates = orderedEmptyCells(game)

        // Collect all scored moves for difficulty handling
        val allMoves = mutableListOf<AiMove>()
        var best: AiMove? = null

        candidates.forEach { coordinate ->
            for (letter in 'A'..'Z') {
                val simulated = WordEngine.place(
                    game.board, coordinate.row, coordinate.col, letter, game.currentTurnPlayerId
                ) ?: continue
                val score = moveScore(simulated, coordinate.row, coordinate.col, used)
                val move = AiMove(coordinate.row, coordinate.col, letter, score)
                allMoves += move
                if (score > (best?.score ?: 0)) {
                    best = move
                }
            }
        }

        if (allMoves.isEmpty()) {
            // No empty cells (should have returned earlier) – fallback
            if (candidates.isEmpty()) return null
            val fallbackCell = candidates.take(adjacentCandidateCount(game).coerceAtLeast(1)).random(random)
            val commonLetters = charArrayOf('E', 'A', 'O', 'I', 'N')
            return AiMove(fallbackCell.row, fallbackCell.col, commonLetters.random(random), POINTS_PER_LETTER)
        }

        return when (difficulty) {
            AiDifficulty.HARD -> {
                // Current optimal logic unchanged
                best ?: allMoves.maxByOrNull { it.score }
            }
            AiDifficulty.MEDIUM -> {
                // Pick randomly among top 3 scoring moves
                val top = allMoves.sortedByDescending { it.score }.take(3)
                if (top.isEmpty()) {
                    best
                } else {
                    top.random(random)
                }
            }
            AiDifficulty.EASY -> {
                // 50% best, 50% fallback random common letter
                if (random.nextDouble() < 0.5) {
                    val fallbackCell = candidates.take(adjacentCandidateCount(game).coerceAtLeast(1)).random(random)
                    val commonLetters = charArrayOf('E', 'A', 'O', 'I', 'N')
                    AiMove(fallbackCell.row, fallbackCell.col, commonLetters.random(random), POINTS_PER_LETTER)
                } else {
                    best ?: allMoves.maxByOrNull { it.score }
                }
            }
        }
    }

    /**
     * Mirrors `GameRepository.placeLetter`: one point for the letter itself, plus the longest
     * unused dictionary word running through the new cell on each axis.
     */
    private fun moveScore(board: com.wordbattle.com.data.model.BoardState, row: Int, col: Int, used: Set<String>): Int {
        var score = POINTS_PER_LETTER
        val claimed = used.toMutableSet()
        WordAxis.entries.forEach { axis ->
            val word = WordEngine.segments(board, row, col, axis)
                .firstOrNull { dictionary.isValidWord(it.word) && it.word.uppercase() !in claimed }
            if (word != null) {
                score += word.word.length
                claimed += word.word.uppercase()
            }
        }
        return score
    }

    private fun orderedEmptyCells(game: GameState): List<BoardCoordinate> {
        val board = game.board
        val empties = board.cells.flatten().filter { it.letter == null }
        if (empties.size == board.rows * board.cols) {
            return listOf(BoardCoordinate(board.rows / 2, board.cols / 2))
        }
        return empties.sortedWith(
            compareByDescending<com.wordbattle.com.data.model.Cell> { hasFilledNeighbor(game, it.row, it.col) }
                .thenBy { kotlin.math.abs(it.row - board.rows / 2) + kotlin.math.abs(it.col - board.cols / 2) }
        ).map { BoardCoordinate(it.row, it.col) }
    }

    private fun adjacentCandidateCount(game: GameState): Int =
        game.board.cells.flatten().count { it.letter == null && hasFilledNeighbor(game, it.row, it.col) }

    private fun hasFilledNeighbor(game: GameState, row: Int, col: Int): Boolean =
        listOf(row - 1 to col, row + 1 to col, row to col - 1, row to col + 1)
            .any { (r, c) -> game.board.cell(r, c)?.letter != null }

    companion object {
        private const val POINTS_PER_LETTER = 1
    }
}
