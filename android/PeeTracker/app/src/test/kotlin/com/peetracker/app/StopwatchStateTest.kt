package com.peetracker.app

import com.peetracker.app.ui.logging.StopwatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchStateTest {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    // Both clocks are driven by hand so the assertions are exact rather than timing-dependent.
    private var fakeElapsed = 10_000L
    private var fakeWallClock = 1_700_000_000_000L

    private val stopwatch = StopwatchState(
        scope = scope,
        elapsedRealtimeMillis = { fakeElapsed },
        wallClockMillis = { fakeWallClock }
    )

    @After
    fun tearDown() {
        job.cancel()
    }

    @Test
    fun `initial state is stopped with zero elapsed time`() {
        val snapshot = stopwatch.snapshot.value
        assertFalse(snapshot.isRunning)
        assertEquals(0, snapshot.elapsedSeconds)
        assertNull(snapshot.startTimestamp)
    }

    @Test
    fun `start marks stopwatch running and records a start timestamp`() {
        stopwatch.start()
        val snapshot = stopwatch.snapshot.value
        assertTrue(snapshot.isRunning)
        assertNotNull(snapshot.startTimestamp)
    }

    @Test
    fun `start records the start timestamp from the wall clock`() {
        stopwatch.start()
        assertEquals(fakeWallClock, stopwatch.snapshot.value.startTimestamp?.time)
    }

    @Test
    fun `stop reports elapsed seconds measured on the monotonic clock`() {
        stopwatch.start()
        fakeElapsed += 3_000
        val stopped = stopwatch.stop()

        assertFalse(stopped.isRunning)
        assertEquals(3, stopped.elapsedSeconds)
        assertEquals(stopped, stopwatch.snapshot.value)
    }

    // Regression: elapsed time used to be derived from System.currentTimeMillis(), so an NTP
    // resync, a manual clock change or a DST transition mid-trip corrupted durationSeconds — and
    // a backwards jump made it negative. durationSeconds feeds the group's permanent
    // totalDurationSeconds aggregate, so that bad value would have stuck.
    @Test
    fun `a wall clock jump during a trip does not affect the measured duration`() {
        stopwatch.start()

        fakeWallClock -= 3_600_000 // clock jumps an hour backwards mid-trip
        fakeElapsed += 5_000

        val stopped = stopwatch.stop()
        assertEquals(5, stopped.elapsedSeconds)
        assertTrue(stopped.elapsedSeconds >= 0)
    }

    @Test
    fun `stop without a prior start is a no-op returning zero elapsed time`() {
        val stopped = stopwatch.stop()
        assertFalse(stopped.isRunning)
        assertEquals(0, stopped.elapsedSeconds)
    }

    @Test
    fun `reset clears the snapshot back to defaults`() {
        stopwatch.start()
        stopwatch.stop()
        stopwatch.reset()

        val snapshot = stopwatch.snapshot.value
        assertFalse(snapshot.isRunning)
        assertEquals(0, snapshot.elapsedSeconds)
        assertNull(snapshot.startTimestamp)
    }
}
