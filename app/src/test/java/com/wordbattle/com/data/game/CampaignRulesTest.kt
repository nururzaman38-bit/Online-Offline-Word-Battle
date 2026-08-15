package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.model.LevelType
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CampaignRulesTest {

    private val now = Instant.parse("2026-08-14T12:00:00Z")

    @Test
    fun `stars for score attack based on par and limit`() {
        val level = LevelDefinition(
            levelNumber = 1,
            type = LevelType.SCORE_ATTACK,
            targetScore = 30,
            aiDifficulty = AiDifficulty.EASY,
            turnTimeSeconds = null,
            turnLimit = 10,
            parTurns = 6
        )
        assertEquals(3, CampaignRules.starsForScoreAttack(level, 6))
        assertEquals(3, CampaignRules.starsForScoreAttack(level, 5))
        assertEquals(2, CampaignRules.starsForScoreAttack(level, 7))
        assertEquals(2, CampaignRules.starsForScoreAttack(level, 10))
        assertEquals(1, CampaignRules.starsForScoreAttack(level, 11))
    }

    @Test
    fun `stars for puzzle based on par time`() {
        val level = LevelDefinition(
            levelNumber = 21,
            type = LevelType.PUZZLE_FILL,
            parTimeSeconds = 60
        )
        assertEquals(3, CampaignRules.starsForPuzzle(level, 60))
        assertEquals(3, CampaignRules.starsForPuzzle(level, 30))
        assertEquals(2, CampaignRules.starsForPuzzle(level, 90)) // 60*1.5
        assertEquals(2, CampaignRules.starsForPuzzle(level, 89))
        assertEquals(1, CampaignRules.starsForPuzzle(level, 91))
    }

    @Test
    fun `unlock logic increments when completing current level`() {
        assertEquals(2, CampaignRules.nextCampaignLevel(1, 1, true))
        assertEquals(1, CampaignRules.nextCampaignLevel(1, 1, false)) // fail
        assertEquals(5, CampaignRules.nextCampaignLevel(5, 3, true)) // replay old level doesn't increment
        assertEquals(6, CampaignRules.nextCampaignLevel(5, 5, true))
    }

    @Test
    fun `should save stars only if better`() {
        assertTrue(CampaignRules.shouldSaveStars(null, 2))
        assertTrue(CampaignRules.shouldSaveStars(2, 3))
        assertFalse(CampaignRules.shouldSaveStars(3, 2))
        assertFalse(CampaignRules.shouldSaveStars(3, 3))
    }

    @Test
    fun `coins reward only on first completion`() {
        assertEquals(25, CampaignRules.coinsRewardForFirstCompletion(3, true)) // 10+3*5
        assertEquals(20, CampaignRules.coinsRewardForFirstCompletion(2, true))
        assertEquals(0, CampaignRules.coinsRewardForFirstCompletion(3, false))
    }

    @Test
    fun `lives regen time based`() {
        val last = now.minus(40, ChronoUnit.MINUTES).toString()
        val result = CampaignRules.regenLives(current = 1, max = 3, lastRegenAtIso = last, now = now)
        assertEquals(2, result.regenerated)
        assertEquals(3, result.newCurrent)

        val last2 = now.minus(10, ChronoUnit.MINUTES).toString()
        val result2 = CampaignRules.regenLives(2, 3, last2, now)
        assertEquals(0, result2.regenerated)
        assertEquals(2, result2.newCurrent)

        val last3 = now.minus(100, ChronoUnit.MINUTES).toString()
        val result3 = CampaignRules.regenLives(0, 3, last3, now)
        assertEquals(3, result3.regenerated) // capped at max
        assertEquals(3, result3.newCurrent)
    }

    @Test
    fun `can enter puzzle only with lives`() {
        assertTrue(CampaignRules.canEnterPuzzle(1))
        assertFalse(CampaignRules.canEnterPuzzle(0))
    }

    @Test
    fun `consume and purchase life`() {
        assertEquals(2, CampaignRules.consumeLife(3))
        assertEquals(0, CampaignRules.consumeLife(0))

        val purchaseOk = CampaignRules.purchaseLife(1, 3, 100)
        assertTrue(purchaseOk.success)
        assertEquals(2, purchaseOk.newLives)
        assertEquals(70, purchaseOk.newCoins)

        val purchaseFailCoins = CampaignRules.purchaseLife(1, 3, 10)
        assertFalse(purchaseFailCoins.success)

        val purchaseFailMax = CampaignRules.purchaseLife(3, 3, 100)
        assertFalse(purchaseFailMax.success)
    }

    @Test
    fun `daily life request cap pure check`() {
        assertTrue(CampaignRules.canSendLifeRequest(0))
        assertTrue(CampaignRules.canSendLifeRequest(4))
        assertFalse(CampaignRules.canSendLifeRequest(5))
        assertFalse(CampaignRules.canSendLifeRequest(10))
    }

    @Test
    fun `score attack failed when turns exceed limit without target`() {
        val level = LevelDefinition(
            levelNumber = 1,
            type = LevelType.SCORE_ATTACK,
            targetScore = 30,
            turnLimit = 10
        )
        assertTrue(CampaignRules.isScoreAttackFailed(level, 11, false))
        assertFalse(CampaignRules.isScoreAttackFailed(level, 11, true)) // reached target even if over
        assertFalse(CampaignRules.isScoreAttackFailed(level, 10, false))
    }
}
