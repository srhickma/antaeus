package io.pleo.antaeus.core.time

import java.time.Duration
import java.time.ZonedDateTime
import java.time.Clock as JavaClock

class Clock {
    private var clock: JavaClock = JavaClock.systemDefaultZone()
    private val watchers: MutableList<ClockWatcher> = mutableListOf()

    fun watch(watcher: ClockWatcher) {
        watchers.add(watcher)
    }

    @Synchronized
    fun currentTime(): ZonedDateTime {
        return ZonedDateTime.now(clock)
    }

    @Synchronized
    fun advance(offset: Duration) {
        clock = JavaClock.offset(clock, offset)
        for (watcher in watchers) {
            watcher.timeChanged()
        }
    }
}
