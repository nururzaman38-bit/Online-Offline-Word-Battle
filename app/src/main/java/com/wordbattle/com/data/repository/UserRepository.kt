package com.wordbattle.com.data.repository

import com.wordbattle.com.data.local.CachedProfileEntity
import com.wordbattle.com.data.local.ProfileDao
import com.wordbattle.com.data.model.FriendProfile
import com.wordbattle.com.data.model.UserProfile
import com.wordbattle.com.data.remote.dto.FriendshipDto
import com.wordbattle.com.data.remote.dto.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UserRepository(
    private val client: SupabaseClient,
    private val profileDao: ProfileDao,
    private val json: Json
) {
    suspend fun ensureProfile(fallback: UserProfile): UserProfile {
        val existing = client.from("profiles").select {
            filter { eq("id", fallback.uid) }
            limit(1)
        }.decodeList<ProfileDto>().firstOrNull()
        val profile = existing?.toModel() ?: client.from("profiles").insert(ProfileDto.from(fallback)) {
            select()
        }.decodeSingle<ProfileDto>().toModel()
        cache(profile)
        return profile
    }

    suspend fun getProfile(uid: String, allowCache: Boolean = true): UserProfile? {
        val remote = runCatching {
            client.from("profiles").select { filter { eq("id", uid) }; limit(1) }
                .decodeList<ProfileDto>().firstOrNull()?.toModel()
        }.getOrNull()
        if (remote != null) {
            cache(remote)
            return remote
        }
        return if (allowCache) profileDao.get(uid)?.let {
            runCatching { json.decodeFromString<UserProfile>(it.json) }.getOrNull()
        } else null
    }

    suspend fun leaderboard(weekly: Boolean): List<UserProfile> =
        client.from("profiles").select {
            order(if (weekly) "weekly_score" else "wins", Order.DESCENDING)
            limit(100)
        }.decodeList<ProfileDto>().map(ProfileDto::toModel)

    suspend fun searchProfiles(query: String, currentUid: String): List<UserProfile> {
        if (query.trim().length < 2) return emptyList()
        // Profiles are public by the supplied RLS; local filtering avoids wildcard escaping mistakes.
        return client.from("profiles").select { limit(100) }.decodeList<ProfileDto>()
            .asSequence().map(ProfileDto::toModel)
            .filter { it.uid != currentUid && it.displayName.contains(query.trim(), ignoreCase = true) }
            .take(20).toList()
    }

    suspend fun friends(uid: String): List<FriendProfile> {
        val outgoing = client.from("friends").select { filter { eq("user_id", uid) } }
            .decodeList<FriendshipDto>()
        val incoming = client.from("friends").select { filter { eq("friend_id", uid) } }
            .decodeList<FriendshipDto>()
        val links = (outgoing + incoming).distinctBy { it.userId to it.friendId }
        if (links.isEmpty()) return emptyList()
        val allProfiles = client.from("profiles").select().decodeList<ProfileDto>().associateBy(ProfileDto::id)
        return links.mapNotNull { link ->
            val otherId = if (link.userId == uid) link.friendId else link.userId
            allProfiles[otherId]?.toModel()?.let {
                val visibleStatus = if (link.status == "pending" && link.userId == uid) "pending_outgoing" else link.status
                FriendProfile(it, visibleStatus, isOnline = false)
            }
        }
    }

    suspend fun addFriend(uid: String, friendId: String) {
        require(uid != friendId) { "You cannot add yourself" }
        client.from("friends").insert(FriendshipDto(uid, friendId, "pending"))
    }

    suspend fun acceptFriend(uid: String, requesterId: String) {
        client.from("friends").update({ set("status", "accepted") }) {
            filter { eq("user_id", requesterId); eq("friend_id", uid) }
        }
    }

    suspend fun recordFinishedGame(profile: UserProfile, won: Boolean, score: Int): UserProfile {
        val updated = client.from("profiles").update({
            set("games_played", profile.gamesPlayed + 1)
            set("wins", profile.wins + if (won) 1 else 0)
            set("weekly_score", profile.weeklyScore + score)
        }) {
            select()
            filter { eq("id", profile.uid) }
        }.decodeSingle<ProfileDto>().toModel()
        cache(updated)
        return updated
    }

    private suspend fun cache(profile: UserProfile) {
        profileDao.upsert(CachedProfileEntity(profile.uid, json.encodeToString(profile)))
    }
}
