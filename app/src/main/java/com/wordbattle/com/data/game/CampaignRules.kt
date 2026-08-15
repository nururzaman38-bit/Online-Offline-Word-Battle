package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.CampaignConstants
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.LevelType
import java.time.Duration
import java.time.Instant

/**
 * Pure functions for campaign progression, star calculation and lives.
 * Follows ProfileRules pattern – no Android dependency, easily unit-tested.
 */
object CampaignRules {

    // -----------------------------------------------------------------------
    // Star calculation – SCORE_ATTACK
    // -----------------------------------------------------------------------

    /**
     * Returns stars 1..3 for SCORE_ATTACK.
     * @param playerTurnsUsed human player turns only
     */
    fun starsForScoreAttack(level: LevelDefinition, playerTurnsUsed: Int): Int {
        require(level.type == LevelType.SCORE_ATTACK)
        val par = level.parTurns ?: return 1
        val limit = level.turnLimit ?: return 1
        return when {
            playerTurnsUsed <= par -> 3
            playerTurnsUsed <= limit -> 2
            else -> 1
        }
    }

    /**
     * Returns stars 1..3 for PUZZLE_FILL.
     * Placeholder thresholds: <= par => 3, <= par*1.5 => 2, else 1
     */
    fun starsForPuzzle(level: LevelDefinition, elapsedSeconds: Int): Int {
        require(level.type == LevelType.PUZZLE_FILL)
        val par = level.parTimeSeconds ?: return 1
        return when {
            elapsedSeconds <= par -> 3
            elapsedSeconds <= (par * 1.5).toInt() -> 2
            else -> 1
        }
    }

    // -----------------------------------------------------------------------
    // Unlock rule
    // -----------------------------------------------------------------------

    /**
     * Pure unlock logic:
     * - levelNumber == currentCampaignLevel AND completed => campaignLevel +1
     * - star saved if better than previous
     * Replay or fail => campaignLevel unchanged.
     *
     * @return new campaign level after completing [levelNumber] with [stars]
     */
    fun nextCampaignLevel(
        currentCampaignLevel: Int,
        levelNumber: Int,
        completed: Boolean
    ): Int {
        if (!completed) return currentCampaignLevel
        return if (levelNumber == currentCampaignLevel) {
            (currentCampaignLevel + 1).coerceAtMost(CampaignConstants.TOTAL_CAMPAIGN_LEVELS + 1)
        } else {
            currentCampaignLevel
        }
    }

    fun shouldSaveStars(existingStars: Int?, newStars: Int): Boolean {
        if (existingStars == null) return true
        return newStars > existingStars
    }

    fun coinsRewardForFirstCompletion(stars: Int, isFirstCompletion: Boolean): Int {
        if (!isFirstCompletion) return 0
        return 10 + stars * 5
    }

    // -----------------------------------------------------------------------
    // Lives – time based regen
    // -----------------------------------------------------------------------

    data class LivesState(
        val current: Int,
        val max: Int,
        val lastRegenAtIso: String?
    )

    data class LivesRegenResult(
        val newCurrent: Int,
        val newLastRegenAtIso: String,
        val regenerated: Int
    )

    /**
     * Time-based regeneration, no running timer.
     * regenerated = min(max - current, elapsedMinutes / 20)
     * Updates lastLifeRegenAt so that app cold start still correct.
     */
    fun regenLives(
        current: Int,
        max: Int,
        lastRegenAtIso: String?,
        now: Instant = Instant.now()
    ): LivesRegenResult {
        val last = ProfileRules.parseInstant(lastRegenAtIso) ?: now
        val elapsedMinutes = Duration.between(last, now).toMinutes().coerceAtLeast(0)
        val canRegen = (max - current).coerceAtLeast(0)
        val regenerated = minOf(canRegen, (elapsedMinutes / CampaignConstants.LIFE_REGEN_MINUTES).toInt())

        if (regenerated <= 0) {
            return LivesRegenResult(current, last.toString(), 0)
        }

        val newCurrent = current + regenerated
        val newLast = if (newCurrent >= max) {
            // When capped, set last to now to avoid overcount
            now
        } else {
            last.plus(Duration.ofMinutes((regenerated * CampaignConstants.LIFE_REGEN_MINUTES).toLong()))
        }
        return LivesRegenResult(newCurrent, newLast.toString(), regenerated)
    }

    fun canEnterPuzzle(livesCurrent: Int): Boolean = livesCurrent > 0

    fun consumeLife(current: Int): Int = (current - 1).coerceAtLeast(0)

    data class PurchaseResult(val newLives: Int, val newCoins: Int, val success: Boolean)

    fun purchaseLife(livesCurrent: Int, livesMax: Int, coins: Int): PurchaseResult {
        if (coins < CampaignConstants.LIFE_COST_COINS) return PurchaseResult(livesCurrent, coins, false)
        if (livesCurrent >= livesMax) return PurchaseResult(livesCurrent, coins, false)
        return PurchaseResult(
            newLives = livesCurrent + 1,
            newCoins = coins - CampaignConstants.LIFE_COST_COINS,
            success = true
        )
    }

    data class RewardLifeResult(val newLives: Int, val capped: Boolean)

    fun rewardLife(livesCurrent: Int, livesMax: Int): RewardLifeResult {
        if (livesCurrent >= livesMax) return RewardLifeResult(livesCurrent, capped = true)
        return RewardLifeResult(livesCurrent + 1, capped = false)
    }

    // -----------------------------------------------------------------------
    // Daily life request cap – pure check
    // -----------------------------------------------------------------------

    fun canSendLifeRequest(todaySentCount: Int): Boolean =
        todaySentCount < CampaignConstants.DAILY_LIFE_REQUEST_LIMIT

    // -----------------------------------------------------------------------
    // Level failure check – SCORE_ATTACK turn limit
    // -----------------------------------------------------------------------

    fun isScoreAttackFailed(level: LevelDefinition, playerTurnsUsed: Int, hasReachedTarget: Boolean): Boolean {
        if (level.type != LevelType.SCORE_ATTACK) return false
        val limit = level.turnLimit ?: return false
        return playerTurnsUsed > limit && !hasReachedTarget
    }
}
