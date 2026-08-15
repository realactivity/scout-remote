package dev.lantern.app.platform

import dev.lantern.core.platform.AuthCallbackHandler

/**
 * MSAL redirect / PKCE callback. Real interactive sign-in is human-gated
 * (no tenant in CI). Throws a clear error when the Entra app is unset.
 */
class MsalAuthCallbackHandler : AuthCallbackHandler {
    override suspend fun launchInteractiveSignIn(authorizationUri: String, redirectUri: String): String {
        throw IllegalStateException(
            "Interactive MSAL sign-in is human-gated. Configure LANTERN_CLIENT_ID and a desktop/Android redirect, then use the real-tenant smoke kit.",
        )
    }
}
