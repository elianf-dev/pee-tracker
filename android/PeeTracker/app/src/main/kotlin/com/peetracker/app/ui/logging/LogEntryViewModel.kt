package com.peetracker.app.ui.logging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peetracker.app.data.model.Volume
import com.peetracker.app.data.remote.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class LogEntryUiState(
    val stopwatch: StopwatchSnapshot = StopwatchSnapshot(),
    val isVolumeSheetVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val lastSubmittedAt: Long? = null
)

@HiltViewModel
class LogEntryViewModel @Inject constructor(
    private val logRepository: LogRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId")
        ?: error("LogEntryViewModel requires a groupId nav argument")

    private val stopwatch = StopwatchState(viewModelScope)

    private val _uiState = MutableStateFlow(LogEntryUiState())
    val uiState: StateFlow<LogEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stopwatch.snapshot.collect { snapshot ->
                _uiState.value = _uiState.value.copy(stopwatch = snapshot)
            }
        }
    }

    fun onStartStopPressed() {
        if (_uiState.value.stopwatch.isRunning) {
            stopwatch.stop()
            _uiState.value = _uiState.value.copy(isVolumeSheetVisible = true)
        } else {
            stopwatch.start()
        }
    }

    fun onVolumeSelected(volume: Volume) {
        val snapshot = _uiState.value.stopwatch
        val startTimestamp = snapshot.startTimestamp ?: Date()

        _uiState.value = _uiState.value.copy(isVolumeSheetVisible = false, isSubmitting = true)

        viewModelScope.launch {
            runCatching {
                logRepository.submitLog(
                    groupId = groupId,
                    startTimestamp = startTimestamp,
                    durationSeconds = snapshot.elapsedSeconds,
                    volume = volume
                )
            }.onSuccess {
                stopwatch.reset()
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    lastSubmittedAt = System.currentTimeMillis()
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = throwable.message ?: "Failed to save log"
                )
            }
        }
    }

    fun onVolumeSheetDismissed() {
        if (_uiState.value.isVolumeSheetVisible) {
            onVolumeSelected(Volume.MEDIUM)
        }
    }
}
