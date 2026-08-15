package dev.scoutremote.core.platform

import kotlin.time.Duration

/**
 * OS-protected secret storage. Android: Keystore-backed. iOS (Phase 4): Keychain-backed.
 * MSAL token caches live behind this abstraction and never anywhere else (spec §10, §16.1).
 */
interface SecureStorage {
    suspend fun get(key: String): ByteArray?
    suspend fun set(key: String, value: ByteArray)
    suspend fun remove(key: String)
}

/**
 * Background work at OS-permitted intervals. Android: WorkManager with constraints.
 * Push infrastructure is a later, optional deployment mode (spec §9.2, §17).
 */
interface BackgroundScheduler {
    suspend fun scheduleStatusRefresh(interval: Duration)
    suspend fun cancelScheduledWork()
}

/**
 * User notifications. Policy (spec §9.2): notify on command completion/failure/
 * waiting_for_desktop_approval; never per heartbeat — routine activity collapses to a digest.
 */
interface NotificationService {
    suspend fun notifyCommandOutcome(commandId: String, title: String, body: String)
    suspend fun showAttentionRequired(reason: String)
}

/**
 * Platform auth-callback/browser handling for MSAL redirects.
 * URIs are strings to keep this module free of platform types.
 */
interface AuthCallbackHandler {
    suspend fun launchInteractiveSignIn(authorizationUri: String, redirectUri: String): String
}

/** Opens artifacts in the appropriate Microsoft 365 app via OneDrive links (spec §9). */
interface ExternalAppLauncher {
    suspend fun open(target: String): Boolean
}
