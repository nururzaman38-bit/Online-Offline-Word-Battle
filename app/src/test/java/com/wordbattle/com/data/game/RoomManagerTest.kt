package com.wordbattle.com.data.game

import com.wordbattle.com.data.model.GameMode
import com.wordbattle.com.data.model.GameState
import com.wordbattle.com.data.model.Player
import com.wordbattle.com.data.model.PlayerType
import com.wordbattle.com.data.model.Room
import com.wordbattle.com.data.model.RoomSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomManagerTest {

    private fun room(
        localSlots: Int,
        onlineSlots: Int,
        slots: List<RoomSlot>,
        status: String = "lobby"
    ) = Room(
        roomId = "room-1",
        roomCode = "ABC123",
        passcode = "1234",
        hostId = "host",
        totalSlots = localSlots + onlineSlots,
        localSlotsCount = localSlots,
        onlineSlotsCount = onlineSlots,
        slots = slots,
        status = status
    )

    private fun slot(index: Int, uid: String? = null, name: String? = null, ready: Boolean = false) =
        RoomSlot(index, uid, name, ready)

    @Test
    fun `valid split is accepted`() {
        assertNull(RoomManager.validateSlots(1, 1))
        assertNull(RoomManager.validateSlots(2, 1))
        assertNull(RoomManager.validateSlots(1, 3))
        assertTrue(RoomManager.isValidSlotConfiguration(3, 1))
    }

    @Test
    fun `a room always needs at least one local seat`() {
        assertEquals(RoomManager.SlotError.LOCAL_TOO_FEW, RoomManager.validateSlots(0, 2))
        assertEquals(RoomManager.SlotError.LOCAL_TOO_FEW, RoomManager.validateSlots(-1, 3))
    }

    @Test
    fun `an online room always needs at least one online seat`() {
        assertEquals(RoomManager.SlotError.ONLINE_TOO_FEW, RoomManager.validateSlots(2, 0))
    }

    @Test
    fun `more than four players is rejected`() {
        assertEquals(RoomManager.SlotError.TOTAL_TOO_MANY, RoomManager.validateSlots(3, 2))
        assertEquals(RoomManager.SlotError.TOTAL_TOO_MANY, RoomManager.validateSlots(4, 1))
        assertFalse(RoomManager.isValidSlotConfiguration(2, 4))
    }

    @Test
    fun `slot ownership follows the local slot count`() {
        val room = room(2, 1, listOf(slot(0), slot(1), slot(2)))
        assertTrue(RoomManager.isLocalSlot(room, 0))
        assertTrue(RoomManager.isLocalSlot(room, 1))
        assertFalse(RoomManager.isLocalSlot(room, 2))
        assertTrue(RoomManager.isOnlineSlot(room, 2))
        assertFalse(RoomManager.isOnlineSlot(room, 3))
    }

    @Test
    fun `slots are complete only when every index exists exactly once`() {
        assertTrue(RoomManager.slotsAreComplete(room(1, 1, listOf(slot(0), slot(1)))))
        assertFalse(RoomManager.slotsAreComplete(room(1, 1, listOf(slot(0)))))
        assertFalse(RoomManager.slotsAreComplete(room(1, 1, listOf(slot(0), slot(0)))))
        assertFalse(RoomManager.slotsAreComplete(room(2, 1, listOf(slot(0), slot(1)))))
    }

    @Test
    fun `joiner claims the first empty online slot`() {
        val room = room(
            localSlots = 1,
            onlineSlots = 2,
            slots = listOf(slot(0, "host", "Host", ready = true), slot(1), slot(2))
        )
        assertEquals(1, RoomManager.claimableSlot(room, "guest")?.slotIndex)
    }

    @Test
    fun `joiner never claims a local slot`() {
        val room = room(
            localSlots = 2,
            onlineSlots = 1,
            slots = listOf(
                slot(0, "host", "Host", ready = true),
                slot(1, null, null),
                slot(2, "other", "Other")
            )
        )
        // Slot 1 is empty but local, slot 2 is online but taken.
        assertNull(RoomManager.claimableSlot(room, "guest"))
    }

    @Test
    fun `a member does not claim a second slot`() {
        val room = room(
            localSlots = 1,
            onlineSlots = 2,
            slots = listOf(slot(0, "host", "Host", true), slot(1, "guest", "Guest"), slot(2))
        )
        assertNull(RoomManager.claimableSlot(room, "guest"))
        assertTrue(RoomManager.isMember(room, "guest"))
        assertFalse(RoomManager.isMember(room, "stranger"))
    }

    @Test
    fun `a started room cannot be joined`() {
        val room = room(1, 1, listOf(slot(0, "host", "Host", true), slot(1)), status = "in_progress")
        assertNull(RoomManager.claimableSlot(room, "guest"))
    }

    @Test
    fun `host can only start when every seat is filled and ready`() {
        val incomplete = room(1, 1, listOf(slot(0, "host", "Host", true), slot(1)))
        assertFalse(RoomManager.canHostStart(incomplete))

        val notReady = room(1, 1, listOf(slot(0, "host", "Host", true), slot(1, "guest", "Guest", false)))
        assertFalse(RoomManager.canHostStart(notReady))

        val ready = room(1, 1, listOf(slot(0, "host", "Host", true), slot(1, "guest", "Guest", true)))
        assertTrue(RoomManager.canHostStart(ready))
    }

    private fun game(currentTurn: String) = GameState(
        gameId = "game-1",
        mode = GameMode.MIXED_ONLINE,
        players = listOf(
            Player("host", "Host", PlayerType.HUMAN_ONLINE, turnOrder = 0),
            Player("local-2", "Local 2", PlayerType.HUMAN_LOCAL, turnOrder = 1),
            Player("guest", "Guest", PlayerType.HUMAN_ONLINE, turnOrder = 2)
        ),
        currentTurnPlayerId = currentTurn
    )

    @Test
    fun `host device owns its local seats plus its own uid`() {
        val owned = RoomManager.ownedPlayerIds(game("host"), "host", isHostDevice = true, hostLocalSlots = 2)
        assertEquals(setOf("host", "local-2"), owned)
    }

    @Test
    fun `joining device only owns its own seat`() {
        val owned = RoomManager.ownedPlayerIds(game("host"), "guest", isHostDevice = false, hostLocalSlots = 2)
        assertEquals(setOf("guest"), owned)
    }

    @Test
    fun `only the device owning the current turn may play`() {
        val hostOwned = setOf("host", "local-2")
        val guestOwned = setOf("guest")

        assertTrue(RoomManager.canPlay(game("local-2"), hostOwned))
        assertFalse(RoomManager.canPlay(game("guest"), hostOwned))
        assertTrue(RoomManager.canPlay(game("guest"), guestOwned))
        assertFalse(RoomManager.canPlay(game("host"), guestOwned))
        assertFalse(RoomManager.canPlay(game("host"), emptySet()))
    }

    @Test
    fun `waiting name resolves the current player`() {
        assertEquals("Guest", RoomManager.waitingForName(game("guest")))
        assertNull(RoomManager.waitingForName(game("nobody")))
    }
}
