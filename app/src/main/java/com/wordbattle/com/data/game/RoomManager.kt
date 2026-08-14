package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.Room

/** Shared room invariants used by host and UI layers. */
object RoomManager {
    fun canHostStart(room: Room): Boolean =
        room.status == "lobby" &&
            room.slots.size == room.totalSlots &&
            room.slots.all { it.filledByName != null && it.isReady }

    fun ownedPlayerIds(
        game: GameState,
        currentUid: String?,
        isHostDevice: Boolean,
        hostLocalSlots: Int
    ): Set<String> = if (isHostDevice) {
        game.players.filter { it.turnOrder < hostLocalSlots }.mapTo(mutableSetOf()) { it.id }
    } else {
        listOfNotNull(currentUid).toSet()
    }
}
