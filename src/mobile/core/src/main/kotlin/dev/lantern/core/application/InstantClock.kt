package dev.lantern.core.application

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

fun interface InstantClock {
    fun now(): Instant

    companion object {
        val System: InstantClock = InstantClock { Clock.System.now() }
    }
}
