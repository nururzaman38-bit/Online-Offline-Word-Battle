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
    @SerialName("photo_url") val photoUrl: String? = null,
    val coins: Int = 0,
    val gems: Int = 0,
    val level: Int = 1,
    @SerialName("games_played") val gamesPlayed: Int = 0,
    val wins: Int = 0,
    @SerialName("weekly_score") val weeklyScore: Int = 0
) {
    fun toModel() = UserProfile(id, displayName, photoUrl, coins, gems, level, gamesPlayed, wins, weeklyScore)
    companion object {
        fun from(profile: UserProfile) = ProfileDto(
            profile.uid, profile.displayName, profile.photoUrl, profile.coins, profile.gems,
            profile.level, profile.gamesPlayed, profile.wins, profile.weeklyScore
        )
    }
}

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

@Serializable
data class RoomSlotDto(
    val id: String? = null,
    @SerialName("room_id") val roomId: String,
    @SerialName("slot_index") val slotIndex: Int,
    @SerialName("filled_by") val filledBy: String? = null,
    @SerialName("filled_by_name") val filledByName: String? = null,
    @SerialName("is_ready") val isReady: Boolean = false
) {
    fun toModel() = RoomSlot(slotIndex, filledBy, filledByName, isReady)
}

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
