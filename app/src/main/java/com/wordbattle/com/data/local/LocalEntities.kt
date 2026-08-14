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
