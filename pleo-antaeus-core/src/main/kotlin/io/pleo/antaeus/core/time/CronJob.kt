package io.pleo.antaeus.core.time

import com.github.shyiko.skedule.Schedule
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class CronJob(
        private val schedule: Schedule,
        private val clock: Clock,
        private val task: () -> Unit
) : ClockWatcher {
    private var next: ZonedDateTime
    private var waiter: Thread
    private val lock: Lock = ReentrantLock()

    init {
        clock.watch(this)
        next = schedule.next(clock.currentTime())

        waiter = thread { startWaiter() }
    }

    override fun timeChanged() {
        waiter.interrupt()
        checkTime()
    }

    private fun startWaiter() {
        while (true) {
            try {
                // This will not cause overflow, as long as no schedule has a period
                // of over 292 million years.
                Thread.sleep(ChronoUnit.MILLIS.between(next, clock.currentTime()))
            } catch (e: InterruptedException) {
                // We have been interrupted, re-calculate our sleep duration.
                continue
            }

            checkTime()
        }
    }

    private fun checkTime() {
        lock.lock()
        if (next < clock.currentTime()) {
            next = schedule.next(clock.currentTime())
            lock.unlock()

            task()
        } else {
            lock.unlock()
        }
    }
}
