package com.peetracker.app.ui.logging

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

class StopwatchState(private val scope: CoroutineScope) {

    private val _snapshot = MutableStateFlow(StopwatchSnapshot())
    val snapshot: StateFlow<StopwatchSnapshot> = _snapshot.asStateFlow()

    private var startElapsedRealtime: Long = 0
    private var tickerJob: Job? = null

    fun start() {
        if (_snapshot.value.isRunning) return
        startElapsedRealtime = System.currentTimeMillis()
        _snapshot.value = StopwatchSnapshot(
            isRunning = true,
            elapsedSeconds = 0,
            startTimestamp = Date(startElapsedRealtime)
        )
        tickerJob = scope.launch {
            while (_snapshot.value.isRunning) {
                val elapsed = (System.currentTimeMillis() - startElapsedRealtime) / 1000
                _snapshot.value = _snapshot.value.copy(elapsedSeconds = elapsed)
                delay(200)
            }
        }
    }

    fun stop(): StopwatchSnapshot {
        tickerJob?.cancel()
        tickerJob = null
        val finalElapsed = if (_snapshot.value.startTimestamp != null) {
            (System.currentTimeMillis() - startElapsedRealtime) / 1000
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
