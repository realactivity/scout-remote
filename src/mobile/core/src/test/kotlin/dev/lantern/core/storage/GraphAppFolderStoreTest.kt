package dev.lantern.core.storage

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.lantern.core.metrics.InMemoryLanternMetrics
import dev.lantern.core.metrics.MetricResult
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class GraphAppFolderStoreTest {
    private val servers = mutableListOf<RecordingServer>()

    @AfterTest
    fun stopServers() {
        servers.forEach(RecordingServer::close)
    }

    @Test
    fun `conditional read downloads content once then returns not modified`() = runBlocking {
        val server = server()
        server.enqueue(200, APPROOTOOT)
        server.enqueue(200, itemJson("protocol.json", "item-protocol", "etag-1"))
        server.enqueue(200, """{"protocol":"lantern"}""")
        server.enqueue(304, "")
        val store = store(server)

        val first = assertIs<StoreReadResult.Found>(store.read("protocol.json"))
        val second = store.read("protocol.json", knownEtag = "etag-1")

        assertContentEquals("""{"protocol":"lantern"}""".encodeToByteArray(), first.item.content)
        assertEquals(StoreReadResult.NotModified, second)
        assertEquals(4, server.requests.size)
        assertEquals("\"etag-1\"", server.requests.last().headers["If-none-match"]?.single())
    }

    @Test
    fun `create-if-absent uses one conditional put after approot`() = runBlocking {
        val server = server()
        server.enqueue(200, APPROOTOOT)
        server.enqueue(412, "")
        val store = store(server)

        val result = store.createIfAbsent("commands/inbox/a.json", "{}".encodeToByteArray())

        assertNull(result)
        assertEquals(2, server.requests.size)
        assertEquals("PUT", server.requests.last().method)
        assertEquals("*", server.requests.last().headers["If-none-match"]?.single())
    }

    @Test
    fun `item id read uses direct endpoint and skips unchanged content`() = runBlocking {
        val server = server()
        server.enqueue(200, APPROOTOOT)
        server.enqueue(200, itemJson("protocol.json", "item-protocol", "etag-1"))
        server.enqueue(200, """{"protocol":"lantern"}""")
        server.enqueue(304, "")
        val store = store(server)

        val first = store.readByItemId("item-protocol")
        val second = store.readByItemId("item-protocol", "etag-1")

        assertIs<StoreReadResult.Found>(first)
        assertEquals(StoreReadResult.NotModified, second)
        assertTrue(server.requests[1].path.contains("/items/item-protocol"))
        assertTrue(!server.requests[1].path.contains(":/"))
        assertEquals(1, server.requests.count { it.path.endsWith("/content") })
    }

    @Test
    fun `delta paginates and reconstructs nested paths and deletions`() = runBlocking {
        val server = server()
        val nextLink = "${server.baseUrl}/delta-page-2"
        val deltaLink = "${server.baseUrl}/delta-final"
        server.enqueue(200, APPROOTOOT)
        server.enqueue(
            200,
            """
            {
              "value": [{
                "file": {},
                "id": "status-1",
                "name": "status.json",
                "eTag": "\"etag-1\"",
                "lastModifiedDateTime": "2026-08-15T05:00:00Z",
                "parentReference": {"path": "/drive/root:/Apps/Lantern/agents/primary"}
              }],
              "@odata.nextLink": "$nextLink"
            }
            """.trimIndent(),
        )
        server.enqueue(
            200,
            """
            {
              "value": [{
                "id": "response-old",
                "name": "old.json",
                "deleted": {"state": "deleted"},
                "parentReference": {"path": "/drive/root:/Apps/Lantern/responses"}
              }],
              "@odata.deltaLink": "$deltaLink"
            }
            """.trimIndent(),
        )
        val store = store(server)

        val page = store.listDelta(null)

        assertEquals("agents/primary/status.json", page.items.single().path)
        assertEquals("responses/old.json", page.deletions.single().path)
        assertEquals(deltaLink, page.cursor)
        assertEquals(3, server.requests.size)
    }

    @Test
    fun `stale delta cursor resets from initial link`() = runBlocking {
        val server = server()
        val deltaLink = "${server.baseUrl}/delta-new"
        server.enqueue(200, APPROOTOOT)
        server.enqueue(410, "")
        server.enqueue(200, """{"value":[],"@odata.deltaLink":"$deltaLink"}""")
        val store = store(server)

        val page = store.listDelta("${server.baseUrl}/delta-stale")

        assertTrue(page.reset)
        assertEquals(deltaLink, page.cursor)
        assertTrue(server.requests.last().path.endsWith("/drives/drive-1/items/approot-1/delta"))
        assertEquals(3, server.requests.size)
    }

    @Test
    fun `retry records content-free throttling metrics`() = runBlocking {
        val server = server()
        val metrics = InMemoryLanternMetrics()
        val delays = mutableListOf<Long>()
        server.enqueue(200, APPROOTOOT)
        server.enqueue(503, "", mapOf("Retry-After" to "1"))
        server.enqueue(200, itemJson("status.json", "status-1", "etag-2"))
        val store = store(server, metrics = metrics, delayProvider = { delays += it })

        store.write("agents/primary/status.json", "payload".encodeToByteArray())

        assertEquals(listOf(1_000L), delays)
        val metric = metrics.operations.last()
        assertEquals(MetricResult.SUCCESS, metric.result)
        assertEquals(1, metric.retryCount)
        assertTrue(metrics.operations.none { it.toString().contains("status.json") })
    }

    private fun server(): RecordingServer = RecordingServer().also(servers::add)

    private fun store(
        server: RecordingServer,
        metrics: InMemoryLanternMetrics = InMemoryLanternMetrics(),
        delayProvider: suspend (Long) -> Unit = {},
    ): GraphAppFolderStore = GraphAppFolderStore(
        tokenProvider = { "synthetic-token" },
        metrics = metrics,
        graphRoot = server.baseUrl,
        delayProvider = delayProvider,
    )

    private fun itemJson(name: String, id: String, etag: String): String =
        """
        {
          "id": "$id",
          "name": "$name",
          "eTag": "\"$etag\"",
          "lastModifiedDateTime": "2026-08-15T05:00:00Z",
          "parentReference": {"path": "/drive/root:/Apps/Lantern"}
        }
        """.trimIndent()

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    )

    private data class StubResponse(
        val status: Int,
        val body: ByteArray,
        val headers: Map<String, String>,
    )

    private class RecordingServer : AutoCloseable {
        private val responses = ArrayDeque<StubResponse>()
        val requests = mutableListOf<RecordedRequest>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}/v1.0"

        init {
            server.executor = Executors.newCachedThreadPool()
            server.createContext("/") { exchange -> handle(exchange) }
            server.start()
        }

        fun enqueue(status: Int, body: String, headers: Map<String, String> = emptyMap()) {
            responses += StubResponse(status, body.encodeToByteArray(), headers)
        }

        private fun handle(exchange: HttpExchange) {
            val request = RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.toString(),
                headers = exchange.requestHeaders.entries.associate { it.key to it.value.toList() },
                body = exchange.requestBody.use { it.readAllBytes() },
            )
            synchronized(requests) { requests += request }
            val response = synchronized(responses) {
                check(responses.isNotEmpty()) { "Unexpected request ${request.method} ${request.path}" }
                responses.removeFirst()
            }
            response.headers.forEach(exchange.responseHeaders::add)
            exchange.sendResponseHeaders(response.status, response.body.size.toLong())
            exchange.responseBody.use { it.write(response.body) }
        }

        override fun close() {
            server.stop(0)
        }
    }

    companion object {
        private const val APPROOTOOT =
            """{"id":"approot-1","name":"Lantern","parentReference":{"driveId":"drive-1","path":"/drive/root:/Apps"}}"""
    }
}
