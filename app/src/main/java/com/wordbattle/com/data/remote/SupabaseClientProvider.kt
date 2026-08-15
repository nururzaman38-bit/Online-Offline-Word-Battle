package com.wordbattle.com.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {
    const val PROJECT_URL = "https://nbaqziiifpkajekyrmsk.supabase.co"

    // A publishable client key is intentionally safe to ship. Never add a service_role key here.
    const val PUBLISHABLE_KEY = "sb_publishable_vKlUzrpprdtgztIkb3-XmA_1Is9OUl7"

    /** Problems with the compiled-in URL/key pair. Empty in a correctly configured build. */
    val configurationProblems: List<SupabaseConfig.Problem>
        get() = SupabaseConfig.problems(PROJECT_URL, PUBLISHABLE_KEY)

    val isConfigured: Boolean get() = configurationProblems.isEmpty()

    val client by lazy {
        val problems = configurationProblems
        check(problems.isEmpty()) { SupabaseConfig.describe(problems) }
        createSupabaseClient(
            supabaseUrl = PROJECT_URL,
            supabaseKey = PUBLISHABLE_KEY
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
