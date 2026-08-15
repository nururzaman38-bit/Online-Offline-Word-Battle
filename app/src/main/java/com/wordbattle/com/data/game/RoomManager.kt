package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.RoomSlot

/** Shared room invariants used by host, repository and UI layers. */
object RoomManager {

    const val MIN_TOTAL_SLOTS = 2
    const val MAX_TOTAL_SLOTS = 4
    const val MIN_LOCAL_SLOTS = 1
    const val MIN_ONLINE_SLOTS = 1

    /** Reasons a local/online slot split cannot be used to create a room. */
    enum class SlotError {
        LOCAL_TOO_FEW,
        ONLINE_TOO_FEW,
        TOTAL_TOO_FEW,
        TOTAL_TOO_MANY
    }

    /**
     * Validates the local/online split chosen by the host.
     *
     * @return `null` when the configuration is valid, otherwise the first problem found.
     */
    fun validateSlots(localSlots: Int, onlineSlots: Int): SlotError? = when {
        localSlots < MIN_LOCAL_SLOTS -> SlotError.LOCAL_TOO_FEW
        onlineSlots < MIN_ONLINE_SLOTS -> SlotError.ONLINE_TOO_FEW
        localSlots + onlineSlots < MIN_TOTAL_SLOTS -> SlotError.TOTAL_TOO_FEW
        localSlots + onlineSlots > MAX_TOTAL_SLOTS -> SlotError.TOTAL_TOO_MANY
        else -> null
    }

    fun isValidSlotConfiguration(localSlots: Int, onlineSlots: Int): Boolean =
        validateSlots(localSlots, onlineSlots) == null

    /** True when [slotIndex] belongs to the host device (a local, pass-and-play seat). */
    fun isLocalSlot(room: Room, slotIndex: Int): Boolean = slotIndex < room.localSlotsCount

    /** True when [slotIndex] is an online seat that a remote device may claim. */
    fun isOnlineSlot(room: Room, slotIndex: Int): Boolean =
        slotIndex >= room.localSlotsCount && slotIndex < room.totalSlots

    /** True when the generated slot rows match the room configuration exactly. */
    fun slotsAreComplete(room: Room): Boolean {
        if (room.totalSlots != room.localSlotsCount + room.onlineSlotsCount) return false
        if (room.slots.size != room.totalSlots) return false
        return room.slots.map(RoomSlot::slotIndex).sorted() == (0 until room.totalSlots).toList()
    }

    /** The online slot a joining user may take, or `null` when the room cannot accept them. */
    fun claimableSlot(room: Room, uid: String): RoomSlot? {
        if (room.status != "lobby") return null
        return room.slots
            .sortedBy(RoomSlot::slotIndex)
            .firstOrNull { isOnlineSlot(room, it.slotIndex) && it.filledByUid == null && it.filledByName == null }
            ?.takeIf { room.slots.none { slot -> slot.filledByUid == uid } }
    }

    /** True when [uid] already occupies a seat in [room]. */
    fun isMember(room: Room, uid: String): Boolean = room.slots.any { it.filledByUid == uid }

    fun canHostStart(room: Room): Boolean =
        room.status == "lobby" &&
            slotsAreComplete(room) &&
            room.slots.all { it.filledByName != null && it.isReady }

    fun ownedPlayerIds(
        game: GameState,
        currentUid: String?,
        isHostDevice: Boolean,
        hostLocalSlots: Int
    ): Set<String> {
        val owned = mutableSetOf<String>()
        if (isHostDevice) {
            // The host device drives every seat that lives on this phone.
            game.players.filter { it.turnOrder < hostLocalSlots }.forEach { owned += it.id }
        }
        // A signed-in user always owns the seat bound to their own uid, host or not.
        currentUid?.let { uid -> game.players.filter { it.id == uid }.forEach { owned += it.id } }
        return owned
    }

    /** Only the device that owns the current turn may interact with the board. */
    fun canPlay(game: GameState, ownedPlayerIds: Set<String>): Boolean =
        game.currentTurnPlayerId in ownedPlayerIds

    /** Name of the player everybody else is waiting for. */
    fun waitingForName(game: GameState): String? =
        game.players.firstOrNull { it.id == game.currentTurnPlayerId }?.name
}
