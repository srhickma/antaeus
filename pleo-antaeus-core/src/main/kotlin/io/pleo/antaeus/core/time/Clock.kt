package io.pleo.antaeus.core.time

import java.time.Duration
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Clock as JavaClock

@Singleton
class Clock @Inject constructor() {
    private val baseClock = JavaClock.systemDefaultZone()
    private var clock = JavaClock.systemDefaultZone()
    private var offset = Duration.ZERO
    private val watchers: MutableList<ClockWatcher> = mutableListOf()

    fun watch(watcher: ClockWatcher) {
        watchers.add(watcher)
    }

    fun unwatch(watcher: ClockWatcher) {
        watchers.remove(watcher)
    }

    @Synchronized
    fun currentTime(): ZonedDateTime {
        return ZonedDateTime.now(clock)
    }

    @Synchronized
    fun advance(offset: Duration) {
        this.offset = this.offset.plus(offset)
        clock = JavaClock.offset(baseClock, this.offset)
        for (watcher in watchers) {
            watcher.timeChanged()
        }
    }
}
