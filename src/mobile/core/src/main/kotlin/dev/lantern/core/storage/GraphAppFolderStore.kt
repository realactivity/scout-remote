package dev.lantern.core.storage

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import dev.lantern.core.metrics.LanternMetrics
import dev.lantern.core.metrics.MetricOperation
import dev.lantern.core.metrics.MetricResult
import dev.lantern.core.metrics.NoOpLanternMetrics
import dev.lantern.core.metrics.OperationMetric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * is keyed on commandId. Mutable writes send If-Match. 429 and 503 responses
 * honor Retry-After. Exercised by the real-tenant smoke kit, not by CI.
 */
class GraphAppFolderStore(
    private val tokenProvider: suspend () -> String,
    private val http: HttpClient = defaultHttpClient(),
    private val maxRetries: Int = 4,
    private val metrics: LanternMetrics = NoOpLanternMetrics,
    private val graphRoot: String = GRAPH,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) : AppFolderStore {
    private var approotId: String? = null
    private var driveId: String? = null
    private var approotPath: String? = null
    private val approotMutex = Mutex()

    override suspend fun get(path: String): StoredItem? =
        when (val result = read(path)) {
            is StoreReadResult.Found -> result.item
            StoreReadResult.Missing, StoreReadResult.NotModified -> null
        }

    override suspend fun read(path: String, knownEtag: String?): StoreReadResult {
        ensureApproot()
        val metadataUri = itemByPathUri(path)
        val contentUri = metadataUri + ":/content"
        return readMetadataAndContent(metadataUri, contentUri, normalize(path), knownEtag)
    }

    override suspend fun readByItemId(itemId: String, knownEtag: String?): StoreReadResult {
        ensureApproot()
        val metadataUri = "$graphRoot/drives/$driveId/items/${encode(itemId)}"
        return readMetadataAndContent(
            metadataUri,
            "$metadataUri/content",
            fallbackPath = itemId,
            knownEtag = knownEtag,
        )
    }

    override suspend fun list(prefix: String): List<StoredItem> {
        ensureApproot()
        val folder = normalize(prefix)
        val uri = if (folder.isEmpty()) {
            "$graphRoot/drives/$driveId/items/$approotId/children"
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
        val response = send(
            "PUT",
            itemByPathUri(path) + ":/content?@microsoft.graph.conflictBehavior=fail",
            content,
            ifNoneMatch = "*",
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
        val initialUri = "$graphRoot/drives/$driveId/items/$approotId/delta"
        var next: String? = cursor?.takeIf { it.isNotBlank() } ?: initialUri
        var reset = false
        val items = mutableListOf<StoredItem>()
        val deletions = mutableListOf<DeletedItem>()
        var finalCursor: String? = null
        while (!next.isNullOrBlank()) {
            val response = send("GET", next, null, null)
            if (response.statusCode() == 410 && !reset) {
                reset = true
                next = initialUri
                items.clear()
                deletions.clear()
                continue
            }
            check(response.statusCode() in 200..299) { "Graph delta ${response.statusCode()}" }
            val obj = Json.parseToJsonElement(String(response.body())).jsonObject
            for (element in obj["value"]?.jsonArray.orEmpty()) {
                val child = element.jsonObject
                val itemId = child["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val path = deltaPath(child)
                if (child["deleted"] != null || child["@removed"] != null) {
                    deletions += DeletedItem(itemId, path)
                    continue
                }
                if (child["file"] == null || path == null) {
                    continue
                }
                items += StoredItem(
                    path = path,
                    etag = readEtag(child),
                    itemId = itemId,
                    lastModifiedUtc = readTimestamp(child),
                    content = null,
                )
            }
            finalCursor = obj["@odata.deltaLink"]?.jsonPrimitive?.contentOrNull
            next = obj["@odata.nextLink"]?.jsonPrimitive?.contentOrNull
        }
        return DeltaPage(items, finalCursor, deletions, reset)
    }

    suspend fun resolveByItemId(driveItemId: String): ArtifactResolution? {
        ensureApproot()
        val response = send("GET", "$graphRoot/drives/$driveId/items/${encode(driveItemId)}", null, null)
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
        approotMutex.withLock {
            if (approotId != null && driveId != null) {
                return@withLock
            }
            val response = send("GET", "$graphRoot/me/drive/special/approot", null, null)
            check(response.statusCode() in 200..299) {
                "Graph approot ${response.statusCode()}: ${String(response.body())}"
            }
            val obj = Json.parseToJsonElement(String(response.body())).jsonObject
            approotId = obj["id"]?.jsonPrimitive?.content
            val parent = obj["parentReference"]?.jsonObject
            driveId = parent?.get("driveId")?.jsonPrimitive?.contentOrNull
            val parentPath = parent?.get("path")?.jsonPrimitive?.contentOrNull
            val approotName = obj["name"]?.jsonPrimitive?.contentOrNull
            if (parentPath != null && approotName != null) {
                approotPath = graphRelative(parentPath).trimEnd('/') + "/" + approotName
            }
            if (driveId == null) {
                val drive = send("GET", "$graphRoot/me/drive", null, null)
                check(drive.statusCode() in 200..299)
                driveId = Json.parseToJsonElement(String(drive.body())).jsonObject["id"]?.jsonPrimitive?.content
            }
            check(!approotId.isNullOrBlank() && !driveId.isNullOrBlank()) { "Graph approot ids missing" }
        }
    }

    private suspend fun readMetadataAndContent(
        metadataUri: String,
        contentUri: String,
        fallbackPath: String,
        knownEtag: String?,
    ): StoreReadResult {
        val metadataResponse = send(
            method = "GET",
            uri = metadataUri,
            body = null,
            ifMatch = null,
            ifNoneMatch = knownEtag,
        )
        if (metadataResponse.statusCode() == 304) {
            return StoreReadResult.NotModified
        }
        if (metadataResponse.statusCode() == 404) {
            return StoreReadResult.Missing
        }
        check(metadataResponse.statusCode() in 200..299) {
            "Graph metadata ${metadataResponse.statusCode()}"
        }
        val metadata = Json.parseToJsonElement(String(metadataResponse.body())).jsonObject
        val downloadUrl = metadata["@microsoft.graph.downloadUrl"]?.jsonPrimitive?.contentOrNull
        var contentResponse = if (downloadUrl != null) {
            download(downloadUrl)
        } else {
            send("GET", contentUri, null, null)
        }
        if (contentResponse.statusCode() in 300..399) {
            val location = contentResponse.headers().firstValue("Location").orElse(null)
                ?: error("Graph content redirect did not include Location")
            contentResponse = download(location)
        }
        if (contentResponse.statusCode() == 404) {
            return StoreReadResult.Missing
        }
        check(contentResponse.statusCode() in 200..299) {
            "Graph content ${contentResponse.statusCode()}"
        }
        val path = deltaPath(metadata) ?: normalize(fallbackPath)
        return StoreReadResult.Found(
            StoredItem(
                path = path,
                etag = readEtag(metadata),
                itemId = metadata["id"]?.jsonPrimitive?.contentOrNull ?: fallbackPath,
                lastModifiedUtc = readTimestamp(metadata),
                content = contentResponse.body(),
            ),
        )
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

    private fun deltaPath(item: JsonObject): String? {
        val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val parentPath = item["parentReference"]?.jsonObject
            ?.get("path")?.jsonPrimitive?.contentOrNull
        if (parentPath.isNullOrBlank()) {
            return normalize(name)
        }
        val fullPath = (graphRelative(parentPath).trimEnd('/') + "/" + name).trimStart('/')
        val root = approotPath?.trim('/')
        if (!root.isNullOrBlank() && (fullPath == root || fullPath.startsWith("$root/"))) {
            return fullPath.removePrefix(root).trimStart('/')
        }
        val protocolRoots = listOf("agents", "commands", "responses", "conversations", "activity", "telemetry")
        val segments = fullPath.split('/')
        val rootIndex = segments.indexOfFirst { it in protocolRoots }
        return if (rootIndex >= 0) {
            segments.drop(rootIndex).joinToString("/")
        } else {
            normalize(name)
        }
    }

    private fun graphRelative(path: String): String =
        path.substringAfter("root:", path).trim('/')

    private suspend fun send(
        method: String,
        uri: String,
        body: ByteArray?,
        ifMatch: String?,
        ifNoneMatch: String? = null,
    ): HttpResponse<ByteArray> {
        require(sameOrigin(URI.create(uri), URI.create(graphRoot))) {
            "Refusing to send a Graph token to an unexpected origin"
        }
        var attempt = 0
        val started = System.nanoTime()
        while (true) {
            val token = tokenProvider()
            val builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $token")
            if (ifMatch != null) {
                builder.header("If-Match", quoteEtag(ifMatch))
            }
            if (ifNoneMatch != null) {
                builder.header(
                    "If-None-Match",
                    if (ifNoneMatch == "*") "*" else quoteEtag(ifNoneMatch),
                )
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
            if ((response.statusCode() != 429 && response.statusCode() != 503) || attempt >= maxRetries) {
                metrics.recordOperation(
                    OperationMetric(
                        operation = metricOperation(method, uri),
                        result = metricResult(response.statusCode()),
                        durationMs = (System.nanoTime() - started) / 1_000_000,
                        retryCount = attempt,
                    ),
                )
                return response
            }
            val retryAfter = response.headers().firstValue("Retry-After").orElse(null)
            val delayMs = retryDelayMillis(retryAfter, java.time.Instant.now())
                ?: (1L shl attempt) * 1000
            delayProvider(delayMs.coerceIn(1_000, 30_000))
            attempt++
        }
    }

    private suspend fun download(uri: String): HttpResponse<ByteArray> {
        val target = URI.create(uri)
        require(
            target.scheme.equals("https", ignoreCase = true) ||
                (target.scheme.equals("http", ignoreCase = true) &&
                    target.host in setOf("127.0.0.1", "localhost", "::1")),
        ) { "Refusing an insecure Graph download URL" }
        val request = HttpRequest.newBuilder(target)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val started = System.nanoTime()
        val response = withContext(Dispatchers.IO) {
            http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        }
        metrics.recordOperation(
            OperationMetric(
                operation = MetricOperation.GRAPH_READ,
                result = metricResult(response.statusCode()),
                durationMs = (System.nanoTime() - started) / 1_000_000,
            ),
        )
        return response
    }

    private fun itemByPathUri(path: String): String {
        val escaped = normalize(path).split('/').joinToString("/") { encode(it) }
        return "$graphRoot/drives/$driveId/items/$approotId:/$escaped"
    }

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

    private fun normalize(path: String): String {
        val segments = path.replace('\\', '/').trimStart('/').split('/').filter { it.isNotEmpty() }
        require(segments.none { it == "." || it == ".." || '\u0000' in it }) {
            "Invalid App Folder path: $path"
        }
        return segments.joinToString("/")
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun sameOrigin(left: URI, right: URI): Boolean =
        left.scheme.equals(right.scheme, ignoreCase = true) &&
            left.host.equals(right.host, ignoreCase = true) &&
            effectivePort(left) == effectivePort(right)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun metricOperation(method: String, uri: String): MetricOperation = when {
        uri.contains("/delta") -> MetricOperation.GRAPH_DELTA
        method == "GET" -> MetricOperation.GRAPH_READ
        method == "DELETE" -> MetricOperation.GRAPH_DELETE
        else -> MetricOperation.GRAPH_WRITE
    }

    private fun metricResult(status: Int): MetricResult = when {
        status in 200..299 -> MetricResult.SUCCESS
        status in 300..399 && status != 304 -> MetricResult.REDIRECT
        status == 304 -> MetricResult.NOT_MODIFIED
        status == 404 -> MetricResult.NOT_FOUND
        status == 409 || status == 412 -> MetricResult.CONFLICT
        status == 429 -> MetricResult.THROTTLED
        status == 503 -> MetricResult.UNAVAILABLE
        status in 400..499 -> MetricResult.CLIENT_ERROR
        else -> MetricResult.SERVER_ERROR
    }

    companion object {
        private const val GRAPH = "https://graph.microsoft.com/v1.0"

        private fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

        internal fun retryDelayMillis(raw: String?, now: java.time.Instant): Long? {
            raw ?: return null
            raw.toLongOrNull()?.let { return it * 1000 }
            return runCatching {
                val retryAt = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                java.time.Duration.between(now, retryAt).toMillis().coerceAtLeast(1_000)
            }.getOrNull()
        }
    }
}

data class ArtifactResolution(
    val driveItemId: String,
    val name: String,
    val sizeBytes: Long?,
    val createdUtc: Instant?,
    val webUrl: String?,
)
