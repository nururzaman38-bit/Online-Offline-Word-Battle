package com.wordbattle.com.data.remote.dto

import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.RoomSlot
import com.wordbattle.com.data.model.UsedWord
import com.wordbattle.com.data.model.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val coins: Int = 0,
    val gems: Int = 0,
    val level: Int = 1,
    @SerialName("games_played") val gamesPlayed: Int = 0,
    val wins: Int = 0,
    @SerialName("weekly_score") val weeklyScore: Int = 0,
    @SerialName("display_name_updated_at") val displayNameUpdatedAt: String? = null,
    @SerialName("lives_current") val livesCurrent: Int = 3,
    @SerialName("lives_max") val livesMax: Int = 3,
    @SerialName("last_life_regen_at") val lastLifeRegenAt: String? = null,
    @SerialName("campaign_level") val campaignLevel: Int = 1,
    @SerialName("campaign_stars_total") val campaignStarsTotal: Int = 0
) {
    fun toModel() = UserProfile(
        uid = id,
        displayName = displayName,
        username = username,
        photoUrl = photoUrl,
        coins = coins,
        gems = gems,
        level = level,
        gamesPlayed = gamesPlayed,
        wins = wins,
        weeklyScore = weeklyScore,
        displayNameUpdatedAt = displayNameUpdatedAt,
        livesCurrent = livesCurrent,
        livesMax = livesMax,
        lastLifeRegenAt = lastLifeRegenAt,
        campaignLevel = campaignLevel,
        campaignStarsTotal = campaignStarsTotal
    )

    companion object {
        fun from(profile: UserProfile) = ProfileDto(
            id = profile.uid,
            displayName = profile.displayName,
            username = profile.username,
            photoUrl = profile.photoUrl,
            coins = profile.coins,
            gems = profile.gems,
            level = profile.level,
            gamesPlayed = profile.gamesPlayed,
            wins = profile.wins,
            weeklyScore = profile.weeklyScore,
            displayNameUpdatedAt = profile.displayNameUpdatedAt,
            livesCurrent = profile.livesCurrent,
            livesMax = profile.livesMax,
            lastLifeRegenAt = profile.lastLifeRegenAt,
            campaignLevel = profile.campaignLevel,
            campaignStarsTotal = profile.campaignStarsTotal
        )
    }
}

/** Insert payload for a brand new profile row. Server-side defaults fill the rest. */
@Serializable
data class NewProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null
)

/** Patch payload for the first-login / edit-profile screen. */
@Serializable
data class ProfileIdentityDto(
    @SerialName("display_name") val displayName: String,
    val username: String
)

@Serializable
data class FriendshipDto(
    @SerialName("user_id") val userId: String,
    @SerialName("friend_id") val friendId: String,
    val status: String = "pending"
)

@Serializable
data class RoomDto(
    val id: String,
    @SerialName("room_code") val roomCode: String,
    val passcode: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("total_slots") val totalSlots: Int,
    @SerialName("local_slots") val localSlots: Int,
    @SerialName("online_slots") val onlineSlots: Int,
    val status: String = "lobby",
    @SerialName("game_id") val gameId: String? = null
) {
    fun toModel(slots: List<RoomSlot>) = Room(
        id, roomCode, passcode, hostId, totalSlots, localSlots, onlineSlots,
        slots.sortedBy(RoomSlot::slotIndex), gameId, status
    )
}

@Serializable
data class NewRoomDto(
    @SerialName("room_code") val roomCode: String,
    val passcode: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("total_slots") val totalSlots: Int,
    @SerialName("local_slots") val localSlots: Int,
    @SerialName("online_slots") val onlineSlots: Int
)

/**
 * A room slot row as returned by Postgres. `id` is non-null here because the database always
 * generates it; use [NewRoomSlotDto] for inserts so `gen_random_uuid()` is allowed to apply.
 */
@Serializable
data class RoomSlotDto(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("slot_index") val slotIndex: Int,
    @SerialName("filled_by") val filledBy: String? = null,
    @SerialName("filled_by_name") val filledByName: String? = null,
    @SerialName("is_ready") val isReady: Boolean = false
) {
    fun toModel() = RoomSlot(slotIndex, filledBy, filledByName, isReady)
}

