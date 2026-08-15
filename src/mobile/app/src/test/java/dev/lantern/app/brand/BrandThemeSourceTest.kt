package dev.lantern.app.brand

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandThemeSourceTest {

    @Test
    fun `generated copies match docs brand-tokens json`() {
        val docs = readDocs()
        val copies = listOf(
            Path.of(System.getProperty("user.dir")).resolve("src/main/assets/brand-tokens.json"),
            Path.of(System.getProperty("user.dir")).resolve("src/test/resources/brand-tokens.json"),
        ).filter { Files.isRegularFile(it) }
        assertTrue(copies.isNotEmpty(), "copyBrandTokens should produce at least one generated copy")
        for (copy in copies) {
            assertEquals(docs, String(Files.readAllBytes(copy)), "$copy must match docs/brand-tokens.json")
        }
        val tokens = BrandTokens.fromJson(docs)
        assertEquals("#0D1826", tokens.midnight)
        assertEquals("Signals, not vibes.", tokens.tagline)
    }

    private fun readDocs(): String {
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isRegularFile(dir.resolve("docs/brand-tokens.json"))) {
            dir = dir.parent ?: error("docs/brand-tokens.json not found")
        }
        return String(Files.readAllBytes(dir.resolve("docs/brand-tokens.json")))
    }
}
