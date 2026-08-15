package dev.lantern.app.brand

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Machine-readable brand tokens loaded from docs/brand-tokens.json.
 * No ad-hoc hex lives in UI code; the JSON file is the color source.
 */
data class BrandTokens(
    val midnight: String,
    val steeple: String,
    val bone: String,
    val candle: String,
    val idle: String,
    val working: String,
    val waiting: String,
    val offline: String,
    val error: String,
    val cardFill: String,
    val hairline: String,
    val textSecondary: String,
    val textLabel: String,
    val textBody: String,
    val tagline: String,
    val displayName: String,
) {
    companion object {
        fun fromJson(text: String): BrandTokens {
            val root = Json.parseToJsonElement(text).jsonObject
            val brand = root.getValue("brand").jsonObject
            val color = root.getValue("color").jsonObject
            val core = color.getValue("core").jsonObject
            val status = color.getValue("status").jsonObject
            val support = color.getValue("support").jsonObject
            fun hex(obj: kotlinx.serialization.json.JsonObject, key: String): String =
                obj.getValue(key).jsonObject.getValue("hex").jsonPrimitive.content
            return BrandTokens(
                midnight = hex(core, "midnight"),
                steeple = hex(core, "steeple"),
                bone = hex(core, "bone"),
                candle = hex(core, "candle"),
                idle = hex(status, "idle"),
                working = hex(status, "working"),
                waiting = hex(status, "waiting"),
                offline = hex(status, "offline"),
                error = hex(status, "error"),
                cardFill = hex(support, "cardFill"),
                hairline = hex(support, "hairline"),
                textSecondary = hex(support, "textSecondary"),
                textLabel = hex(support, "textLabel"),
                textBody = hex(support, "textBody"),
                tagline = brand.getValue("tagline").jsonPrimitive.content,
                displayName = brand.getValue("displayName").jsonPrimitive.content,
            )
        }
    }
}
