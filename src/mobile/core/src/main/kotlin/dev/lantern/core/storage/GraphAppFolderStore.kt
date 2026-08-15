package dev.lantern.core.storage

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Microsoft Graph backend against /me/drive/special/approot. Create-if-absent
 * is keyed on commandId. Mutable writes send If-Match. 429 responses honor
 * Retry-After. Exercised by the real-tenant smoke kit, not by CI.
 */
class GraphAppFolderStore(
    private val tokenProvider: suspend () -> String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
    private val maxRetries: Int = 4,
) : AppFolderStore {
    private var approotId: String? = null
    private var driveId: String? = null

    override suspend fun get(path: String): StoredItem? {
        ensureApproot()
        val response = send("GET", itemByPathUri(path) + ":/content", null, null)
        if (response.statusCode() == 404) {
            return null
        }
        check(response.statusCode() in 200..299) { "Graph GET ${response.statusCode()}: ${response.body()}" }
        val bytes = response.body()
        val meta = metadata(path)
        return StoredItem(
            path = normalize(path),
            etag = meta?.etag ?: FilesystemAppFolderStore.etagOf(bytes),
            itemId = meta?.itemId ?: normalize(path),
            lastModifiedUtc = meta?.lastModifiedUtc ?: Instant.fromEpochMilliseconds(0),
            content = bytes,
        )
    }

    override suspend fun list(prefix: String): List<StoredItem> {
        ensureApproot()
        val folder = normalize(prefix)
        val uri = if (folder.isEmpty()) {
            "$GRAPH/drives/$driveId/items/$approotId/children"
        } else {
            itemByPathUri(folder) + ":/children"
        }
        return enumerateChildren(uri, folder)
    }

    override suspend fun write(path: String, content: ByteArray): StoredItem {
        ensureApproot()
        val response = send("PUT", itemByPathUri(path) + ":/content", content, null)
        check(response.statusCode() in 200..299) { "Graph PUT ${response.statusCode()}: ${String(response.body())}" }
        return parseItem(String(response.body()), path, content)
    }

    override suspend fun createIfAbsent(path: String, content: ByteArray): StoredItem? {
        ensureApproot()
        if (metadata(path) != null) {
            return null
        }
        val response = send(
            "PUT",
            itemByPathUri(path) + ":/content?@microsoft.graph.conflictBehavior=fail",
            content,
            ifNoneMatch = true,
            ifMatch = null,
        )
        if (response.statusCode() == 409 || response.statusCode() == 412) {
            return null
        }
        check(response.statusCode() in 200..299) { "Graph create ${response.statusCode()}: ${String(response.body())}" }
        return parseItem(String(response.body()), path, content)
    }

    override suspend fun updateIfMatch(path: String, content: ByteArray, etag: String): StoredItem {
        ensureApproot()
        val response = send("PUT", itemByPathUri(path) + ":/content", content, etag)
        if (response.statusCode() == 412) {
            val current = metadata(path)
            throw ETagConflictException(path, etag, current?.etag ?: "unknown")
        }
        check(response.statusCode() in 200..299) { "Graph update ${response.statusCode()}: ${String(response.body())}" }
        return parseItem(String(response.body()), path, content)
    }

    override suspend fun delete(path: String) {
        ensureApproot()
        val response = send("DELETE", itemByPathUri(path), null, null)
        if (response.statusCode() == 404) {
            return
        }
        check(response.statusCode() in 200..299) { "Graph DELETE ${response.statusCode()}" }
    }

    override suspend fun listDelta(cursor: String?): DeltaPage {
        ensureApproot()
        val uri = cursor?.takeIf { it.isNotBlank() } ?: "$GRAPH/drives/$driveId/items/$approotId/delta"
        val response = send("GET", uri, null, null)
        check(response.statusCode() in 200..299) { "Graph delta ${response.statusCode()}" }
        val obj = Json.parseToJsonElement(String(response.body())).jsonObject
        val items = obj["value"]?.jsonArray.orEmpty().mapNotNull { el ->
            val child = el.jsonObject
            if (child["file"] == null) {
                return@mapNotNull null
            }
            val name = child["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            StoredItem(
                path = name,
                etag = readEtag(child),
                itemId = child["id"]?.jsonPrimitive?.contentOrNull ?: name,
                lastModifiedUtc = readTimestamp(child),
                content = null,
            )
        }
        val next = obj["@odata.deltaLink"]?.jsonPrimitive?.contentOrNull
            ?: obj["@odata.nextLink"]?.jsonPrimitive?.contentOrNull
        return DeltaPage(items, next)
    }

    suspend fun resolveByItemId(driveItemId: String): ArtifactResolution? {
        ensureApproot()
        val response = send("GET", "$GRAPH/drives/$driveId/items/${encode(driveItemId)}", null, null)
        if (response.statusCode() == 404) {
            return null
        }
        check(response.statusCode() in 200..299) { "Graph item ${response.statusCode()}" }
        val obj = Json.parseToJsonElement(String(response.body())).jsonObject
        return ArtifactResolution(
            driveItemId = obj["id"]?.jsonPrimitive?.contentOrNull ?: driveItemId,
            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: driveItemId,
            sizeBytes = obj["size"]?.jsonPrimitive?.longOrNull,
            createdUtc = readTimestamp(obj),
            webUrl = obj["webUrl"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun ensureApproot() {
        if (approotId != null && driveId != null) {
            return
        }
        val response = send("GET", "$GRAPH/me/drive/special/approot", null, null)
        check(response.statusCode() in 200..299) { "Graph approot ${response.statusCode()}: ${String(response.body())}" }
        val obj = Json.parseToJsonElement(String(response.body())).jsonObject
        approotId = obj["id"]?.jsonPrimitive?.content
        driveId = obj["parentReference"]?.jsonObject?.get("driveId")?.jsonPrimitive?.contentOrNull
        if (driveId == null) {
            val drive = send("GET", "$GRAPH/me/drive", null, null)
            check(drive.statusCode() in 200..299)
            driveId = Json.parseToJsonElement(String(drive.body())).jsonObject["id"]?.jsonPrimitive?.content
        }
        check(!approotId.isNullOrBlank() && !driveId.isNullOrBlank()) { "Graph approot ids missing" }
    }

    private suspend fun metadata(path: String): StoredItem? {
        val response = send("GET", itemByPathUri(path), null, null)
        if (response.statusCode() == 404) {
            return null
        }
        check(response.statusCode() in 200..299) { "Graph meta ${response.statusCode()}" }
        return parseItem(String(response.body()), path, null)
    }

    private suspend fun enumerateChildren(uri: String, folder: String): List<StoredItem> {
        val items = mutableListOf<StoredItem>()
        var next: String? = uri
        while (!next.isNullOrBlank()) {
            val response = send("GET", next, null, null)
            check(response.statusCode() in 200..299) { "Graph children ${response.statusCode()}" }
            val obj = Json.parseToJsonElement(String(response.body())).jsonObject
            for (el in obj["value"]?.jsonArray.orEmpty()) {
                val child = el.jsonObject
                val name = child["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val childPath = if (folder.isEmpty()) name else "${folder.trimEnd('/')}/$name"
                if (child["folder"] != null) {
                    items += enumerateChildren(itemByPathUri(childPath) + ":/children", childPath)
                    continue
                }
                items += StoredItem(
                    path = childPath,
                    etag = readEtag(child),
                    itemId = child["id"]?.jsonPrimitive?.contentOrNull ?: childPath,
                    lastModifiedUtc = readTimestamp(child),
                    content = null,
                )
            }
            next = obj["@odata.nextLink"]?.jsonPrimitive?.contentOrNull
        }
        return items
    }

    private suspend fun send(
        method: String,
        uri: String,
        body: ByteArray?,
        ifMatch: String?,
        ifNoneMatch: Boolean = false,
    ): HttpResponse<ByteArray> {
        var attempt = 0
        while (true) {
            val token = tokenProvider()
            val builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $token")
            if (ifMatch != null) {
                builder.header("If-Match", quoteEtag(ifMatch))
            }
            if (ifNoneMatch) {
                builder.header("If-None-Match", "*")
            }
            val request = when {
                method == "GET" -> builder.GET().build()
                method == "DELETE" -> builder.DELETE().build()
                body != null -> builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build()
                else -> builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
            }
            val response = withContext(Dispatchers.IO) {
                http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            }
            if (response.statusCode() != 429 || attempt >= maxRetries) {
                return response
            }
            val retryAfter = response.headers().firstValue("Retry-After").orElse(null)
            val delayMs = retryAfter?.toLongOrNull()?.times(1000)
                ?: (1L shl attempt) * 1000
            delay(delayMs)
            attempt++
        }
    }

    private fun itemByPathUri(path: String): String =
        "$GRAPH/drives/$driveId/items/$approotId:/${normalize(path)}"

    private fun parseItem(body: String, path: String, content: ByteArray?): StoredItem {
        if (body.isBlank()) {
            return StoredItem(normalize(path), FilesystemAppFolderStore.etagOf(content ?: ByteArray(0)), normalize(path), Instant.fromEpochMilliseconds(0), content)
        }
        val obj = Json.parseToJsonElement(body).jsonObject
        return StoredItem(
            path = normalize(path),
            etag = readEtag(obj),
            itemId = obj["id"]?.jsonPrimitive?.contentOrNull ?: normalize(path),
            lastModifiedUtc = readTimestamp(obj),
            content = content,
        )
    }

    private fun readEtag(obj: JsonObject): String =
        (obj["eTag"]?.jsonPrimitive?.contentOrNull ?: obj["cTag"]?.jsonPrimitive?.contentOrNull ?: "unknown")
            .trim('"')

    private fun readTimestamp(obj: JsonObject): Instant {
        val raw = obj["lastModifiedDateTime"]?.jsonPrimitive?.contentOrNull
            ?: obj["createdDateTime"]?.jsonPrimitive?.contentOrNull
            ?: return Instant.fromEpochMilliseconds(0)
        return Instant.parse(raw)
    }

    private fun quoteEtag(etag: String): String = if (etag.startsWith('"')) etag else "\"$etag\""

    private fun normalize(path: String): String = path.replace('\\', '/').trimStart('/')

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    companion object {
        private const val GRAPH = "https://graph.microsoft.com/v1.0"
    }
}

data class ArtifactResolution(
    val driveItemId: String,
    val name: String,
    val sizeBytes: Long?,
    val createdUtc: Instant?,
    val webUrl: String?,
)
