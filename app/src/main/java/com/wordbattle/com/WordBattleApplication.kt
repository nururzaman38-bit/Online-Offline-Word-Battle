package com.wordbattle.com

import android.app.Application
import com.wordbattle.com.data.local.AppDatabase
import com.wordbattle.com.data.remote.SupabaseClientProvider
import com.wordbattle.com.data.repository.AuthRepository
import com.wordbattle.com.data.repository.RoomRepository
import com.wordbattle.com.data.repository.UserRepository
import kotlinx.serialization.json.Json

class WordBattleApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    val database = AppDatabase.getInstance(application)
    val supabase = SupabaseClientProvider.client
    val userRepository = UserRepository(supabase, database.profileDao(), json)
    val authRepository = AuthRepository(supabase, userRepository)
    val roomRepository = RoomRepository(supabase, database.roomCacheDao(), json)
}
