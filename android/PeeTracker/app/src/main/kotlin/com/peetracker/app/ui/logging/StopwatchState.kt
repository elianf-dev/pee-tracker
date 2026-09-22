package com.peetracker.app.ui.logging

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

data class StopwatchSnapshot(
    val isRunning: Boolean = false,
    val elapsedSeconds: Long = 0,
    val startTimestamp: Date? = null
)

class StopwatchState(
    private val scope: CoroutineScope,
    // Elapsed time is measured on a monotonic clock, never the wall clock: durationSeconds ends
    // up in the group's permanent totalDurationSeconds aggregate, and System.currentTimeMillis()
    // jumps on NTP resync, a manual clock change, or a DST transition that lands mid-trip —
    // backwards jumps produce negative durations. startTimestamp genuinely wants the wall clock,
    // since dateKeyLocal/weekKeyLocal are derived from it, so the two sources stay separate.
    // Both are injectable so the JVM unit tests don't need the Android framework.
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClockMillis: () -> Long = { System.currentTimeMillis() }
) {

    private val _snapshot = MutableStateFlow(StopwatchSnapshot())
    val snapshot: StateFlow<StopwatchSnapshot> = _snapshot.asStateFlow()

    private var startElapsed: Long = 0
    private var tickerJob: Job? = null

    fun start() {
        if (_snapshot.value.isRunning) return
        startElapsed = elapsedRealtimeMillis()
        _snapshot.value = StopwatchSnapshot(
            isRunning = true,
            elapsedSeconds = 0,
            startTimestamp = Date(wallClockMillis())
        )
        tickerJob = scope.launch {
            while (_snapshot.value.isRunning) {
                val elapsed = (elapsedRealtimeMillis() - startElapsed) / 1000
                _snapshot.value = _snapshot.value.copy(elapsedSeconds = elapsed)
                delay(200)
            }
        }
    }

    fun stop(): StopwatchSnapshot {
        tickerJob?.cancel()
        tickerJob = null
        val finalElapsed = if (_snapshot.value.startTimestamp != null) {
            (elapsedRealtimeMillis() - startElapsed) / 1000
        } else {
            0
        }
        val finalSnapshot = _snapshot.value.copy(isRunning = false, elapsedSeconds = finalElapsed)
        _snapshot.value = finalSnapshot
        return finalSnapshot
    }

    fun reset() {
        tickerJob?.cancel()
        tickerJob = null
        _snapshot.value = StopwatchSnapshot()
    }
}
