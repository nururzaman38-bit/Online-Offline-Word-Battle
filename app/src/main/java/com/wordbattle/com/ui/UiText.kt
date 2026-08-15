package com.wordbattle.com.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.wordbattle.com.R
import com.wordbattle.com.data.model.AppErrorCode

/**
 * A piece of user-facing text that the ViewModel can produce without touching resources.
 *
 * The ViewModel has no business knowing which language the UI is in, so it emits a [UiText]
 * (usually a string resource id plus formatting arguments) and the Composable layer resolves it.
 * That keeps every visible sentence translatable in `values/strings.xml` and `values-bn/strings.xml`.
 */
sealed interface UiText {

    /** A localized string resource with optional format arguments. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Text that is already user data (a name, a word, a room code) and must not be translated. */
    data class Raw(val value: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

/** Resolves a [UiText] inside a Composable, reacting to language changes automatically. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
}

/** Resolves a [UiText] outside composition (e.g. for share intents). */
fun UiText.resolve(context: android.content.Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
}

/** Convenience for Composables that need the current [android.content.Context]. */
@Composable
fun rememberStringResolver(): (UiText) -> String {
    val context = LocalContext.current
    return { it.resolve(context) }
}

/** Maps a stable [AppErrorCode] onto its localized explanation. */
@StringRes
fun AppErrorCode.messageRes(): Int = when (this) {
    AppErrorCode.NO_INTERNET -> R.string.error_no_internet
    AppErrorCode.NOT_SIGNED_IN -> R.string.error_not_signed_in
    AppErrorCode.PROFILE_MISSING -> R.string.error_profile_missing
    AppErrorCode.SUPABASE_MISCONFIGURED -> R.string.error_supabase_misconfigured
    AppErrorCode.INVALID_SLOTS -> R.string.error_invalid_slots
    AppErrorCode.ROOM_CODE_UNAVAILABLE -> R.string.error_room_code_unavailable
    AppErrorCode.ROOM_CREATE_DENIED -> R.string.error_room_create_denied
    AppErrorCode.ROOM_CREATE_FAILED -> R.string.error_room_create_failed
    AppErrorCode.ROOM_SLOTS_FAILED -> R.string.error_room_slots_failed
    AppErrorCode.ROOM_NOT_FOUND -> R.string.error_room_not_found
    AppErrorCode.ROOM_FULL -> R.string.error_room_full
    AppErrorCode.ROOM_ALREADY_STARTED -> R.string.error_room_already_started
    AppErrorCode.ROOM_JOIN_DENIED -> R.string.error_room_join_denied
    AppErrorCode.NOT_HOST -> R.string.error_not_host
    AppErrorCode.LOBBY_NOT_READY -> R.string.error_lobby_not_ready
    AppErrorCode.USERNAME_TAKEN -> R.string.error_username_taken
    AppErrorCode.USERNAME_INVALID -> R.string.error_username_invalid
    AppErrorCode.DISPLAY_NAME_INVALID -> R.string.error_display_name_invalid
    AppErrorCode.DISPLAY_NAME_COOLDOWN -> R.string.error_display_name_cooldown
    AppErrorCode.GAME_NOT_IN_PROGRESS -> R.string.error_game_not_in_progress
    AppErrorCode.NOT_YOUR_TURN -> R.string.error_not_your_turn
    AppErrorCode.TURN_ALREADY_ADVANCED -> R.string.error_turn_already_advanced
    AppErrorCode.UNKNOWN -> R.string.error_unknown
}

/** Localized message for any error code. */
fun AppErrorCode.asUiText(): UiText = UiText.Res(messageRes())
