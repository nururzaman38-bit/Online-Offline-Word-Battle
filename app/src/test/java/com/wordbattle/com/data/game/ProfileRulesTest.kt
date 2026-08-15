package com.wordbattle.com.data.game

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {

    private val now: Instant = Instant.parse("2026-08-14T12:00:00Z")

    @Test
    fun `display names are trimmed and inner whitespace collapsed`() {
        assertEquals("Word Player", ProfileRules.normalizeDisplayName("  Word   Player  "))
    }

    @Test
    fun `display name length is 3 to 20`() {
        assertEquals(ProfileRules.DisplayNameError.TOO_SHORT, ProfileRules.validateDisplayName("ab"))
        assertEquals(ProfileRules.DisplayNameError.TOO_SHORT, ProfileRules.validateDisplayName("   a  "))
        assertNull(ProfileRules.validateDisplayName("abc"))
        assertNull(ProfileRules.validateDisplayName("a".repeat(20)))
        assertEquals(ProfileRules.DisplayNameError.TOO_LONG, ProfileRules.validateDisplayName("a".repeat(21)))
        assertTrue(ProfileRules.isValidDisplayName("Nurur Zaman"))
        assertFalse(ProfileRules.isValidDisplayName(""))
    }

    @Test
    fun `usernames are lowercased and trimmed`() {
        assertEquals("word_battle1", ProfileRules.normalizeUsername("  Word_Battle1 "))
    }

    @Test
    fun `usernames accept only lowercase letters digits and underscore`() {
        assertNull(ProfileRules.validateUsername("player_01"))
        assertNull(ProfileRules.validateUsername("ABC"))
        assertEquals(ProfileRules.UsernameError.TOO_SHORT, ProfileRules.validateUsername("ab"))
        assertEquals(ProfileRules.UsernameError.TOO_LONG, ProfileRules.validateUsername("a".repeat(21)))
        assertEquals(ProfileRules.UsernameError.INVALID_CHARACTERS, ProfileRules.validateUsername("has space"))
        assertEquals(ProfileRules.UsernameError.INVALID_CHARACTERS, ProfileRules.validateUsername("dash-name"))
        assertEquals(ProfileRules.UsernameError.INVALID_CHARACTERS, ProfileRules.validateUsername("emoji😀name"))
        assertFalse(ProfileRules.isValidUsername("bad!"))
        assertTrue(ProfileRules.isValidUsername("nurur38"))
    }

    @Test
    fun `no cooldown when the name was never changed`() {
        assertEquals(0, ProfileRules.cooldownDaysRemaining(null, now))
        assertEquals(0, ProfileRules.cooldownDaysRemaining("   ", now))
        assertTrue(ProfileRules.canChangeDisplayName(null, now))
    }

    @Test
    fun `cooldown counts ten whole days`() {
        val justNow = now.toString()
        assertEquals(10, ProfileRules.cooldownDaysRemaining(justNow, now))

        val fourDaysAgo = now.minus(4, ChronoUnit.DAYS).toString()
        assertEquals(6, ProfileRules.cooldownDaysRemaining(fourDaysAgo, now))

        val elevenDaysAgo = now.minus(11, ChronoUnit.DAYS).toString()
        assertEquals(0, ProfileRules.cooldownDaysRemaining(elevenDaysAgo, now))
        assertTrue(ProfileRules.canChangeDisplayName(elevenDaysAgo, now))
    }

    @Test
    fun `a partial day still counts as one remaining day`() {
        val almostOver = now.minus(10, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS).toString()
        assertEquals(1, ProfileRules.cooldownDaysRemaining(almostOver, now))
        assertFalse(ProfileRules.canChangeDisplayName(almostOver, now))
    }

    @Test
    fun `postgres timestamp shapes are parsed`() {
        val expected = Instant.parse("2026-08-04T12:00:00Z")
        assertEquals(expected, ProfileRules.parseInstant("2026-08-04T12:00:00Z"))
        assertEquals(expected, ProfileRules.parseInstant("2026-08-04 12:00:00+00"))
        assertEquals(expected, ProfileRules.parseInstant("2026-08-04T12:00:00+00:00"))
        assertNull(ProfileRules.parseInstant("not a timestamp"))
        assertNull(ProfileRules.parseInstant(null))
    }
}
