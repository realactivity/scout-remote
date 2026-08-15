package dev.scoutremote.core.application

/**
 * MSAL sign-in and token acquisition (spec §10). Public client, PKCE, delegated scopes only.
 * Each client (Android, later iOS, desktop bridge) authenticates independently under the same
 * Entra app registration; tokens are never transferred between devices.
 */
interface IdentityService {

    suspend fun getCurrentAccount(): SignedInAccount?

    /** Silent first; interactive only when tenant policy or token state requires it. */
    suspend fun signIn(): SignedInAccount

    suspend fun acquireGraphToken(): String

    suspend fun signOut()

    companion object {
        /** Least-privilege MVP scopes; broader scopes only for explicit features (spec §10.2). */
        val MVP_SCOPES: List<String> = listOf("Files.ReadWrite.AppFolder")
    }
}

data class SignedInAccount(
    val entraObjectId: String,
    val username: String,
    val tenantId: String,
)