/**
 * Insert payload for a room slot.
 *
 * Deliberately has **no** `id` field: sending `id = null` makes PostgREST write an explicit NULL
 * into a `not null` primary key, which fails instead of letting `gen_random_uuid()` supply a value.
 *
 * Also deliberately has **no defaults** on `filled_by`, `filled_by_name`, `is_ready`: Supabase's
 * Kotlin serializer is configured with `encodeDefaults = false`, so any property left at its
 * default is omitted from the JSON. PostgREST bulk insert requires every object in the array to
 * contain exactly the same keys (PGRST102 "All object keys must match"), so host, local and empty
 * seats must all send the same five keys. Using non-default (explicit) values for every seat keeps
 * the key set uniform.
 */
@Serializable
data class NewRoomSlotDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("slot_index") val slotIndex: Int,
    @SerialName("filled_by") val filledBy: String?,
    @SerialName("filled_by_name") val filledByName: String?,
    @SerialName("is_ready") val isReady: Boolean
)

@Serializable
data class GameDto(
    val id: String,
    @SerialName("room_id") val roomId: String? = null,
    val mode: String,
    @SerialName("target_score") val targetScore: Int,
    val board: BoardState,
    val players: List<Player>,
    @SerialName("used_words") val usedWords: List<UsedWord> = emptyList(),
    @SerialName("current_turn_player_id") val currentTurnPlayerId: String,
    val status: String,
    val rankings: List<String> = emptyList()
) {
    fun toModel() = GameState(
        id,
        when (mode) {
            "computer" -> com.wordbattle.com.data.model.GameMode.COMPUTER
            "local" -> com.wordbattle.com.data.model.GameMode.LOCAL
            "campaign_score" -> com.wordbattle.com.data.model.GameMode.CAMPAIGN_SCORE
            "campaign_puzzle" -> com.wordbattle.com.data.model.GameMode.CAMPAIGN_PUZZLE
            else -> com.wordbattle.com.data.model.GameMode.MIXED_ONLINE
        },
        targetScore,
        board,
        players,
        usedWords,
        currentTurnPlayerId,
        when (status) {
            "lobby" -> GameStatus.LOBBY
            "finished" -> GameStatus.FINISHED
            "level_failed" -> GameStatus.LEVEL_FAILED
            else -> GameStatus.IN_PROGRESS
        },
        rankings
    )
}

@Serializable
data class NewGameDto(
    @SerialName("room_id") val roomId: String,
    val mode: String = "mixed_online",
    @SerialName("target_score") val targetScore: Int,
    val board: BoardState,
    val players: List<Player>,
    @SerialName("used_words") val usedWords: List<UsedWord> = emptyList(),
    @SerialName("current_turn_player_id") val currentTurnPlayerId: String,
    val status: String = "in_progress",
    val rankings: List<String> = emptyList()
)

@Serializable
data class GameUpdateDto(
    val board: BoardState,
    val players: List<Player>,
    @SerialName("used_words") val usedWords: List<UsedWord>,
    @SerialName("current_turn_player_id") val currentTurnPlayerId: String,
    val status: String,
    val rankings: List<String>
) {
    companion object {
        fun from(game: GameState) = GameUpdateDto(
            game.board,
            game.players,
            game.usedWords,
            game.currentTurnPlayerId,
            game.status.name.lowercase(),
            game.rankingsAssigned
        )
    }
}

@Serializable
data class CampaignProgressDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("level_number") val levelNumber: Int,
    val stars: Int,
    @SerialName("best_time_seconds") val bestTimeSeconds: Int? = null,
    @SerialName("best_turns") val bestTurns: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class NewCampaignProgressDto(
    @SerialName("user_id") val userId: String,
    @SerialName("level_number") val levelNumber: Int,
    val stars: Int,
    @SerialName("best_time_seconds") val bestTimeSeconds: Int? = null,
    @SerialName("best_turns") val bestTurns: Int? = null
)

@Serializable
data class RequestDto(
    val id: String,
    val type: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val status: String = "pending",
    val payload: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class NewRequestDto(
    val type: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val status: String = "pending",
    val payload: kotlinx.serialization.json.JsonObject? = null
)

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
data class NewMessageDto(
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val body: String
)
