package io.pleo.antaeus.core.time

import com.github.shyiko.skedule.Schedule
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class CronJob(
        private val schedule: Schedule,
        private val clock: Clock,
        private val task: () -> Unit
) : ClockWatcher {
    private var next: ZonedDateTime
    private var waiter: AtomicReference<Thread>
    private val lock: Lock = ReentrantLock()

    init {
        clock.watch(this)
        next = schedule.next(clock.currentTime())

        // It is OK to use heavyweight threads, since all schedules have periods of
        // at least one minute. The cost of creating a thread is negligible if it
        // happens at most once per minute (per job, ignoring advancements).
        waiter = AtomicReference(thread { startWaiter() })
    }

    override fun timeChanged() {
        // Interrupt the waiter and start a new one, since the target time
        // has changed and the sleep duration is inconsistent.
        waiter.getAndSet(thread { startWaiter() }).interrupt()

        checkTime()
    }

    private fun startWaiter() {
        try {
            // This will not cause overflow, as long as no schedule has a period
            // of over 292 million years.
            Thread.sleep(ChronoUnit.MILLIS.between(clock.currentTime(), next))
        } catch (e: InterruptedException) {
            // We have been interrupted, gracefully exit.
            return
        }

        checkTime()
    }

    private fun checkTime() {
        lock.lock()
        if (ChronoUnit.MILLIS.between(clock.currentTime(), next) <= 0L) {
            next = schedule.next(clock.currentTime())
            if (ChronoUnit.MILLIS.between(clock.currentTime(), next) <= 0L) {
                // If this schedule is not purely periodic, add a second to ensure the
                // next target is not still the current one. This is NOT safe for pure
                // periodic schedules (e.g. 1 min => every 61 seconds).
                next = schedule.next(clock.currentTime().plusSeconds(1))
            }

            lock.unlock()

            // Start a new waiter.
            waiter.getAndSet(thread { startWaiter() })

            task()
        } else {
            lock.unlock()
        }
    }
}
