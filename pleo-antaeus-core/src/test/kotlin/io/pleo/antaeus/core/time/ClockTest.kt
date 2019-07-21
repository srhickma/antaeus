package io.pleo.antaeus.core.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ClockTest {
    @Test
    fun `current time`() {
        val clock = Clock()

        assertEquals(0, ChronoUnit.SECONDS.between(ZonedDateTime.now(), clock.currentTime()))

        Thread.sleep(2000)

        assertEquals(0, ChronoUnit.SECONDS.between(ZonedDateTime.now(), clock.currentTime()))
    }

    @Test
    fun `advance time`() {
        val clock = Clock()

        val initialTime = clock.currentTime()

        clock.advance(Duration.ofHours(20).plus(Duration.ofMinutes(4)))

        assertEquals(20 * 60 + 4, ChronoUnit.MINUTES.between(initialTime, clock.currentTime()))

        clock.advance(Duration.ofMinutes(56))

        assertEquals(21 * 60, ChronoUnit.MINUTES.between(initialTime, clock.currentTime()))
    }

    @Test
    fun `watch time`() {
        val clock = Clock()

        var called = false
        val initialTime = clock.currentTime()

        clock.watch(object : ClockWatcher {
            override fun timeChanged() {
                called = true
                assertEquals(16, ChronoUnit.HOURS.between(initialTime, clock.currentTime()))
            }
        })

        clock.advance(Duration.ofHours(16))

        assertTrue(called)
    }
}
