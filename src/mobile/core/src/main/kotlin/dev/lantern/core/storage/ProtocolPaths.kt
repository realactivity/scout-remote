package dev.lantern.core.storage

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** App Folder paths from contracts/README.md (spec §6). */
object ProtocolPaths {
    const val PROTOCOL: String = "protocol.json"
    const val INBOX: String = "commands/inbox"
    const val PROCESSED: String = "commands/processed"
    const val RESPONSES: String = "responses"
    const val CONVERSATIONS: String = "conversations"
    const val ACTIVITY: String = "activity"

    fun status(agentId: String): String = "agents/$agentId/status.json"

    fun inboxCommand(commandId: String): String = "$INBOX/$commandId.json"

    fun receipt(commandId: String): String = "$PROCESSED/$commandId.json"

    fun response(commandId: String): String = "$RESPONSES/$commandId.json"

    fun conversationFolder(conversationId: String): String = "$CONVERSATIONS/$conversationId"

    fun activityFolder(day: String): String = "$ACTIVITY/$day"

    fun activityEvent(day: String, eventId: String): String = "${activityFolder(day)}/$eventId.json"

    fun activityDay(instant: Instant): String =
        instant.toLocalDateTime(TimeZone.UTC).date.toString()
}
