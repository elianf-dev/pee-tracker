package com.peetracker.app.ui.logging

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.peetracker.app.data.model.Volume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumePickerSheet(
    onVolumeSelected: (Volume) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selected by remember { mutableStateOf(Volume.MEDIUM) }
    val options = listOf(Volume.LOW, Volume.MEDIUM, Volume.HIGH)

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Text(
            text = "How much?",
            modifier = Modifier.padding(16.dp)
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            options.forEachIndexed { index, volume ->
                val isSelected = selected == volume
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "volumeSegmentScale"
                )
                SegmentedButton(
                    modifier = Modifier.scale(scale),
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = {
                        selected = volume
                        onVolumeSelected(volume)
                    },
                    selected = isSelected
                ) {
                    Text(volume.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}
