package com.wordbattle.com.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
    @PrimaryKey val uid: String,
    val json: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_games")
data class CachedGameEntity(
    @PrimaryKey val gameId: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_rooms")
data class CachedRoomEntity(
    @PrimaryKey val roomId: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_campaign_progress")
data class CachedCampaignProgressEntity(
    @PrimaryKey val id: String, // "${uid}-${levelNumber}"
    val uid: String,
    val levelNumber: Int,
    val stars: Int,
    val bestTimeSeconds: Int? = null,
    val bestTurns: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_requests")
data class CachedRequestEntity(
    @PrimaryKey val id: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_messages")
data class CachedMessageEntity(
    @PrimaryKey val id: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis()
)
