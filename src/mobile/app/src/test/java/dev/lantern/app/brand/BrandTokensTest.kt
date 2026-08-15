package dev.lantern.app.brand

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandTokensTest {

    @Test
    fun `theme tokens match docs brand-tokens json`() {
        val json = readTokensJson()
        val tokens = BrandTokens.fromJson(json)
        assertEquals("#0D1826", tokens.midnight)
        assertEquals("#16273B", tokens.steeple)
        assertEquals("#EDE6D6", tokens.bone)
        assertEquals("#FFC96B", tokens.candle)
        assertEquals("#4CAF7D", tokens.idle)
        assertEquals("#5B9BD9", tokens.working)
        assertEquals("#E8961E", tokens.waiting)
        assertEquals("#7C8794", tokens.offline)
        assertEquals("#E05252", tokens.error)
        assertEquals("Signals, not vibes.", tokens.tagline)
        assertTrue(json.contains("\"hex\": \"${tokens.midnight}\""))
    }

    @Test
    fun `proposed command mappings stay on the semantic palette`() {
        val tokens = BrandTokens.fromJson(readTokensJson())
        val colors = setOf(tokens.idle, tokens.working, tokens.waiting, tokens.offline, tokens.error)
        assertEquals(5, colors.size)
    }

    private fun readTokensJson(): String {
        val fromResources = javaClass.classLoader?.getResourceAsStream("brand-tokens.json")
        if (fromResources != null) {
            return fromResources.bufferedReader().readText()
        }
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isRegularFile(dir.resolve("docs/brand-tokens.json"))) {
            dir = dir.parent ?: error("docs/brand-tokens.json not found")
        }
        return String(Files.readAllBytes(dir.resolve("docs/brand-tokens.json")))
    }
}
