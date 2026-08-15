package dev.lantern.app.platform

import android.util.Log
import dev.lantern.core.platform.NotificationService

/**
 * Phase 0 stub. Policy is implemented (completion / failure / desktop
 * attention only). This class does not post a system notification yet; it
 * records the request so Phase 1 can attach NotificationManager.
 */
class SystemNotificationService : NotificationService {
    override suspend fun notifyCommandOutcome(commandId: String, title: String, body: String) {
        Log.i(TAG, "notifyCommandOutcome commandId=$commandId title=$title")
    }

    override suspend fun showAttentionRequired(reason: String) {
        Log.i(TAG, "showAttentionRequired reason=$reason")
    }

    companion object {
        private const val TAG = "lantern.notify"
    }
}
