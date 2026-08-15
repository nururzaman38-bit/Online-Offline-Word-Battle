package com.wordbattle.com.data.repository

import com.wordbattle.com.data.game.RoomManager
import com.wordbattle.com.data.local.CachedRoomEntity
import com.wordbattle.com.data.model.AppErrorCode
import com.wordbattle.com.data.model.AppException
import com.wordbattle.com.data.model.appErrorCode
import com.wordbattle.com.data.local.RoomCacheDao
import com.wordbattle.com.data.model.BoardState
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.JoinRoomResult
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.data.remote.dto.GameDto
import com.wordbattle.com.data.remote.dto.GameUpdateDto
import com.wordbattle.com.data.remote.dto.NewGameDto
import com.wordbattle.com.data.remote.dto.NewRoomDto
import com.wordbattle.com.data.remote.dto.NewRoomSlotDto
import com.wordbattle.com.data.remote.dto.ProfileDto
import com.wordbattle.com.data.remote.dto.RoomDto
import com.wordbattle.com.data.remote.dto.RoomSlotDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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

    /**
     * Creates a room plus its slot rows for [profile].
     *
     * Fails fast and honestly: authentication, profile and RLS problems are reported as themselves,
     * and only a room-code collision is retried. If slot creation fails the half-created room is
     * deleted so the user never sees a ghost lobby.
     */
    suspend fun createRoom(profile: UserProfile, localSlots: Int, onlineSlots: Int): Room {
        RoomManager.validateSlots(localSlots, onlineSlots)?.let { problem ->
            throw AppException(AppErrorCode.INVALID_SLOTS, "Invalid slot configuration: $problem")
        }

        val uid = requireAuthenticatedUid()
        if (uid != profile.uid) {
            throw AppException(
                AppErrorCode.NOT_SIGNED_IN,
                "Signed-in uid ($uid) does not match the active profile (${profile.uid})"
            )
        }
        requireProfileRow(uid)

        val totalSlots = localSlots + onlineSlots
        var lastCollision: Throwable? = null
        var room: RoomDto? = null
        repeat(MAX_ROOM_CODE_ATTEMPTS) {
            if (room != null) return@repeat
            val attempt = runCatching {
                client.from("rooms").insert(
                    NewRoomDto(randomText(6), randomPasscode(), uid, totalSlots, localSlots, onlineSlots)
                ) { select() }.decodeSingle<RoomDto>()
            }
            attempt.onSuccess { room = it }
            attempt.onFailure { failure ->
                // Retry ONLY when the generated room code clashed. Every other failure (RLS,
                // auth, missing profile, offline) is surfaced immediately.
                if (failure.appErrorCode() == AppErrorCode.ROOM_CODE_UNAVAILABLE) {
                    lastCollision = failure
                } else {
                    throw asRoomCreationFailure(failure)
                }
            }
        }
        val created = room ?: throw AppException(
            AppErrorCode.ROOM_CODE_UNAVAILABLE,
            "Could not generate a free room code after $MAX_ROOM_CODE_ATTEMPTS attempts",
            lastCollision
        )

        val slots = List(created.totalSlots) { index ->
            when {
                index == 0 -> NewRoomSlotDto(
                    roomId = created.id,
                    slotIndex = index,
                    filledBy = uid,
                    filledByName = profile.displayName,
                    isReady = true
                )
                index < localSlots -> NewRoomSlotDto(
                    roomId = created.id,
                    slotIndex = index,
                    filledByName = "Local Player ${index + 1}",
                    isReady = true
                )
                else -> NewRoomSlotDto(roomId = created.id, slotIndex = index)
            }
        }
        runCatching { client.from("room_slots").insert(slots) }.onFailure { failure ->
            deleteIncompleteRoom(created.id)
            throw AppException(
                AppErrorCode.ROOM_SLOTS_FAILED,
                "Room ${created.roomCode} was rolled back: ${failure.message}",
                failure
            )
        }

        val loaded = runCatching { getRoom(created.id) }.getOrNull()
        if (loaded == null || !RoomManager.slotsAreComplete(loaded)) {
            deleteIncompleteRoom(created.id)
            throw AppException(
                AppErrorCode.ROOM_SLOTS_FAILED,
                "Room ${created.roomCode} was created without a complete set of slots"
            )
        }
        return loaded
    }

    /** Removes a room the host could not finish setting up. Slots cascade with the room row. */
    suspend fun deleteIncompleteRoom(roomId: String) {
        runCatching { client.from("room_slots").delete { filter { eq("room_id", roomId) } } }
        runCatching { client.from("rooms").delete { filter { eq("id", roomId) } } }
        runCatching { cache.delete(roomId) }
    }

    private fun requireAuthenticatedUid(): String = client.auth.currentUserOrNull()?.id
        ?: throw AppException(AppErrorCode.NOT_SIGNED_IN, "No Supabase session; sign in again")

    private suspend fun requireProfileRow(uid: String) {
        val exists = runCatching {
            client.from("profiles").select { filter { eq("id", uid) }; limit(1) }
                .decodeList<ProfileDto>().firstOrNull()
        }.getOrElse { failure ->
            throw AppException(failure.appErrorCode(), "Could not verify profile: ${failure.message}", failure)
        }
        if (exists == null) {
            throw AppException(AppErrorCode.PROFILE_MISSING, "No profiles row for uid $uid")
        }
    }

    private fun asRoomCreationFailure(failure: Throwable): AppException {
        val code = failure.appErrorCode()
        return AppException(
            if (code == AppErrorCode.UNKNOWN) AppErrorCode.ROOM_CREATE_FAILED else code,
            failure.message,
            failure
        )
    }

    suspend fun joinRoom(roomCode: String, passcode: String, profile: UserProfile): JoinRoomResult {
        val code = roomCode.trim().uppercase()
        val pin = passcode.trim()
        if (code.length != 6 || pin.length != 4) return JoinRoomResult.Error(AppErrorCode.ROOM_NOT_FOUND)
        val uid = client.auth.currentUserOrNull()?.id
            ?: return JoinRoomResult.Error(AppErrorCode.NOT_SIGNED_IN)
        return runCatching {
            val row = client.from("rooms").select {
                filter { eq("room_code", code); eq("passcode", pin) }
                limit(1)
            }.decodeList<RoomDto>().firstOrNull()
                ?: return JoinRoomResult.Error(AppErrorCode.ROOM_NOT_FOUND)
            if (row.status != "lobby") return JoinRoomResult.Error(AppErrorCode.ROOM_ALREADY_STARTED)
            val slotRows = getSlots(row.id)
            val room = row.toModel(slotRows.map(RoomSlotDto::toModel))
            if (RoomManager.isMember(room, uid)) {
                return JoinRoomResult.Success(room)
            }
            val target = RoomManager.claimableSlot(room, uid)
                ?: return JoinRoomResult.Error(AppErrorCode.ROOM_FULL)
            val targetRow = slotRows.firstOrNull { it.slotIndex == target.slotIndex }
                ?: return JoinRoomResult.Error(AppErrorCode.ROOM_FULL)
            client.from("room_slots").update({
                set("filled_by", uid)
                set("filled_by_name", profile.displayName)
                set("is_ready", false)
            }) {
                // RLS additionally requires the target slot to still be empty, so two devices
                // racing for the same seat cannot overwrite each other.
                filter { eq("id", targetRow.id) }
            }
            val refreshed = getRoom(row.id) ?: return JoinRoomResult.Error(AppErrorCode.ROOM_NOT_FOUND)
            if (!RoomManager.isMember(refreshed, uid)) {
                return JoinRoomResult.Error(AppErrorCode.ROOM_FULL)
            }
            JoinRoomResult.Success(refreshed)
        }.getOrElse { failure ->
            val code2 = failure.appErrorCode()
            JoinRoomResult.Error(
                if (code2 == AppErrorCode.UNKNOWN || code2 == AppErrorCode.ROOM_CREATE_DENIED) {
                    AppErrorCode.ROOM_JOIN_DENIED
                } else {
                    code2
                },
                failure.message
            )
        }
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
        val room = getRoom(roomId) ?: throw AppException(AppErrorCode.ROOM_NOT_FOUND)
        if (room.hostId != hostUid) throw AppException(AppErrorCode.NOT_HOST)
        if (room.status != "lobby") throw AppException(AppErrorCode.ROOM_ALREADY_STARTED)
        if (!RoomManager.canHostStart(room)) throw AppException(AppErrorCode.LOBBY_NOT_READY)
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

    private fun randomPasscode(): String = (1000 + random.nextInt(9000)).toString()

    private companion object {
        const val MAX_ROOM_CODE_ATTEMPTS = 5
    }
}
