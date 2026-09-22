package com.peetracker.app.ui.logging

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.peetracker.app.data.model.Volume

@Composable
fun LogEntryScreen(
    viewModel: LogEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(uiState.lastSubmittedAt) {
        if (uiState.lastSubmittedAt != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatElapsed(uiState.stopwatch.elapsedSeconds),
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(48.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.92f else 1f,
                label = "startStopScale"
            )

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onStartStopPressed()
                },
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale),
                shape = CircleShape,
                colors = if (uiState.stopwatch.isRunning) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
                interactionSource = interactionSource,
                enabled = !uiState.isSubmitting
            ) {
                Text(if (uiState.stopwatch.isRunning) "Stop" else "Start")
            }

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(24.dp))
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (uiState.isVolumeSheetVisible) {
        VolumePickerSheet(
            onVolumeSelected = { volume: Volume -> viewModel.onVolumeSelected(volume) },
            onDismissRequest = { viewModel.onVolumeSheetDismissed() }
        )
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
