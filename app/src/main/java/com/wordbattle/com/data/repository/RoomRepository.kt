package com.wordbattle.com.data.repository

import com.wordbattle.com.data.game.RoomManager
import com.wordbattle.com.data.local.CachedRoomEntity
import com.wordbattle.com.data.local.RoomCacheDao
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.GameStatus
import com.wordbattle.com.data.model.JoinRoomResult
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.data.remote.dto.GameDto
import com.wordbattle.com.data.remote.dto.GameUpdateDto
import com.wordbattle.com.data.remote.dto.NewGameDto
import com.wordbattle.com.data.remote.dto.NewRoomDto
import com.wordbattle.com.data.remote.dto.RoomDto
import com.wordbattle.com.data.remote.dto.RoomSlotDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom

class RoomRepository(
    private val client: SupabaseClient,
    private val cache: RoomCacheDao,
    private val json: Json
) {
    private val random = SecureRandom()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    suspend fun createRoom(profile: UserProfile, localSlots: Int, onlineSlots: Int): Room {
        require(localSlots >= 1 && onlineSlots >= 1)
        require(localSlots + onlineSlots in 2..4)
        var lastFailure: Throwable? = null
        var created: RoomDto? = null
        repeat(5) {
            if (created != null) return@repeat
            val code = randomText(6)
            val passcode = (1000 + random.nextInt(9000)).toString()
            runCatching {
                client.from("rooms").insert(
                    NewRoomDto(code, passcode, profile.uid, localSlots + onlineSlots, localSlots, onlineSlots)
                ) { select() }.decodeSingle<RoomDto>()
            }.onSuccess { created = it }.onFailure { lastFailure = it }
        }
        val row = created ?: throw IllegalStateException("Could not create a unique room. Please retry.", lastFailure)
        val slots = List(row.totalSlots) { index ->
            when {
                index == 0 -> RoomSlotDto(
                    roomId = row.id,
                    slotIndex = index,
                    filledBy = profile.uid,
                    filledByName = profile.displayName,
                    isReady = true
                )
                index < localSlots -> RoomSlotDto(
                    roomId = row.id,
                    slotIndex = index,
                    filledByName = "Local Player ${index + 1}",
                    isReady = true
                )
                else -> RoomSlotDto(roomId = row.id, slotIndex = index)
            }
        }
        client.from("room_slots").insert(slots)
        return getRoom(row.id) ?: error("Room was created but could not be loaded")
    }

    suspend fun joinRoom(roomCode: String, passcode: String, profile: UserProfile): JoinRoomResult {
        val code = roomCode.trim().uppercase()
        val pin = passcode.trim()
        if (code.length != 6 || pin.length != 4) return JoinRoomResult.Error("Room not found")
        return runCatching {
            val row = client.from("rooms").select {
                filter { eq("room_code", code); eq("passcode", pin) }
                limit(1)
            }.decodeList<RoomDto>().firstOrNull() ?: return JoinRoomResult.Error("Room not found")
            if (row.status != "lobby") return JoinRoomResult.Error("This room has already started")
            val slots = getSlots(row.id)
            slots.firstOrNull { it.filledBy == profile.uid }?.let {
                return JoinRoomResult.Success(row.toModel(slots.map(RoomSlotDto::toModel)))
            }
            val available = slots.firstOrNull {
                it.slotIndex >= row.localSlots && it.filledBy == null
            } ?: return JoinRoomResult.Error("This room is full")
            client.from("room_slots").update({
                set("filled_by", profile.uid)
                set("filled_by_name", profile.displayName)
                set("is_ready", false)
            }) { filter { eq("id", requireNotNull(available.id)) } }
            JoinRoomResult.Success(requireNotNull(getRoom(row.id)))
        }.getOrElse { JoinRoomResult.Error(it.message ?: "Unable to join room") }
    }

    suspend fun setReady(roomId: String, uid: String, ready: Boolean) {
        client.from("room_slots").update({ set("is_ready", ready) }) {
            filter { eq("room_id", roomId); eq("filled_by", uid) }
        }
    }

    suspend fun getRoom(roomId: String): Room? {
        val row = client.from("rooms").select {
            filter { eq("id", roomId) }; limit(1)
        }.decodeList<RoomDto>().firstOrNull() ?: return null
        val room = row.toModel(getSlots(roomId).map(RoomSlotDto::toModel))
        cache.upsert(CachedRoomEntity(room.roomId, json.encodeToString(room)))
        return room
    }

    suspend fun startGame(roomId: String, hostUid: String, targetScore: Int = 100): GameState {
        val room = requireNotNull(getRoom(roomId)) { "Room not found" }
        require(room.hostId == hostUid) { "Only the host can start" }
        require(room.status == "lobby") { "Room has already started" }
        require(RoomManager.canHostStart(room)) { "All online players must join and be ready" }
        val players = room.slots.sortedBy { it.slotIndex }.map { slot ->
            val isHostLocal = slot.slotIndex < room.localSlotsCount
            Player(
                id = slot.filledByUid ?: "$hostUid:local:${slot.slotIndex}",
                name = slot.filledByName ?: "Player ${slot.slotIndex + 1}",
                type = if (isHostLocal) PlayerType.HUMAN_LOCAL else PlayerType.HUMAN_ONLINE,
                isReady = true,
                turnOrder = slot.slotIndex
            )
        }
        val inserted = client.from("games").insert(
            NewGameDto(
                roomId = roomId,
                targetScore = targetScore,
                board = BoardState.empty(),
                players = players,
                currentTurnPlayerId = players.first().id
            )
        ) { select() }.decodeSingle<GameDto>()
        client.from("rooms").update({
            set("game_id", inserted.id)
            set("status", "in_progress")
        }) { filter { eq("id", roomId) } }
        return inserted.toModel()
    }

    suspend fun getGame(gameId: String): GameState? = client.from("games").select {
        filter { eq("id", gameId) }; limit(1)
    }.decodeList<GameDto>().firstOrNull()?.toModel()

    suspend fun updateGame(game: GameState) {
        client.from("games").update(GameUpdateDto.from(game)) {
            filter { eq("id", game.gameId) }
        }
    }

    suspend fun finishRoom(roomId: String, hostUid: String) {
        val room = getRoom(roomId) ?: return
        if (room.hostId != hostUid) return
        client.from("rooms").update({ set("status", "finished") }) {
            filter { eq("id", roomId) }
        }
    }

    fun observeRoom(roomId: String): Flow<Room> = callbackFlow {
        getRoom(roomId)?.let { send(it) }
        val channel = client.channel("room-$roomId-${System.nanoTime()}")
        val slotChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "room_slots"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        val roomChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "rooms"
            filter("id", FilterOperator.EQ, roomId)
        }
        val collector = launch {
            merge(slotChanges, roomChanges).collect { getRoom(roomId)?.let { trySend(it) } }
        }
        channel.subscribe()
        awaitClose {
            collector.cancel()
            cleanupScope.launch { client.realtime.removeChannel(channel) }
        }
    }

    fun observeGame(gameId: String): Flow<GameState> = callbackFlow {
        getGame(gameId)?.let { send(it) }
        val channel = client.channel("game-$gameId-${System.nanoTime()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "games"
            filter("id", FilterOperator.EQ, gameId)
        }
        val collector = launch {
            changes.collect { getGame(gameId)?.let { trySend(it) } }
        }
        channel.subscribe()
        awaitClose {
            collector.cancel()
            cleanupScope.launch { client.realtime.removeChannel(channel) }
        }
    }

    private suspend fun getSlots(roomId: String): List<RoomSlotDto> =
        client.from("room_slots").select {
            filter { eq("room_id", roomId) }
            order("slot_index", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList()

    private fun randomText(length: Int): String = buildString(length) {
        repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}
