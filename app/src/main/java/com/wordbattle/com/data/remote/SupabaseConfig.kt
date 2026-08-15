package com.wordbattle.com.data.remote

/**
 * Pure validation helpers for the Supabase connection settings.
 *
 * These live outside [SupabaseClientProvider] so they can be unit tested without touching the
 * Supabase SDK, and so a misconfigured URL/key fails loudly instead of surfacing later as an
 * unexplained "room could not be created" error.
 */
object SupabaseConfig {

    /** Legacy anon JWT keys start with the base64 header of `{"alg":`. */
    private const val LEGACY_ANON_PREFIX = "eyJ"
    private const val PUBLISHABLE_PREFIX = "sb_publishable_"
    private const val SECRET_PREFIX = "sb_secret_"

    enum class Problem {
        URL_BLANK,
        URL_NOT_HTTPS,
        URL_HAS_WHITESPACE,
        URL_HAS_TRAILING_SLASH,
        URL_NOT_SUPABASE,
        KEY_BLANK,
        KEY_HAS_WHITESPACE,
        KEY_UNKNOWN_FORMAT,
        KEY_LOOKS_LIKE_SERVICE_ROLE
    }

    /** Returns every problem found with the supplied URL/key pair; empty means the pair is usable. */
    fun problems(url: String, key: String): List<Problem> {
        val found = mutableListOf<Problem>()
        when {
            url.isBlank() -> found += Problem.URL_BLANK
            url != url.trim() || url.any(Char::isWhitespace) -> found += Problem.URL_HAS_WHITESPACE
        }
        if (url.isNotBlank()) {
            if (!url.startsWith("https://")) found += Problem.URL_NOT_HTTPS
            if (url.endsWith("/")) found += Problem.URL_HAS_TRAILING_SLASH
            val host = url.removePrefix("https://").removePrefix("http://").substringBefore('/')
            if (!host.endsWith(".supabase.co") && !host.endsWith(".supabase.in")) {
                found += Problem.URL_NOT_SUPABASE
            }
        }
        when {
            key.isBlank() -> found += Problem.KEY_BLANK
            key != key.trim() || key.any(Char::isWhitespace) -> found += Problem.KEY_HAS_WHITESPACE
        }
        if (key.isNotBlank()) {
            if (key.startsWith(SECRET_PREFIX) || key.contains("service_role")) {
                found += Problem.KEY_LOOKS_LIKE_SERVICE_ROLE
            } else if (!key.startsWith(PUBLISHABLE_PREFIX) && !key.startsWith(LEGACY_ANON_PREFIX)) {
                found += Problem.KEY_UNKNOWN_FORMAT
            }
        }
        return found
    }

    fun isValid(url: String, key: String): Boolean = problems(url, key).isEmpty()

    fun describe(problems: List<Problem>): String =
        "Supabase configuration is invalid: " + problems.joinToString(", ") { it.name }
}
