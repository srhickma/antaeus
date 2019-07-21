package io.pleo.antaeus.core.time

import com.github.shyiko.skedule.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class CronJobTest {
    @Test
    fun `simple schedule advancement`() {
        val clock = Clock()
        val schedule = Schedule.every(1, ChronoUnit.MINUTES)

        var counter = 0L
        val initialTime = clock.currentTime()

        CronJob(schedule, clock) {
            ++counter
            assertEquals(counter, minutesBetween(initialTime, clock.currentTime()))
        }

        clock.advance(Duration.ofSeconds(59))
        assertEquals(0L, counter)

        Thread.sleep(2000)

        assertEquals(1L, counter)
        clock.advance(Duration.ofSeconds(58))
        assertEquals(1L, counter)

        Thread.sleep(2000)

        assertEquals(2L, counter)
        clock.advance(Duration.ofSeconds(58))
        assertEquals(2L, counter)

        Thread.sleep(2000)

        assertEquals(3L, counter)
        clock.advance(Duration.ofSeconds(58))
        assertEquals(3L, counter)

        Thread.sleep(2000)

        assertEquals(4L, counter)
    }

    @Test
    fun `advance past deadline`() {
        val clock = Clock()
        val schedule = Schedule.every(1, ChronoUnit.MINUTES)

        var counter = 0L
        val initialTime = clock.currentTime()

        CronJob(schedule, clock) {
            ++counter
            assertEquals(2 * counter, minutesBetween(initialTime, clock.currentTime()))
        }

        clock.advance(Duration.ofSeconds(120))

        assertEquals(1L, counter)

        clock.advance(Duration.ofSeconds(120))

        assertEquals(2L, counter)

        clock.advance(Duration.ofSeconds(120))

        assertEquals(3L, counter)

        clock.advance(Duration.ofSeconds(120))

        assertEquals(4L, counter)
    }

    @Test
    fun `rapid advancement`() {
        val clock = Clock()
        val schedule = Schedule.every(1, ChronoUnit.MINUTES)

        var counter = 0L

        CronJob(schedule, clock) {
            ++counter
        }

        for (i in 1..58) {
            clock.advance(Duration.ofSeconds(1))
        }

        assertEquals(0L, counter)

        Thread.sleep(2500)

        assertEquals(1L, counter)
    }

    @Test
    fun `periodic schedule`() {
        val clock = Clock()
        val schedule = Schedule.at(LocalTime.NOON).every(DayOfWeek.MONDAY)

        var counter = 0L

        CronJob(schedule, clock) {
            ++counter
        }

        val now = ZonedDateTime.now()
        val offset = secondsBetween(now, schedule.next(now))

        clock.advance(Duration.ofSeconds(offset - 2))
        assertEquals(0L, counter)

        Thread.sleep(2500)

        assertEquals(1L, counter)
        clock.advance(Duration.ofDays(7).minus(Duration.ofSeconds(2)))
        assertEquals(1L, counter)

        Thread.sleep(2500)

        assertEquals(2L, counter)
    }

    @Test
    fun `locking stress`() {
        val clock = Clock()
        val schedule = Schedule.every(1, ChronoUnit.MINUTES)

        var counter = 0L

        CronJob(schedule, clock) {
            ++counter
        }

        for (i in 1..(60 * 1000 * 2)) {
            clock.advance(Duration.ofMillis(1))
        }

        assertEquals(2L, counter)
    }

    private fun secondsBetween(a: ZonedDateTime, b: ZonedDateTime): Long {
        return Math.round(ChronoUnit.MILLIS.between(a, b) / 1000.0)
    }

    private fun minutesBetween(a: ZonedDateTime, b: ZonedDateTime): Long {
        return Math.round(ChronoUnit.MILLIS.between(a, b) / 60000.0)
    }
}
