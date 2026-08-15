package com.wordbattle.com

import android.app.Application
import com.wordbattle.com.data.audio.SoundManager
import com.wordbattle.com.data.local.AppDatabase
import com.wordbattle.com.data.network.NetworkConnectivityObserver
import com.wordbattle.com.data.remote.SupabaseClientProvider
import com.wordbattle.com.data.repository.AuthRepository
import com.wordbattle.com.data.repository.RoomRepository
import com.wordbattle.com.data.repository.UserRepository
import kotlinx.serialization.json.Json

class WordBattleApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Start listening immediately so the first screen already knows whether we are online.
        container.network.start()
    }
}

class AppContainer(application: Application) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    // For Supabase payloads we want defaults + nulls when explicitly set (campaign, requests)
    val supabaseJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }
    val database = AppDatabase.getInstance(application)
    val supabase = SupabaseClientProvider.client
    val userRepository = UserRepository(supabase, database.profileDao(), json)
    val authRepository = AuthRepository(supabase, userRepository)
    val roomRepository = RoomRepository(supabase, database.roomCacheDao(), json)
    val campaignRepository = com.wordbattle.com.data.repository.CampaignRepository(supabase, database.campaignProgressDao())
    val requestRepository = com.wordbattle.com.data.repository.RequestRepository(supabase, database.requestDao(), supabaseJson)
    val messageRepository = com.wordbattle.com.data.repository.MessageRepository(supabase, database.messageDao(), supabaseJson)

    /** Single source of truth for internet availability, shared by every screen. */
    val network = NetworkConnectivityObserver(application)

    /** Effects + looping battle theme; survives configuration changes with the container. */
    val sound = SoundManager(application)
}
