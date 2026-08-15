package dev.lantern.core

import dev.lantern.core.ulid.Ulid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UlidTest {

    @Test
    fun `generated values match the commandId pattern`() {
        repeat(32) {
            val id = Ulid.generate()
            assertTrue(Ulid.isValid(id), id)
        }
    }

    @Test
    fun `time component is monotonic for increasing timestamps`() {
        val a = Ulid.generate(nowMs = 1_000, entropy = ByteArray(10))
        val b = Ulid.generate(nowMs = 2_000, entropy = ByteArray(10))
        assertTrue(a < b)
        assertNotEquals(a, Ulid.generate())
        assertEquals(26, a.length)
    }
}
