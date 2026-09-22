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
    private val stopwatch = StopwatchState(scope)

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
    fun `stop marks stopwatch not running and preserves elapsed time`() {
        stopwatch.start()
        Thread.sleep(50)
        val stopped = stopwatch.stop()

        assertFalse(stopped.isRunning)
        assertTrue(stopped.elapsedSeconds >= 0)
        assertEquals(stopped, stopwatch.snapshot.value)
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
