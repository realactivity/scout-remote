package dev.lantern.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.lantern.core.platform.ExternalAppLauncher

class AndroidExternalAppLauncher(private val context: Context) : ExternalAppLauncher {
    override suspend fun open(target: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
