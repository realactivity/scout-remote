package dev.lantern.core.brand

import dev.lantern.core.domain.AgentState
import dev.lantern.core.domain.CommandStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class BrandTokensTest {

    @Test
    fun `paneStatus maps stale and unknown to offline`() {
        assertEquals(StatusToken.OFFLINE, paneStatus(AgentState.IDLE, stale = true))
        assertEquals(StatusToken.OFFLINE, paneStatus(AgentState.UNKNOWN, stale = false))
        assertEquals(StatusToken.IDLE, paneStatus(AgentState.IDLE, stale = false))
        assertEquals(StatusToken.WORKING, paneStatus(AgentState.WORKING, stale = false))
        assertEquals(StatusToken.WAITING, paneStatus(AgentState.WAITING_FOR_DESKTOP, stale = false))
    }

    @Test
    fun `commandPaneStatus stays on the semantic palette`() {
        assertEquals(StatusToken.IDLE, commandPaneStatus(CommandStatus.COMPLETED))
        assertEquals(StatusToken.WORKING, commandPaneStatus(CommandStatus.ACKNOWLEDGED))
        assertEquals(StatusToken.WORKING, commandPaneStatus(CommandStatus.RUNNING))
        assertEquals(StatusToken.WAITING, commandPaneStatus(CommandStatus.WAITING_FOR_DESKTOP_APPROVAL))
        assertEquals(StatusToken.ERROR, commandPaneStatus(CommandStatus.FAILED))
        assertEquals(StatusToken.OFFLINE, commandPaneStatus(CommandStatus.EXPIRED))
        assertEquals(StatusToken.OFFLINE, commandPaneStatus(CommandStatus.CANCELED))
    }

    @Test
    fun `fromJson reads docs brand-tokens`() {
        val tokens = BrandTokens.fromJson(readDocsTokens())
        assertEquals("#0D1826", tokens.midnight)
        assertEquals("#EDE6D6", tokens.bone)
        assertEquals("#FFC96B", tokens.candle)
        assertEquals("#4CAF7D", tokens.idle)
        assertEquals("Signals, not vibes.", tokens.tagline)
        assertEquals("#E05252", tokens.statusHex(StatusToken.ERROR))
    }

    private fun readDocsTokens(): String {
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isRegularFile(dir.resolve("docs/brand-tokens.json"))) {
            dir = dir.parent ?: error("docs/brand-tokens.json not found")
        }
        return String(Files.readAllBytes(dir.resolve("docs/brand-tokens.json")))
    }
}
