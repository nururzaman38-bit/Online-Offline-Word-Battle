package com.wordbattle.com.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.wordbattle.com.BuildConfig
import com.wordbattle.com.data.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

class AuthRepository(
    private val client: SupabaseClient,
    private val users: UserRepository
) {
    suspend fun hasSession(): Boolean {
        client.auth.awaitInitialization()
        return client.auth.currentSessionOrNull() != null
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun signInWithGoogle(context: Context): UserProfile {
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            "Set GOOGLE_WEB_CLIENT_ID in local.properties before using Google sign-in"
        }
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
        val result = CredentialManager.create(context).getCredential(context, request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        client.auth.signInWith(IDToken) {
            idToken = credential.idToken
            provider = Google
            nonce = rawNonce
        }
        val uid = requireNotNull(client.auth.currentUserOrNull()?.id) { "Google sign-in did not return a user" }
        val profile = UserProfile(
            uid = uid,
            displayName = credential.displayName ?: credential.givenName ?: "Word Player",
            photoUrl = credential.profilePictureUri?.toString()
        )
        return users.ensureProfile(profile)
    }

    suspend fun signInWithEmail(email: String, password: String): UserProfile {
        require(email.isNotBlank() && password.length >= 6) { "Enter a valid email and a 6+ character password" }
        client.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
        val uid = requireNotNull(client.auth.currentUserOrNull()?.id)
        return users.ensureProfile(UserProfile(uid, email.substringBefore('@').ifBlank { "Word Player" }))
    }

    /**
     * Single email entry point used by the redesigned login screen.
     *
     * There is no Login/Register toggle any more: we try to sign in first and only create the
     * account when Supabase says the credentials do not belong to an existing user.
     */
    suspend fun signInOrSignUpWithEmail(email: String, password: String): UserProfile {
        require(email.isNotBlank() && password.length >= 6) { "Enter a valid email and a 6+ character password" }
        return runCatching { signInWithEmail(email, password) }.getOrElse { failure ->
            if (!looksLikeMissingAccount(failure)) throw failure
            signUpWithEmail(email, password)
        }
    }

    /** Distinguishes "no such account" from a wrong password or a network failure. */
    private fun looksLikeMissingAccount(error: Throwable): Boolean {
        val text = generateSequence(error) { it.cause }.take(5)
            .joinToString(" ") { "${it.message.orEmpty()} ${it::class.simpleName.orEmpty()}" }
            .lowercase()
        if (SupabaseErrorClassifierBridge.isNetwork(text)) return false
        return text.contains("invalid login credentials") ||
            text.contains("invalid_credentials") ||
            text.contains("user not found") ||
            text.contains("email not found") ||
            text.contains("400")
    }

    suspend fun signUpWithEmail(email: String, password: String): UserProfile {
        require(email.isNotBlank() && password.length >= 6) { "Enter a valid email and a 6+ character password" }
        client.auth.signUpWith(Email) { this.email = email.trim(); this.password = password }
        val uid = requireNotNull(client.auth.currentUserOrNull()?.id) {
            "Check your email to confirm your account, then sign in"
        }
        return users.ensureProfile(UserProfile(uid, email.substringBefore('@').ifBlank { "Word Player" }))
    }

    suspend fun signOut() = client.auth.signOut()
}

/** Thin alias so [AuthRepository] can reuse the shared network heuristics. */
private object SupabaseErrorClassifierBridge {
    fun isNetwork(text: String): Boolean =
        com.wordbattle.com.data.model.SupabaseErrorClassifier.isNetwork(text)
}
