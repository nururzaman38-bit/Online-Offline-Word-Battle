package com.wordbattle.com.data.remote

import com.wordbattle.com.data.remote.dto.NewRoomSlotDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the PGRST102 bulk insert bug.
 *
 * Supabase's PostgREST rejects a JSON array if objects don't share exactly the same keys.
 * The Kotlin DTO had defaults (filled_by = null, filled_by_name = null, is_ready = false) and
 * Supabase serializes with `encodeDefaults = false`, so empty seats sent only 2 keys while host
 * sent 5. This caused "Could not create player seats" for every room shape.
 *
 * After the fix NewRoomSlotDto has no defaults and createRoom() always passes five explicit
 * arguments, keeping the key set uniform.
 */
class NewRoomSlotDtoTest {

    // Same Json config Supabase uses for PostgREST — this is what drops default-valued keys.
    private val json = Json { encodeDefaults = false }

    private val expectedKeys = setOf("room_id", "slot_index", "filled_by", "filled_by_name", "is_ready")

    /**
     * Mirrors RoomRepository.createRoom() slot creation logic exactly.
     */
    private fun createSlots(
        roomId: String,
        uid: String,
        displayName: String,
        totalSlots: Int,
        localSlots: Int
    ): List<NewRoomSlotDto> = List(totalSlots) { index ->
        when {
            index == 0 -> NewRoomSlotDto(
                roomId = roomId,
                slotIndex = index,
                filledBy = uid,
                filledByName = displayName,
                isReady = true
            )
            index < localSlots -> NewRoomSlotDto(
                roomId = roomId,
                slotIndex = index,
                filledBy = null,
                filledByName = "Local Player ${index + 1}",
                isReady = true
            )
            else -> NewRoomSlotDto(
                roomId = roomId,
                slotIndex = index,
                filledBy = null,
                filledByName = null,
                isReady = false
            )
        }
    }

    private fun JsonObject.keySet(): Set<String> = keys

    @Test
    fun `all seats share identical five keys`() {
        val slots = createSlots(
            roomId = "room-123",
            uid = "uid-host",
            displayName = "Host Player",
            totalSlots = 4,
            localSlots = 2
        )
        val encodedObjects = slots.map { dto ->
            json.encodeToJsonElement(NewRoomSlotDto.serializer(), dto).jsonObject
        }
        // Every seat must have exactly the five expected keys.
        encodedObjects.forEach { obj ->
            assertEquals(expectedKeys, obj.keySet())
        }
        // And all key sets must be identical to each other.
        val firstKeys = encodedObjects.first().keySet()
        encodedObjects.forEach { obj ->
            assertEquals(firstKeys, obj.keySet())
        }
    }

    @Test
    fun `empty online seat keeps filled_by and filled_by_name as explicit null`() {
        val slots = createSlots(
            roomId = "room-123",
            uid = "uid-host",
            displayName = "Host",
            totalSlots = 3,
            localSlots = 1
        )
        // Last slot is an empty online seat.
        val emptySeat = slots.last()
        val obj = json.encodeToJsonElement(NewRoomSlotDto.serializer(), emptySeat).jsonObject

        assertTrue("filled_by must be present", obj.containsKey("filled_by"))
        assertTrue("filled_by_name must be present", obj.containsKey("filled_by_name"))
        assertTrue("filled_by must be explicit null, not omitted", obj["filled_by"] is JsonNull)
        assertTrue("filled_by_name must be explicit null, not omitted", obj["filled_by_name"] is JsonNull)
        assertEquals(false, obj["is_ready"]?.toString()?.toBoolean() ?: true)
    }

    @Test
    fun `payload contains no id key`() {
        val slots = createSlots(
            roomId = "room-xyz",
            uid = "uid-1",
            displayName = "Host",
            totalSlots = 3,
            localSlots = 2
        )
        // Encode as array as the real insert does.
        val encodedArrayString = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(NewRoomSlotDto.serializer()),
            slots
        )
        // Quickly check raw string doesn't contain an \"id\" key outside of room_id.
        // Parse and check each object properly.
        val array = json.parseToJsonElement(encodedArrayString) as JsonArray
        array.forEach { element ->
            val obj = element.jsonObject
            assertFalse("payload must not contain 'id' — gen_random_uuid() should generate it", obj.containsKey("id"))
            // Also ensure no accidental extra keys beyond expected.
            assertTrue("unexpected keys: ${obj.keys - expectedKeys}", (obj.keys - expectedKeys).isEmpty())
        }
    }

    @Test
    fun `every valid room shape keeps uniform key set - the main bug catcher`() {
        // Valid shapes: 2..4 total slots, 1..(total-1) local slots => same as RoomManager validation.
        for (total in 2..4) {
            for (local in 1 until total) {
                val slots = createSlots(
                    roomId = "room-$total-$local",
                    uid = "uid-host",
                    displayName = "Host",
                    totalSlots = total,
                    localSlots = local
                )
                val objects = slots.map { dto ->
                    json.encodeToJsonElement(NewRoomSlotDto.serializer(), dto).jsonObject
                }
                // All seats in this shape must share the same key set
                val reference = objects.first().keys
                objects.forEach { obj ->
                    assertEquals(
                        "Room shape total=$total local=$local must have uniform keys; got ${objects.map { it.keys }}",
                        reference,
                        obj.keys
                    )
                    assertEquals(
                        "Room shape total=$total local=$local must have exactly $expectedKeys",
                        expectedKeys,
                        obj.keys
                    )
                }
            }
        }
    }

    @Test
    fun `host seat has filled_by uid and is_ready true`() {
        val uid = "test-uid-123"
        val slots = createSlots(
            roomId = "room-123",
            uid = uid,
            displayName = "Host Display",
            totalSlots = 4,
            localSlots = 1
        )
        val host = slots.first()
        assertEquals(uid, host.filledBy)
        assertEquals("Host Display", host.filledByName)
        assertEquals(true, host.isReady)

        val obj = json.encodeToJsonElement(NewRoomSlotDto.serializer(), host).jsonObject
        assertEquals("\"$uid\"", obj["filled_by"].toString().let {
            // json element string includes quotes; we compare via raw string parsing
            // but easier: check object value as string via json decoding
            // Instead verify parsed value directly:
            obj["filled_by"].toString()
        })
        // Verify boolean true is encoded
        assertEquals("true", obj["is_ready"].toString())
        // Ensure filled_by and is_ready are present
        assertTrue(obj.containsKey("filled_by"))
        assertTrue(obj.containsKey("is_ready"))
    }

    @Test
    fun `specific room shapes documented in bug report`() {
        // Covers the shapes listed in the task: 1+1, 2+1, 1+2, 3+1, 2+2, 1+3
        val cases = listOf(
            Pair(2, 1), // 1+1
            Pair(3, 2), // 2+1
            Pair(3, 1), // 1+2
            Pair(4, 3), // 3+1
            Pair(4, 2), // 2+2
            Pair(4, 1)  // 1+3
        )
        for ((total, local) in cases) {
            val slots = createSlots("room-$total-$local", "uid", "Host", total, local)
            assertEquals("total slots mismatch for $total/$local", total, slots.size)
            slots.forEach { dto ->
                val obj = json.encodeToJsonElement(NewRoomSlotDto.serializer(), dto).jsonObject
                assertEquals(expectedKeys, obj.keys)
            }
        }
    }
}
