package com.wordbattle.com.data.game

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure validation rules for display names, usernames and the display-name change cooldown.
 * The same rules are enforced in Postgres (see `supabase/update_username_cooldown.sql`).
 */
object ProfileRules {

    const val MIN_DISPLAY_NAME = 3
    const val MAX_DISPLAY_NAME = 20
    const val MIN_USERNAME = 3
    const val MAX_USERNAME = 20
    const val DISPLAY_NAME_COOLDOWN_DAYS = 10

    private val USERNAME_REGEX = Regex("^[a-z0-9_]{$MIN_USERNAME,$MAX_USERNAME}$")

    enum class DisplayNameError { TOO_SHORT, TOO_LONG }

    enum class UsernameError { TOO_SHORT, TOO_LONG, INVALID_CHARACTERS }

    /** Trims and collapses inner whitespace so " Word   Player " becomes "Word Player". */
    fun normalizeDisplayName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /** Usernames are stored lowercase and without surrounding whitespace. */
    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    fun validateDisplayName(raw: String): DisplayNameError? {
        val value = normalizeDisplayName(raw)
        return when {
            value.length < MIN_DISPLAY_NAME -> DisplayNameError.TOO_SHORT
            value.length > MAX_DISPLAY_NAME -> DisplayNameError.TOO_LONG
            else -> null
        }
    }

    fun validateUsername(raw: String): UsernameError? {
        val value = normalizeUsername(raw)
        return when {
            value.length < MIN_USERNAME -> UsernameError.TOO_SHORT
            value.length > MAX_USERNAME -> UsernameError.TOO_LONG
            !USERNAME_REGEX.matches(value) -> UsernameError.INVALID_CHARACTERS
            else -> null
        }
    }

    fun isValidDisplayName(raw: String): Boolean = validateDisplayName(raw) == null

    fun isValidUsername(raw: String): Boolean = validateUsername(raw) == null

    /**
     * Whole days the user still has to wait before changing their display name again.
     *
     * @param lastChangeIso timestamp of the previous change, `null` when never changed.
     * @return 0 when a change is allowed right now.
     */
    fun cooldownDaysRemaining(lastChangeIso: String?, now: Instant = Instant.now()): Int {
        val last = parseInstant(lastChangeIso) ?: return 0
        val elapsed = Duration.between(last, now)
        if (elapsed.isNegative) return DISPLAY_NAME_COOLDOWN_DAYS
        val remaining = Duration.ofDays(DISPLAY_NAME_COOLDOWN_DAYS.toLong()) - elapsed
        if (remaining.isNegative || remaining.isZero) return 0
        // Round up: 0.2 days left still means "1 day".
        return Math.ceil(remaining.toMinutes() / (60.0 * 24.0)).toInt().coerceAtLeast(1)
    }

    fun canChangeDisplayName(lastChangeIso: String?, now: Instant = Instant.now()): Boolean =
        cooldownDaysRemaining(lastChangeIso, now) == 0

    /** Accepts both `2026-08-14T10:00:00Z` and Postgres' `2026-08-14 10:00:00+00` shapes. */
    fun parseInstant(value: String?): Instant? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        for (candidate in listOf(text, text.replace(' ', 'T'))) {
            val direct = runCatching { Instant.parse(candidate) }.getOrNull()
            if (direct != null) return direct
            val offset = runCatching { OffsetDateTime.parse(normalizeOffset(candidate)).toInstant() }.getOrNull()
            if (offset != null) return offset
        }
        return null
    }

    private fun normalizeOffset(value: String): String {
        // Postgres emits "+00" / "+05:30"; OffsetDateTime needs at least "+00:00".
        val match = Regex("([+-])(\\d{2})$").find(value) ?: return value
        return value.substring(0, match.range.first) + match.groupValues[1] + match.groupValues[2] + ":00"
    }
}
