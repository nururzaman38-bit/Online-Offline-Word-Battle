package com.wordbattle.com.data.repository

import com.wordbattle.com.data.local.CachedRequestEntity
import com.wordbattle.com.data.local.RequestDao
import com.wordbattle.com.data.model.GameRequest
import com.wordbattle.com.data.model.RequestStatus
import com.wordbattle.com.data.model.RequestType
import com.wordbattle.com.data.remote.dto.NewRequestDto
import com.wordbattle.com.data.remote.dto.RequestDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RequestRepository(
    private val client: SupabaseClient,
    private val dao: RequestDao,
    private val json: Json
) {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dtoToModel(dto: RequestDto): GameRequest = GameRequest(
        id = dto.id,
        type = try { RequestType.valueOf(dto.type) } catch (_: Exception) { RequestType.FRIEND },
        senderId = dto.senderId,
        receiverId = dto.receiverId,
        status = try { RequestStatus.valueOf(dto.status) } catch (_: Exception) { RequestStatus.pending },
        payload = dto.payload?.let { mapOf("json" to it.toString()) },
        createdAt = dto.createdAt
    )

    suspend fun getRequests(uid: String): List<GameRequest> {
        return runCatching {
            client.from("requests").select {
                filter { eq("receiver_id", uid) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<RequestDto>().map { dtoToModel(it) }.also { list ->
                list.forEach {
                    dao.upsert(CachedRequestEntity(it.id, json.encodeToString(it)))
                }
            }
        }.getOrElse {
            dao.getAll().mapNotNull {
                runCatching { json.decodeFromString<GameRequest>(it.json) }.getOrNull()
            }
        }
    }

    suspend fun getAllForUser(uid: String): List<GameRequest> {
        return runCatching {
            client.from("requests").select {
                filter {
                    or {
                        eq("sender_id", uid)
                        eq("receiver_id", uid)
                    }
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<RequestDto>().map { dtoToModel(it) }
        }.getOrDefault(emptyList())
    }

    suspend fun sendRequest(
        type: RequestType,
        senderId: String,
        receiverId: String,
        payload: JsonObject? = null
    ): GameRequest {
        val newDto = NewRequestDto(
            type = type.name,
            senderId = senderId,
            receiverId = receiverId,
            payload = payload
        )
        val saved = client.from("requests").insert(newDto) { select() }.decodeSingle<RequestDto>()
        val model = dtoToModel(saved)
        dao.upsert(CachedRequestEntity(model.id, json.encodeToString(model)))
        return model
    }

    suspend fun updateStatus(requestId: String, status: RequestStatus): GameRequest {
        val updated = client.from("requests").update({
            set("status", status.name)
        }) {
            filter { eq("id", requestId) }
            select()
        }.decodeSingle<RequestDto>()
        val model = dtoToModel(updated)
        dao.upsert(CachedRequestEntity(model.id, json.encodeToString(model)))
        return model
    }

    suspend fun sendGameInvite(senderId: String, receiverId: String, roomCode: String, passcode: String): GameRequest {
        val payload = buildJsonObject {
            put("roomCode", roomCode)
            put("passcode", passcode)
        }
        return sendRequest(RequestType.GAME_INVITE, senderId, receiverId, payload)
    }

    fun observeRequests(uid: String): Flow<List<GameRequest>> = callbackFlow {
        trySend(getAllForUser(uid))
        val channel = client.channel("requests-$uid-${System.nanoTime()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "requests"
        }
        val collector = launch {
            changes.collect { trySend(getAllForUser(uid)) }
        }
        channel.subscribe()
        awaitClose {
            collector.cancel()
            cleanupScope.launch { client.realtime.removeChannel(channel) }
        }
    }
}
