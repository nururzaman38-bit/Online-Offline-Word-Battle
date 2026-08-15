package com.wordbattle.com.data.model

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {

    @Test
    fun `an AppException keeps its own code`() {
        val error: Throwable = AppException(AppErrorCode.ROOM_FULL, "no seats")
        assertEquals(AppErrorCode.ROOM_FULL, error.appErrorCode())
    }

    @Test
    fun `network failures are reported as no internet`() {
        assertEquals(AppErrorCode.NO_INTERNET, UnknownHostException("nbaq.supabase.co").appErrorCode())
        assertEquals(AppErrorCode.NO_INTERNET, IOException("Failed to connect to /10.0.2.2").appErrorCode())
        assertEquals(AppErrorCode.NO_INTERNET, IOException("Socket timeout").appErrorCode())
    }

    @Test
    fun `row level security denials are not mistaken for something retryable`() {
        val rls = IllegalStateException(
            "new row violates row-level security policy for table \"rooms\""
        )
        assertEquals(AppErrorCode.ROOM_CREATE_DENIED, rls.appErrorCode())
        assertTrue(SupabaseErrorClassifier.isRlsDenied("permission denied for table rooms"))
        assertTrue(SupabaseErrorClassifier.isRlsDenied("42501"))
    }

    @Test
    fun `only a room code clash is treated as a duplicate room code`() {
        assertTrue(
            SupabaseErrorClassifier.isDuplicateRoomCode(
                "duplicate key value violates unique constraint \"rooms_room_code_key\""
            )
        )
        assertTrue(SupabaseErrorClassifier.isDuplicateRoomCode("23505 room_code"))
        // A duplicate on another column must not trigger the room-code retry loop.
        assertFalse(
            SupabaseErrorClassifier.isDuplicateRoomCode(
                "duplicate key value violates unique constraint \"profiles_username_key\""
            )
        )
    }

    @Test
    fun `username uniqueness maps to username taken`() {
        val taken = IllegalStateException(
            "duplicate key value violates unique constraint \"profiles_username_key\": username"
        )
        assertEquals(AppErrorCode.USERNAME_TAKEN, taken.appErrorCode())
    }

    @Test
    fun `the cooldown trigger message maps to the cooldown code`() {
        val cooldown = IllegalStateException(
            "display_name_change_cooldown: display name can be changed again in 4 day(s)"
        )
        assertEquals(AppErrorCode.DISPLAY_NAME_COOLDOWN, cooldown.appErrorCode())
    }

    @Test
    fun `missing session maps to not signed in`() {
        assertEquals(AppErrorCode.NOT_SIGNED_IN, IllegalStateException("JWT expired").appErrorCode())
    }

    @Test
    fun `unrecognized failures stay unknown`() {
        assertEquals(AppErrorCode.UNKNOWN, IllegalStateException("teapot").appErrorCode())
    }

    @Test
    fun `nested causes are inspected too`() {
        val wrapped = IllegalStateException("insert failed", UnknownHostException("no dns"))
        assertEquals(AppErrorCode.NO_INTERNET, wrapped.appErrorCode())
    }
}
