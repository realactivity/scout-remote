package dev.lantern.app.identity

import android.content.Context
import dev.lantern.app.BuildConfig
import dev.lantern.core.application.IdentityService
import dev.lantern.core.application.SignedInAccount

/**
 * Phase 0 identity scaffold. The Entra registration and interactive MSAL
 * implementation are human-gated, so this class fails clearly instead of
 * pretending a token or account exists.
 */
class MsalIdentityService(private val context: Context) : IdentityService {
    @Volatile
    private var account: SignedInAccount? = null

    fun cachedObjectId(): String? = account?.entraObjectId

    override suspend fun getCurrentAccount(): SignedInAccount? = account

    override suspend fun signIn(): SignedInAccount {
        if (BuildConfig.LANTERN_CLIENT_ID.isBlank()) {
            throw IllegalStateException(
                "LANTERN_CLIENT_ID is not configured. Android MSAL sign-in is human-gated.",
            )
        }
        throw IllegalStateException(
            "Interactive MSAL sign-in requires a human with tenant access. Use scripts/smoke-tenant/.",
        )
    }

    override suspend fun acquireGraphToken(): String {
        throw IllegalStateException("Graph tokens are not acquired in the unsigned Phase 0 debug build.")
    }

    override suspend fun signOut() {
        account = null
    }

    @Suppress("unused")
    private fun contextRef(): Context = context
}
