package com.wordbattle.com.data.repository

import com.wordbattle.com.data.local.CachedMessageEntity
import com.wordbattle.com.data.local.MessageDao
import com.wordbattle.com.data.model.ChatMessage
import com.wordbattle.com.data.remote.dto.MessageDto
import com.wordbattle.com.data.remote.dto.NewMessageDto
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

class MessageRepository(
    private val client: SupabaseClient,
    private val dao: MessageDao,
    private val json: Json
) {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dtoToModel(dto: MessageDto) = ChatMessage(
        id = dto.id,
        senderId = dto.senderId,
        receiverId = dto.receiverId,
        body = dto.body,
        createdAt = dto.createdAt,
        readAt = dto.readAt
    )

    suspend fun getConversations(uid: String): List<ChatMessage> {
        // Latest message per conversation partner
        return runCatching {
            client.from("messages").select {
                filter {
                    or {
                        eq("sender_id", uid)
                        eq("receiver_id", uid)
                    }
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(100)
            }.decodeList<MessageDto>().map { dtoToModel(it) }
        }.getOrDefault(emptyList())
    }

    suspend fun getThread(uid: String, otherId: String): List<ChatMessage> {
        return runCatching {
            client.from("messages").select {
                filter {
                    or {
                        and {
                            eq("sender_id", uid)
                            eq("receiver_id", otherId)
                        }
                        and {
                            eq("sender_id", otherId)
                            eq("receiver_id", uid)
                        }
                    }
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<MessageDto>().map { dtoToModel(it) }
        }.getOrDefault(emptyList())
    }

    suspend fun sendMessage(senderId: String, receiverId: String, body: String): ChatMessage {
        val newDto = NewMessageDto(senderId, receiverId, body)
        val saved = client.from("messages").insert(newDto) { select() }.decodeSingle<MessageDto>()
        val model = dtoToModel(saved)
        dao.upsert(CachedMessageEntity(model.id, json.encodeToString(model)))
        return model
    }

    suspend fun markRead(messageId: String) {
        runCatching {
            client.from("messages").update({
                set("read_at", java.time.Instant.now().toString())
            }) {
                filter { eq("id", messageId) }
            }
        }
    }

    fun observeMessages(uid: String): Flow<List<ChatMessage>> = callbackFlow {
        trySend(getConversations(uid))
        val channel = client.channel("messages-$uid-${System.nanoTime()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val collector = launch {
            changes.collect { trySend(getConversations(uid)) }
        }
        channel.subscribe()
        awaitClose {
            collector.cancel()
            cleanupScope.launch { client.realtime.removeChannel(channel) }
        }
    }

    fun observeThread(uid: String, otherId: String): Flow<List<ChatMessage>> = callbackFlow {
        trySend(getThread(uid, otherId))
        val channel = client.channel("messages-thread-$uid-$otherId-${System.nanoTime()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val collector = launch {
            changes.collect { trySend(getThread(uid, otherId)) }
        }
        channel.subscribe()
        awaitClose {
            collector.cancel()
            cleanupScope.launch { client.realtime.removeChannel(channel) }
        }
    }
}
