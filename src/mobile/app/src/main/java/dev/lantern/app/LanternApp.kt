package dev.lantern.app

import android.app.Application
import dev.lantern.app.identity.MsalIdentityService
import dev.lantern.app.platform.AndroidExternalAppLauncher
import dev.lantern.app.platform.KeystoreSecureStorage
import dev.lantern.app.platform.MsalAuthCallbackHandler
import dev.lantern.app.platform.SystemNotificationService
import dev.lantern.app.platform.WorkManagerBackgroundScheduler
import dev.lantern.core.application.BridgeSyncServiceImpl
import dev.lantern.core.application.CommandServiceImpl
import dev.lantern.core.application.FileCommandQueue
import dev.lantern.core.application.InstantClock
import dev.lantern.core.domain.CommandRequester
import dev.lantern.core.storage.FilesystemAppFolderStore
import dev.lantern.core.transport.GraphBridgeTransport

class LanternApp : Application() {
    lateinit var commandService: CommandServiceImpl
        private set
    lateinit var syncService: BridgeSyncServiceImpl
        private set
    lateinit var identity: MsalIdentityService
        private set

    override fun onCreate() {
        super.onCreate()
        val approot = filesDir.resolve("approot").toPath()
        val queueDir = filesDir.resolve("command-queue").toPath()
        val store = FilesystemAppFolderStore(approot)
        val transport = GraphBridgeTransport(store)
        val clock = InstantClock.System
        identity = MsalIdentityService(this)
        commandService = CommandServiceImpl(
            transport = transport,
            queue = FileCommandQueue(queueDir),
            clock = clock,
            requester = {
                CommandRequester(identity.cachedObjectId() ?: "unsigned-local-dev")
            },
        )
        syncService = BridgeSyncServiceImpl(transport, clock)
        KeystoreSecureStorage(this)
        WorkManagerBackgroundScheduler()
        SystemNotificationService()
        MsalAuthCallbackHandler()
        AndroidExternalAppLauncher(this)
    }
}
