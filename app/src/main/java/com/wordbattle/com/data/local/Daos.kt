package com.wordbattle.com.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM cached_profiles WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): CachedProfileEntity?

    @Query("SELECT * FROM cached_profiles WHERE uid = :uid LIMIT 1")
    fun observe(uid: String): Flow<CachedProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CachedProfileEntity)

    @Query("DELETE FROM cached_profiles")
    suspend fun clear()
}

@Dao
interface GameDao {
    @Query("SELECT * FROM cached_games WHERE gameId = :gameId LIMIT 1")
    suspend fun get(gameId: String): CachedGameEntity?

    @Query("SELECT * FROM cached_games WHERE gameId = :gameId LIMIT 1")
    fun observe(gameId: String): Flow<CachedGameEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: CachedGameEntity)

    @Query("DELETE FROM cached_games WHERE gameId = :gameId")
    suspend fun delete(gameId: String)
}

@Dao
interface RoomCacheDao {
    @Query("SELECT * FROM cached_rooms WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: String): CachedRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: CachedRoomEntity)

    @Query("DELETE FROM cached_rooms WHERE roomId = :roomId")
    suspend fun delete(roomId: String)
}

@Dao
interface CampaignProgressDao {
    @Query("SELECT * FROM cached_campaign_progress WHERE uid = :uid ORDER BY levelNumber ASC")
    suspend fun getAllForUser(uid: String): List<CachedCampaignProgressEntity>

    @Query("SELECT * FROM cached_campaign_progress WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CachedCampaignProgressEntity?

    @Query("SELECT * FROM cached_campaign_progress WHERE uid = :uid AND levelNumber = :level LIMIT 1")
    suspend fun getForLevel(uid: String, level: Int): CachedCampaignProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: CachedCampaignProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<CachedCampaignProgressEntity>)

    @Query("DELETE FROM cached_campaign_progress WHERE uid = :uid")
    suspend fun clearForUser(uid: String)
}

@Dao
interface RequestDao {
    @Query("SELECT * FROM cached_requests")
    suspend fun getAll(): List<CachedRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: CachedRequestEntity)

    @Query("DELETE FROM cached_requests WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_requests")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM cached_messages")
    suspend fun getAll(): List<CachedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: CachedMessageEntity)

    @Query("DELETE FROM cached_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_messages")
    suspend fun clear()
}
