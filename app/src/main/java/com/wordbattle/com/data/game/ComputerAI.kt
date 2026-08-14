package com.wordbattle.com.data.game

import com.wordbattle.com.data.dictionary.WordDictionary
import com.wordbattle.com.data.model.AiMove
import com.wordbattle.com.data.model.BoardCoordinate
import com.wordbattle.com.data.model.GameState
import kotlin.random.Random

/** Exhaustive, deterministic-capable, fully offline computer player. */
class ComputerAI(
    private val dictionary: WordDictionary,
    private val random: Random = Random.Default
) {
    fun chooseMove(game: GameState): AiMove? {
        if (game.board.cells.flatten().none { it.letter == null }) return null
        val used = game.usedWords.map { it.word.uppercase() }.toHashSet()
        val candidates = orderedEmptyCells(game)
        var best: AiMove? = null

        candidates.forEach { coordinate ->
            for (letter in 'A'..'Z') {
                val simulated = WordEngine.place(
                    game.board, coordinate.row, coordinate.col, letter, game.currentTurnPlayerId
                ) ?: continue
                val score = WordEngine.findCandidateWords(simulated, coordinate.row, coordinate.col)
                    .filter { dictionary.isValidWord(it.word) && it.word !in used }
                    .sumOf { it.word.length }
                if (score > (best?.score ?: 0)) {
                    best = AiMove(coordinate.row, coordinate.col, letter, score)
                }
            }
        }
        if (best != null) return best

        val fallbackCell = candidates.take(adjacentCandidateCount(game).coerceAtLeast(1)).random(random)
        val commonLetters = charArrayOf('E', 'A', 'O', 'I', 'N')
        return AiMove(fallbackCell.row, fallbackCell.col, commonLetters.random(random), 0)
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
}
