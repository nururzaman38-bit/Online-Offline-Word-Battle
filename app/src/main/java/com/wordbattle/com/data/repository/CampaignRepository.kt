package com.wordbattle.com.data.repository

import com.wordbattle.com.data.game.CampaignLevelCatalog
import com.wordbattle.com.data.game.CampaignRules
import com.wordbattle.com.data.local.CachedCampaignProgressEntity
import com.wordbattle.com.data.local.CampaignProgressDao
import com.wordbattle.com.data.model.CampaignProgress
import com.wordbattle.com.data.model.LevelDefinition
import com.wordbattle.com.data.remote.dto.CampaignProgressDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant

class CampaignRepository(
    private val client: SupabaseClient,
    private val dao: CampaignProgressDao
) {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getLevel(levelNumber: Int): LevelDefinition =
        CampaignLevelCatalog.generateLevelDefinition(levelNumber)

    fun getAllLevels(): List<LevelDefinition> = CampaignLevelCatalog.allLevels()

    suspend fun getProgress(uid: String): List<CampaignProgress> {
        // Try remote first, fallback to cache
        return runCatching {
            client.from("campaign_progress").select {
                filter { eq("user_id", uid) }
            }.decodeList<CampaignProgressDto>().map {
                CampaignProgress(it.levelNumber, it.stars, it.bestTimeSeconds, it.bestTurns)
            }.also { remoteList ->
                // Cache
                val entities = remoteList.map {
                    CachedCampaignProgressEntity(
                        id = "$uid-${it.levelNumber}",
                        uid = uid,
                        levelNumber = it.levelNumber,
                        stars = it.stars,
                        bestTimeSeconds = it.bestTimeSeconds,
                        bestTurns = it.bestTurns
                    )
                }
                dao.upsertAll(entities)
            }
        }.getOrElse {
            dao.getAllForUser(uid).map {
                CampaignProgress(it.levelNumber, it.stars, it.bestTimeSeconds, it.bestTurns)
            }
        }
    }

    suspend fun getProgressForLevel(uid: String, levelNumber: Int): CampaignProgress? {
        return getProgress(uid).firstOrNull { it.levelNumber == levelNumber }
    }

    suspend fun saveProgress(
        uid: String,
        levelNumber: Int,
        stars: Int,
        bestTimeSeconds: Int? = null,
        bestTurns: Int? = null
    ): CampaignProgress {
        val existing = runCatching {
            client.from("campaign_progress").select {
                filter { eq("user_id", uid); eq("level_number", levelNumber) }
                limit(1)
            }.decodeList<CampaignProgressDto>().firstOrNull()
        }.getOrNull()
            ?: dao.getForLevel(uid, levelNumber)?.let {
                CampaignProgressDto(
                    id = it.id,
                    userId = it.uid,
                    levelNumber = it.levelNumber,
                    stars = it.stars,
                    bestTimeSeconds = it.bestTimeSeconds,
                    bestTurns = it.bestTurns
                )
            }

        val shouldSave = if (existing != null) {
            CampaignRules.shouldSaveStars(existing.stars, stars)
        } else true

        if (!shouldSave && existing != null) {
            // Keep existing best values if not better
            return CampaignProgress(existing.levelNumber, existing.stars, existing.bestTimeSeconds, existing.bestTurns)
        }

        val newDto = com.wordbattle.com.data.remote.dto.NewCampaignProgressDto(
            userId = uid,
            levelNumber = levelNumber,
            stars = stars,
            bestTimeSeconds = bestTimeSeconds ?: existing?.bestTimeSeconds,
            bestTurns = bestTurns ?: existing?.bestTurns
        )

        return try {
            val saved = if (existing == null || dao.getForLevel(uid, levelNumber) == null && runCatching { client.from("campaign_progress").select { filter { eq("user_id", uid); eq("level_number", levelNumber) }; limit(1) }.decodeList<CampaignProgressDto>().firstOrNull() }.getOrNull() == null) {
                // No remote existing, insert
                client.from("campaign_progress").insert(newDto) { select() }.decodeSingle<CampaignProgressDto>()
            } else {
                client.from("campaign_progress").update({
                    set("stars", stars)
                    if (bestTimeSeconds != null) set("best_time_seconds", bestTimeSeconds)
                    if (bestTurns != null) set("best_turns", bestTurns)
                }) {
                    filter { eq("user_id", uid); eq("level_number", levelNumber) }
                    select()
                }.decodeSingle<CampaignProgressDto>()
            }

            dao.upsert(
                CachedCampaignProgressEntity(
                    id = "$uid-${saved.levelNumber}",
                    uid = uid,
                    levelNumber = saved.levelNumber,
                    stars = saved.stars,
                    bestTimeSeconds = saved.bestTimeSeconds,
                    bestTurns = saved.bestTurns
                )
            )

            CampaignProgress(saved.levelNumber, saved.stars, saved.bestTimeSeconds, saved.bestTurns)
        } catch (_: Exception) {
            // Offline: save locally only
            dao.upsert(
                CachedCampaignProgressEntity(
                    id = "$uid-$levelNumber",
                    uid = uid,
                    levelNumber = levelNumber,
                    stars = stars,
                    bestTimeSeconds = bestTimeSeconds ?: existing?.bestTimeSeconds,
                    bestTurns = bestTurns ?: existing?.bestTurns
                )
            )
            CampaignProgress(levelNumber, stars, bestTimeSeconds ?: existing?.bestTimeSeconds, bestTurns ?: existing?.bestTurns)
        }
    }

    fun observeProgress(uid: String): Flow<List<CampaignProgress>> = callbackFlow {
        trySend(getProgress(uid))
        val channel = client.channel("campaign-progress-$uid-${System.nanoTime()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "campaign_progress"
            filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, uid)
        }
        val collector = launch {
            changes.collect {
                trySend(getProgress(uid))
            }
        }
        channel.subscribe()
        awaitClose {
            collector.cancel()
            cleanupScope.launch { client.realtime.removeChannel(channel) }
        }
    }

    // Helper for coin reward on first completion
    fun coinReward(stars: Int, isFirst: Boolean): Int =
        CampaignRules.coinsRewardForFirstCompletion(stars, isFirst)
}
