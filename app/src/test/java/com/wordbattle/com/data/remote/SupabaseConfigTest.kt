package com.wordbattle.com.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {

    private val url = "https://nbaqziiifpkajekyrmsk.supabase.co"
    private val key = "sb_publishable_vKlUzrpprdtgztIkb3-XmA_1Is9OUl7"

    @Test
    fun `the shipped configuration is valid`() {
        assertEquals(emptyList<SupabaseConfig.Problem>(), SupabaseClientProvider.configurationProblems)
        assertTrue(SupabaseClientProvider.isConfigured)
        assertTrue(SupabaseConfig.isValid(url, key))
    }

    @Test
    fun `legacy anon jwt keys are still accepted`() {
        assertTrue(SupabaseConfig.isValid(url, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"))
    }

    @Test
    fun `a service role key is rejected`() {
        assertTrue(
            SupabaseConfig.problems(url, "sb_secret_abcdefghijklmnop")
                .contains(SupabaseConfig.Problem.KEY_LOOKS_LIKE_SERVICE_ROLE)
        )
        assertTrue(
            SupabaseConfig.problems(url, "eyJhbGciOi.eyJyb2xlIjoic2VydmljZV9yb2xlIn0.service_role")
                .contains(SupabaseConfig.Problem.KEY_LOOKS_LIKE_SERVICE_ROLE)
        )
    }

    @Test
    fun `bad urls are reported`() {
        assertTrue(SupabaseConfig.problems("", key).contains(SupabaseConfig.Problem.URL_BLANK))
        assertTrue(
            SupabaseConfig.problems("http://nbaq.supabase.co", key)
                .contains(SupabaseConfig.Problem.URL_NOT_HTTPS)
        )
        assertTrue(
            SupabaseConfig.problems("$url/", key)
                .contains(SupabaseConfig.Problem.URL_HAS_TRAILING_SLASH)
        )
        assertTrue(
            SupabaseConfig.problems("https://example.com", key)
                .contains(SupabaseConfig.Problem.URL_NOT_SUPABASE)
        )
        assertTrue(
            SupabaseConfig.problems(" $url", key)
                .contains(SupabaseConfig.Problem.URL_HAS_WHITESPACE)
        )
    }

    @Test
    fun `bad keys are reported`() {
        assertTrue(SupabaseConfig.problems(url, "").contains(SupabaseConfig.Problem.KEY_BLANK))
        assertTrue(
            SupabaseConfig.problems(url, "not-a-key").contains(SupabaseConfig.Problem.KEY_UNKNOWN_FORMAT)
        )
        assertTrue(
            SupabaseConfig.problems(url, "sb_publishable_with space")
                .contains(SupabaseConfig.Problem.KEY_HAS_WHITESPACE)
        )
        assertFalse(SupabaseConfig.isValid(url, ""))
    }

    @Test
    fun `describe names every problem`() {
        val described = SupabaseConfig.describe(SupabaseConfig.problems("", ""))
        assertTrue(described.contains("URL_BLANK"))
        assertTrue(described.contains("KEY_BLANK"))
    }
}
