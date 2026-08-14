package com.wordbattle.com.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {
    const val PROJECT_URL = "https://nbaqziiifpkajekyrmsk.supabase.co"
    // A publishable client key is intentionally safe to ship. Never add a service_role key here.
    const val PUBLISHABLE_KEY = "sb_publishable_vKlUzrpprdtgztIkb3-XmA_1Is9OUl7"

    val client by lazy {
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
