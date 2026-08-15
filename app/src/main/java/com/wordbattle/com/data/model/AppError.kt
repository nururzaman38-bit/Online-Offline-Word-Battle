package com.wordbattle.com.data.model

/**
 * Stable, language independent error identities.
 *
 * Repositories throw [AppException] with one of these codes; the UI layer turns the code into a
 * localized message (English + Bengali) via `AppErrorCode.asUiText()`. This keeps user-facing text out of
 * the data layer while still giving people a friendly explanation instead of a Postgres error.
 */
enum class AppErrorCode {
    NO_INTERNET,
    NOT_SIGNED_IN,
    PROFILE_MISSING,
    SUPABASE_MISCONFIGURED,
    INVALID_SLOTS,
    ROOM_CODE_UNAVAILABLE,
    ROOM_CREATE_DENIED,
    ROOM_CREATE_FAILED,
    ROOM_SLOTS_FAILED,
    ROOM_NOT_FOUND,
    ROOM_FULL,
    ROOM_ALREADY_STARTED,
    ROOM_JOIN_DENIED,
    NOT_HOST,
    LOBBY_NOT_READY,
    USERNAME_TAKEN,
    USERNAME_INVALID,
    DISPLAY_NAME_INVALID,
    DISPLAY_NAME_COOLDOWN,
    GAME_NOT_IN_PROGRESS,
    NOT_YOUR_TURN,
    TURN_ALREADY_ADVANCED,
    UNKNOWN
}

/** Exception carrying an [AppErrorCode] plus optional technical detail for logs. */
class AppException(
    val code: AppErrorCode,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(detail ?: code.name, cause)

/** Maps any throwable onto a code, preserving one we already produced. */
fun Throwable.appErrorCode(): AppErrorCode = when (this) {
    is AppException -> code
    else -> SupabaseErrorClassifier.classify(this)
}

/**
 * Best-effort classification of raw PostgREST/auth failures so RLS, auth and profile problems are
 * reported honestly instead of being retried or hidden behind "please try again".
 */
object SupabaseErrorClassifier {

    fun classify(error: Throwable): AppErrorCode {
        val text = buildString {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < 5) {
                append(current.message.orEmpty()).append(' ')
                append(current::class.simpleName.orEmpty()).append(' ')
                current = current.cause
                depth++
            }
        }.lowercase()
        return when {
            isNetwork(text) -> AppErrorCode.NO_INTERNET
            isRlsDenied(text) -> AppErrorCode.ROOM_CREATE_DENIED
            text.contains("jwt") || text.contains("not authenticated") || text.contains("401") ->
                AppErrorCode.NOT_SIGNED_IN
            text.contains("profiles") && text.contains("foreign key") -> AppErrorCode.PROFILE_MISSING
            isDuplicateRoomCode(text) -> AppErrorCode.ROOM_CODE_UNAVAILABLE
            text.contains("username") && text.contains("duplicate") -> AppErrorCode.USERNAME_TAKEN
            text.contains("username") && text.contains("unique") -> AppErrorCode.USERNAME_TAKEN
            text.contains("display_name_change_cooldown") || text.contains("cooldown") ->
                AppErrorCode.DISPLAY_NAME_COOLDOWN
            else -> AppErrorCode.UNKNOWN
        }
    }

    fun isNetwork(text: String): Boolean =
        text.contains("unknownhost") || text.contains("unable to resolve host") ||
            text.contains("connectexception") || text.contains("sockettimeout") ||
            text.contains("timeout") || text.contains("failed to connect") ||
            text.contains("network is unreachable") || text.contains("no address associated")

    fun isRlsDenied(text: String): Boolean =
        text.contains("row-level security") || text.contains("row level security") ||
            text.contains("42501") || text.contains("permission denied") ||
            text.contains("violates row")

    /** True only for a `room_code` uniqueness clash — the single case worth retrying. */
    fun isDuplicateRoomCode(text: String): Boolean =
        (text.contains("23505") || text.contains("duplicate key")) &&
            (text.contains("room_code") || text.contains("rooms_room_code_key"))
}
